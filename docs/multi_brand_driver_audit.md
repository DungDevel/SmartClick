# SmartClick — Multi-Brand TV Driver Capability Audit (Step 3F)

Document ID: `MULTI-BRAND-AUDIT-03`
Scope: Comprehensive architectural and protocol audit of multi-brand TV drivers in SmartClick (`tv/runtime`, `core/tv-api`).

---

## 1. Git Status & Preservation Snapshot
- **Repository Root:** `D:\SmartClick`
- **Current Branch:** `main`
- **Git Status Summary:** Preserved all existing uncommitted user changes and untracked checkpoints/protocols without running any destructive git commands (`reset`, `clean`, `stash`).

---

## 2. Multi-Brand Driver Audit Matrix

| Brand / Platform | Protocol / Transport | Discovery & Pairing / Auth | Implemented Remote Commands | Cast Media & Supported Formats | Play/Pause/Stop/Seek & State Source | Existing Unit / Integration Tests | Limitations, Security Risks & Firmware Dependencies | Driver Status |
|-------------------|----------------------|----------------------------|----------------------------|-------------------------------|-------------------------------------|-----------------------------------|-----------------------------------------------------|-----------------------|
| **Amazon Fire TV** | ADB (Android Debug Bridge) over TCP (`5555`) | Fingerprint via port 5555 or mDNS; Pairing requires enabling ADB Debugging in Developer Options. | Volume, Mute, Power Toggle/On/Off, Keyboard Text (`input keyevent`), DPad, Back, Home. | N/A (Standard remote control via ADB input keyevent) | N/A (Remote key events only) | `FireTvControllerTest.kt`, `FireTvKeyMapperTest.kt` | Requires ADB debugging enabled on Fire TV; network firewalls or ADB auth prompts may block connection. | `IMPLEMENTED_UNVERIFIED` |
| **Sony BRAVIA** | IRCC (InfraRed Compatible Control) SOAP over HTTP (`port 80 / 52323`) | Fingerprint via system info API or port check; Pairing via Pre-Shared Key (PSK) header (`X-Auth-PSK`). | Volume, Mute, Power, Channel Control, IRCC Keycodes (Home, DPad, Back, etc.). | N/A (Control & IRCC commands) | N/A | `SonyBraviaControllerTest.kt`, `SonyKeyMapperTest.kt` | Requires setting pre-shared key (PSK) and enabling IP control in Sony Bravia network settings. | `IMPLEMENTED_UNVERIFIED` |
| **Vizio SmartCast** | Vizio SmartCast Native API over HTTPS (`port 7345`) | Fingerprint via port 7345 & system attributes; Pairing via PIN confirmation on TV (`/pairing/start`). | Volume, Mute, Power, Key commands via encrypted JSON payload (`/menu_native/command/key_command`). | N/A (Remote control and menu navigation) | N/A | `VizioKeyMapperTest.kt` | Requires valid auth token obtained through pairing flow; TLS certificate validation applies. | `IMPLEMENTED_UNVERIFIED` |
| **Hisense VIDAA** | Proprietary VIDAA MQTT Protocol | MQTT endpoint discovery; Pairing flow blocked due to lack of client certificates. | None (Blocked) | None | None | `VidaaDriverTest.kt` | **Blocked:** Requires proprietary MQTT/TLS client certificates and vendor-specific authentication keys not available publicly. | `BLOCKED` |
| **Roku** | Roku ECP (External Control Protocol) over HTTP (`port 8060`) | Fingerprint via `/query/device-info` XML parsing; No pairing required (Settings > Control by mobile apps > Default). | DPad, Select, Back, Home, Volume, Play, Pause, Search, Text input (`/keypress/Lit_*`), App Launch (`/launch/*`). | Basic ECP Casting (`/input/15985?t=v&u=...`) for Video, Photo, Audio with URL encoding. | Supported via ECP keypress (`Play`, `Fwd`, `Rev`, `Back` for Stop). State managed via local flow. | `RokuDriverTest.kt` | Roku TV must allow mobile app control in system settings; basic ECP casting supports limited media codecs depending on Roku model. | `IMPLEMENTED_UNVERIFIED` |
| **Google Cast V2** | Cast V2 TLS + Protobuf Framing (`port 8009`) | mDNS discovery (`_googlecast._tcp`), Mutual TLS auth with `GoogleCastAuthManager`, challenge-response. | Media control, Volume, Mute, Pause, Play, Seek. | Video, Audio, Photo streaming with `LocalMediaServer` HTTP streaming. | Full Play/Pause/Stop/Seek with real-time `CastPlaybackState` sync from receiver status. | `GoogleCastControllerTest.kt`, `GoogleCastProtocolClientTest.kt`, `GoogleCastSessionTest.kt`, etc. (75 tests) | Requires compatible Cast receiver / Chromecast device; network Wi-Fi isolation must be disabled. | `IMPLEMENTED_UNVERIFIED` (Core protocol tested via mock server) |
| **Samsung Tizen** | Samsung Smart TV WebSocket / DLNA UPnP (`port 8001 / 9197`) | mDNS / SSDP discovery, secure WebSocket pairing with PIN code prompt. | Remote key events, App launch. | DLNA UPnP Media Renderer casting (`SamsungDlnaCastController`). | Play/Pause/Stop/Seek via DLNA SOAP actions. | `SamsungTizenDriverTest.kt`, `SamsungKeyMapperTest.kt`, `SamsungDlnaCastControllerTest.kt` | Requires TV authorization prompt on first pairing; DLNA media playback depends on TV supported codecs. | `IMPLEMENTED_UNVERIFIED` |
| **LG webOS** | LG webOS WSS (WebSocket Secure over port 3000) | SSDP / mDNS discovery, handshake pairing token prompt on TV screen. | Remote control pointer, key events, app launch. | webOS Cast transport (`LgWebOsCastTransport`). | Play/Pause/Stop/Seek via webOS media controller API. | `LgWebOsDriverTest.kt`, `LgWebOsControllerTest.kt`, `LgWebOsCastControllerTest.kt` | Requires pairing confirmation on LG TV screen; depends on webOS API version (3.0+). | `IMPLEMENTED_UNVERIFIED` |

