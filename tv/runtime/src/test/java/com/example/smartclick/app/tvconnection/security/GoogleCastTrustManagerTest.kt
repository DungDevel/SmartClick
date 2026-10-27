package com.example.smartclick.app.tvconnection.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CertificatePolicies
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.PolicyInformation
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date

class GoogleCastTrustManagerTest {

    private lateinit var officialRoots: List<X509Certificate>

    @Before
    fun setup() {
        org.bouncycastle.jce.provider.BouncyCastleProvider().let {
            if (java.security.Security.getProvider(it.name) == null) {
                java.security.Security.addProvider(it)
            }
        }
        officialRoots = GoogleCastRootCertificates.getDefaultRoots()
    }

    @Test
    fun `1 - official roots load successfully and match SHA-256 fingerprints`() {
        assertNotNull(officialRoots)
        assertEquals(2, officialRoots.size)

        val castRoot = officialRoots[0]
        val eurekaRoot = officialRoots[1]

        val castHash = sha256Hex(castRoot.encoded)
        val eurekaHash = sha256Hex(eurekaRoot.encoded)

        assertEquals(GoogleCastRootCertificates.CAST_ROOT_SHA256, castHash)
        assertEquals(GoogleCastRootCertificates.EUREKA_ROOT_SHA256, eurekaHash)
    }

    @Test
    fun `2 - empty trust store fails closed`() {
        val trustManager = GoogleCastTrustManager(emptyList())
        val dummyKpg = KeyPairGenerator.getInstance("RSA")
        dummyKpg.initialize(2048)
        val kp = dummyKpg.generateKeyPair()

        val builder = JcaX509v3CertificateBuilder(
            X500Name("CN=Test"), BigInteger.ONE,
            Date(System.currentTimeMillis() - 10000), Date(System.currentTimeMillis() + 10000),
            X500Name("CN=Test"), kp.public
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(builder.build(signer))

        val chain = arrayOf(cert)

        val exception = assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(chain, "RSA")
        }
        assertTrue(exception.message!!.contains("empty trust store") || exception.message!!.contains("Security policy violation"))
    }

