package com.example.smartclick.app.domain.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

object WakeOnLan {
    suspend fun wake(macStr: String?): Boolean = withContext(Dispatchers.IO) {
        if (macStr.isNullOrBlank()) {
            Log.e("WOL", "MAC address is empty")
            return@withContext false
        }
        try {
            val macBytes = getMacBytes(macStr)
            val bytes = ByteArray(6 + 16 * macBytes.size)
            for (i in 0..5) {
                bytes[i] = 0xff.toByte()
            }
            var i = 6
            while (i < bytes.size) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                i += macBytes.size
            }
            val address = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(bytes, bytes.size, address, 9)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
            Log.d("WOL", "Magic packet sent to $macStr")
            true
        } catch (e: Exception) {
            Log.e("WOL", "Failed to send WOL packet", e)
            false
        }
    }

    private fun getMacBytes(macStr: String): ByteArray {
        val bytes = ByteArray(6)
        val hex = macStr.split(":", "-")
        require(hex.size == 6) { "Invalid MAC address." }
        for (i in 0..5) {
            bytes[i] = hex[i].toInt(16).toByte()
        }
        return bytes
    }
}
