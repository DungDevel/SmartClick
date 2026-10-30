package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.data.catalog.CatalogException
import com.example.smartclick.app.data.catalog.ChannelCatalog
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvProtocol
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

sealed interface InstalledChannelsState {
    data object Loading : InstalledChannelsState
    data class Success(val apps: List<AppLaunchConfig>) : InstalledChannelsState
    data object Empty : InstalledChannelsState
    data object NoActiveDevice : InstalledChannelsState
    data object NotSupported : InstalledChannelsState
    data class Error(val reason: ErrorReason) : InstalledChannelsState
}

enum class ErrorReason { LOST_CONNECTION, TIMEOUT, PARSE, UNKNOWN }

class GetInstalledChannelsUseCase @Inject constructor(
    private val catalog: ChannelCatalog,
    private val activeTvSession: IActiveTvSession
) {
    suspend operator fun invoke(): InstalledChannelsState {
        val controller = activeTvSession.currentController.value
            ?: return InstalledChannelsState.NoActiveDevice
        if (TvCapability.INSTALLED_APPS !in controller.capabilities) return InstalledChannelsState.NotSupported
        if (controller.device == null) return InstalledChannelsState.NoActiveDevice
        if (controller.connectionState.value !is TvConnectionState.Connected) {
            return InstalledChannelsState.Error(ErrorReason.LOST_CONNECTION)
        }

        return try {
            val entries = catalog.load().getOrElse {
                return InstalledChannelsState.Error(classify(it))
            }
            val installedApps = controller.getApps().getOrElse {
                return if (it is UnsupportedOperationException) InstalledChannelsState.NotSupported
                else InstalledChannelsState.Error(classify(it))
            }
            val protocolKey = controller.device?.protocol?.catalogKey()
                ?: return InstalledChannelsState.NoActiveDevice
            val installedById = installedApps.associateBy { it.id }
            val matched = entries.asSequence()
                .filter { it.type == "app" }
                .mapNotNull { entry ->
                    val protocolId = entry.protocolIds[protocolKey] ?: return@mapNotNull null
                    val installed = installedById[protocolId] ?: return@mapNotNull null
                    AppLaunchConfig(entry.id, entry.name, installed.id)
                }
                .toList()
            if (matched.isEmpty()) InstalledChannelsState.Empty else InstalledChannelsState.Success(matched)
        } catch (error: Throwable) {
            if (error is TimeoutCancellationException) return InstalledChannelsState.Error(ErrorReason.TIMEOUT)
            if (error is kotlinx.coroutines.CancellationException) throw error
            InstalledChannelsState.Error(classify(error))
        }
    }

    private fun classify(error: Throwable): ErrorReason = when (error) {
        is CatalogException, is org.xmlpull.v1.XmlPullParserException -> ErrorReason.PARSE
        is SocketTimeoutException, is TimeoutException, is TimeoutCancellationException -> ErrorReason.TIMEOUT
        is IOException -> ErrorReason.LOST_CONNECTION
        else -> ErrorReason.UNKNOWN
    }

    private fun TvProtocol.catalogKey(): String? = when (this) {
        TvProtocol.ROKU_ECP -> "roku"
        TvProtocol.LG_WEBOS, TvProtocol.LG_NETCAST -> "lgwebos"
        TvProtocol.SAMSUNG_TIZEN, TvProtocol.SAMSUNG_LEGACY -> "samsung"
        TvProtocol.ANDROID_TV_V1, TvProtocol.ANDROID_TV_V2 -> "androidtv"
        else -> null
    }
}
