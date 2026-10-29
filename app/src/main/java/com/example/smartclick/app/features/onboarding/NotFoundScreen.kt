package com.example.smartclick.app.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.features.devices.DevicesViewModel
import com.example.smartclick.app.domain.connection.ConnectOutcome
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotFoundScreen(
    onRetry: () -> Unit,
    onNext: () -> Unit = {},
    onSkip: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    var showManualIpDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.connectionResult.collect { result ->
            when (result) {
                is ConnectOutcome.Connected -> {
                    onNext()
                }
                is ConnectOutcome.Failed -> {
                    android.util.Log.w("ConnectionUi", "Connection failed: ${result.reason}")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(ConnectionMessageMapper.connectionFailure(result.reason))
                        )
                    }
                }
                else -> {}
            }
        }
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = { Text(stringResource(R.string.enter_ip_manually)) },
            text = {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualIpDialog = false
                        if (ipInput.isNotBlank()) {
                            viewModel.connectToManualIp(ipInput.trim())
                        }
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = colors.bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

        Spacer(modifier = Modifier.weight(1f))

        // Illustration placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(colors.surf),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📺",
                fontSize = 50.sp
            )
            // Overlay X
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.red),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "❌",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.not_found_title),
            color = colors.tx1,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = stringResource(R.string.cannot_find_tvs_network),
            color = colors.tx2,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.acc,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.try_again),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            OutlinedButton(
                onClick = { showManualIpDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.bord),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.tx1
                )
            ) {
                Text(
                    text = stringResource(R.string.enter_ip_manually),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.skip_for_now),
                    color = colors.tx2,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
    }
}
