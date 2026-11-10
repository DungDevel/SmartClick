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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.smartclick.app.core.ui.components.ReconnectBanner
import com.example.smartclick.app.core.ui.components.WifiBanner
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.features.remote.components.BottomActions
import com.example.smartclick.app.features.remote.components.VoiceSearchSheet
import com.example.smartclick.app.features.remote.components.DPadModeContent
import com.example.smartclick.app.features.remote.components.DPad
import com.example.smartclick.app.features.remote.components.KeyboardModal
import com.example.smartclick.app.features.remote.components.NumPad
import com.example.smartclick.app.features.remote.components.TouchPad
import com.example.smartclick.app.domain.usecase.SendCommandResult
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import com.example.smartclick.app.core.ui.theme.raisedSurface

// TẠM THỜI: true = bỏ qua yêu cầu Premium để test micro. Nhớ đặt lại false trước khi phát hành.
private const val VOICE_TEST_UNLOCK = false

@Composable
fun RemoteScreen(
    onNavigateToDevices: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
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
    val coroutineScope = rememberCoroutineScope()
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
            android.widget.Toast.makeText(context, context.getString(R.string.voice_error_permission_denied), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelVoiceSession() }
    }

    var showPowerOffDialog by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    val currentOnNavigateToPremium by rememberUpdatedState(onNavigateToPremium)

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
                RemoteUiEvent.WakeOnLanUnavailable -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.wol_unavailable), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.WakeOnLanFailed -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.wol_failed_retry), android.widget.Toast.LENGTH_LONG).show()
                }
                RemoteUiEvent.VoicePremiumRequired -> {
                    if (VOICE_TEST_UNLOCK) {
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) viewModel.startListening()
                        else recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        showVoiceSheet = false
                        currentOnNavigateToPremium()
                    }
                }
                RemoteUiEvent.RequestVoicePermission -> {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                RemoteUiEvent.VoiceNotSupportedError -> {
                    showVoiceSheet = false
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

    fun dispatchCommand(command: RemoteCommand, withHaptic: Boolean = true) {
        if (isLocked) {
            android.widget.Toast.makeText(context, context.getString(R.string.remote_is_locked), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (withHaptic && isHapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        if (command == RemoteCommand.PowerOff || (command == RemoteCommand.Power && controlsEnabled)) {
            showPowerOffDialog = true
            return
        }

        if (command == RemoteCommand.Power && !controlsEnabled) {
            viewModel.sendCommand(RemoteCommand.Power)
            return
        }

        if (!controlsEnabled) {
            android.widget.Toast.makeText(context, context.getString(R.string.no_tv_connected), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.sendCommand(command)
    }

    val dispatchPointerMove by rememberUpdatedState<(RemoteCommand.PointerMove) -> Unit>({
        dispatchCommand(it, withHaptic = false)
    })
    val pointerMoveHaptic by rememberUpdatedState {
        if (isHapticEnabled && !isLocked) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val pointerMoveDispatcher = remember(coroutineScope) {
        TouchpadPointerMoveDispatcher(
            scope = coroutineScope,
            send = { dispatchPointerMove(it) },
            haptic = { pointerMoveHaptic() }
        )
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
                    viewModel.confirmPowerOff()
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
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 0.dp),
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
                    .size(45.dp)
                    .raisedSurface(CircleShape, pressable = false)
                    .clickable { onNavigateToDevices() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.smart_tv),
                    contentDescription = stringResource(R.string.select_device),
                    modifier = Modifier.size(22.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.tx2)
                )
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

        // Thông báo mất Wi-Fi: chỉ hiện một dòng chữ nhỏ, không có khung nền
        if (isWifiOff && !isReconnecting) {
            val wifiHint = when (networkState) {
                is NetworkState.Disconnected -> stringResource(R.string.wifi_off_remote_message)
                is NetworkState.ConnectedNoWifi -> stringResource(R.string.no_wifi_network_message)
                else -> ""
            }
            if (wifiHint.isNotEmpty()) {
                Text(
                    text = wifiHint,
                    color = colors.tx2.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                )
            }
        }

        run {
            val contextForToast = androidx.compose.ui.platform.LocalContext.current
            val controlsAlpha = if (isLocked) 0.3f else 1f
            Column(modifier = Modifier.fillMaxSize()) {
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

                    val powerState = RemotePowerButton.stateFor(connectionState, activeDevice?.macAddress?.isNotEmpty() == true)
                    val powerAlpha = RemotePowerButton.alphaFor(powerState, isLocked, pulseAlpha)

                    val powerBg: Color
                    val powerBorder: Color
                    val powerIconTint: Color

                    when (powerState) {
                        RemotePowerButton.PowerVisualState.CONNECTED -> {
                            powerBg = colors.orange
                            powerBorder = Color.Transparent
                            powerIconTint = Color.White
                        }
                        RemotePowerButton.PowerVisualState.CONNECTING -> {
                            powerBg = colors.orange.copy(alpha = 0.3f)
                            powerBorder = Color.Transparent
                            powerIconTint = Color.White
                        }
                        RemotePowerButton.PowerVisualState.WAKEABLE -> {
                            powerBg = colors.surfEl
                            powerBorder = colors.orange.copy(alpha = 0.5f)
                            powerIconTint = colors.orange
                        }
                        RemotePowerButton.PowerVisualState.UNAVAILABLE -> {
                            powerBg = colors.surfEl
                            powerBorder = colors.bord
                            powerIconTint = colors.tx1
                        }
                        RemotePowerButton.PowerVisualState.FAILED -> {
                            powerBg = colors.acc
                            powerBorder = Color.Transparent
                            powerIconTint = Color.White
                        }
                    }

                    // Power (5-states visual representation)
                    var lastPowerTapTime by remember { mutableLongStateOf(0L) }
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .then(
                                if (powerState == RemotePowerButton.PowerVisualState.CONNECTED ||
                                    powerState == RemotePowerButton.PowerVisualState.CONNECTING ||
                                    powerState == RemotePowerButton.PowerVisualState.FAILED
                                ) Modifier.background(powerBg, CircleShape)
                                else Modifier.raisedSurface(CircleShape, pressable = false)
                            )
                            .then(
                                if (powerState == RemotePowerButton.PowerVisualState.WAKEABLE)
                                    Modifier.border(1.dp, powerBorder, CircleShape)
                                else Modifier
                            )
                            .alpha(powerAlpha)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastPowerTapTime > 250L) {
                                        lastPowerTapTime = now
                                        dispatchCommand(RemoteCommand.Power)
                                    }
                                })
                            },
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
                        val mAlpha = if (!controlsEnabled) 1f else if (muteState == -1 || !hasVolume) 0.4f else 1f

                        // Mute (3-states: Unknown, Muted, Unmuted)
                        Box(
                            modifier = Modifier
                                .alpha(controlsAlpha)
                                .size(45.dp)
                                .raisedSurface(CircleShape)
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
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.tx2)
                            )
                        }

                        // Mode Switcher
                        Row(
                            modifier = Modifier
                                .alpha(controlsAlpha)
                                .width(200.dp)
                                .height(45.dp)
                                .raisedSurface(
                                    shape = RoundedCornerShape(12.dp),
                                    pressable = false,
                                    fill = SolidColor(colors.surf),
                                    shadowTint = Color(0xFF102B4E).copy(alpha = 0.10f),
                                    innerShadow = false
                                )
                                .padding(4.dp),
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
                                        .then(
                                            if (isSelected) Modifier.raisedSurface(
                                                shape = RoundedCornerShape(8.dp),
                                                pressable = false,
                                                fill = Brush.verticalGradient(listOf(Color(0xFF365D89), Color(0xFF102B4E))),
                                                shadowTint = Color(0xFF1E3D68).copy(alpha = 0.18f),
                                                innerShadow = false
                                            ) else Modifier
                                        )
                                        .clickable {
                                            viewModel.setMode(mode)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = mode.name,
                                        modifier = Modifier.size(20.dp),
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                            if (isSelected) Color.White else colors.tx2
                                        )
                                    )
                                }
                            }
                        }

                        // Lock (không làm mờ, vì đây là nút duy nhất để mở khóa)
                        Box(
                            modifier = Modifier
                                .size(45.dp)
                                .then(
                                    if (isLocked) Modifier.background(colors.acc, CircleShape)
                                    else Modifier.raisedSurface(CircleShape, pressable = false)
                                )
                                .pointerInput(isLocked) {
                                    detectTapGestures(
                                        onPress = {
                                            if (isLocked) {
                                                viewModel.unlockScreen()
                                                if (isHapticEnabled) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                            try {
                                                awaitRelease()
                                            } finally {
                                                if (isLocked) {
                                                    viewModel.cancelUnlockScreen()
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
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (isLocked) Color.White else colors.tx2
                                )
                            )
                        }
                    }
                    if (isLocked) {
                        Text(
                            text = stringResource(R.string.remote_is_locked),
                            color = colors.tx2,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 4.dp)
                        )
                    }

                    // Scrollable Body
                    Column(
                        modifier = Modifier
                            .alpha(controlsAlpha)
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        var showKeyboard by remember { mutableStateOf(false) }
                        var dictatedText by remember { mutableStateOf("") }
                        val secondaryActions = RemoteSecondaryActions.forCapabilities(capabilities, viewModel.dictationAvailable)
                        val hasKeyboard = secondaryActions.keyboard
                        val canDictate = secondaryActions.dictation
                        when {
                            activeMode == RemoteMode.TOUCHPAD -> {
                                TouchPad(
                                    onMove = pointerMoveDispatcher::onMove,
                                    onDragEnd = pointerMoveDispatcher::onDragEnd,
                                    onTap = { dispatchCommand(RemoteCommand.PointerClick) },
                                    onLeftClick = { dispatchCommand(RemoteCommand.PointerClick) },
                                    onRightClick = { dispatchCommand(RemoteCommand.Back) },
                                    onKeyboardRequest = { showKeyboard = true }
                                )
                            }

                            activeMode == RemoteMode.NUMPAD -> {
                                val isDigitsSupported = capabilities.contains(TvCapability.DIGITS)
                                NumPad(
                                    modifier = Modifier.alpha(if (isDigitsSupported || !controlsEnabled) 1f else 0.4f),
                                    canDelete = capabilities.contains(TvCapability.BACKSPACE) || !controlsEnabled,
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
                                dispatchCommand = { cmd -> dispatchCommand(cmd) },
                                capabilities = capabilities,
                                connected = controlsEnabled,
                                navigationEnabled = !isLocked,
                                onHoldCommand = ::dispatchHeldCommand,
                                onPressStart = ::onHeldPressStart,
                                controlsEnabled = !isLocked,
                                showKeyboard = hasKeyboard,
                                showDictation = canDictate,
                                showHeadphone = secondaryActions.headphone,
                                onKeyboard = { showKeyboard = true },
                                onDictate = {
                                    showKeyboard = true
                                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    viewModel.prepareDictation(hasPerm)
                                }
                            )
                        }

                        if (showKeyboard && TvCapability.KEYBOARD_TEXT in capabilities) {
                            KeyboardModal(
                                onDismissRequest = { showKeyboard = false },
                                onSendText = { command -> dispatchCommand(command) },
                                dictatedText = dictatedText,
                                onDictate = {
                                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    viewModel.prepareDictation(hasPerm)
                                }
                            )
                        }

                        LaunchedEffect(viewModel) { viewModel.dictationResults.collect { dictatedText = it } }

                        Spacer(modifier = Modifier.height(16.dp))

                        val speechState by viewModel.speechState.collectAsState()
                        val voiceOutcome by viewModel.voiceOutcome.collectAsState()

                        val startVoice: () -> Unit = {
                            if (isHapticEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            viewModel.prepareVoiceListening(hasRecordAudioPermission = hasPerm)
                        }

                        BottomActions(
                            speechState = speechState,
                            onStartVoice = {
                                showVoiceSheet = true
                                startVoice()
                            },
                            onStopVoice = {}
                        )

                        if (showVoiceSheet) {
                            VoiceSearchSheet(
                                state = speechState,
                                outcome = voiceOutcome,
                                onCancel = {
                                    viewModel.cancelVoiceSession()
                                    showVoiceSheet = false
                                },
                                onFinished = { showVoiceSheet = false },
                                onRetry = { startVoice() }
                            )
                        }
                    }
                }
            }
    }
}
