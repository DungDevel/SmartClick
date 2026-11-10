package com.example.smartclick.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

class MuteStateStore(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun setMuteState(deviceId: String, state: Int) {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("mute_$deviceId")] = state
        }
    }

    suspend fun getMuteState(deviceId: String): Int? {
        return try {
            dataStore.data.first()[intPreferencesKey("mute_$deviceId")]
        } catch (e: Exception) {
            null
        }
    }
}
