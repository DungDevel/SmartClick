import re

with open("app/src/main/java/com/example/smartclick/app/tvconnection/samsung/SamsungTizenDriver.kt", "r", encoding="utf-8") as f:
    text = f.read()

# I will add a var to capture the cert hash
text = text.replace("override fun onOpen(webSocket: WebSocket, response: Response) {\n                // Connection opened, wait for TV UI confirmation\n            }", 
"""var certHashToSave: String? = null
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection opened, capture certificate hash
                val certs = response.handshake?.peerCertificates
                if (!certs.isNullOrEmpty()) {
                    val cert = certs[0]
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    certHashToSave = md.digest(cert.encoded).joinToString("") { "%02x".format(it) }
                }
            }""")

# And pass it to the Credentials
text = text.replace("trySend(PairingStep.Succeeded(Credentials(mapOf(\"token\" to token))))",
"""val credsMap = mutableMapOf("token" to token)
                            if (certHashToSave != null) {
                                credsMap["cert_sha256"] = certHashToSave!!
                            }
                            trySend(PairingStep.Succeeded(Credentials(credsMap)))""")

# Modify createController to use a secure client
# Currently it uses `okHttpClient` which is trust-all. We should create a new okHttpClient for the controller or just let createController create one.

with open("app/src/main/java/com/example/smartclick/app/tvconnection/samsung/SamsungTizenDriver.kt", "w", encoding="utf-8") as f:
    f.write(text)
