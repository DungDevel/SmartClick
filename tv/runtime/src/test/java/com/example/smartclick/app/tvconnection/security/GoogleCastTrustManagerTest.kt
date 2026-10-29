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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    @Test
    fun `9 - valid chain with trusted root included at the end is accepted`() {
        val chain = syntheticChain(rootPathLen = 1, intermediateCount = 0)
        GoogleCastTrustManager(listOf(chain.root)).checkServerTrusted(arrayOf(chain.leaf, chain.root), "RSA")
    }

    @Test
    fun `10 - valid chain with trusted root omitted is accepted`() {
        val chain = syntheticChain(rootPathLen = 1, intermediateCount = 0)
        GoogleCastTrustManager(listOf(chain.root)).checkServerTrusted(arrayOf(chain.leaf), "RSA")
    }

    @Test
    fun `11 - parallel validations do not share audio-only result`() {
        val rootPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rootName = X500Name("CN=Concurrent Root")
        val root = makeCertificate(rootName, rootName, rootPair.public, rootPair.private, 100, caPathLen = 1)
        val regularLeaf = makeCertificate(
            X500Name("CN=Regular Device"), rootName,
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public,
            rootPair.private, 101, issuerIsCa = true
        )
        val audioLeafPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val audioLeafBuilder = JcaX509v3CertificateBuilder(
            rootName, BigInteger.valueOf(102), validityStart(), validityEnd(),
            X500Name("CN=Audio Device"), audioLeafPair.public
        )
        audioLeafBuilder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        audioLeafBuilder.addExtension(
            Extension.certificatePolicies, false,
            CertificatePolicies(PolicyInformation(org.bouncycastle.asn1.ASN1ObjectIdentifier(CAST_AUDIO_POLICY)))
        )
        val audioLeaf = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(audioLeafBuilder.build(JcaContentSignerBuilder("SHA256withRSA").build(rootPair.private)))
        val trustManager = GoogleCastTrustManager(listOf(root))
        val regularChecked = CountDownLatch(1)
        val audioChecked = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val regularResult = pool.submit<Boolean> {
                trustManager.checkServerTrusted(arrayOf(regularLeaf), "RSA")
                regularChecked.countDown()
                check(audioChecked.await(10, TimeUnit.SECONDS))
                trustManager.isAudioOnlyDevice()
            }
            val audioResult = pool.submit<Boolean> {
                check(regularChecked.await(10, TimeUnit.SECONDS))
                trustManager.checkServerTrusted(arrayOf(audioLeaf), "RSA")
                audioChecked.countDown()
                trustManager.isAudioOnlyDevice()
            }
            assertEquals(false, regularResult.get(15, TimeUnit.SECONDS))
            assertEquals(true, audioResult.get(15, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `12 - pathLen exact limit is accepted and exceeding it is rejected`() {
        val exactLimit = syntheticChain(rootPathLen = 1, intermediateCount = 1)
        GoogleCastTrustManager(listOf(exactLimit.root))
            .checkServerTrusted(arrayOf(exactLimit.leaf, exactLimit.intermediates.single()), "RSA")

        val overLimit = syntheticChain(rootPathLen = 1, intermediateCount = 2)
        assertThrows(CertificateException::class.java) {
            GoogleCastTrustManager(listOf(overLimit.root))
                .checkServerTrusted(arrayOf(overLimit.leaf) + overLimit.intermediates, "RSA")
        }
    }

    private data class SyntheticChain(
        val root: X509Certificate,
        val leaf: X509Certificate,
        val intermediates: List<X509Certificate>
    )

    private fun syntheticChain(rootPathLen: Int, intermediateCount: Int): SyntheticChain {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val rootPair = kpg.generateKeyPair()
        val rootName = X500Name("CN=Synthetic Root ${System.nanoTime()}")
        val root = makeCertificate(rootName, rootName, rootPair.public, rootPair.private, 200, caPathLen = rootPathLen)
        var issuerName = rootName
        var issuerKey = rootPair
        val intermediates = mutableListOf<X509Certificate>()
        repeat(intermediateCount) { index ->
            val subject = X500Name("CN=Synthetic Intermediate $index ${System.nanoTime()}")
            val pair = kpg.generateKeyPair()
            intermediates += makeCertificate(subject, issuerName, pair.public, issuerKey.private, 210L + index, caPathLen = 0)
            issuerName = subject
            issuerKey = pair
        }
        val leafPair = kpg.generateKeyPair()
        val leaf = makeCertificate(X500Name("CN=Synthetic Leaf"), issuerName, leafPair.public, issuerKey.private, 220, issuerIsCa = true)
        return SyntheticChain(root, leaf, intermediates)
    }

    private fun makeCertificate(
        subject: X500Name,
        issuer: X500Name,
        publicKey: java.security.PublicKey,
        signingKey: java.security.PrivateKey,
        serial: Long,
        caPathLen: Int? = null,
        issuerIsCa: Boolean = false
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            issuer, BigInteger.valueOf(serial), validityStart(), validityEnd(), subject, publicKey
        )
        if (caPathLen != null) {
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(caPathLen))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        } else {
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey)))
    }

    private fun validityStart() = Date(System.currentTimeMillis() - 60_000)
    private fun validityEnd() = Date(System.currentTimeMillis() + 60_000)

    private companion object {
        const val CAST_AUDIO_POLICY = "1.3.6.1.4.1.11129.2.5.2"
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