---

## 3. Official Protocol References & Security Compliance
- **Roku ECP:** Compliant with Roku External Control Protocol specification (`port 8060`, XML query responses, `/keypress/` endpoints). No insecure protocols or unverified shortcuts used.
- **Sony IRCC:** Follows Sony Bravia API standards (`/sony/IRCC`, SOAP action headers, PSK authentication).
- **Amazon Fire TV ADB:** Standard TCP ADB connection (`5555`) with `A_CNX` handshake and `shell:input keyevent`.
- **Security Check:** All drivers strictly prohibit `TrustAll`, hardcoded master credentials, or bypassing TLS verification. Authentication tokens and PSKs are stored securely via `TvCredentialStore`.

---

## 4. Prioritization & Action Plan for Hardware Verification

### Ưu tiên hoàn thiện trước (High Priority — Core Casting & Popular Brands):
1. **Google Cast V2:** Core casting backend (đã pass toàn bộ unit tests, ưu tiên test thực tế trên Chromecast / Android TV).
2. **Samsung Tizen & LG webOS:** Hai dòng Smart TV phổ biến nhất tại thị trường gia dụng (đã có khung driver, DLNA transport và webOS cast transport).
3. **Roku ECP:** Giao thức HTTP đơn giản, dễ xác thực trên phần cứng Roku TV.

### Cần nghiên cứu thêm (Medium Priority):
4. **Amazon Fire TV & Sony BRAVIA:** Đã triển khai remote control qua ADB và IRCC SOAP; cần kiểm tra thực tế độ trễ lệnh và cấu hình PSK/ADB developer options trên thiết bị thật.
5. **Vizio SmartCast:** Cần xác thực luồng pairing PIN qua cổng 7345 trên thiết bị Vizio thực tế.

### Bị chặn (Blocked):
6. **Hisense VIDAA (VIDAA MQTT):** Tạm thời `BLOCKED` do thiếu chứng chỉ MQTT/TLS client certificate độc quyền từ nhà sản xuất Hisense.

### Thiết bị phần cứng tối thiểu cần chuẩn bị cho phòng lab kiểm thử:
- 01 Google Cast Receiver / Chromecast dongle.
- 01 Samsung Smart TV (Tizen OS).
- 01 LG Smart TV (webOS).
- 01 Roku Streaming Stick / Roku TV.
- 01 Android Smartphone (Android 10+).
- *Tùy chọn:* Sony Bravia TV, Amazon Fire TV Stick, Vizio TV.
