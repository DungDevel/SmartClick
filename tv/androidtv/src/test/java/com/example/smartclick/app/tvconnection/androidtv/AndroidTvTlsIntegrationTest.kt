package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTvTlsIntegrationTest {

    private lateinit var server: MockAndroidTvServer
    private lateinit var session: AndroidTvRemoteSession

    @Before
    fun setup() {
        server = MockAndroidTvServer()
        server.start()

        // Wait briefly for socket to be ready
        Thread.sleep(100)

        session = AndroidTvRemoteSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientCertPair = server.clientCertPair,
            expectedCertSha256 = server.serverFingerprint
        )
    }

    @After
    fun teardown() {
        server.stop()
    }

    @Test
    fun `test TLS connection fails with invalid expectedCertSha256`() = runTest(timeout = 5.seconds) {
        val invalidSession = AndroidTvRemoteSession(
            ipAddress = "127.0.0.1",
            port = server.port,
            clientCertPair = server.clientCertPair,
            expectedCertSha256 = "invalid_hash"
        )

        val dummyDevice = TvDevice(
            id = "test_dev",
            name = "Test TV",
            ipAddress = "127.0.0.1",
            macAddress = "00:11:22:33:44:55",
            protocol = com.example.smartclick.app.domain.model.TvProtocol.ANDROID_TV_V2
        )

        val connectResult = invalidSession.connect(dummyDevice)
        assertTrue("Connect should fail (or return failure) due to TLS validation error", connectResult.isFailure)

        val finalState = invalidSession.connectionState.value
        assertTrue(finalState is TvConnectionState.Failed)
    }

    @Test
    fun `test TLS connection and handshake with mock server`() = runTest(timeout = 5.seconds) {
        val dummyDevice = TvDevice(
            id = "test_dev",
            name = "Test TV",
            ipAddress = "127.0.0.1",
            macAddress = "00:11:22:33:44:55",
            protocol = com.example.smartclick.app.domain.model.TvProtocol.ANDROID_TV_V2
        )

        val connectResult = session.connect(dummyDevice)
        assertTrue("Connect should succeed", connectResult.isSuccess)

        val connectedState = session.connectionState.first { it is TvConnectionState.Connected }
        assertTrue(connectedState is TvConnectionState.Connected)
        assertEquals(dummyDevice, (connectedState as TvConnectionState.Connected).device)

        // Test sending a command
        val cmdResult = session.sendCommand(RemoteCommand.Ok)
        assertTrue("Command should succeed", cmdResult.isSuccess)

        session.disconnect()
        val finalState = session.connectionState.value
        assertTrue(finalState is TvConnectionState.Idle)
    }
}
