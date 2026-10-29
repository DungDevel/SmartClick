package com.example.smartclick.app.tvconnection.firetv

import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FireTvController(
    private val ipAddress: String,
    private val port: Int = 5555
) : ITvController {

    private val _connectionState = MutableStateFlow<TvConnectionState>(TvConnectionState.Idle)
    override val connectionState: StateFlow<TvConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: TvDevice? = null
    override val device: TvDevice? get() = currentDevice

    override val capabilities: Set<TvCapability> = setOf(
        TvCapability.VOLUME,
        TvCapability.MUTE_ON_OFF,
        TvCapability.POWER_TOGGLE,
        TvCapability.POWER_ON,
        TvCapability.POWER_OFF,
        TvCapability.DPAD,
        TvCapability.BACKSPACE
    )

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), 3000)
                val outputStream = socket.outputStream

                // ADB Connect Packet: A_CNX (command = 0x434e5843)
                val banner = "device::\u0000".toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(0x434e5843) // A_CNX
                    putInt(0x01000000) // version
                    putInt(1048576)    // maxdata
                    putInt(banner.size)
                    putInt(0)          // checksum
                    putInt(0x434e5843.inv()) // magic
                }.array()

                outputStream.write(header)
                outputStream.write(banner)
                outputStream.flush()

                currentDevice = device
                _connectionState.value = TvConnectionState.Connected(device)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _connectionState.value = TvConnectionState.Failed(e.message ?: "ADB connection failed")
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = TvConnectionState.Idle
        currentDevice = null
    }

    override suspend fun sendCommand(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        val keyCode = FireTvKeyMapper.mapCommandToKeyCode(command) ?: return@withContext Result.success(Unit)

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), 3000)
                val outputStream: OutputStream = socket.outputStream

                val shellCmd = "shell:input keyevent $keyCode\n"
                outputStream.write(shellCmd.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
