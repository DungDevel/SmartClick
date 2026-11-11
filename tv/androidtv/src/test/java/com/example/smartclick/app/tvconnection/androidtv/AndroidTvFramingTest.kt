package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteAppLinkLaunchRequest
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

class AndroidTvFramingTest {

    @Test
    fun testVarintLengthPrefixBoundaries() {
        // Test byte prefix sizes at critical varint boundaries
        // 127 bytes is the max for a 1-byte varint prefix
        // 128 bytes requires a 2-byte varint prefix
        val sizesToTest = listOf(10, 127, 128, 300)

        for (size in sizesToTest) {
            val dummyString = "a".repeat(size)
            val largeMsg = RemoteMessage.newBuilder().setRemoteAppLinkLaunchRequest(
                RemoteAppLinkLaunchRequest.newBuilder()
                    .setAppLink(dummyString)
                    .build()
            ).build()

            val out = ByteArrayOutputStream()
            largeMsg.writeDelimitedTo(out)
            val bytes = out.toByteArray()

            // Validate varint prefix sizes based on message payload size
            val payloadSize = largeMsg.serializedSize
            val prefixSize = bytes.size - payloadSize

            if (payloadSize <= 127) {
                assertEquals("Payload size $payloadSize should have 1 byte varint prefix", 1, prefixSize)
                // First byte is the size itself
                assertEquals("First byte should be payload size", payloadSize.toByte(), bytes[0])
            } else {
                assertEquals("Payload size $payloadSize > 127 should have 2 bytes varint prefix (for up to 16383)", 2, prefixSize)
                // Decode 2-byte varint to verify
                val decodedSize = (bytes[0].toInt() and 0x7F) or ((bytes[1].toInt() and 0x7F) shl 7)
                assertEquals("Decoded varint size should match payload size", payloadSize, decodedSize)
            }

            // Verify parsing back
            val parsedMsg = RemoteMessage.parseDelimitedFrom(ByteArrayInputStream(bytes))
            assertNotNull("Message should parse correctly", parsedMsg)
            assertEquals("Message contents should match", dummyString, parsedMsg?.remoteAppLinkLaunchRequest?.appLink)
        }
    }
}
