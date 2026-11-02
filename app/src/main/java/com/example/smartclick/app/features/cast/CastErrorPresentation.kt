package com.example.smartclick.app.features.cast

import com.example.smartclick.app.domain.connection.CastError
import com.example.smartclick.R

enum class CastErrorAction { RETRY, REPAIR_DEVICE, CHOOSE_MEDIA, NONE }

enum class CastErrorMessage {
    DEVICE_NOT_FOUND, INVALID_SOURCE, NETWORK, DISCOVERY, NO_LAN_ADDRESS, UNSUPPORTED_DEVICE,
    UNSUPPORTED_COMMAND, MEDIA_REGISTRATION, DEVICE_REJECTED, INVALID_CREDENTIALS,
    CERTIFICATE_CHANGED, UNSUPPORTED_FORMAT, MEDIA_UNAVAILABLE, PROTOCOL
}

data class CastErrorPresentation(val message: CastErrorMessage, val action: CastErrorAction)

fun CastError.toPresentation(): CastErrorPresentation = when (this) {
    is CastError.DeviceNotFound -> CastErrorPresentation(CastErrorMessage.DEVICE_NOT_FOUND, CastErrorAction.RETRY)
    is CastError.InvalidLocationUri -> CastErrorPresentation(CastErrorMessage.INVALID_SOURCE, CastErrorAction.CHOOSE_MEDIA)
    is CastError.DiscoveryFailed -> CastErrorPresentation(CastErrorMessage.DISCOVERY, CastErrorAction.RETRY)
    is CastError.MissingAVTransport -> CastErrorPresentation(CastErrorMessage.UNSUPPORTED_DEVICE, CastErrorAction.NONE)
    is CastError.UnsupportedCommand -> CastErrorPresentation(CastErrorMessage.UNSUPPORTED_COMMAND, CastErrorAction.NONE)
    is CastError.SeekNotSupported -> CastErrorPresentation(CastErrorMessage.UNSUPPORTED_COMMAND, CastErrorAction.NONE)
    is CastError.MediaRegistrationFailed -> CastErrorPresentation(CastErrorMessage.MEDIA_REGISTRATION, CastErrorAction.RETRY)
    is CastError.NoLanAddress -> CastErrorPresentation(CastErrorMessage.NO_LAN_ADDRESS, CastErrorAction.RETRY)
    is CastError.NetworkError -> CastErrorPresentation(CastErrorMessage.NETWORK, CastErrorAction.RETRY)
    is CastError.SoapFault -> CastErrorPresentation(CastErrorMessage.DEVICE_REJECTED, CastErrorAction.NONE)
    is CastError.DeviceRejectedRequest -> CastErrorPresentation(CastErrorMessage.DEVICE_REJECTED, CastErrorAction.NONE)
    is CastError.InvalidCredentials -> CastErrorPresentation(CastErrorMessage.INVALID_CREDENTIALS, CastErrorAction.REPAIR_DEVICE)
    is CastError.TlsAuthenticationFailed -> CastErrorPresentation(CastErrorMessage.CERTIFICATE_CHANGED, CastErrorAction.REPAIR_DEVICE)
    is CastError.UnsupportedMediaSource -> CastErrorPresentation(CastErrorMessage.INVALID_SOURCE, CastErrorAction.CHOOSE_MEDIA)
    is CastError.UnsupportedMediaFormat -> CastErrorPresentation(CastErrorMessage.UNSUPPORTED_FORMAT, CastErrorAction.CHOOSE_MEDIA)
    is CastError.MediaSourceUnavailable -> CastErrorPresentation(CastErrorMessage.MEDIA_UNAVAILABLE, CastErrorAction.CHOOSE_MEDIA)
    is CastError.ProtocolCommandFailed -> CastErrorPresentation(CastErrorMessage.PROTOCOL, CastErrorAction.RETRY)
}

fun CastErrorMessage.stringRes(): Int = when (this) {
    CastErrorMessage.DEVICE_NOT_FOUND -> R.string.cast_error_device_not_found
    CastErrorMessage.INVALID_SOURCE -> R.string.cast_error_invalid_source
    CastErrorMessage.NETWORK -> R.string.cast_error_network
    CastErrorMessage.DISCOVERY -> R.string.cast_error_discovery
    CastErrorMessage.NO_LAN_ADDRESS -> R.string.cast_error_no_lan
    CastErrorMessage.UNSUPPORTED_DEVICE -> R.string.cast_error_unsupported_device
    CastErrorMessage.UNSUPPORTED_COMMAND -> R.string.cast_error_unsupported_command
    CastErrorMessage.MEDIA_REGISTRATION -> R.string.cast_error_media_registration
    CastErrorMessage.DEVICE_REJECTED -> R.string.cast_error_device_rejected
    CastErrorMessage.INVALID_CREDENTIALS -> R.string.cast_error_invalid_credentials
    CastErrorMessage.CERTIFICATE_CHANGED -> R.string.cast_error_certificate_changed
    CastErrorMessage.UNSUPPORTED_FORMAT -> R.string.cast_unsupported_format
    CastErrorMessage.MEDIA_UNAVAILABLE -> R.string.cast_media_unavailable
    CastErrorMessage.PROTOCOL -> R.string.cast_error_protocol
}
