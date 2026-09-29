import re

with open("app/src/main/java/com/example/smartclick/app/tvconnection/androidtv/AndroidTvRemoteSession.kt", "r", encoding="utf-8") as f:
    text = f.read()

# Change constructor to receive certSha256
text = text.replace("private val clientCertPair: Pair<PrivateKey, X509Certificate>", "private val clientCertPair: Pair<PrivateKey, X509Certificate>,\n    private val expectedCertSha256: String?")

# Replace trustAllCerts inside connect() with PinningTrustManager
search = """val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })"""
replace = """val trustManager = arrayOf<TrustManager>(com.example.smartclick.app.tvconnection.security.PinningTrustManager(expectedCertSha256))"""
text = text.replace(search, replace)
text = text.replace("trustAllCerts", "trustManager")

with open("app/src/main/java/com/example/smartclick/app/tvconnection/androidtv/AndroidTvRemoteSession.kt", "w", encoding="utf-8") as f:
    f.write(text)

with open("app/src/main/java/com/example/smartclick/app/tvconnection/androidtv/AndroidTvDriver.kt", "r", encoding="utf-8") as f:
    text2 = f.read()

text2 = text2.replace("AndroidTvRemoteSession(device.ipAddress, controlPort, AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate())", 
"AndroidTvRemoteSession(device.ipAddress, controlPort, AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate(), credentials?.protocolValues?.get(\"cert_sha256\"))")

with open("app/src/main/java/com/example/smartclick/app/tvconnection/androidtv/AndroidTvDriver.kt", "w", encoding="utf-8") as f:
    f.write(text2)
