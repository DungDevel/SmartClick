package com.example.smartclick.app.tvconnection.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) : ITvCredentialStore {

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "tv_credentials_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveCredentials(deviceId: String, credentials: Credentials): String = withContext(Dispatchers.IO) {
        val ref = UUID.randomUUID().toString()
        val json = JSONObject()
        credentials.protocolValues.forEach { (key, value) ->
            json.put(key, value)
        }
        
        sharedPreferences.edit {
            putString("cred_${deviceId}_$ref", json.toString())
        }

        ref
    }

    override suspend fun getCredentials(deviceId: String, credentialsRef: String): Credentials? = withContext(Dispatchers.IO) {
        val jsonString = sharedPreferences.getString("cred_${deviceId}_$credentialsRef", null)
            ?: return@withContext null

        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.getString(key)
        }

        Credentials(map)
    }

    override suspend fun clearCredentials(deviceId: String, credentialsRef: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit {
            remove("cred_${deviceId}_$credentialsRef")
        }
    }
}
