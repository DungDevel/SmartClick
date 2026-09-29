package com.example.smartclick.app.tvconnection.androidtv

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

class AndroidTvPairingSessionTest {

    @Test
    fun testGenerateSecret() {
        val clientCert = mockk<X509Certificate>()
        val serverCert = mockk<X509Certificate>()

        val clientKey = mockk<RSAPublicKey>()
        val serverKey = mockk<RSAPublicKey>()

        every { clientCert.publicKey } returns clientKey
        every { serverCert.publicKey } returns serverKey

        // Dummy modulus and exponent for testing
        val clientModulus = BigInteger("123456789")
        val clientExponent = BigInteger("65537")

        val serverModulus = BigInteger("987654321")
        val serverExponent = BigInteger("65537")

        every { clientKey.modulus } returns clientModulus
        every { clientKey.publicExponent } returns clientExponent

        every { serverKey.modulus } returns serverModulus
        every { serverKey.publicExponent } returns serverExponent

        val pin = "1A2B3C"

        val hash = AndroidTvPairingSession.generateSecret(clientCert, serverCert, pin)

        // Calculate expected hash directly to verify independent of session
        val md = java.security.MessageDigest.getInstance("SHA-256")

        md.update(clientModulus.toByteArray().let { if (it[0].toInt() == 0 && it.size > 1) it.copyOfRange(1, it.size) else it })

        val clientExpStr = clientExponent.toString(16).uppercase()
        val paddedClientExp = if (("0$clientExpStr").length % 2 != 0) "00$clientExpStr" else "0$clientExpStr"
        md.update(hexToBytes(paddedClientExp))

        md.update(serverModulus.toByteArray().let { if (it[0].toInt() == 0 && it.size > 1) it.copyOfRange(1, it.size) else it })

        val serverExpStr = serverExponent.toString(16).uppercase()
        val paddedServerExp = if (("0$serverExpStr").length % 2 != 0) "00$serverExpStr" else "0$serverExpStr"
        md.update(hexToBytes(paddedServerExp))

        md.update(hexToBytes("2B3C")) // substring(2) -> 2B3C

        val expectedHash = md.digest()

        assertEquals(expectedHash.toList(), hash.toList())
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
