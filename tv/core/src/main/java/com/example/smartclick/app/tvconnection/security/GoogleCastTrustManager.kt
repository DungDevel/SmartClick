package com.example.smartclick.app.tvconnection.security

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x509.PolicyInformation
import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import javax.net.ssl.X509TrustManager

/**
 * TrustManager specifically for Google Cast Device Authentication.
 * Enforces strict chain validation against approved Cast Root CAs in both debug and release builds.
 * Implements deterministic trust-anchor selection, path-length accounting (handling self-issued certs,
 * root-included/excluded chains), Cast-specific certificate policy checks (OID 1.3.6.1.4.1.11129.2.5.2),
 * and leaf Key Usage enforcement (digitalSignature).
 * Fails closed if the trust store is empty or validation fails.
 */
class GoogleCastTrustManager(
    private val trustedRootCerts: List<X509Certificate>
) : X509TrustManager {

    companion object {
        const val CAST_AUDIO_ONLY_POLICY_OID = "1.3.6.1.4.1.11129.2.5.2"
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        validateServerTrusted(chain, authType)
    }

    /** Validates this chain and returns its audio-only policy without shared per-connection state. */
    @Throws(CertificateException::class)
    fun isAudioOnlyDevice(chain: Array<out X509Certificate>?, authType: String?): Boolean =
        validateServerTrusted(chain, authType)

    private fun validateServerTrusted(chain: Array<out X509Certificate>?, authType: String?): Boolean {
        if (chain.isNullOrEmpty()) throw CertificateException("Empty certificate chain")

        try {
            // 1. Validate validity periods
            chain.forEach { it.checkValidity() }

            if (trustedRootCerts.isEmpty()) {
                throw CertificateException("Security policy violation: empty trust store is not permitted.")
            }

            // 2. Leaf device certificate checks
            val leafCert = chain[0]
            val isTrustedRootLeaf = trustedRootCerts.any { root ->
                root == leafCert || (root.subjectX500Principal == leafCert.subjectX500Principal && root.publicKey == leafCert.publicKey)
            }
            if (isTrustedRootLeaf && chain.size == 1) {
                throw CertificateException("Security policy violation: Root certificate cannot be accepted as a leaf device certificate.")
            }

            // Verify Key Usage on leaf certificate: must include digitalSignature (bit index 0)
            val keyUsage = leafCert.keyUsage
            if (keyUsage != null) {
                if (keyUsage.size > 0 && !keyUsage[0]) {
                    throw CertificateException("Leaf certificate Key Usage lacks digitalSignature.")
                }
            } else {
                throw CertificateException("Leaf certificate missing Key Usage extension.")
            }

            // Verify subject DN presence
            if (leafCert.subjectX500Principal.name.isNullOrEmpty()) {
                throw CertificateException("Leaf certificate missing subject DN.")
            }

            // 3. Deterministic Trust-Anchor Selection & Chain Normalization
            val normalizedChainList = mutableListOf<X509Certificate>()
            var matchedRoot: X509Certificate? = null

            val lastCert = chain.last()
            val potentialRootMatch = trustedRootCerts.find { root ->
                root == lastCert || (root.subjectX500Principal == lastCert.subjectX500Principal && root.publicKey == lastCert.publicKey)
            }

            if (potentialRootMatch != null) {
                // Chain includes the trusted root at the end
                matchedRoot = potentialRootMatch
                for (i in 0 until chain.size - 1) {
                    normalizedChainList.add(chain[i])
                }
            } else {
                // Chain excludes the root; find issuing root in trustedRootCerts deterministically
                matchedRoot = trustedRootCerts.find { root ->
                    lastCert.issuerX500Principal == root.subjectX500Principal
                } ?: throw CertificateException("Trust anchor not found for chain issuing authority: ${lastCert.issuerX500Principal}")

                for (cert in chain) {
                    normalizedChainList.add(cert)
                }
            }

            // 4. Path-Length Accounting (handling intermediates, excluding leaf, respecting self-issued certs under RFC 5280)
            val intermediates = if (normalizedChainList.size > 1) normalizedChainList.subList(1, normalizedChainList.size) else emptyList()

            var effectiveIntermediateCount = 0
            for (intCert in intermediates) {
                val isSelfIssued = intCert.subjectX500Principal == intCert.issuerX500Principal
                if (!isSelfIssued) {
                    effectiveIntermediateCount++
                }
            }

            val pathLenLimit = matchedRoot.basicConstraints
            if (pathLenLimit >= 0) {
                if (effectiveIntermediateCount > pathLenLimit) {
                    throw CertificateException("Certification path length ($effectiveIntermediateCount) exceeds trust anchor pathLenConstraint ($pathLenLimit).")
                }
            }

            // 5. Cast-Specific Certificate Policy Scan (OID: 1.3.6.1.4.1.11129.2.5.2)
            var audioOnlyFound = false
            val allCertsForPolicy = normalizedChainList + matchedRoot
            for (cert in allCertsForPolicy) {
                if (hasAudioOnlyPolicy(cert)) {
                    audioOnlyFound = true
                    break
                }
            }
            // 6. JCA PKIX Validation
            val cf = CertificateFactory.getInstance("X.509")
            val certPath = cf.generateCertPath(normalizedChainList)

            val trustAnchors = setOf(TrustAnchor(matchedRoot, null))
            val pkixParams = PKIXParameters(trustAnchors).apply {
                isRevocationEnabled = false
            }

            val validator = CertPathValidator.getInstance("PKIX")
            validator.validate(certPath, pkixParams)

            return audioOnlyFound

        } catch (e: Exception) {
            if (e is CertificateException) throw e
            throw CertificateException("Chain validation failed: ${e.message}", e)
        }
    }

    private fun hasAudioOnlyPolicy(cert: X509Certificate): Boolean {
        val extVal = cert.getExtensionValue("2.5.29.32") ?: return false
        try {
            val octetStr = ASN1OctetString.getInstance(extVal)
            val asn1Seq = ASN1Sequence.getInstance(octetStr.octets)
            for (i in 0 until asn1Seq.size()) {
                val policyInfo = PolicyInformation.getInstance(asn1Seq.getObjectAt(i))
                val policyId = policyInfo.policyIdentifier.id
                if (policyId == CAST_AUDIO_ONLY_POLICY_OID) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Fallback: check raw bytes in extension value for OID DER encoding
            val extBytesHex = extVal.joinToString("") { "%02x".format(it) }
            if (extBytesHex.contains("2b06010401d679020502") || extBytesHex.contains("2b060104018679020502") || extBytesHex.contains("d669")) {
                return true
            }
        }
        return false
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

    override fun getAcceptedIssuers(): Array<X509Certificate> = trustedRootCerts.toTypedArray()
}
