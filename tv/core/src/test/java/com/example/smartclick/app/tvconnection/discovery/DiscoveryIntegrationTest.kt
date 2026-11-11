package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import android.net.Network
import com.example.smartclick.app.domain.connection.IDiscoveryProbe
import com.example.smartclick.app.domain.connection.IWifiNetworkProvider
import com.example.smartclick.app.domain.connection.RawCandidate
import com.example.smartclick.app.domain.model.BrandProfile
import com.example.smartclick.app.domain.model.ProbePlan
import com.example.smartclick.app.domain.model.TvDevice
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.repository.IBrandProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoveryIntegrationTest {

    private lateinit var brandProfileRepo: IBrandProfileRepository
    private lateinit var wifiNetworkProvider: IWifiNetworkProvider
    private lateinit var probeFactory: ProbeFactory
    private lateinit var fingerprintEngine: DeviceFingerprintEngine
    private lateinit var orchestrator: DiscoveryOrchestrator
    private lateinit var testDispatcher: TestDispatcher

    private lateinit var mockNetwork: Network
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockContext = mockk()
        every { mockContext.applicationContext } returns mockContext
        val mockWifiManager = mockk<android.net.wifi.WifiManager>(relaxed = true)
        every { mockContext.getSystemService(Context.WIFI_SERVICE) } returns mockWifiManager

        brandProfileRepo = mockk()
        wifiNetworkProvider = mockk()
        probeFactory = mockk()
        fingerprintEngine = mockk()
        mockNetwork = mockk()

        testDispatcher = StandardTestDispatcher()

        orchestrator = DiscoveryOrchestrator(
            mockContext,
            brandProfileRepo,
            wifiNetworkProvider,
            probeFactory,
            fingerprintEngine,
            testDispatcher
        )
    }

    @Test
    fun `scanForDevices handles mixed async sources where mDNS is fast and SSDP is delayed`() = runTest(testDispatcher) {
        every { wifiNetworkProvider.getActiveWifiNetwork() } returns mockNetwork
        coEvery { brandProfileRepo.getProfile("all") } returns BrandProfile("all", "All", ProbePlan.All)

        val ssdpProbe = mockk<IDiscoveryProbe>()
        val mdnsProbe = mockk<IDiscoveryProbe>()

        val ssdpCandidate = RawCandidate("192.168.1.20", TvProtocol.DIAL_ONLY, location = "http://192.168.1.20:56700/dd.xml")
        val mdnsCandidate = RawCandidate("192.168.1.21", TvProtocol.ANDROID_TV_V2)

        every { mdnsProbe.discover(mockNetwork) } returns flowOf(mdnsCandidate)
        every { ssdpProbe.discover(mockNetwork) } returns flow {
            kotlinx.coroutines.delay(500)
            emit(ssdpCandidate)
        }

        every { probeFactory.createAllProbes() } returns listOf(ssdpProbe, mdnsProbe)

        val device1 = TvDevice("d1", "Android TV", "192.168.1.21", "mac1", protocol = TvProtocol.ANDROID_TV_V2)
        val device2 = TvDevice("d2", "DIAL TV", "192.168.1.20", "mac2", protocol = TvProtocol.DIAL_ONLY)

        coEvery { fingerprintEngine.identify(mdnsCandidate, null) } returns device1
        coEvery { fingerprintEngine.identify(ssdpCandidate, null) } returns device2

        val results = mutableListOf<List<TvDevice>>()
        val job = launch {
            orchestrator.scanForDevices("all").toList(results)
        }

        advanceUntilIdle()

        assertTrue(results.isNotEmpty())
        val finalDevices = results.last()
        assertEquals(2, finalDevices.size)
        assertTrue(finalDevices.contains(device1))
        assertTrue(finalDevices.contains(device2))

        job.cancel()
    }

    @Test
    fun `scanForDevices handles one discovery source throwing exception gracefully`() = runTest(testDispatcher) {
        every { wifiNetworkProvider.getActiveWifiNetwork() } returns mockNetwork
        coEvery { brandProfileRepo.getProfile("all") } returns BrandProfile("all", "All", ProbePlan.All)

        val failingProbe = mockk<IDiscoveryProbe>()
        val workingProbe = mockk<IDiscoveryProbe>()

        val workingCandidate = RawCandidate("192.168.1.30", TvProtocol.ROKU_ECP)

        every { failingProbe.discover(mockNetwork) } returns flow {
            throw RuntimeException("Discovery socket failure")
        }
        every { workingProbe.discover(mockNetwork) } returns flowOf(workingCandidate)

        every { probeFactory.createAllProbes() } returns listOf(failingProbe, workingProbe)

        val workingDevice = TvDevice("d3", "Roku TV", "192.168.1.30", "mac3", protocol = TvProtocol.ROKU_ECP)
        coEvery { fingerprintEngine.identify(workingCandidate, null) } returns workingDevice

        val results = mutableListOf<List<TvDevice>>()
        val job = launch {
            orchestrator.scanForDevices("all").toList(results)
        }

        advanceUntilIdle()

        assertTrue(results.isNotEmpty())
        val finalDevices = results.last()
        assertEquals(1, finalDevices.size)
        assertEquals(workingDevice, finalDevices[0])

        job.cancel()
    }

    @Test
    fun `scanForDevices deduplicates candidates sharing the same IP address across different probes`() = runTest(testDispatcher) {
        every { wifiNetworkProvider.getActiveWifiNetwork() } returns mockNetwork
        coEvery { brandProfileRepo.getProfile("all") } returns BrandProfile("all", "All", ProbePlan.All)

        val probe1 = mockk<IDiscoveryProbe>()
        val probe2 = mockk<IDiscoveryProbe>()

        val candidate1 = RawCandidate("192.168.1.50", TvProtocol.DIAL_ONLY)
        val candidate2 = RawCandidate("192.168.1.50", TvProtocol.ANDROID_TV_V2)

        every { probe1.discover(mockNetwork) } returns flowOf(candidate1)
        every { probe2.discover(mockNetwork) } returns flowOf(candidate2)

        every { probeFactory.createAllProbes() } returns listOf(probe1, probe2)

        val device = TvDevice("d50", "Shared IP TV", "192.168.1.50", "mac50", protocol = TvProtocol.DIAL_ONLY)
        coEvery { fingerprintEngine.identify(candidate1, null) } returns device

        val results = mutableListOf<List<TvDevice>>()
        val job = launch {
            orchestrator.scanForDevices("all").toList(results)
        }

        advanceUntilIdle()

        assertTrue(results.isNotEmpty())
        val finalDevices = results.last()
        assertEquals(1, finalDevices.size)
        assertEquals(device, finalDevices[0])

        job.cancel()
    }

    @Test
    fun `scanForDevices propagates CancellationException correctly`() = runTest(testDispatcher) {
        every { wifiNetworkProvider.getActiveWifiNetwork() } returns mockNetwork
        coEvery { brandProfileRepo.getProfile("all") } returns BrandProfile("all", "All", ProbePlan.All)

        val slowProbe = mockk<IDiscoveryProbe>()
        every { slowProbe.discover(mockNetwork) } returns flow {
            kotlinx.coroutines.delay(1000)
            emit(RawCandidate("192.168.1.60", TvProtocol.ROKU_ECP))
        }

        every { probeFactory.createAllProbes() } returns listOf(slowProbe)

        val job = launch {
            orchestrator.scanForDevices("all").toList(mutableListOf())
        }

        kotlinx.coroutines.yield()
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
    }
}
