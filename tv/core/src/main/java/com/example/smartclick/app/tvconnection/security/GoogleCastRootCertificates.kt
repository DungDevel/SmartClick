package com.example.smartclick.app.tvconnection.security

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * Pinned Upstream Chromium Revision: 8d395ba07308f7f46011f3ac42d91498e90b8c28
 * Source paths in Chromium:
 * - components/cast_certificate/cast_root_ca_cert_der-inc.h
 * - components/cast_certificate/eureka_root_ca_der-inc.h
 *
 * Copyright 2014 The Chromium Authors. All rights reserved.
 * Distributed under a BSD-style license.
 */
object GoogleCastRootCertificates {
    const val CHROMIUM_REVISION = "8d395ba07308f7f46011f3ac42d91498e90b8c28"

    private const val CAST_ROOT_HEX = "308203c5308202ada003020102020102300d06092a864886f70d01010505003075310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d4d6f756e7461696e205669657731133011060355040a0c0a476f6f676c6520496e63310d300b060355040b0c04436173743115301306035504030c0c4361737420526f6f74204341301e170d3134303430323137333432365a170d3334303332383137333432365a3075310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d4d6f756e7461696e205669657731133011060355040a0c0a476f6f676c6520496e63310d300b060355040b0c04436173743115301306035504030c0c4361737420526f6f7420434130820122300d06092a864886f70d01010105000382010f003082010a0282010100bad9659dda39d3c177f6d4d0ae8f580868394a95ed70cffd7908a9aae5e9b8a72da067478a9ec9cf70b305876911ec709897c3e6c3c3ebbdc6b03dfc4fc15e389fdacf7330065b7937c15e8c8747949a41922ad695c4715c275d08b180c692bd1be34197a1ec759f559e3e9f8f1cc7656407d3b396a1049f91c4de0a7b6cd9c8c07831a01942a9e883e3cefcf1cec22e2446950919cac046b2e501bad74ff3bff669ad9904faa007390ee6df514707c0e4a95c4b94c52fb3a0307fe7956bb2af320df18cd56dcb7b47a708abcb27a34dcf4a5af105d1f862c5102a7469aae64b96fb9bd863e45866d3ad8a6eff7b5ef9a5561e2d82315bf0e224e6414a1fae130203010001a360305e300f0603551d13040830060101ff020102301d0603551d0e041604147c9a1e7ddf7954bcd7cc5eca9986457965742819301f0603551d230418301680147c9a1e7ddf7954bcd7cc5eca9986457965742819300b0603551d0f040403020106300d06092a864886f70d0101050500038201010080f45afb3d28195120d7d4fb12974a65f258359277306af1d7b6511a7f9acdc77b0342ad556a00aff0e106c2bd6b7875dbfe4111534a39bb9a3ac659342f2c33e3b2d65c7fdd78eb715b39da8390c531e23f23efdaeb2b2d775edec343d2c96b5982cad5edfaa1645bcbf10d1a62e19ce8a71870f05f1796f8ed86dbae1de0cf3e5d2eee166d952b3cfd97f3055a24684d39b6f8e458baf5e0267851c55b5d4e09e56c478b7a5a2e8953e6cc365b263cf872430282d22bcdf0d3a3ec133e52d5833d07dc1d43657a330201a3ceb7d660513b09c2238a32fe981960629385cd3446dbd5230f79da77002a026d8358ce037735e1a32093c24aa2a4461c752c1f4d"
    private const val EUREKA_ROOT_HEX = "308203c3308202aba003020102020101300d06092a864886f70d0101050500307c310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d4d6f756e7461696e205669657731133011060355040a0c0a476f6f676c6520496e6331123010060355040b0c09476f6f676c652054563117301506035504030c0e457572656b6120526f6f74204341301e170d3132313231373232333933335a170d3332313231323232333933335a307c310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d4d6f756e7461696e205669657731133011060355040a0c0a476f6f676c6520496e6331123010060355040b0c09476f6f676c652054563117301506035504030c0e457572656b6120526f6f7420434130820122300d06092a864886f70d01010105000382010f003082010a0282010100b911d0ea12dc32e1df5c336b19731d9d9ed03976bfa58409a6fd6e6de9dc8f364ee98802bd9ff4e844fd4cf59a02566a472a636c5845cc7c6624dc7979c32aa4b28ba0f7a2b5cd067edbbeec0c86f20d24607484ca29238402d8a7ed3bf1ec264754e3b12de6640ff672c5e9985217c0fcf22c20c840f847c9329e3b97b18bf5982470636619c152e804053d5f8dbcd84baf77986f1f78d1b650274de4ec1469671f58afa9a011263c9432077fd7e9691fae3f4f638a8f89d6f219785c218eb1b657d8c0e1ee7d6eddf13a0a6af1bafff9832fdcb5a420176336efc86219cc56ceb2ea31894b7858c1bf031399e012f288aa9b94dadd7679171e34d10ac407450203010001a350304e301d0603551d0e04160414444e2a4758d8b94891f64fce74a91d329a8d8de9301f0603551d23041830168014444e2a4758d8b94891f64fce74a91d329a8d8de9300c0603551d13040530030101ff300d06092a864886f70d010105050003820101003fc826a06e5c054079a198a933de687485eeaeb71c3359b011de9ff44fd3eb51097d477e6e5185f454cd839825b1bab057ec93db12e2ec51497a96739bc8966d858cd3e13cfa32e2580c776d870c3401aa30a976e0c0e7db5e1be91030a4e0094926b958cd5a07e55075de9a3bf6537eb1535e45274f17e30833b2500abbf4fc259729de417530fa7738aa658a734fea117beb7c1760270ebc3e7652d88bed1af8eb37bb11fdae70170afee0ad06b31f698a7204c2c0330bd62f634c331114b8623688c503650119a3ef00bb6f0e92ff341ca1d631d05c5e9f997dc7cabd7c720bf45ca57e6e04a8d2992c510114fea248f07ebe840db4d3e2f30e7dde8bf533"

