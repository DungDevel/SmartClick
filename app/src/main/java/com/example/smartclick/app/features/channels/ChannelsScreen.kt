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
                // PRO Badge
                Box(
                    modifier = Modifier
                        .background(colors.orange, RoundedCornerShape(99.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.pro),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
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

        if (uiState.activeTvName == null && !isReconnecting) {
            NoActiveDeviceState(onNavigateToDevices = onNavigateToDevices)
        } else {
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

                // 3x3 Grid (using rows/columns manually to avoid LazyGrid inside scroll)
                val chunkedApps = uiState.apps.chunked(3)
                chunkedApps.forEach { rowApps ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowApps.forEach { app ->
                            val (bg, fg) = getAppIconStyle(app.appId)
                            val label = getAppLabel(app.appName)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(6.dp, RoundedCornerShape(18.dp))
                                    .background(colors.surf, RoundedCornerShape(18.dp))
                                    .border(1.dp, colors.bord, RoundedCornerShape(18.dp))
                                    .clickable { viewModel.launchApp(app.appId) }
                                    .padding(top = 14.dp, bottom = 12.dp, start = 8.dp, end = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // App Icon
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .shadow(8.dp, RoundedCornerShape(14.dp))
                                        .background(bg, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = fg,
                                        fontWeight = FontWeight.Black,
                                        fontSize = if (label.length > 3) 10.sp else 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = app.appName,
                                    color = colors.tx1,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Fill empty spaces if a row has less than 3 items
                        if (rowApps.size < 3) {
                            repeat(3 - rowApps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
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
                    // PRO Badge
                    Box(
                        modifier = Modifier
                            .background(colors.orange, RoundedCornerShape(99.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.pro),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
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
        } // end of else block
    }
}
}

