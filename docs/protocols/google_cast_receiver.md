# Google Cast V2 - Receiver Protocol Sequence

Phase A of Checkpoint 6 establishes the protocol sequence required to interact with a Google Cast receiver (e.g. Android TV, Chromecast) and launch the SmartClick application or check its status.

All messages operate on the authenticated multiplexed TLS connection, encased in Protobuf framing.

## Sequence

### 1. Default Connection (Virtual Protocol)
To communicate with the physical Cast device itself, the application establishes a virtual connection to the default receiver `receiver-0`.
* **Flow:** Sender -> Receiver
* **Message Params:**
  - Source ID: `sender-<id>`
  - Destination ID: `receiver-0`
  - Namespace: `urn:x-cast:com.google.cast.tp.connection`
  - Payload String: `{"type": "CONNECT"}`

### 2. Status Discovery
After default connection, sender fetches the system status to see if the TV is available, volume level, or if any application is currently running.
* **Flow:** Sender -> Receiver
* **Message Params:**
  - Source ID: `sender-<id>`
  - Destination ID: `receiver-0`
  - Namespace: `urn:x-cast:com.google.cast.receiver`
  - Payload String: `{"type": "GET_STATUS", "requestId": 1}`
* **Response (RECEIVER_STATUS):**
  - Originates from `receiver-0` to `sender-<id>`
  - Payload includes current volume, mute status, and an array of `applications` (if any are running), detailing `appId`, `sessionId`, and `transportId`.

### 3. Application Launch
If SmartClick Cast Receiver application is not in the `applications` array, the sender launches it:
* **Flow:** Sender -> Receiver
* **Message Params:**
  - Source ID: `sender-<id>`
  - Destination ID: `receiver-0`
  - Namespace: `urn:x-cast:com.google.cast.receiver`
  - Payload String: `{"type": "LAUNCH", "appId": "<SMARTCLICK_APP_ID>", "requestId": 2}`
* **Response:**
  - The TV launches the application.
  - A `RECEIVER_STATUS` broadcast is sent out informing of the new state, containing the specific `transportId` (e.g. `web-12345`) for the launched app.

### 4. Application Virtual Connection
To send commands to the SmartClick Cast Receiver app itself (not the OS), the sender establishes a new virtual connection.
* **Flow:** Sender -> App (via `transportId`)
* **Message Params:**
  - Source ID: `sender-<id>`
  - Destination ID: `<transportId>` (from RECEIVER_STATUS)
  - Namespace: `urn:x-cast:com.google.cast.tp.connection`
  - Payload String: `{"type": "CONNECT"}`

From this point on, direct custom logic commands (namespaces like `urn:x-cast:com.example.smartclick`) map Source ID: `sender-<id>` to Destination ID: `<transportId>`.

### 5. Heartbeat 
The connection to the TV must be maintained via PING/PONG requests. If the TV does not receive PINGs, it terminates the connection.
* **Flow:** Sender -> Receiver
* **Message Params:**
  - Source ID: `sender-<id>`
  - Destination ID: `receiver-0`
  - Namespace: `urn:x-cast:com.google.cast.tp.heartbeat`
  - Payload String: `{"type": "PING"}`
* **Response (PONG):**
  - Payload String: `{"type": "PONG"}`

## Next step
Phase B: Code implementation of this state machine on top of `GoogleCastSession`.
