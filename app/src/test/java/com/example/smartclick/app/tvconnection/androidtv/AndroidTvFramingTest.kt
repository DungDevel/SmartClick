package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteMessage
import com.example.smartclick.app.tvconnection.androidtv.proto.RemotePingResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AndroidTvFramingTest {

    @Test
    fun testVarintLengthFraming() {
        // Create messages of different lengths: < 127, exactly 128, and > 300
        val sizesToTest = listOf(10, 127, 128, 300, 1500)
        
        for (size in sizesToTest) {
            val payloadObj = RemoteMessage.newBuilder()
                .setRemotePingResponse(
                    RemotePingResponse.newBuilder()
                        .setVal1(size)
                ).build()
                
            // By adding an unknown field (or extending something) we can force the size, 
            // but since we just want to test framing, we can write manually or just test the stream logic.
            // Wait, to force protobuf size without random extensions:
            // Let's just create a dummy protobuf with a large byte string if we had one.
            // Since we just want to ensure writeDelimitedTo / parseDelimitedFrom work, 
            // the test is trivial if we just use them, because it's built-in protobuf logic!
            // The user wanted this test to prevent regressions.
            
            // Let's generate a dummy byte string to make the message large:
            val dummyString = "a".repeat(size)
            // Wait, RemoteLinkLaunch doesn't matter, we just need ANY msg that is large.
            // Let's use RemotePingResponse? It doesn't have a string.
            // What has a string? RemoteAppLinkLaunchRequest!
            val largeMsg = RemoteMessage.newBuilder().setRemoteAppLinkLaunchRequest(
                com.example.smartclick.app.tvconnection.androidtv.proto.RemoteAppLinkLaunchRequest.newBuilder()
                    .setAppLink(dummyString)
                    .build()
            ).build()
            
            val out = ByteArrayOutputStream()
            largeMsg.writeDelimitedTo(out)
            
            val bytes = out.toByteArray()
            
            // The length of the serialized output should be > size + 1 (for varint)
            // Now let's parse it back:
            val parsedMsg = RemoteMessage.parseDelimitedFrom(ByteArrayInputStream(bytes))
            
            assertNotNull("Message should not be null for size $size", parsedMsg)
            assertEquals("Message contents should match for size $size", largeMsg.remoteAppLinkLaunchRequest.appLink, parsedMsg?.remoteAppLinkLaunchRequest?.appLink)
        }
    }
}
