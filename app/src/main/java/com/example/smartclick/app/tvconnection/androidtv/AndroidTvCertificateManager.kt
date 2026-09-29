package com.example.smartclick.app.tvconnection.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

object AndroidTvCertificateManager {
    private const val ALIAS = "AndroidTvRemoteClientCert"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun getOrGenerateClientKeyAndCertificate(): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (!keyStore.containsAlias(ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
            val parameterSpec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setCertificateSubject(X500Principal("CN=SmartClick_AndroidTvRemote"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build()
            keyPairGenerator.initialize(parameterSpec)
            keyPairGenerator.generateKeyPair()
        }

        val privateKey = keyStore.getKey(ALIAS, null) as PrivateKey
        val certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        return Pair(privateKey, certificate)
    }
}
