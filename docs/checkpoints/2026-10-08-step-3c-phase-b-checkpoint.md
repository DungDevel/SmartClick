# SmartClick — EOD Checkpoint

## Date
2026-10-08

## Current Project State
- **Project Phase**: Step 3C (Android TV / Google Cast) - Phase A (Research & Architecture Spike) completed.
- **Status**: Architecture approved with strict security and protocol corrections. Ready for Phase B implementation.
- **Baseline Commit**: `4552db0`
- **Latest Action**: Finalized Implementation Plan for Step 3C Phase B.

## Work Completed Today
### Step 3C — Android TV / Google Cast (Phase A)
- Completed detailed technical research spike for Google Cast V2.
- Determined that Raw CastV2 implementation is required instead of Play Services Cast SDK to maintain lightweight architecture.
- Identified Default Media Receiver `CC1AD845` as the target application.
- Defined discovery mechanism via `_googlecast._tcp`.
- Defined CastV2 message framing (4-byte big-endian length prefix).
- Mapped essential protocol flow: `CONNECT` $\rightarrow$ `LAUNCH` $\rightarrow$ `RECEIVER_STATUS` $\rightarrow$ `LOAD` $\rightarrow$ `MEDIA_STATUS`.
- Established a strict event-driven playback state model: playback state is driven exclusively by `MEDIA_STATUS`, not RPC success.
- Defined heartbeat mechanism (`PING`/`PONG`).
- Separated identity models:
  - `senderId` (Sender endpoint)
  - `receiverTransportId` (Receiver transport endpoint)
  - `appSessionId` (Application session)
  - `mediaSessionId` (Media stream session)
  - `requestId` (RPC correlation)

### Security Decisions
- **TLS vs Device Auth**: Explicitly separated into two layers.
  - **TLS Layer**: Transport encryption via SSLSocket (Port 8009). Custom `X509TrustManager` for basic validity; no `trustAllCertificates()`.
  - **Cast Device Auth Layer**: Cryptographic hardware authentication via `urn:x-cast:com.google.cast.tp.deviceauth`.
- **Device Auth Requirements**:
  - Cryptographically secure `sender_nonce` verification.
  - Certificate chain verification terminating at pinned Cast/Eureka Root CAs.
  - Signature verification over `(sender_nonce || TLS_peer_certificate_DER)`.
  - Algorithm validation (Prefer SHA256 / RSA-PSS).
- **Constraint**: Cast certificate validation is a high-criticality point for Phase B implementation.

### CORS
- **Status**: `PENDING` real-device validation.
- **Policy**: No wildcard `*` allowed. No blind Origin reflection. `LocalMediaServer` remains unchanged until specific `Origin` headers are observed from real hardware.

### Architecture Decisions
- **Interface Segregation**: `ITvController` (Remote) and `ICastController` (Casting) remain distinct.
- **Command Mapping**: `CastCommand` limited to media commands.
- **Lifecycle**: `ICastController.teardown()` handles all cleanup.
- **Dependency**: No vendor-specific logic in `:core:model` or `:core:tv-api`.
- **Identity**: Cast device ID used for persistent identity; IP address is only supporting evidence.
- **Infrastructure**: No second `LocalMediaServer` instance.

## Approved Architecture Decisions (Decision Log)
| Decision | Reason | Status |
|---|---|---|
| Raw CastV2 over SDK | Lightweight, architectural control, avoid heavy SDK | Approved |
| AppID `CC1AD845` | Standard Default Media Receiver for arbitrary HTTP media | Approved |
| Dual-Layer Security | Separate TLS transport from Hardware Device Auth | Approved |
| No `trustAllCertificates` | Prevent MitM and insecure connections | Approved |
| No Wildcard CORS | Required for protected media/security | Approved |
| Event-driven State | Prevent fake "Playing" state on RPC success | Approved |
| No IP-based Identity | IP is volatile; Cast ID is stable | Approved |
| Stable `LocalMediaServer` | Prevent regression in existing streaming logic | Approved |
| Core API Stability | Maintain clean architecture boundaries | Approved |

## Not Completed
- Step 3C Phase B implementation (NOT STARTED).
- Production `GoogleCastTransport` and `GoogleCastController` code.
- Cast Device Auth implementation.
- Real-device validation on Android TV / Google TV / Chromecast.
- Empirical determination of CORS requirements.
- Step 3D / UI implementation.

## Risks / Open Questions
- **Crypto Capability**: Verification that standard Android JCE can fully handle Cast-specific certificate chain and signature verification without native libraries.
- **Trust Store**: Implementation of the bundled Cast Root CA trust store.
- **Receiver Compatibility**: Actual compatibility of the Default Media Receiver across various OEM Android TV versions.
- **CORS Behavior**: Exact `Origin` header values expected by the Chromium-based receiver.

## Next Steps (2026-10-09)
### Step 3C Phase B
1. Finalize Cast V2 protocol model.
2. Implement defensive Cast V2 framing (4-byte length, size limits).
3. Implement transport/session lifecycle (SSLSocket, Coroutine loop).
4. Implement Cast Device Auth (Challenge/Response, Chain/Signature verify).
5. Implement protocol sequence: `CONNECT` $\rightarrow$ `LAUNCH` $\rightarrow$ `RECEIVER_STATUS` $\rightarrow$ `LOAD`.
6. Implement `MEDIA_STATUS` parsing and playback state synchronization.
7. Implement media commands: `PLAY` / `PAUSE` / `STOP` / `SEEK` / `VOLUME`.
8. Implement heartbeat `PING`/`PONG`.
9. Integrate `GoogleCastController` into `AndroidTvDriver`.
10. Write deterministic unit tests (Auth failures, framing, session flow).
11. Build and conduct security audit of final diff.

### Future Roadmap (Planning only)
- Real-device validation (Casting, CORS, Playback).
- Step 3D / `CastMediaUseCase` + UI integration.

## Validation Status
- **Architecture**: Approved.
- **Implementation**: Not started.
- **Tests**: Not started.
- **Real Device**: Not started.

## Git Status
- **Branch**: `main`
- **Last Commit**: `4552db0` (feat: add casting backend for Samsung and LG)
- **Untracked**: `tv/runtime/src/main/proto/cast_channel.proto` (Proto definition created).
- **Changes**: No production code modified.
