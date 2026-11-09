package com.example.smartclick.app.domain.connection.fake

import com.example.smartclick.app.domain.connection.contract.AbstractDriverContractTest
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol

class FakeDriverTest : AbstractDriverContractTest() {

    override fun createDriver(): IProtocolDriver = FakeDriver()

    override fun createDummyDevice(): TvDevice = TvDevice(
        id = "fake-device",
        name = "Fake TV",
        ipAddress = "127.0.0.1",
        macAddress = "",
        protocol = TvProtocol.UNKNOWN,
        isPaired = true,
        capabilities = emptySet()
    )
}
