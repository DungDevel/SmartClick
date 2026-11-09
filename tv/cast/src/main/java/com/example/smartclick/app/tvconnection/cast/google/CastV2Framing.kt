package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

object CastV2Framing {

    /**
     * Maximum allowed payload length for CastV2 frames to prevent unbounded memory allocation.
     * We set a safe upper bound (256 KB). Standard is usually up to 64KB.
     */
    const val MAX_FRAME_SIZE = 256 * 1024

    /**
     * Writes a CastMessage with a 4-byte Big-Endian length header.
     */
    fun writeFrame(outputStream: OutputStream, message: CastMessage) {
        val payload = message.toByteArray()
        val length = payload.size

        val header = ByteArray(4)
        header[0] = (length ushr 24).toByte()
        header[1] = (length ushr 16).toByte()
        header[2] = (length ushr 8).toByte()
        header[3] = length.toByte()

        outputStream.write(header)
        outputStream.write(payload)
        outputStream.flush()
    }

    /**
     * Reads exactly one CastMessage from the stream.
     * Blocks until the 4-byte header and following payload are fully read.
     * Throws an EOFException if the stream drops prematurely.
     * Throws an IllegalArgumentException if framing rules are violated.
     */
    @Throws(EOFException::class, IllegalArgumentException::class)
    fun readFrame(inputStream: InputStream): CastMessage {
        val header = readExactly(inputStream, 4)

        // Interpret Big-Endian size
        val length = ((header[0].toInt() and 0xFF) shl 24) or
                     ((header[1].toInt() and 0xFF) shl 16) or
                     ((header[2].toInt() and 0xFF) shl 8) or
                     ((header[3].toInt() and 0xFF))

        if (length < 0) {
            throw IllegalArgumentException("Invalid frame length: $length (negative)")
        }
        if (length > MAX_FRAME_SIZE) {
            throw IllegalArgumentException("Frame length exceeds limit: $length > $MAX_FRAME_SIZE")
        }
        if (length == 0) {
            throw IllegalArgumentException("Zero-length frame is strictly not permitted")
        }

        val payload = readExactly(inputStream, length)

        // Invalid Protocol Buffer formats will throw InvalidProtocolBufferException (subclass of IOException)
        return CastMessage.parseFrom(payload)
    }

    /**
     * Ensures we read exactly the requested amount of bytes, handling stream fragmentation.
     */
    @Throws(EOFException::class)
    private fun readExactly(inputStream: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val bytesRead = inputStream.read(buffer, totalRead, length - totalRead)
            if (bytesRead == -1) {
                throw EOFException("Unexpected EOF. Expected $length bytes, got $totalRead bytes")
            }
            totalRead += bytesRead
        }
        return buffer
    }
}

