package com.example.smartclick.app.features.onboarding

import com.example.smartclick.R
import com.example.smartclick.app.domain.connection.PairingFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionMessageMapperTest {

    @Test
    fun `connection failure reasons map to safe localized categories`() {
        assertEquals(R.string.connection_error_no_driver, ConnectionMessageMapper.connectionFailure("No driver found for protocol: UNKNOWN"))
        assertEquals(R.string.connection_error_unsupported, ConnectionMessageMapper.connectionFailure("Unsupported protocol"))
        assertEquals(R.string.connection_error_certificate, ConnectionMessageMapper.connectionFailure("failed to create certificate-pinned LG client"))
        assertEquals(R.string.connection_error_pairing, ConnectionMessageMapper.connectionFailure("Pairing credentials invalid"))
        assertEquals(R.string.connection_error_network, ConnectionMessageMapper.connectionFailure("Network connection timed out"))
        assertEquals(R.string.connection_error_generic, ConnectionMessageMapper.connectionFailure("sensitive exception detail"))
    }

    @Test
    fun `pairing failures map to resources without exposing raw messages`() {
        assertEquals(R.string.connection_error_certificate, ConnectionMessageMapper.pairingFailure(PairingFailure.Network("certificate-pinned client failed")))
        assertEquals(R.string.connection_error_network, ConnectionMessageMapper.pairingFailure(PairingFailure.Network("socket timeout")))
        assertEquals(R.string.connection_error_generic, ConnectionMessageMapper.pairingFailure(PairingFailure.Unknown("private exception details")))
    }
}
