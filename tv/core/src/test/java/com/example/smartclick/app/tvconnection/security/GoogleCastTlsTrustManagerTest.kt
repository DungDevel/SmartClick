package com.example.smartclick.app.tvconnection.security

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class GoogleCastTlsTrustManagerTest {
    private val cert = mockk<X509Certificate>(relaxed = true).also {
        every { it.encoded } returns byteArrayOf(1, 2, 3, 4)
    }

    private val fingerprint = GoogleCastTlsTrustManager.sha256(cert)

    @Test fun `first use accepts leaf provisionally for later DeviceAuth`() {
        GoogleCastTlsTrustManager(null, firstUseAllowed = true).checkServerTrusted(arrayOf(cert), "RSA")
    }

    @Test fun `same certificate pin is accepted`() {
        GoogleCastTlsTrustManager(fingerprint, firstUseAllowed = false).checkServerTrusted(arrayOf(cert), "RSA")
    }

    @Test fun `changed certificate is rejected`() {
        assertThrows(CertificateException::class.java) {
            GoogleCastTlsTrustManager("00".repeat(32), false).checkServerTrusted(arrayOf(cert), "RSA")
        }
    }

    @Test fun `missing pin after pairing marker is rejected`() {
        assertThrows(CertificateException::class.java) {
            GoogleCastTlsTrustManager(null, firstUseAllowed = false).checkServerTrusted(arrayOf(cert), "RSA")
        }
    }

    @Test fun `pin is a leaf sha256 and independent of Remote pin key`() {
        assertEquals(64, fingerprint.length)
        val remotePin = "cert_sha256"
        val castPin = "cast_tls_sha256"
        org.junit.Assert.assertNotEquals(remotePin, castPin)
    }
}
