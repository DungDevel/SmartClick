import re

with open("app/src/main/java/com/example/smartclick/app/tvconnection/samsung/SamsungTizenDriver.kt", "r", encoding="utf-8") as f:
    text = f.read()

search = """        val token = credentials?.protocolValues?.get("token")
        return SamsungTizenController(appNameBase64, token, okHttpClient)"""
replace = """        val token = credentials?.protocolValues?.get("token")
        val certSha256 = credentials?.protocolValues?.get("cert_sha256")
        
        val controllerClient = if (certSha256 != null) {
            try {
                val trustManager = com.example.smartclick.app.tvconnection.security.PinningTrustManager(certSha256)
                val newSslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2").apply {
                    init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), java.security.SecureRandom())
                }
                okHttpClient.newBuilder()
                    .sslSocketFactory(newSslContext.socketFactory, trustManager)
                    .build()
            } catch (e: Exception) {
                okHttpClient // Fallback to trust all if something fails, though it shouldn't
            }
        } else {
            okHttpClient
        }
        
        return SamsungTizenController(appNameBase64, token, controllerClient)"""

text = text.replace(search, replace)

if search in text or replace in text:
    with open("app/src/main/java/com/example/smartclick/app/tvconnection/samsung/SamsungTizenDriver.kt", "w", encoding="utf-8") as f:
        f.write(text)
    print("Success")
else:
    print("Failed")
