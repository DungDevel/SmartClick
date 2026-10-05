package com.example.smartclick.app.tvconnection.androidtv

import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.UUID

class AndroidTvDriverTest : AbstractDriverContractTest() {

    @Before
    fun setup() {
        mockkObject(AndroidTvCertificateManager)
        every { AndroidTvCertificateManager.getOrGenerateClientKeyAndCertificate() } returns Pair(
            mockk<PrivateKey>(relaxed = true),
            mockk<X509Certificate>(relaxed = true)
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    override fun createDriver(): IProtocolDriver = AndroidTvDriver()

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = UUID.randomUUID().toString(),
        name = "Android TV",
        ipAddress = "192.168.1.100",
        macAddress = "",
        protocol = TvProtocol.ANDROID_TV_V2,
        isPaired = true,
        capabilities = emptySet()
    )
}
