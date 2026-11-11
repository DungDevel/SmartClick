package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.ITvCredentialStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTvCastCredentialPersistenceTest {
    @Test
    fun `Cast pin merge preserves existing Remote and unrelated values`() = runTest {
        val original = mapOf("cert_sha256" to "remote-pin", "other" to "kept")
        val store = FakeCredentialStore(Credentials(original))
        val driver = AndroidTvDriver(null, store)

        driver.persistCastTlsPin("device", "ref", "cast-pin")

        assertEquals("remote-pin", store.record?.protocolValues?.get("cert_sha256"))
        assertEquals("kept", store.record?.protocolValues?.get("other"))
        assertEquals("cast-pin", store.record?.protocolValues?.get(AndroidTvDriver.CAST_TLS_SHA256))
        assertEquals("true", store.record?.protocolValues?.get(AndroidTvDriver.CAST_TLS_PIN_ESTABLISHED))
        assertEquals(1, store.readCount)
    }

    @Test
    fun `Cast pin update keeps credential values added since controller creation`() = runTest {
        val initial = mapOf("cert_sha256" to "remote-pin")
        val store = FakeCredentialStore(Credentials(initial)).apply {
            record = Credentials(initial + ("added_later" to "keep-this"))
        }
        val driver = AndroidTvDriver(null, store)

        driver.persistCastTlsPin("device", "ref", "cast-pin")

        assertEquals("keep-this", store.record?.protocolValues?.get("added_later"))
        assertEquals(1, store.readCount)
    }

    @Test
    fun `deleted credential record is not recreated while persisting Cast pin`() = runTest {
        val store = FakeCredentialStore(null)
        val driver = AndroidTvDriver(null, store)

        val failure = runCatching {
            driver.persistCastTlsPin("device", "ref", "cast-pin")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("no longer exists"))
        assertEquals(1, store.readCount)
        assertEquals(0, store.updateCount)
    }

    private class FakeCredentialStore(var record: Credentials?) : ITvCredentialStore {
        var updateCount = 0
        var readCount = 0

        override suspend fun saveCredentials(deviceId: String, credentials: Credentials) = "ref"
        override suspend fun getCredentials(deviceId: String, credentialsRef: String): Credentials? {
            readCount++
            return record
        }
        override suspend fun updateCredentials(deviceId: String, credentialsRef: String, credentials: Credentials): Boolean {
            updateCount++
            if (record == null) return false
            record = credentials
            return true
        }
        override suspend fun clearCredentials(deviceId: String, credentialsRef: String) { record = null }
    }
}
