# Google Cast V2 Integration & Authentication Audit (D-CAST-1)

## Local media server threat model (Cast D1)

`LocalMediaServer` serves one registered `content://` URI at a time over HTTP on the current private IPv4 LAN address and an OS-assigned port. Each registration receives a fresh 256-bit `SecureRandom` token encoded as a 43-character URL-safe Base64 path (`/<token>`). A new registration invalidates the previous token; `unregisterMedia`, `stop`, and the idle shutdown invalidate it as well. Requests require the exact token path and a private/LAN peer address. Unknown paths, absent tokens, stale tokens, and incorrect tokens all receive the same 404 response. No filesystem path is derived from an HTTP request and directory listing is not implemented.

Only `content://` inputs and the server's explicit image/video MIME allowlist are accepted. Content is opened through Android `ContentResolver`. Single byte ranges and suffix ranges are supported; malformed, unsatisfiable, out-of-file, and multiple ranges receive 416 with `Content-Range: bytes */<length>` (or `*` when length cannot be determined). HEAD preserves response headers and length without sending a body. Responses set `Accept-Ranges: bytes`, `X-Content-Type-Options: nosniff`, and `Cache-Control: no-store`. The server closes active content streams on stop, periodically stops after ten minutes without traffic or registration, uses an ephemeral port, and does not serve requests from peers outside private LAN ranges.

**Limit:** the URL carries its bearer token over plain HTTP. Anyone on the reachable LAN who learns the URL can download the currently selected media until it is replaced, unregistered, or the server stops. No HTTPS is used because TVs generally do not trust a self-signed certificate. These checks are unit-tested; playback and downloads have not been verified on a physical TV. Supported video formats can still vary by receiver model and firmware.

## 1. Upstream Conformance & Reference Anchors
This audit evaluates SmartClick's Google Cast authentication and cryptographic verification against authoritative upstream Chromium and Open Screen implementations. The implementation snapshot covered here is branch `fix/cast-trust-hardening`, source revision `02af5f1` (the documentation update is committed after that source revision):
- **Upstream Project:** Chromium (`components/cast_certificate`) & Open Screen (`cast/`)
- **Pinned Upstream Commit:** `e537d88599f578d055452f1b402802f5a043dc3d`
- **Reference Source Paths:**
  - `components/cast_certificate/cast_cert_validator.cc` (Certificate validation logic, policies, and key usage constraints).
  - `components/cast_certificate/cast_root_ca_cert_der-inc.h` & `eureka_root_ca_der-inc.h` (Official Cast Root CA and Eureka Root CA trust anchors).
  - `openscreen/cast/common/channel/device_auth.cc` (Challenge-response and TLS peer certificate binding).

## 2. Authentication Architecture & Protocol Comparison

| Security Dimension | Upstream Chromium / Open Screen Behavior | SmartClick Implementation (`GoogleCastAuthManager`) | Compliance Status |
| :--- | :--- | :--- | :--- |
| **Trust Anchors** | Validates against official Google Cast Root CA and Eureka Root CA with validity checks. | `GoogleCastTrustManager` validates against the embedded Google Cast and Eureka trust anchors; included roots are removed from the submitted CertPath. | **Implemented; unit-tested with generated certificates** |
| **Chain & Policy Validation** | Enforces certificate chain building, basic constraints, key usage, and validity periods (`checkValidity()`). | Checks validity, leaf `digitalSignature` KeyUsage, subject DN, pathLen, and Cast audio-only policy OID `1.3.6.1.4.1.11129.2.5.2`, then runs PKIX validation. Audio-only status is returned per validated chain. | **Implemented; unit-tested with generated certificates** |
| **TLS Peer Binding** | Binds challenge response signature to the exact live TLS peer certificate DER representation (`nonce || tls_peer_cert_der`). | Concatenates `sender_nonce` and `tls_peer_cert_der` as signed data input for signature verification. | **Conformant** |
| **Challenge-Response** | Generates cryptographically secure random nonce (`sender_nonce`), challenges device, and verifies asymmetric signature (`SHA256withRSA` / `RSASSA-PSS`). | Implements strict nonce matching, cryptographic signature verification, and algorithm restriction (rejecting SHA1/insecure hashes). | **Conformant** |
| **Revocation / CRL** | Online Certificate Status Protocol (OCSP) / CRL checking is deliberately deferred in standalone environments. | PKIX revocation is explicitly disabled (`isRevocationEnabled = false`) under the current assumption that Cast devices are on the local network. CRL/OCSP status is not checked. | **Disabled; not verified** |
| **Fingerprint / TOFU** | Relies on dynamic certificate chains and cryptographic challenge-response rather than static TOFU pinning. | Does not use static TOFU/fingerprint pinning; relies on cryptographic challenge-response and chain validation. | **Conformant** |

## 3. Automated Test Evidence
Automated unit tests using generated/fake certificates verify challenge-response behavior, trust-chain validation, and failure handling. These results do not establish behavior with certificates presented by a physical Cast device:
- **Test Files:** `GoogleCastAuthChallengeSecurityTest.kt`, `GoogleCastTrustManagerTest.kt`
- **Test Suites:** `:tv:runtime:testDebugUnitTest` / `:tv:runtime:testReleaseUnitTest`
- **Coverage Highlights:**
  1. Correctly signed challenge-response acceptance.
  2. Incorrect challenge signature rejection.
  3. Nonce mismatch / replay prevention rejection.
  4. Wrong private key signature rejection.
  5. TLS peer certificate mismatch rejection.
  6. Malformed or truncated authentication payload handling.
  7. Failed authentication safety (no unauthorized session establishment).

## 4. Limitations & Real-Device Testing
- **Revocation:** Revocation is currently disabled (`isRevocationEnabled = false`) based on the local-network device assumption; no CRL or OCSP checks are performed.
- **Hardware Verification:** This implementation has not been tried with a real Google Cast device. The reported results currently come only from unit tests using generated/fake certificates; hardware compatibility remains unverified.
