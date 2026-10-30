package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.data.catalog.CatalogEntry
import com.example.smartclick.app.data.catalog.ChannelCatalog
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.TvApp
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetInstalledChannelsUseCaseTest {
    private val catalog = mockk<ChannelCatalog>()

    private fun entries() = listOf(
        CatalogEntry("app:youtube", "YouTube", "apps", mapOf("roku" to "837"), emptyList(), "app"),
        CatalogEntry("app:netflix", "Netflix", "apps", mapOf("roku" to "12"), emptyList(), "app"),
        CatalogEntry("channel:vtv", "VTV", "channels", mapOf("roku" to "1"), emptyList(), "channel")
    )

    private fun session(controller: ITvController?): IActiveTvSession {
        val session = mockk<IActiveTvSession>()
        every { session.currentController } returns MutableStateFlow(controller)
        return session
    }

    private fun controller(capabilities: Set<TvCapability> = setOf(TvCapability.INSTALLED_APPS)): ITvController {
        val controller = mockk<ITvController>()
        val device = TvDevice("tv", "TV", "192.168.1.10", "00:00:00:00:00:00", protocol = TvProtocol.ROKU_ECP)
        every { controller.device } returns device
        every { controller.capabilities } returns capabilities
        every { controller.connectionState } returns MutableStateFlow(TvConnectionState.Connected(device))
        return controller
    }

    @Test fun `intersects protocol ids and hides installed apps outside catalog`() = runTest {
        every { catalog.load() } returns Result.success(entries())
        val controller = controller()
        coEvery { controller.getApps() } returns Result.success(listOf(TvApp("837", "YouTube"), TvApp("external", "Other")))
        val result = GetInstalledChannelsUseCase(catalog, session(controller))()
        assertTrue(result is InstalledChannelsState.Success)
        assertEquals(listOf("app:youtube"), (result as InstalledChannelsState.Success).apps.map { it.appId })
        assertEquals("837", result.apps.single().packageName)
    }

    @Test fun `missing installed apps capability is not supported`() = runTest {
        assertEquals(InstalledChannelsState.NotSupported, GetInstalledChannelsUseCase(catalog, session(controller(emptySet())))())
    }

    @Test fun `controller throwing unsupported is not supported`() = runTest {
        every { catalog.load() } returns Result.success(entries())
        val controller = controller()
        coEvery { controller.getApps() } throws UnsupportedOperationException("not supported")
        assertEquals(InstalledChannelsState.NotSupported, GetInstalledChannelsUseCase(catalog, session(controller))())
    }

    @Test fun `getApps failure becomes classified error`() = runTest {
        every { catalog.load() } returns Result.success(entries())
        val controller = controller()
        coEvery { controller.getApps() } returns Result.failure(java.net.SocketTimeoutException())
        val result = GetInstalledChannelsUseCase(catalog, session(controller))()
        assertEquals(ErrorReason.TIMEOUT, (result as InstalledChannelsState.Error).reason)
    }

    @Test fun `parser failure is classified separately from connection failures`() = runTest {
        every { catalog.load() } returns Result.success(entries())
        val controller = controller()
        coEvery { controller.getApps() } returns Result.failure(org.xmlpull.v1.XmlPullParserException("bad xml"))
        val result = GetInstalledChannelsUseCase(catalog, session(controller))()
        assertEquals(ErrorReason.PARSE, (result as InstalledChannelsState.Error).reason)
    }

    @Test fun `disconnected active controller is a lost connection`() = runTest {
        val controller = controller()
        every { controller.connectionState } returns MutableStateFlow(TvConnectionState.Idle)
        assertEquals(ErrorReason.LOST_CONNECTION, (GetInstalledChannelsUseCase(catalog, session(controller))() as InstalledChannelsState.Error).reason)
    }

    @Test fun `no active controller is distinguished`() = runTest {
        assertEquals(InstalledChannelsState.NoActiveDevice, GetInstalledChannelsUseCase(catalog, session(null))())
    }

    @Test fun `no matching catalog apps is empty`() = runTest {
        every { catalog.load() } returns Result.success(entries())
        val controller = controller()
        coEvery { controller.getApps() } returns Result.success(listOf(TvApp("external", "Other")))
        assertEquals(InstalledChannelsState.Empty, GetInstalledChannelsUseCase(catalog, session(controller))())
    }

    @Test fun `launch reports controller success and failure honestly`() = runTest {
        val controller = controller()
        val session = session(controller)
        val useCase = LaunchAppOnTvUseCase(session, mockk(relaxed = true), mockk(relaxed = true))
        coEvery { controller.launchApp("837") } returns Result.success(Unit)
        assertTrue(useCase("837").isSuccess)
        coEvery { controller.launchApp("837") } returns Result.failure(IllegalStateException("rejected"))
        assertTrue(useCase("837").isFailure)
    }
}
