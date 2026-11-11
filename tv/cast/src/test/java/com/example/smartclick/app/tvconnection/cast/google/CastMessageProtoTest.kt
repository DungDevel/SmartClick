package com.example.smartclick.app.tvconnection.cast.google

import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.CastMessage
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.AuthChallenge
import com.example.smartclick.app.tvconnection.cast.google.proto.CastChannel.DeviceAuthMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMessageProtoTest {

    @Test
    fun `serialize and deserialize a valid CastMessage with string payload`() {
        // Arrange
        val originalMessage = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId("sender-123")
            .setDestinationId("receiver-456")
            .setNamespace("urn:x-cast:com.google.cast.media")
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8("{\"type\":\"PLAY\"}")
            .build()

        // Act
        val byteArray = originalMessage.toByteArray()
        val deserializedMessage = CastMessage.parseFrom(byteArray)

        // Assert
        assertEquals(CastMessage.ProtocolVersion.CASTV2_1_0, deserializedMessage.protocolVersion)
        assertEquals("sender-123", deserializedMessage.sourceId)
        assertEquals("receiver-456", deserializedMessage.destinationId)
        assertEquals("urn:x-cast:com.google.cast.media", deserializedMessage.namespace)
        assertEquals(CastMessage.PayloadType.STRING, deserializedMessage.payloadType)
        assertEquals("{\"type\":\"PLAY\"}", deserializedMessage.payloadUtf8)
        assertTrue(deserializedMessage.hasPayloadUtf8())
    }

    @Test
    fun `serialize and deserialize AuthChallenge in DeviceAuthMessage`() {
        // Arrange
        val challenge = AuthChallenge.newBuilder()
            .setSignatureAlgorithm(AuthChallenge.SignatureAlgorithm.RSASSA_PSS)
            .setHashAlgorithm(AuthChallenge.HashAlgorithm.SHA256)
            .build()

        val originalMessage = DeviceAuthMessage.newBuilder()
            .setChallenge(challenge)
            .build()

        // Act
        val byteArray = originalMessage.toByteArray()
        val deserializedMessage = DeviceAuthMessage.parseFrom(byteArray)

        // Assert
        assertTrue(deserializedMessage.hasChallenge())
        assertEquals(AuthChallenge.SignatureAlgorithm.RSASSA_PSS, deserializedMessage.challenge.signatureAlgorithm)
        assertEquals(AuthChallenge.HashAlgorithm.SHA256, deserializedMessage.challenge.hashAlgorithm)
    }
}
