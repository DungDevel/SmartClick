package com.example.smartclick.app.features.remote

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.components.NoActiveDeviceState
import com.example.smartclick.app.core.ui.components.ReconnectBanner
import com.example.smartclick.app.core.ui.components.WifiBanner
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.features.remote.components.BottomActions
import com.example.smartclick.app.features.remote.components.DPadModeContent
import com.example.smartclick.app.features.remote.components.DPad
import com.example.smartclick.app.features.remote.components.KeyboardModal
import com.example.smartclick.app.features.remote.components.NumPad
import com.example.smartclick.app.features.remote.components.TouchPad
import com.example.smartclick.app.domain.usecase.SendCommandResult

@Composable
fun RemoteScreen(
    onNavigateToDevices: () -> Unit = {},
    viewModel: RemoteViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val activeMode by viewModel.activeMode.collectAsState()
    val colors = SmartClickThemeTokens.colors

    val activeDevice by viewModel.activeDevice.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val controlsEnabled = connectionState is TvConnectionState.Connected
    val isHapticEnabled by viewModel.isHapticEnabled.collectAsState()
    val haptic = LocalHapticFeedback.current
    val networkState by viewModel.networkState.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()

    val isReconnecting = connectionState is TvConnectionState.Reconnecting
    val isWifiOff = networkState !is NetworkState.WifiConnected

    val context = androidx.compose.ui.platform.LocalContext.current
    val isLocked by viewModel.isLocked.collectAsState()

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            android.widget.Toast.makeText(context, context.getString(R.string.voice_not_supported), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showPowerOffDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RemoteUiEvent.CommandNotSupported -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.command_not_supported), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.CommandNotConnected -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.command_not_connected), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.CommandFailed -> {
                    if (isHapticEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    android.widget.Toast.makeText(context, context.getString(R.string.command_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.WolSent -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.wol_sent), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.VoicePremiumRequired -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.voice_premium_required), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.VoiceNotSupportedError -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.command_not_supported), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.VoiceUnrecognizedError -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.voice_unrecognized), android.widget.Toast.LENGTH_SHORT).show()
                }
                is RemoteUiEvent.VoiceError -> {
                    android.widget.Toast.makeText(context, (event as RemoteUiEvent.VoiceError).message, android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.RequestPowerOffConfirmation -> {
                    showPowerOffDialog = true
                }
            }
        }
    }

    fun dispatchCommand(command: RemoteCommand) {
        if (isLocked) {
            android.widget.Toast.makeText(context, context.getString(R.string.remote_is_locked), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (isHapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        if (command == RemoteCommand.PowerOff || (command == RemoteCommand.Power && controlsEnabled)) {
            showPowerOffDialog = true
            return
        }

        if (command == RemoteCommand.Power && !controlsEnabled) {
            val mac = activeDevice?.macAddress
            if (!mac.isNullOrBlank()) {
                viewModel.sendCommand(RemoteCommand.Power)
                return
            } else {
                android.widget.Toast.makeText(context, context.getString(R.string.no_tv_connected), android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (!controlsEnabled) {
            android.widget.Toast.makeText(context, context.getString(R.string.no_tv_connected), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.sendCommand(command)
    }

    suspend fun dispatchHeldCommand(command: RemoteCommand): SendCommandResult {
        if (!isRepeatableHeldCommand(command)) return SendCommandResult.NOT_SUPPORTED
        if (isLocked || !controlsEnabled) return SendCommandResult.NOT_CONNECTED
        return viewModel.sendCommandForHold(command)
    }

    fun onHeldPressStart() {
        if (isHapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    if (showPowerOffDialog) {
        AlertDialog(
            onDismissRequest = { showPowerOffDialog = false },
            title = { Text(stringResource(R.string.power)) },
            text = { Text(stringResource(R.string.power_off_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showPowerOffDialog = false
                    viewModel.sendCommand(RemoteCommand.PowerOff)
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPowerOffDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Mute State: 0: Vol, 1: Mute, -1: Unknown
    val hasVolume = capabilities.contains(TvCapability.VOLUME)
    val muteState by viewModel.muteState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_remote),
                color = colors.tx1,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Devices Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surfEl)
                    .border(1.dp, colors.bord, CircleShape)
                    .clickable { onNavigateToDevices() },
                contentAlignment = Alignment.Center
            ) {
                Text("📺", fontSize = 20.sp)
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (connectionState) {
                is TvConnectionState.Connected -> colors.green
                is TvConnectionState.Connecting, is TvConnectionState.Reconnecting -> colors.orange
                is TvConnectionState.Failed -> colors.acc
                else -> colors.tx2
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            val statusText = when (connectionState) {
                is TvConnectionState.Connected -> "${activeDevice?.name} · " + stringResource(R.string.connected)
                is TvConnectionState.Connecting, is TvConnectionState.Reconnecting -> "${activeDevice?.name} · " + stringResource(R.string.connecting)
                is TvConnectionState.Failed -> "${activeDevice?.name} · " + stringResource(R.string.failed)
                else -> stringResource(R.string.no_device)
            }
            Text(
                text = statusText,
                color = colors.tx2,
                fontSize = 15.sp
            )
        }

        val reconnectAttempt = (connectionState as? TvConnectionState.Reconnecting)?.attempt ?: 0
        ReconnectBanner(isVisible = isReconnecting, attempt = reconnectAttempt)

        WifiBanner(
            isVisible = isWifiOff && !isReconnecting,
            message = when (networkState) {
                is NetworkState.Disconnected -> stringResource(R.string.wifi_off_remote_message)
                is NetworkState.ConnectedNoWifi -> stringResource(R.string.no_wifi_network_message)
                else -> ""
            }
        )

        if (activeDevice == null && !isReconnecting) {
            NoActiveDeviceState(onNavigateToDevices = onNavigateToDevices)
        } else {
            val contextForToast = androidx.compose.ui.platform.LocalContext.current
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().alpha(if (controlsEnabled) (if (isLocked) 0.3f else 1f) else 0.4f)) {
            // Mode + action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                val infiniteTransition = rememberInfiniteTransition(label = "power_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha_pulse"
                )

                val powerBg: Color
                val powerBorder: Color
                val powerIconTint: Color
                var powerAlpha = 1f

                val isConnectingOrReconnecting = connectionState is TvConnectionState.Connecting || connectionState is TvConnectionState.Reconnecting

                when {
                    connectionState is TvConnectionState.Connected -> {
                        powerBg = colors.orange
                        powerBorder = Color.Transparent
                        powerIconTint = Color.White
                    }
                    isConnectingOrReconnecting -> {
                        powerBg = colors.orange.copy(alpha = 0.3f)
                        powerBorder = Color.Transparent
                        powerIconTint = Color.White
                        powerAlpha = pulseAlpha
                    }
                    activeDevice?.macAddress?.isNotEmpty() == true -> {
                        powerBg = colors.surfEl
                        powerBorder = colors.orange.copy(alpha = 0.5f)
                        powerIconTint = colors.orange
                    }
                    else -> {
                        powerBg = colors.surfEl
                        powerBorder = colors.bord
                        powerIconTint = colors.tx1
                    }
                }

                // Power (5-states visual representation)
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(powerBg, CircleShape)
                        .border(1.dp, powerBorder, CircleShape)
                        .alpha(powerAlpha)
                        .clickable { dispatchCommand(RemoteCommand.Power) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.turn_off),
                        contentDescription = stringResource(R.string.power),
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(powerIconTint)
                    )
                }

                val hasMuteOnOff = capabilities.contains(TvCapability.MUTE_ON_OFF)
                val muteIcon = when (muteState) {
                    1 -> R.drawable.mute
                    else -> R.drawable.volume
                }
                val mAlpha = if (muteState == -1 || !hasVolume) 0.4f else 1f

                // Mute (3-states: Unknown, Muted, Unmuted)
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(colors.surfEl, CircleShape)
                        .border(1.dp, colors.bord, CircleShape)
                        .alpha(mAlpha)
                        .clickable(enabled = hasVolume) {
                            if (hasMuteOnOff) {
                                if (muteState == -1 || muteState == 0) {
                                    dispatchCommand(RemoteCommand.MuteOn)
                                } else {
                                    dispatchCommand(RemoteCommand.MuteOff)
                                }
                            } else {
                                dispatchCommand(RemoteCommand.Mute)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = muteIcon),
                        contentDescription = stringResource(R.string.mute),
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.tx1)
                    )
                }

                // Mode Switcher
                Row(
                    modifier = Modifier
                        .width(200.dp)
                        .height(50.dp)
                        .background(
                            colors.surf,
                            RoundedCornerShape(11.dp)
                        )
                        .border(
                            1.dp,
                            colors.bord,
                            RoundedCornerShape(11.dp)
                        )
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modes = listOf(
                        RemoteMode.DPAD to R.drawable.lists,
                        RemoteMode.TOUCHPAD to R.drawable.top_left,
                        RemoteMode.NUMPAD to R.drawable.dial_pad
                    )

                    modes.forEach { (mode, iconRes) ->
                        val isSelected = activeMode == mode

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isSelected) {
                                        colors.acc
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.setMode(mode)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = mode.name,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Lock
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(if (isLocked) colors.acc else colors.surfEl, CircleShape)
                        .border(1.dp, if (isLocked) colors.acc else colors.bord, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (isLocked) {
                                        viewModel.unlockScreen()
                                        if (isHapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                },
                                onTap = {
                                    if (!isLocked) {
                                        viewModel.toggleScreenLock()
                                        if (isHapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    } else {
                                        android.widget.Toast.makeText(contextForToast, contextForToast.getString(R.string.remote_is_locked), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lock),
                        contentDescription = stringResource(R.string.lock),
                        modifier = Modifier.size(20.dp),
                        colorFilter = if (isLocked) androidx.compose.ui.graphics.ColorFilter.tint(Color.White) else null
                    )
                }
            }
            Spacer(modifier = Modifier.height(0.dp))

            // Scrollable Body
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                when {
                    activeMode == RemoteMode.TOUCHPAD -> {
                        var showKeyboard by remember { mutableStateOf(false) }

                        when (RemotePadBehavior.layoutFor(capabilities)) {
                            RemotePadLayout.POINTER -> TouchPad(
                                onMove = { dx, dy -> dispatchCommand(RemoteCommand.PointerMove(dx, dy)) },
                                onTap = { dispatchCommand(RemoteCommand.PointerClick) },
                                onLeftClick = { dispatchCommand(RemoteCommand.PointerClick) },
                                onRightClick = { dispatchCommand(RemoteCommand.Back) },
                                onKeyboardRequest = { showKeyboard = true }
                            )
                            RemotePadLayout.CIRCULAR_DPAD -> {
                                val navigationEnabled = RemotePadBehavior.navigationEnabled(
                                    connected = controlsEnabled,
                                    locked = isLocked,
                                    capabilities = capabilities
                                )
                                DPad(
                                    enabled = navigationEnabled,
                                    onUp = { RemotePadBehavior.dispatchIfEnabled(RemotePadAction.UP, navigationEnabled, ::dispatchCommand) },
                                    onDown = { RemotePadBehavior.dispatchIfEnabled(RemotePadAction.DOWN, navigationEnabled, ::dispatchCommand) },
                                    onLeft = { RemotePadBehavior.dispatchIfEnabled(RemotePadAction.LEFT, navigationEnabled, ::dispatchCommand) },
                                    onRight = { RemotePadBehavior.dispatchIfEnabled(RemotePadAction.RIGHT, navigationEnabled, ::dispatchCommand) },
                                    onOk = { RemotePadBehavior.dispatchIfEnabled(RemotePadAction.OK, navigationEnabled, ::dispatchCommand) },
                                    onHoldCommand = ::dispatchHeldCommand,
                                    onPressStart = ::onHeldPressStart
                                )
                            }
                        }

                        if (showKeyboard) {
                            KeyboardModal(
                                onDismissRequest = { showKeyboard = false },
                                onTextChange = { text -> dispatchCommand(RemoteCommand.Text(text)) },
                                onEnter = { dispatchCommand(RemoteCommand.Ok) },
                                onBackspace = { dispatchCommand(RemoteCommand.Backspace) },
                                canDelete = capabilities.contains(TvCapability.BACKSPACE)
                            )
                        }
                    }

                    activeMode == RemoteMode.NUMPAD -> {
                        val isDigitsSupported = capabilities.contains(TvCapability.DIGITS)
                        NumPad(
                            modifier = Modifier.alpha(if (isDigitsSupported) 1f else 0.4f),
                            canDelete = capabilities.contains(TvCapability.BACKSPACE),
                            onDigit = { digit ->
                                digit.toIntOrNull()?.let {
                                    dispatchCommand(RemoteCommand.Numpad(it))
                                }
                            },
                            onExit = { viewModel.setMode(RemoteMode.DPAD) },
                            onDelete = { dispatchCommand(RemoteCommand.Backspace) }
                        )
                    }

                    else -> DPadModeContent(
                        dispatchCommand = { cmd ->
                            dispatchCommand(cmd)
                        },
                        capabilities = capabilities,
                        navigationEnabled = RemotePadBehavior.navigationEnabled(
                            connected = controlsEnabled,
                            locked = isLocked,
                            capabilities = capabilities
                        ),
                        onHoldCommand = ::dispatchHeldCommand,
                        onPressStart = ::onHeldPressStart,
                        controlsEnabled = controlsEnabled && !isLocked
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val speechState by viewModel.speechState.collectAsState()

                BottomActions(
                    speechState = speechState,
                    onStartVoice = {
                        if (isHapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPerm) {
                            viewModel.startListening()
                        } else {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopVoice = {
                        viewModel.stopListening()
                    }
                )
            }
                }
            }
        }
    }
}
