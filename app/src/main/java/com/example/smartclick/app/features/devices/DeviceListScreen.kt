package com.example.smartclick.app.features.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickColors
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.raisedSurface
import com.example.smartclick.app.domain.connection.ConnectOutcome
import com.example.smartclick.app.domain.connection.PairingFailure
import com.example.smartclick.app.domain.connection.PairingStep
import com.example.smartclick.app.features.onboarding.PairingDialogs
import com.example.smartclick.app.features.onboarding.ConnectionMessageMapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onClose: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val combinedDevices by viewModel.combinedDevices.collectAsState()
    val pairingStep by viewModel.pairingStep.collectAsState()

    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    var deviceToDelete by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.connectionResult.collect { result ->
            when (result) {
                is ConnectOutcome.Connected -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.channels_app_launch_success))
                    }
                }
                is ConnectOutcome.PaywallRequired -> {
                    onNavigateToPremium()
                }
                is ConnectOutcome.Failed -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(ConnectionMessageMapper.connectionFailure(result.reason))
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(pairingStep) {
        if (pairingStep is PairingStep.Failed) {
            val failed = pairingStep as PairingStep.Failed
            if (failed.reason !is PairingFailure.Cancelled) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(ConnectionMessageMapper.pairingFailure(failed.reason))
                    )
                }
            }
            viewModel.cancelPairing()
        }
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = { Text(stringResource(R.string.manual_ip_title), color = colors.tx1) },
            text = {
                Column {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        placeholder = { Text(stringResource(R.string.manual_ip_hint)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.acc,
                            unfocusedBorderColor = colors.tx2.copy(alpha = 0.5f),
                            focusedTextColor = colors.tx1,
                            unfocusedTextColor = colors.tx1
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manualIpInput.isNotBlank()) {
                        viewModel.connectToManualIp(manualIpInput.trim())
                        manualIpInput = ""
                        showManualIpDialog = false
                    }
                }) {
                    Text(stringResource(R.string.connect_tv), color = colors.acc, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text(stringResource(R.string.cancel), color = colors.tx2)
                }
            },
            containerColor = colors.surf
        )
    }

    if (deviceToDelete != null) {
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text(stringResource(R.string.remove_device_title), color = colors.tx1) },
            text = { Text(stringResource(R.string.remove_device_message), color = colors.tx2) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDevice(deviceToDelete!!)
                    deviceToDelete = null
                }) {
                    Text(stringResource(R.string.yes), color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = colors.tx2)
                }
            },
            containerColor = colors.surf
        )
    }

    PairingDialogs(
        pairingStep = pairingStep,
        onSubmitPin = { viewModel.submitPin(it) },
        onCancel = { viewModel.cancelPairing() }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = colors.bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = colors.tx1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.device_management),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.tx1
                    )
                }

                Row {
                    IconButton(onClick = { viewModel.refreshScan() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = colors.acc
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showManualIpDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.acDim),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.enter_ip_manually), color = colors.acc, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val connectedDevice = combinedDevices.firstOrNull { it.isActive }
            val savedDevices = combinedDevices.filter { it.isPaired && !it.isActive }
            val networkDevices = combinedDevices.filter { !it.isPaired }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Group 1: Connected TV
                item {
                    Text(
                        text = stringResource(R.string.connected_tv),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.acc
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (connectedDevice != null) {
                        DeviceCard(
                            row = connectedDevice,
                            colors = colors,
                            onConnectClick = { viewModel.connectToDevice(connectedDevice.device) },
                            onDeleteClick = { deviceToDelete = connectedDevice.device.id }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .raisedSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    pressable = false,
                                    fill = SolidColor(colors.surf),
                                    shadowTint = Color.Black.copy(alpha = 0.05f),
                                    innerShadow = false
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_connected_tv),
                                fontSize = 14.sp,
                                color = colors.tx2
                            )
                        }
                    }
                }

                // Group 2: Saved / Paired TVs
                if (savedDevices.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.saved_tvs),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.acc
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(savedDevices, key = { it.device.id }) { row ->
                        DeviceCard(
                            row = row,
                            colors = colors,
                            onConnectClick = { viewModel.connectToDevice(row.device) },
                            onDeleteClick = { deviceToDelete = row.device.id }
                        )
                    }
                }

                // Group 3: Found on Network
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.network_tvs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.acc
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (networkDevices.isNotEmpty()) {
                    items(networkDevices, key = { it.device.id }) { row ->
                        DeviceCard(
                            row = row,
                            colors = colors,
                            onConnectClick = { viewModel.connectToDevice(row.device) },
                            onDeleteClick = null
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .raisedSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    pressable = false,
                                    fill = SolidColor(colors.surf),
                                    shadowTint = Color.Black.copy(alpha = 0.05f),
                                    innerShadow = false
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_network_tv),
                                fontSize = 14.sp,
                                color = colors.tx2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    row: DeviceRowUi,
    colors: SmartClickColors,
    onConnectClick: () -> Unit,
    onDeleteClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .raisedSurface(
                shape = RoundedCornerShape(16.dp),
                pressable = true,
                fill = SolidColor(colors.surf),
                shadowTint = Color.Black.copy(alpha = 0.06f),
                innerShadow = false
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onConnectClick
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.acDim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.smart_tv),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(colors.acc)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = row.device.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.tx1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${row.device.brand ?: "TV"} • ${row.device.ipAddress}",
                        fontSize = 13.sp,
                        color = colors.tx2
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isActive) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.connected),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                } else {
                    Button(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.acc),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (row.isPaired) stringResource(R.string.connect_to_tv) else stringResource(R.string.pair),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (onDeleteClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
