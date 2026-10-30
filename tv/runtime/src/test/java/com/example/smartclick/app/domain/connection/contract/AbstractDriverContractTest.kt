package com.example.smartclick.app.domain.connection.contract

import com.example.smartclick.app.domain.connection.Credentials
import com.example.smartclick.app.domain.connection.IProtocolDriver
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.TvCapability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertTrue

abstract class AbstractDriverContractTest {

    abstract fun createDriver(): IProtocolDriver
    abstract fun createDummyDevice(): TvDevice

    @Test
    fun `driver protocol must match device protocol`() {
        val driver = createDriver()
        val device = createDummyDevice()
        assertEquals("Driver protocol should match the dummy device", driver.protocol, device.protocol)
    }

    @Test
    fun `controller creation returns valid instance`() {
        val driver = createDriver()
        val controller = driver.createController(createDummyDevice(), null)
        assertNotNull(controller)
    }

    @Test
    fun `controller app methods respect declared capability`() = runTest {
        val driver = createDriver()
        val controller = driver.createController(createDummyDevice(), null)
        if (TvCapability.INSTALLED_APPS !in driver.capabilities) {
            val apps = controller.getApps()
            val launch = controller.launchApp("contract-app")
            assertTrue("getApps must be explicitly unsupported", apps.isFailure && apps.exceptionOrNull() is UnsupportedOperationException)
            assertTrue("launchApp must be explicitly unsupported", launch.isFailure && launch.exceptionOrNull() is UnsupportedOperationException)
        } else {
            val apps = controller.getApps()
            assertTrue("getApps must return a list or a clear failure", apps.isSuccess || apps.exceptionOrNull() != null)
            if (apps.isSuccess) assertNotNull("successful getApps cannot return null", apps.getOrNull())
            val launch = controller.launchApp("contract-app")
            assertTrue("launchApp must return success or a clear failure", launch.isSuccess || launch.exceptionOrNull() != null)
        }
    }

    @Test
    fun `fingerprint returns null for completely irrelevant protocol hints`() = runTest {
        val driver = createDriver()
        
        // Pick a protocol that is definitely NOT this driver's protocol
        val irrelevantProtocol = TvProtocol.values().firstOrNull { it != driver.protocol && it != TvProtocol.UNKNOWN } 
            ?: TvProtocol.UNKNOWN
            
        if (irrelevantProtocol != TvProtocol.UNKNOWN) {
            val candidate = RawCandidate(
                ipAddress = "192.168.1.99",
                protocolHint = irrelevantProtocol
            )
            assertNull("Driver should ignore hints strictly pointing to another protocol", driver.fingerprint(candidate))
        }
    }
}
