package com.example.smartclick.app.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.connection.ConnectOutcome
import com.example.smartclick.app.domain.connection.PairingFailure
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.features.devices.DevicesViewModel
import com.example.smartclick.app.features.devices.ScanState
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFoundScreen(
    onNext: () -> Unit = {},
    onClose: () -> Unit = {},
    onLimitExceeded: () -> Unit = {},
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val combinedDevices by viewModel.combinedDevices.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val pairingStep by viewModel.pairingStep.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.connectionResult.collect { result ->
            isConnecting = false
            when (result) {
                is ConnectOutcome.Connected -> {
                    onNext()
                    onClose() // Also close in case it's an overlay
                }
                is ConnectOutcome.PaywallRequired -> {
                    onLimitExceeded()
                }
                is ConnectOutcome.Failed -> {
                    android.util.Log.w("ConnectionUi", "Connection failed: ${result.reason}")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(ConnectionMessageMapper.connectionFailure(result.reason))
                        )
                    }
                }
            }
        }
    }

    // Auto-dismiss bottom sheet when scan completes
    LaunchedEffect(scanState) {
        if (showBottomSheet && (scanState == ScanState.Found || scanState == ScanState.NotFound)) {
            sheetState.hide()
            showBottomSheet = false
        }
    }

    LaunchedEffect(pairingStep) {
        if (pairingStep is PairingStep.Failed) {
            val failed = pairingStep as PairingStep.Failed
            if (failed.reason !is PairingFailure.Cancelled) {
                android.util.Log.w("ConnectionUi", "Pairing failed: ${failed.reason}")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(ConnectionMessageMapper.pairingFailure(failed.reason))
                    )
                }
            }
            viewModel.cancelPairing()
        }
    }

    if (deviceToDelete != null) {
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text(stringResource(R.string.remove_device_title)) },
            text = { Text(stringResource(R.string.remove_device_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDevice(deviceToDelete!!)
                    deviceToDelete = null
                }) {
                    Text(stringResource(R.string.yes), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    PairingDialogs(
        pairingStep = pairingStep,
        onSubmitPin = { viewModel.submitPin(it) },
        onCancel = { viewModel.cancelPairing() }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2A3EB8),
                                Color(0xFF1A2A8A),
                                colors.bg
                            ),
                            startY = 0f,
                            endY = 600f
                        )
                    )
                    .padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }

                    Text(
                        text = stringResource(R.string.select_device),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    IconButton(onClick = {
                        showBottomSheet = true
                        viewModel.refreshScan()
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = colors.surf,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                if (combinedDevices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_devices_found), color = colors.tx2)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                    ) {
                        itemsIndexed(combinedDevices) { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isConnecting = true
                                        viewModel.connectToDevice(row.device)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = if (row.isPaired) colors.acc.copy(alpha = 0.2f) else colors.surfEl,
                                            shape = CircleShape
                                        )
                                        .border(if (row.isActive) 2.dp else 0.dp, if (row.isActive) colors.acc else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📺", fontSize = 20.sp)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = row.device.name + (if (row.isActive) " (Connected)" else ""),
                                        color = if (row.isActive) colors.acc else colors.tx1,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    val subtitle = if (row.isOnline) row.device.ipAddress else "Offline"
                                    Text(
                                        text = subtitle,
                                        color = colors.tx2,
                                        fontSize = 12.sp
                                    )
                                }

                                if (row.isPaired && !row.isActive) {
                                    IconButton(onClick = { deviceToDelete = row.device.id }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.remove), tint = colors.tx2)
                                    }
                                } else if (row.isActive) {
                                    IconButton(onClick = { deviceToDelete = row.device.id }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.disconnect), tint = colors.acc)
                                    }
                                }
                            }

                            if (index < combinedDevices.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 88.dp, end = 24.dp),
                                    color = colors.bord
                                )
                            }
                        }

                        item {
                            val unsupportedCount by viewModel.unsupportedDeviceCount.collectAsState()
                            com.example.smartclick.app.features.devices.UnsupportedDeviceBanner(
                                unsupportedCount = unsupportedCount,
                                onManualIpSubmit = { ip ->
                                    viewModel.connectToManualIp(ip)
                                },
                                onRequestSupport = {
                                    SupportEmailRequest.dispatch(
                                        supportEmail = context.getString(R.string.support_email_address),
                                        unavailableMessageResourceId = R.string.support_channel_unavailable,
                                        openMailto = { mailtoUri ->
                                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = mailtoUri.toUri()
                                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.support_email_subject))
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    viewModel.supportRequestBody(context.getString(R.string.support_email_device_details))
                                                )
                                            }
                                            try {
                                                context.startActivity(emailIntent)
                                            } catch (_: ActivityNotFoundException) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.support_email_app_not_found))
                                                }
                                            }
                                        },
                                        showUnavailable = { messageResourceId ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(messageResourceId))
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = colors.surf
        ) {
            RadarScanView()
        }
    }
}

@Composable
fun RadarScanView() {
    val colors = SmartClickThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 60.dp, top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Radar Animation
        Box(
            modifier = Modifier
                .size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RadarScale"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RadarAlpha"
            )

            // Halo Rings
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(colors.acc, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(scale * 0.6f)
                    .alpha(alpha)
                    .background(colors.acc, CircleShape)
            )

            // Center Icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(colors.acc, CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("📡", fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.searching_nearby_tvs),
            color = colors.tx1,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.make_sure_tv_on),
            color = colors.tx2,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
@Composable
fun PairingDialogs(
    pairingStep: PairingStep?,
    onSubmitPin: (String) -> Unit,
    onCancel: () -> Unit
) {
    if (pairingStep == null) return

    when (pairingStep) {
        is PairingStep.EnterPin -> {
            var pin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text(stringResource(R.string.enter_pin_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.enter_pin_message))
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= pairingStep.length) pin = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.code_n_digits, pairingStep.length)) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSubmitPin(pin) }) {
                        Text(stringResource(R.string.submit))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is PairingStep.ConfirmOnTv -> {
            var progress by remember(pairingStep) { mutableFloatStateOf(1f) }
            LaunchedEffect(pairingStep) {
                val totalMs = pairingStep.timeoutMs.toFloat()
                var currentMs = totalMs
                val interval = 50f
                while (currentMs > 0) {
                    kotlinx.coroutines.delay(interval.toLong())
                    currentMs -= interval
                    progress = (currentMs / totalMs).coerceIn(0f, 1f)
                }
            }

            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text(stringResource(R.string.confirm_on_tv_title)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.confirm_on_tv_message))
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is PairingStep.Instruction -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val displayMessage = remember(pairingStep.key) {
                val resId = InstructionResourceResolver.resolve(
                    key = pairingStep.key,
                    fallbackResourceId = R.string.pairing_instruction_unavailable,
                    findResourceId = { key -> context.resources.getIdentifier(key, "string", context.packageName) },
                    warn = { warning -> android.util.Log.w("DeviceFoundScreen", warning) }
                )
                context.getString(resId)
            }
            AlertDialog(
                onDismissRequest = onCancel,
                title = { Text(stringResource(R.string.instruction)) },
                text = { Text(displayMessage) },
                confirmButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.dismiss)) // Will naturally be closed when state changes
                    }
                }
            )
        }
        else -> Unit
    }
}
