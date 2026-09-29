import re

with open("app/src/main/java/com/example/smartclick/app/tvconnection/androidtv/AndroidTvPairingSession.kt", "r", encoding="utf-8") as f:
    text = f.read()

# Make trust manager TOFU / Trust All for pairing, this is required because we don't know the cert yet
# But it's okay because we are pairing interactively.

# We need to change the return value of pairing:
search = """collector.emit(PairingStep.Succeeded(Credentials()))"""
replace = """val md = MessageDigest.getInstance("SHA-256")
                    val certHash = md.digest(serverCert.encoded).joinToString("") { "%02x".format(it) }
                    collector.emit(PairingStep.Succeeded(Credentials(mapOf("cert_sha256" to certHash))))"""

text = text.replace(search, replace)
if search in text or replace in text:
    with open("app/src/main/java/com/example/smartclick/app/tvconnection/androidtv/AndroidTvPairingSession.kt", "w", encoding="utf-8") as f:
        f.write(text)
else:
    print("Failed to replace AndroidTvPairingSession")