    @Test
    fun `3 - attacker-created self-signed certificate is rejected by official roots`() {
        val attackerKpg = KeyPairGenerator.getInstance("RSA")
        attackerKpg.initialize(2048)
        val attackerPair = attackerKpg.generateKeyPair()

        val attackerSubject = X500Name("CN=Attacker Device")
        val attackerBuilder = JcaX509v3CertificateBuilder(
            attackerSubject, BigInteger.valueOf(99),
            Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24),
            Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365),
            attackerSubject, attackerPair.public
        )
        attackerBuilder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val attackerSigner = JcaContentSignerBuilder("SHA256withRSA").build(attackerPair.private)
        val attackerCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(attackerBuilder.build(attackerSigner))

        val trustManager = GoogleCastTrustManager(officialRoots)
        val chain = arrayOf(attackerCert)

        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(chain, "RSA")
        }
    }

    @Test
    fun `4 - root certificate cannot be accepted as a device leaf merely because it is a root`() {
        val trustManager = GoogleCastTrustManager(officialRoots)
        val castRoot = officialRoots[0]
        val chain = arrayOf(castRoot)

        val exception = assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(chain, "RSA")
        }
        assertTrue(exception.message!!.contains("Root certificate cannot be accepted as a leaf"))
    }

    @Test
    fun `5 - leaf certificate missing digitalSignature key usage is rejected`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val root = officialRoots[0]

        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            X500Name.getInstance(root.subjectX500Principal.encoded),
            BigInteger.valueOf(123),
            Date(now - 100000), Date(now + 100000),
            X500Name("CN=Invalid Leaf"), kp.public
        )
        val leafCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(kpg.generateKeyPair().private)))

        val trustManager = GoogleCastTrustManager(officialRoots)
        val chain = arrayOf(leafCert)

        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(chain, "RSA")
        }
    }

    @Test
    fun `6 - chain exceeding path-length constraint is rejected`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val rootKp = kpg.generateKeyPair()
        val int1Kp = kpg.generateKeyPair()
        val int2Kp = kpg.generateKeyPair()
        val int3Kp = kpg.generateKeyPair()
        val leafKp = kpg.generateKeyPair()

        val rootName = X500Name("CN=Test Root")
        val int1Name = X500Name("CN=Test Int 1")
        val int2Name = X500Name("CN=Test Int 2")
        val int3Name = X500Name("CN=Test Int 3")
        val leafName = X500Name("CN=Test Leaf")

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 100000)
        val notAfter = Date(now + 100000)

        // Root with pathLen = 2
        val rootBuilder = JcaX509v3CertificateBuilder(rootName, BigInteger.ONE, notBefore, notAfter, rootName, rootKp.public)
        rootBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(2))
        val rootCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(rootBuilder.build(JcaContentSignerBuilder("SHA256withRSA").build(rootKp.private)))

        // Int 1
        val int1Builder = JcaX509v3CertificateBuilder(rootName, BigInteger.valueOf(2), notBefore, notAfter, int1Name, int1Kp.public)
        int1Builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val int1Cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(int1Builder.build(JcaContentSignerBuilder("SHA256withRSA").build(rootKp.private)))

        // Int 2
        val int2Builder = JcaX509v3CertificateBuilder(int1Name, BigInteger.valueOf(3), notBefore, notAfter, int2Name, int2Kp.public)
        int2Builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val int2Cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(int2Builder.build(JcaContentSignerBuilder("SHA256withRSA").build(int1Kp.private)))

        // Int 3 (3rd intermediate, exceeding pathLen=2)
        val int3Builder = JcaX509v3CertificateBuilder(int2Name, BigInteger.valueOf(4), notBefore, notAfter, int3Name, int3Kp.public)
        int3Builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val int3Cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(int3Builder.build(JcaContentSignerBuilder("SHA256withRSA").build(int2Kp.private)))

        // Leaf
        val leafBuilder = JcaX509v3CertificateBuilder(int3Name, BigInteger.valueOf(5), notBefore, notAfter, leafName, leafKp.public)
        leafBuilder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val leafCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(leafBuilder.build(JcaContentSignerBuilder("SHA256withRSA").build(int3Kp.private)))

        val trustManager = GoogleCastTrustManager(listOf(rootCert))
        val chain = arrayOf(leafCert, int3Cert, int2Cert, int1Cert) // 3 intermediates > pathLen 2

        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(chain, "RSA")
        }
    }

    @Test
    fun `7 - cast-specific certificate policy OID detection works correctly`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val rootKp = kpg.generateKeyPair()
        val leafKp = kpg.generateKeyPair()

        val rootName = X500Name("CN=Test Audio Root")
        val leafName = X500Name("CN=Test Audio Leaf")

        val now = System.currentTimeMillis()
        val rootBuilder = JcaX509v3CertificateBuilder(rootName, BigInteger.ONE, Date(now - 100000), Date(now + 100000), rootName, rootKp.public)
        rootBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(2))
        val rootCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(rootBuilder.build(JcaContentSignerBuilder("SHA256withRSA").build(rootKp.private)))

        // Leaf with audio-only policy OID 1.3.6.1.4.1.11129.2.5.2
        val leafBuilder = JcaX509v3CertificateBuilder(rootName, BigInteger.valueOf(2), Date(now - 100000), Date(now + 100000), leafName, leafKp.public)
        leafBuilder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))

        val policyInfo = PolicyInformation(org.bouncycastle.asn1.ASN1ObjectIdentifier(GoogleCastTrustManager.CAST_AUDIO_ONLY_POLICY_OID))
        val policies = CertificatePolicies(policyInfo)
        leafBuilder.addExtension(Extension.certificatePolicies, false, policies)

        val leafCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(leafBuilder.build(JcaContentSignerBuilder("SHA256withRSA").build(rootKp.private)))

        val trustManager = GoogleCastTrustManager(listOf(rootCert))
        val chainRootExcluded = arrayOf(leafCert)
        val chainRootIncluded = arrayOf(leafCert, rootCert)

        trustManager.checkServerTrusted(chainRootExcluded, "RSA")
        assertTrue(trustManager.isAudioOnlyDevice())

        trustManager.checkServerTrusted(chainRootIncluded, "RSA")
        assertTrue(trustManager.isAudioOnlyDevice())
    }

    @Test
    fun `8 - chain with unknown root issuer is rejected`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val builder = JcaX509v3CertificateBuilder(
            X500Name("CN=Unknown Issuer"), BigInteger.ONE,
            Date(System.currentTimeMillis() - 10000), Date(System.currentTimeMillis() + 10000),
            X500Name("CN=Test Leaf"), kp.public
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        val leafCert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private)))

        val trustManager = GoogleCastTrustManager(officialRoots)
        val chain = arrayOf(leafCert)

        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(chain, "RSA")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
