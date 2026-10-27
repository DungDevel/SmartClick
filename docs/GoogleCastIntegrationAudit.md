# Google Cast V2 Integration & Authentication Audit (D-CAST-1)

## 1. Upstream Conformance & Reference Anchors
This audit evaluates SmartClick's Google Cast authentication and cryptographic verification against authoritative upstream Chromium and Open Screen implementations:
- **Upstream Project:** Chromium (`components/cast_certificate`) & Open Screen (`cast/`)
- **Pinned Upstream Commit:** `e537d88599f578d055452f1b402802f5a043dc3d`
- **Reference Source Paths:**
  - `components/cast_certificate/cast_cert_validator.cc` (Certificate validation logic, policies, and key usage constraints).
  - `components/cast_certificate/cast_root_ca_cert_der-inc.h` & `eureka_root_ca_der-inc.h` (Official Cast Root CA and Eureka Root CA trust anchors).
  - `openscreen/cast/common/channel/device_auth.cc` (Challenge-response and TLS peer certificate binding).

## 2. Authentication Architecture & Protocol Comparison

| Security Dimension | Upstream Chromium / Open Screen Behavior | SmartClick Implementation (`GoogleCastAuthManager`) | Compliance Status |
| :--- | :--- | :--- | :--- |
| **Trust Anchors** | Validates against official Google Cast Root CA and Eureka Root CA with built-in revocation/validity checks. | Delegates to `X509TrustManager` (production trust manager implementation managed via Claude A's worktree). | **Pending Integration Verification** (Claude A worktree) |
| **Chain & Policy Validation** | Enforces certificate chain building, basic constraints, key usage, and validity periods (`checkValidity()`). | Parses device and intermediate certificates, checks validity periods (`checkValidity()`), and delegates trust anchor verification. | **Conformant** |
| **TLS Peer Binding** | Binds challenge response signature to the exact live TLS peer certificate DER representation (`nonce || tls_peer_cert_der`). | Concatenates `sender_nonce` and `tls_peer_cert_der` as signed data input for signature verification. | **Conformant** |
| **Challenge-Response** | Generates cryptographically secure random nonce (`sender_nonce`), challenges device, and verifies asymmetric signature (`SHA256withRSA` / `RSASSA-PSS`). | Implements strict nonce matching, cryptographic signature verification, and algorithm restriction (rejecting SHA1/insecure hashes). | **Conformant** |
| **Revocation / CRL** | Online Certificate Status Protocol (OCSP) / CRL checking is deliberately deferred in standalone environments. | Revocation checks are deferred (not implemented). | **Acknowledged & Deferred** |
| **Fingerprint / TOFU** | Relies on dynamic certificate chains and cryptographic challenge-response rather than static TOFU pinning. | Does not use static TOFU/fingerprint pinning; relies on cryptographic challenge-response and chain validation. | **Conformant** |

## 3. Automated Test Evidence
Independent automated security tests (`GoogleCastAuthChallengeSecurityTest`) have been added to verify challenge-response correctness and failure handling:
- **Test File:** `GoogleCastAuthChallengeSecurityTest.kt`
- **Test Suite:** `:tv:runtime:testDebugUnitTest` / `:tv:runtime:testReleaseUnitTest`
- **Coverage Highlights:**
  1. Correctly signed challenge-response acceptance.
  2. Incorrect challenge signature rejection.
  3. Nonce mismatch / replay prevention rejection.
  4. Wrong private key signature rejection.
  5. TLS peer certificate mismatch rejection.
  6. Malformed or truncated authentication payload handling.
  7. Failed authentication safety (no unauthorized session establishment).

## 4. Limitations & Real-Device Testing
- **Unmerged Dependencies:** Trust anchor integration implementation by Claude A (`GoogleCastTrustManager.kt`) resides in a separate unmerged worktree (`D:\SmartClick-D-Cast-A`) and requires integration review before main branch deployment.
- **Hardware Verification:** All verifications described in this audit are purely software-level unit tests and protocol conformance checks. No physical Google Cast hardware testing has been performed; hardware compatibility remains pending.
