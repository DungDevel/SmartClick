package com.example.smartclick.app.features.remote

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.components.NoActiveDeviceState
import com.example.smartclick.app.core.ui.components.ReconnectBanner
import com.example.smartclick.app.core.ui.components.WifiBanner
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.connection.NetworkState
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.features.remote.components.BottomActions
import com.example.smartclick.app.features.remote.components.DPad
import com.example.smartclick.app.features.remote.components.DPadModeContent
import com.example.smartclick.app.features.remote.components.KeyboardModal
import com.example.smartclick.app.features.remote.components.NumPad
import com.example.smartclick.app.features.remote.components.TouchPad
import com.example.smartclick.app.domain.connection.TvConnectionState
import androidx.compose.ui.res.stringResource

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
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RemoteUiEvent.CommandNotSupported -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.command_not_supported), android.widget.Toast.LENGTH_SHORT).show()
                }
                RemoteUiEvent.CommandNotConnected -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.command_not_connected), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun dispatchCommand(command: RemoteCommand) {
        if (isHapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        viewModel.sendCommand(command)
    }

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
                Text("📺", fontSize = 20.sp) // Device Icon
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
                is TvConnectionState.Failed -> colors.acc // Red
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
                Column(modifier = Modifier.fillMaxSize().alpha(if (controlsEnabled) 1f else 0.4f)) {
            // Mode + action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                // Power
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(colors.orange, CircleShape)
                        .clickable { dispatchCommand(RemoteCommand.Power) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.turn_off),
                        contentDescription = stringResource(R.string.power),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Mute
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(colors.surfEl, CircleShape)
                        .border(1.dp, colors.bord, CircleShape)
                        .clickable { dispatchCommand(RemoteCommand.Mute) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mute),
                        contentDescription = stringResource(R.string.mute),
                        modifier = Modifier.size(20.dp)
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
                        .background(colors.surfEl, CircleShape)
                        .border(1.dp, colors.bord, CircleShape)
                        .clickable {
                            android.widget.Toast.makeText(contextForToast, contextForToast.getString(R.string.feature_coming_soon, contextForToast.getString(R.string.lock)), android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lock),
                        contentDescription = stringResource(R.string.lock),
                        modifier = Modifier.size(20.dp)
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

                        val isPointerSupported = capabilities.contains(TvCapability.POINTER)
                        var fallbackDx by remember { mutableFloatStateOf(0f) }
                        var fallbackDy by remember { mutableFloatStateOf(0f) }
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val swipeThreshold = remember(density) { with(density) { 50.dp.toPx() } }
                        var lastSwipeTime by remember { mutableLongStateOf(0L) }

                        TouchPad(
                            onMove = { dx, dy ->
                                if (isPointerSupported) {
                                    dispatchCommand(RemoteCommand.PointerMove(dx, dy))
                                } else {
                                    fallbackDx += dx
                                    fallbackDy += dy
                                    val now = System.currentTimeMillis()
                                    if (now - lastSwipeTime > 150L) {
                                        var handled = false
                                        // Prevents crossing/diagonal swiping by picking the larger magnitude
                                        if (kotlin.math.abs(fallbackDx) > kotlin.math.abs(fallbackDy)) {
                                            if (fallbackDx > swipeThreshold) {
                                                dispatchCommand(RemoteCommand.DPadRight); handled = true
                                            } else if (fallbackDx < -swipeThreshold) {
                                                dispatchCommand(RemoteCommand.DPadLeft); handled = true
                                            }
                                        } else {
                                            if (fallbackDy > swipeThreshold) {
                                                dispatchCommand(RemoteCommand.DPadDown); handled = true
                                            } else if (fallbackDy < -swipeThreshold) {
                                                dispatchCommand(RemoteCommand.DPadUp); handled = true
                                            }
                                        }

                                        if (handled) {
                                            fallbackDx = 0f
                                            fallbackDy = 0f
                                            lastSwipeTime = now
                                        }
                                    }
                                }
                            },
                            onDragEnd = { fallbackDx = 0f; fallbackDy = 0f },
                            onTap = { dispatchCommand(if (isPointerSupported) RemoteCommand.PointerClick else RemoteCommand.Ok) },
                            onLeftClick = { dispatchCommand(if (isPointerSupported) RemoteCommand.PointerClick else RemoteCommand.Ok) },
                            onRightClick = { dispatchCommand(RemoteCommand.Back) },
                            onKeyboardRequest = { showKeyboard = true }
                        )

                        if (showKeyboard) {
                            KeyboardModal(
                                onDismissRequest = { showKeyboard = false },
                                onTextChange = { text -> dispatchCommand(RemoteCommand.Text(text)) },
                                onEnter = { dispatchCommand(RemoteCommand.Ok) },
                                onBackspace = { dispatchCommand(RemoteCommand.Backspace) }
                            )
                        }
                    }

                    activeMode == RemoteMode.NUMPAD -> {
                        val isDigitsSupported = capabilities.contains(TvCapability.DIGITS)
                        NumPad(
                            modifier = Modifier.alpha(if (isDigitsSupported) 1f else 0.4f),
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
                        dispatchCommand = ::dispatchCommand,
                        capabilities = capabilities
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                BottomActions()
            }
                }

                if (!controlsEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                android.widget.Toast.makeText(contextForToast, contextForToast.getString(R.string.no_tv_connected), android.widget.Toast.LENGTH_SHORT).show()
                            }
                    )
                }
            }
        }
    }
}
