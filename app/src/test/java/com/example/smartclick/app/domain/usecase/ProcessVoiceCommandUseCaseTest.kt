package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.ITvController
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.model.TvProtocol
import com.example.smartclick.app.domain.model.voice.AppSymbol
import com.example.smartclick.app.domain.model.voice.Confidence
import com.example.smartclick.app.domain.model.voice.VoiceIntent
import com.example.smartclick.app.domain.voice.AppRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProcessVoiceCommandUseCaseTest {

    private lateinit var activeTvSession: IActiveTvSession
    private lateinit var sendCommandUseCase: SendCommandUseCase
    private lateinit var launchAppOnTvUseCase: LaunchAppOnTvUseCase
    private lateinit var appRegistry: AppRegistry
    private lateinit var useCase: ProcessVoiceCommandUseCase

    private lateinit var controllerFlow: MutableStateFlow<ITvController?>
    private lateinit var controller: ITvController

    @Before
    fun setup() {
        activeTvSession = mockk()
        sendCommandUseCase = mockk()
        launchAppOnTvUseCase = mockk()
        appRegistry = mockk()

        controllerFlow = MutableStateFlow(null)
        every { activeTvSession.currentController } returns controllerFlow

        controller = mockk()
        val mockedDevice = mockk<com.example.smartclick.app.domain.model.TvDevice>()
        every { mockedDevice.protocol } returns TvProtocol.LG_WEBOS
        every { controller.device } returns mockedDevice
        every { controller.capabilities } returns setOf(TvCapability.VOLUME, TvCapability.INSTALLED_APPS)

        useCase = ProcessVoiceCommandUseCase(
            activeTvSession,
            sendCommandUseCase,
            launchAppOnTvUseCase,
            appRegistry
        )
    }

    @Test
    fun `invoke with no controller returns NOT_CONNECTED`() = runTest {
        val result = useCase(VoiceIntent.MuteToggle("mute"))
        assertEquals(VoiceCommandResult.NOT_CONNECTED, result)
    }

    @Test
    fun `invoke Unrecognized returns UNRECOGNIZED`() = runTest {
        controllerFlow.value = controller
        val result = useCase(VoiceIntent.Unrecognized("hello"))
        assertEquals(VoiceCommandResult.UNRECOGNIZED, result)
    }

    @Test
    fun `invoke OpenApp when INSTALLED_APPS capability is missing returns NOT_SUPPORTED`() = runTest {
        every { controller.capabilities } returns setOf(TvCapability.VOLUME)
        controllerFlow.value = controller

        val result = useCase(VoiceIntent.OpenApp(AppSymbol.YOUTUBE, "mo youtube"))
        assertEquals(VoiceCommandResult.NOT_SUPPORTED, result)

        coVerify(exactly = 0) { launchAppOnTvUseCase(any<String>()) }
    }

    @Test
    fun `invoke OpenApp success returns SUCCESS`() = runTest {
        every { appRegistry.getAppId(AppSymbol.YOUTUBE, TvProtocol.LG_WEBOS) } returns "youtube.logan"
        coEvery { launchAppOnTvUseCase("youtube.logan") } returns Result.success(Unit)

        controllerFlow.value = controller

        val result = useCase(VoiceIntent.OpenApp(AppSymbol.YOUTUBE, "mo youtube"))
        assertEquals(VoiceCommandResult.SUCCESS, result)

        coVerify(exactly = 1) { launchAppOnTvUseCase("youtube.logan") }
    }
}
