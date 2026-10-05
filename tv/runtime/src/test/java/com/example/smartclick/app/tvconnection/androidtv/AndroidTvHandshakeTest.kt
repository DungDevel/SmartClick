package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteConfigure
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteDeviceInfo
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteMessage
import com.example.smartclick.app.tvconnection.androidtv.proto.RemoteSetActive
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate

class AndroidTvHandshakeTest {

    @Test
    fun testHandshakeResponses() {
        // Prepare dummy keystore keys just to instantiate the session
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val dummyCert = mockk<X509Certificate>()

        val session = AndroidTvRemoteSession("127.0.0.1", 6466, Pair(keyPair.private, dummyCert), null)

        // Use reflection to capture output and invoke private methods
        val outputStream = ByteArrayOutputStream()

        // Mock socket output stream inside session
        val osField: Field = AndroidTvRemoteSession::class.java.getDeclaredField("outputStream")
        osField.isAccessible = true
        osField.set(session, outputStream)

        val handleMessageMethod: Method = AndroidTvRemoteSession::class.java.getDeclaredMethod("handleMessage", RemoteMessage::class.java)
        handleMessageMethod.isAccessible = true

        // 1. Test RemoteConfigure handshake
        val configureReq = RemoteMessage.newBuilder().setRemoteConfigure(
            RemoteConfigure.newBuilder().setCode1(622).setDeviceInfo(
                RemoteDeviceInfo.newBuilder().setModel("TV").setVendor("TVVendor").build()
            ).build()
        ).build()

        handleMessageMethod.invoke(session, configureReq)

        // Parse the sent response
        // Note: writeDelimitedTo was used, so we need to parse with parseDelimitedFrom
        val configureBytes = outputStream.toByteArray()
        val parsedConfigureResp = RemoteMessage.parseDelimitedFrom(configureBytes.inputStream())

        assertTrue("Should reply with RemoteConfigure", parsedConfigureResp.hasRemoteConfigure())
        assertEquals("Config code1 should be 622", 622, parsedConfigureResp.remoteConfigure.code1)
        assertEquals("Vendor should be SmartClick", "SmartClick", parsedConfigureResp.remoteConfigure.deviceInfo.vendor)
        assertEquals("Model should be SmartClick", "SmartClick", parsedConfigureResp.remoteConfigure.deviceInfo.model)

        outputStream.reset()

        // 2. Test RemoteSetActive handshake
        val setActiveReq = RemoteMessage.newBuilder().setRemoteSetActive(
            RemoteSetActive.newBuilder().setActive(622).build()
        ).build()

        handleMessageMethod.invoke(session, setActiveReq)

        val setActiveBytes = outputStream.toByteArray()
        val parsedSetActiveResp = RemoteMessage.parseDelimitedFrom(setActiveBytes.inputStream())

        assertTrue("Should reply with RemoteSetActive", parsedSetActiveResp.hasRemoteSetActive())
        assertEquals("Active code should be 622", 622, parsedSetActiveResp.remoteSetActive.active)
    }
}
