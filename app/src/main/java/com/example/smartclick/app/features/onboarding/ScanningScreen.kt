package com.example.smartclick.app.features.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.core.ui.components.WifiBanner
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.features.devices.DevicesViewModel
import com.example.smartclick.app.features.devices.ScanState
import com.example.smartclick.app.domain.connection.NetworkState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@Composable
fun ScanningScreen(
    onNext: () -> Unit,
    onFail: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val networkState by viewModel.networkState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()

    val isWifiOff = networkState !is NetworkState.WifiConnected

    LaunchedEffect(scanState) {
        when (scanState) {
            is ScanState.Found -> onNext()
            is ScanState.NotFound -> onFail()
            else -> {}
        }
    }

    val progress = (scanState as? ScanState.Scanning)?.progress ?: 0

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        WifiBanner(
            isVisible = isWifiOff,
            message = when (networkState) {
                is NetworkState.Disconnected -> stringResource(R.string.wifi_off_message)
                is NetworkState.ConnectedNoWifi -> stringResource(R.string.no_wifi_network_message)
                else -> ""
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(120.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, colors.surfEl, CircleShape)
                )

                Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                    drawArc(
                        color = colors.acc,
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(colors.primaryGradient)
                ) {
                    Image(
                        painter = painterResource(R.drawable.wifi),
                        contentDescription = stringResource(R.string.wifi),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (isWifiOff) stringResource(R.string.wifi_off_title) else stringResource(R.string.scanning_network),
                color = colors.tx1,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = if (isWifiOff) stringResource(R.string.wifi_off_message) else stringResource(R.string.looking_for_tvs),
                color = colors.tx2,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(colors.surfEl)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress / 100f)
                        .clip(CircleShape)
                        .background(colors.primaryGradient)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${progress}%",
                color = colors.acc,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.tv_not_found_try_manually),
                color = colors.tx2,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onFail() }
            )
        }
    }
}
