package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import com.google.protobuf.InvalidProtocolBufferException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

class CastV2FramingTest {

    private fun createValidMessage(payloadStr: String = "{}"): CastMessage {
        return CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId("sender")
            .setDestinationId("receiver")
            .setNamespace("urn:x-cast:com.google.cast.media")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payloadStr)
            .build()
    }

    // A custom InputStream that returns at most `chunkSize` bytes at a time
    class ChunkedInputStream(private val data: ByteArray, private val chunkSize: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int {
            if (pos >= data.size) return -1
            return data[pos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val bytesToRead = minOf(len, chunkSize, data.size - pos)
            System.arraycopy(data, pos, b, off, bytesToRead)
            pos += bytesToRead
            return bytesToRead
        }
    }

    @Test
    fun `1 writeFrame and readFrame with symmetric valid message`() {
        val original = createValidMessage("valid_payload")
        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, original)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val decoded = CastV2Framing.readFrame(inputStream)

        assertEquals("valid_payload", decoded.payloadUtf8)
    }

    @Test
    fun `2 Header must be exactly 4 bytes Big-Endian`() {
        val original = createValidMessage()
        val payloadBytes = original.toByteArray()
        val expectedLength = payloadBytes.size

        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, original)

        val fullBytes = outputStream.toByteArray()
        val lengthBuffer = ByteBuffer.wrap(fullBytes, 0, 4)
        val readLength = lengthBuffer.int // ByteBuffer is BigEndian by default

        assertEquals(expectedLength, readLength)
    }

    @Test
    fun `3 Header fragmented over multiple reads should still succeed`() {
        val original = createValidMessage()
        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, original)

        // Chunk size = 1 means header needs 4 separate reads
        val chunkedStream = ChunkedInputStream(outputStream.toByteArray(), 1)
        val decoded = CastV2Framing.readFrame(chunkedStream)

        assertEquals(original.payloadUtf8, decoded.payloadUtf8)
    }

    @Test
    fun `4 Payload fragmented over multiple reads should still succeed`() {
        val original = createValidMessage("A".repeat(100))
        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, original)

        // Chunk size = 10, payload will require ~10 reads
        val chunkedStream = ChunkedInputStream(outputStream.toByteArray(), 10)
        val decoded = CastV2Framing.readFrame(chunkedStream)

        assertEquals(original.payloadUtf8, decoded.payloadUtf8)
    }

    @Test
    fun `5 Multiple frames back-to-back on same stream should decode fine`() {
        val first = createValidMessage("first")
        val second = createValidMessage("second")
        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, first)
        CastV2Framing.writeFrame(outputStream, second)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val decodedFirst = CastV2Framing.readFrame(inputStream)
        val decodedSecond = CastV2Framing.readFrame(inputStream)

        assertEquals("first", decodedFirst.payloadUtf8)
        assertEquals("second", decodedSecond.payloadUtf8)
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun `6 EOF before full header throws EOFException`() {
        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, createValidMessage())
        // Take only 2 bytes of the header
        val brokenBytes = outputStream.toByteArray().copyOfRange(0, 2)
        val stream = ByteArrayInputStream(brokenBytes)

        val e = assertThrows(EOFException::class.java) {
            CastV2Framing.readFrame(stream)
        }
        assertEquals("Unexpected EOF. Expected 4 bytes, got 2 bytes", e.message)
    }

    @Test
    fun `7 EOF before full payload throws EOFException`() {
        val outputStream = ByteArrayOutputStream()
        CastV2Framing.writeFrame(outputStream, createValidMessage())
        val bytes = outputStream.toByteArray()
        // Take header (4 bytes) + 1 byte of payload, cut off the rest
        val brokenBytes = bytes.copyOfRange(0, 5)
        val stream = ByteArrayInputStream(brokenBytes)

        val e = assertThrows(EOFException::class.java) {
            CastV2Framing.readFrame(stream)
        }
        assert(e.message?.contains("Unexpected EOF") == true)
    }

    @Test
    fun `8 Payload exceeding limit throws IllegalArgumentException`() {
        // Construct custom malicious frame header
        val invalidLength = CastV2Framing.MAX_FRAME_SIZE + 1
        val header = ByteArray(4)
        header[0] = (invalidLength ushr 24).toByte()
        header[1] = (invalidLength ushr 16).toByte()
        header[2] = (invalidLength ushr 8).toByte()
        header[3] = invalidLength.toByte()

        val stream = ByteArrayInputStream(header) // only sending header

        val e = assertThrows(IllegalArgumentException::class.java) {
            CastV2Framing.readFrame(stream)
        }
        assert(e.message?.contains("exceeds limit") == true)
    }

    @Test
    fun `9 Invalid Protobuf payload throws InvalidProtocolBufferException`() {
        val invalidPayload = "ThisIsNotAValidProtobufSequence".toByteArray()
        val length = invalidPayload.size

        val header = ByteArray(4)
        header[0] = (length ushr 24).toByte()
        header[1] = (length ushr 16).toByte()
        header[2] = (length ushr 8).toByte()
        header[3] = length.toByte()

        val combined = ByteArrayOutputStream().apply {
            write(header)
            write(invalidPayload)
        }.toByteArray()

        val stream = ByteArrayInputStream(combined)
        assertThrows(InvalidProtocolBufferException::class.java) {
            CastV2Framing.readFrame(stream) // Should throw from parseFrom()
        }
    }

    @Test
    fun `10 Zero-length frame throws IllegalArgumentException`() {
        val header = ByteArray(4) // 0, 0, 0, 0
        val stream = ByteArrayInputStream(header)

        val e = assertThrows(IllegalArgumentException::class.java) {
            CastV2Framing.readFrame(stream)
        }
        assertEquals("Zero-length frame is strictly not permitted", e.message)
    }
}

