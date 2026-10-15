# SmartClick — Hardware Verification Checklist

Document ID: `HW-VERIF-CHECKLIST-01`
Scope: Hardware verification readiness audit & test execution checklist for SmartClick (Google Cast V2, Samsung Tizen, LG webOS).

---

## 1. Environment & Test Setup Metadata
- **APK Version / Build:** `debug` (`v1.0.0-dev`)
- **App Package ID:** `com.example.smartclick`
- **Testing Environment:** Local Wi-Fi Network (LAN) / Same Subnet
- **Required Hardware Devices:**
  - Google Cast compatible TV / Chromecast dongle (Firmware version & model: _____________)
  - Samsung Smart TV (Tizen OS version & model: _____________)
  - LG Smart TV (webOS version & model: _____________)
  - Android Test Smartphone / Tablet running Android 10+

---

## 2. Test Execution Matrix

| ID | Category | Test Case Description | Expected Result | Result (PASS / FAIL / BLOCKED) | Notes / Logs / Bug Ref |
|----|----------|-----------------------|-----------------|---------------------------------|-------------------------|
| 1 | Network & Connectivity | Wi-Fi LAN Discovery & Bonjour / SSDP / mDNS Scan | TV devices on the same local subnet are discovered correctly with correct IP and MAC addresses. | [ ] | |
| 2 | Network & Connectivity | LocalMediaServer IP & Port Binding | `LocalMediaServer` binds correctly to device IPv4 LAN address with random port, serving media over HTTP with secure token validation. | [ ] | |
| 3 | Google Cast V2 | TLS Handshake & Auth (CASTV2_1_0) | Mutual TLS verification completes without trusting unverified certs, challenge-response auth succeeds with `receiver-0`. | [ ] | |
| 4 | Google Cast V2 | App Launch & Transport ID | Default Media Receiver (`CC1AD845`) launches successfully, returning transport ID and active session handle. | [ ] | |
| 5 | Media Streaming | Remote HTTP(S) Video Playback | Streaming test video (e.g., Big Buck Bunny MP4) loads and plays smoothly on TV via Cast/Receiver app. | [ ] | |
| 6 | Media Streaming | Local Media Server File Streaming | Selecting local video/audio/image from device content resolver streams successfully via `LocalMediaServer` URL with token auth & range support. | [ ] | |
| 7 | Transport Controls | Play, Pause, Stop Actions | Tapping Play, Pause, and Stop on `CastScreen` correctly transmits commands to TV and updates playback UI state immediately. | [ ] | |
| 8 | Transport Controls | Seek Position Control | Scrubbing / seeking to specific timestamp (e.g., 30s) updates media playback position correctly on TV. | [ ] | |
| 9 | State Sync | Playback State & Session Info | `CastPlaybackState` (IDLE, BUFFERING, PLAYING, PAUSED, COMPLETED, ERROR) syncs accurately between TV receiver status and app UI. | [ ] | |
| 10 | Samsung Tizen | WebSocket Connection & Pairing | Samsung Tizen device establishes secure WS connection with token/pin pairing flow and media control. | [ ] | |
| 11 | LG webOS | WebSocket Connection & Handshake | LG webOS connects via WSS with handshake key exchange and media/remote control protocol commands. | [ ] | |
| 12 | Error Handling | Invalid URL / Unsupported Media Type | App gracefully handles invalid URLs or unsupported media formats with clear error states and without crashing. | [ ] | |
| 13 | Resilience & Reconnect | Wi-Fi Drop / TV Power Off Recovery | Disconnecting Wi-Fi or turning TV off triggers `TvConnectionState.Reconnecting`, exponential backoff, and reconnection attempt banner. | [ ] | |
| 14 | Session Management | Teardown & Device Switching | Tapping Stop or switching active TV successfully tears down previous session, unregisters media sessions, and resets state. | [ ] | |
| 15 | Security Audit | Log Leakage Prevention | Logcat inspection confirms no sensitive security tokens, passwords, private keys, or auth challenge secrets are logged in plaintext. | [ ] | |

---

## 3. Instructions for Testers
1. Ensure the testing mobile device and Smart TV are connected to the exact same Wi-Fi network (no client isolation / AP isolation enabled).
2. Build and install the debug APK: `.\gradlew.bat assembleDebug` -> output at `app/build/outputs/apk/debug/app-debug.apk`.
3. Record model names, firmware versions, and test results in the matrix above during physical execution.
4. If any test fails, capture Logcat with tag `SmartClick`, `GoogleCast`, `LocalMediaServer`, or protocol-specific tags for investigation.
