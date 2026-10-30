package com.example.smartclick.app.features.channels
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.core.ui.components.NoActiveDeviceState
import com.example.smartclick.app.core.ui.components.ReconnectBanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.ChannelCategory
import com.example.smartclick.app.domain.model.TvChannel
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R
import com.example.smartclick.app.domain.usecase.ErrorReason
import com.example.smartclick.app.domain.usecase.InstalledChannelsState

// UI helper to generate placeholder colors/labels for apps
private fun getAppIconStyle(appId: String): Pair<Color, Color> {
    return when (appId) {
        "1" -> Color(0xFFE53935) to Color.White
        "2" -> Color.Black to Color(0xFFE50914)
        "3" -> Color(0xFF00A8E1) to Color.White
        "4" -> Color(0xFF113CCF) to Color.White
        "5" -> Color(0xFF1ED760) to Color.White
        "6" -> Color(0xFF5822B4) to Color.White
        else -> Color.Gray to Color.White
    }
}

private fun getAppLabel(appName: String): String {
    return appName.take(2).uppercase()
}

// UI helper to generate placeholder for channels
private fun getChannelIconStyle(channelId: String): Pair<Color, Color> {
    val colors = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
        Color(0xFFF06292), Color(0xFFFF8F00), Color(0xFF00838F),
        Color(0xFFD84315), Color(0xFF37474F)
    )
    val index = channelId.last().digitToIntOrNull() ?: 0
    return colors[index % colors.size] to Color.White
}


@Composable
fun ChannelsScreen(
    onNavigateToDevices: () -> Unit = {},

    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surf)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.tab_apps),
                    color = colors.tx1,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.apps_desc),
                color = colors.tx2,
                fontSize = 12.sp
            )
        }

        HorizontalDivider(color = colors.bord)

        val isReconnecting = connectionState is TvConnectionState.Reconnecting
        val reconnectAttempt = (connectionState as? TvConnectionState.Reconnecting)?.attempt ?: 0

        ReconnectBanner(isVisible = isReconnecting, attempt = reconnectAttempt)

            // Scrollable Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
            // Your TV Apps Section
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.your_tv_apps),
                    color = colors.tx1,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                when (val appsState = uiState.appsState) {
                    InstalledChannelsState.Loading -> Text(stringResource(R.string.channels_apps_loading), color = colors.tx2)
                    is InstalledChannelsState.Success -> {
                        appsState.apps.chunked(3).forEach { rowApps ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowApps.forEach { app ->
                                    val (bg, fg) = getAppIconStyle(app.appId)
                                    val label = getAppLabel(app.appName)
                                    Column(
                                        modifier = Modifier.weight(1f).shadow(6.dp, RoundedCornerShape(18.dp))
                                            .background(colors.surf, RoundedCornerShape(18.dp))
                                            .border(1.dp, colors.bord, RoundedCornerShape(18.dp))
                                            .clickable { viewModel.launchApp(app.packageName ?: app.appId) }
                                            .padding(top = 14.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(Modifier.size(56.dp).shadow(8.dp, RoundedCornerShape(14.dp)).background(bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                            Text(label, color = fg, fontWeight = FontWeight.Black, fontSize = if (label.length > 3) 10.sp else 16.sp, textAlign = TextAlign.Center)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(app.appName, color = colors.tx1, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (rowApps.size < 3) repeat(3 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    InstalledChannelsState.Empty -> Text(stringResource(R.string.channels_apps_empty), color = colors.tx2)
                    InstalledChannelsState.NoActiveDevice -> Text(stringResource(R.string.channels_apps_no_device), color = colors.tx2)
                    InstalledChannelsState.NotSupported -> Text(stringResource(R.string.channels_apps_not_supported), color = colors.tx2)
                    is InstalledChannelsState.Error -> Column {
                        val message = when (appsState.reason) {
                            ErrorReason.LOST_CONNECTION -> R.string.channels_apps_error_connection
                            ErrorReason.TIMEOUT -> R.string.channels_apps_error_timeout
                            ErrorReason.PARSE -> R.string.channels_apps_error_parse
                            ErrorReason.UNKNOWN -> R.string.channels_apps_error_unknown
                        }
                        Text(stringResource(message), color = colors.tx2)
                        Button(onClick = viewModel::retryApps) { Text(stringResource(R.string.channels_apps_retry)) }
                    }
                }
                uiState.launchSucceeded?.let { succeeded ->
                    Text(
                        text = stringResource(
                            if (succeeded) R.string.channels_app_launch_success
                            else R.string.channels_app_launch_failed
                        ),
                        color = colors.tx2
                    )
                }
            }

            // Channels Section
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.channels),
                        color = colors.tx1,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Tabs Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(ChannelCategory.POPULAR, ChannelCategory.RECOMMENDED)
                    tabs.forEach { tab ->
                        val isSelected = uiState.selectedTab == tab
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) colors.acc else colors.surfEl,
                                    RoundedCornerShape(99.dp)
                                )
                                .then(
                                    if (isSelected) Modifier.shadow(6.dp, RoundedCornerShape(99.dp), spotColor = colors.acGlow)
                                    else Modifier
                                )
                                .clickable { viewModel.onTabSelected(tab) }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (tab == ChannelCategory.POPULAR) stringResource(R.string.popular) else stringResource(R.string.recommended),
                                color = if (isSelected) Color.White else colors.tx2,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 2-Col Channels Grid
                val chunkedChannels = uiState.channels.chunked(2)
                chunkedChannels.forEach { rowChannels ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowChannels.forEach { channel ->
                            val (bg, fg) = getChannelIconStyle(channel.id)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(4.dp, RoundedCornerShape(18.dp))
                                    .background(colors.surf, RoundedCornerShape(18.dp))
                                    .border(1.dp, colors.bord, RoundedCornerShape(18.dp))
                                    .clickable { viewModel.tuneChannel(channel.id) }
                                    .padding(top = 16.dp, bottom = 14.dp, start = 10.dp, end = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .shadow(8.dp, RoundedCornerShape(14.dp))
                                        .background(bg, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = channel.label,
                                        color = fg,
                                        fontWeight = FontWeight.Black,
                                        fontSize = if (channel.label.length > 3) 11.sp else 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = channel.name,
                                    color = colors.tx1,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (i in 1..5) {
                                        Text(
                                            text = "★",
                                            color = if (i <= channel.rating) colors.orange else colors.tx3,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (rowChannels.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
    }
}
}