    private val CAST_ROOT_DER: ByteArray by lazy { hexToBytes(CAST_ROOT_HEX) }
    private val EUREKA_ROOT_DER: ByteArray by lazy { hexToBytes(EUREKA_ROOT_HEX) }

    const val CAST_ROOT_SHA256 = "809af14700b3fe2611ad597eb1584d6354313b64ccdb3390f097fa3e5826d6ca"
    const val EUREKA_ROOT_SHA256 = "caf6d1e37b532203e01a76bf07187bb731ccd38801565ab2211a2c0ab7f3bc46"

    private val cachedRoots: List<X509Certificate> by lazy {
        loadAndVerifyRoots()
    }

    fun getDefaultRoots(): List<X509Certificate> = cachedRoots

    private fun loadAndVerifyRoots(): List<X509Certificate> {
        try {
            val castHash = sha256Hex(CAST_ROOT_DER)
            val eurekaHash = sha256Hex(EUREKA_ROOT_DER)

            if (castHash != CAST_ROOT_SHA256) {
                throw CertificateException("Cast Root CA SHA-256 fingerprint mismatch! Expected: $CAST_ROOT_SHA256, Got: $castHash")
            }
            if (eurekaHash != EUREKA_ROOT_SHA256) {
                throw CertificateException("Eureka Root CA SHA-256 fingerprint mismatch! Expected: $EUREKA_ROOT_SHA256, Got: $eurekaHash")
            }

            val castAsn1Cert = org.bouncycastle.asn1.x509.Certificate.getInstance(CAST_ROOT_DER)
            val eurekaAsn1Cert = org.bouncycastle.asn1.x509.Certificate.getInstance(EUREKA_ROOT_DER)
            val castHolder = X509CertificateHolder(castAsn1Cert)
            val eurekaHolder = X509CertificateHolder(eurekaAsn1Cert)
            val converter = JcaX509CertificateConverter().setProvider("BC")
            val castCert = converter.getCertificate(castHolder)
            val eurekaCert = converter.getCertificate(eurekaHolder)

            val roots = listOf(castCert, eurekaCert)
            if (roots.isEmpty()) {
                throw CertificateException("Failed to load Google Cast root certificates: empty root list.")
            }
            return roots
        } catch (e: Exception) {
            throw CertificateException("Critical security failure loading Google Cast trust anchors: ${e.message}", e)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.filter { it in "0123456789abcdefABCDEF" }
        require(cleanHex.length % 2 == 0) { "Hex string must have an even length" }
        val bytes = ByteArray(cleanHex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }
}
