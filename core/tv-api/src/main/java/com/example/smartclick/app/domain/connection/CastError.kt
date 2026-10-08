package com.example.smartclick.app.domain.connection

sealed class CastError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class DeviceNotFound : CastError("Device not found")
    class InvalidLocationUri(val uri: String) : CastError("Invalid location URI: $uri")
    class DiscoveryFailed(cause: Throwable? = null) : CastError("Discovery failed", cause)
    class MissingAVTransport : CastError("AVTransport service not found on device")
    class UnsupportedCommand(val commandName: String) : CastError("Unsupported command: $commandName")
    class SeekNotSupported : CastError("Seek operation not supported by device")
    class MediaRegistrationFailed(cause: Throwable? = null) : CastError("Media registration failed", cause)
    class NetworkError(cause: Throwable) : CastError("Network error: ${cause.message}", cause)
    class SoapFault(val faultCode: String, val faultString: String) : CastError("SOAP Fault: $faultCode - $faultString")
    class DeviceRejectedRequest(val reason: String) : CastError("Device rejected request: $reason")
    class InvalidCredentials(message: String) : CastError("Invalid Credentials: $message")
    class ProtocolCommandFailed(message: String) : CastError("Protocol Command Failed: $message")
}

