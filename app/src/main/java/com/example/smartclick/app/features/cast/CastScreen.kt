package com.example.smartclick.app.features.cast
import com.example.smartclick.app.domain.connection.TvConnectionState
import com.example.smartclick.app.core.ui.components.NoActiveDeviceState
import com.example.smartclick.app.core.ui.components.ReconnectBanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.smartclick.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

// We use hardcoded colors for UI mock representation of different media,
// mapped dynamically from the domain's MediaItem ID or type in a real app.
// For now, generate a placeholder color based on ID to maintain visual flair:
private fun getColorForMediaId(id: String): Color {
    val colors = listOf(
        Color(0xFFF44336), Color(0xFF9C27B0), Color(0xFF3F51B5),
        Color(0xFF009688), Color(0xFFFF9800), Color(0xFF795548),
        Color(0xFF607D8B), Color(0xFFE91E63), Color(0xFFCDDC39),
        Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF4CAF50)
    )
    val index = (id.toIntOrNull() ?: 0) % colors.size
    return colors[index]
}

@Composable
fun CastScreen(
    onNavigateToDevices: () -> Unit = {},

    viewModel: CastViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val scrollState = rememberScrollState()

    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // Pulsing animation for the casting indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

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
            Text(
                text = stringResource(R.string.cast_title),
                color = colors.tx1,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = stringResource(R.string.cast_desc),
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
            // Casting Banner & Playback Controls
            if (uiState.isCasting || uiState.playbackState != com.example.smartclick.app.domain.cast.CastPlaybackState.IDLE) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.acDim)
                        .border(1.dp, colors.acc.copy(alpha = 0.26f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(pulseScale)
                                .background(
                                    when (uiState.playbackState) {
                                        com.example.smartclick.app.domain.cast.CastPlaybackState.PLAYING -> colors.green
                                        com.example.smartclick.app.domain.cast.CastPlaybackState.PAUSED -> Color(0xFFFF9800)
                                        com.example.smartclick.app.domain.cast.CastPlaybackState.ERROR -> colors.red
                                        else -> colors.acc
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.casting_to_tv, uiState.activeTvName ?: "TV") + " • ${uiState.playbackState.name}",
                            color = colors.tx1,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.stop),
                            color = colors.red,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { viewModel.stopCasting() }
                        )
                    }

                    // Transport Controls Row
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.playbackState == com.example.smartclick.app.domain.cast.CastPlaybackState.PLAYING) {
                            Button(
                                onClick = { viewModel.pause() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.acc)
                            ) {
                                Text("⏸ Pause", color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.play() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.acc)
                            ) {
                                Text("▶ Play", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.stop() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.red)
                        ) {
                            Text("⏹ Stop", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

        // Scrollable Media Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            val chunkedMedia = uiState.mediaItems.chunked(3)
            chunkedMedia.forEach { rowMedia ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowMedia.forEach { media ->
                        val isSelected = uiState.selectedMediaIds.contains(media.id)
                        val mediaColor = getColorForMediaId(media.id)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(mediaColor) // Use dynamic color
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, colors.acc, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .clickable {
                                    viewModel.toggleSelection(media.id)
                                }
                        ) {
                            if (media.mimeType.startsWith("video")) {
                                // Video badge
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(5.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text("▶", color = Color.White, fontSize = 8.sp)
                                    Text("0:30", color = Color.White, fontSize = 9.sp)
                                }
                            }

                            if (isSelected) {
                                // Selected Overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.acc.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(colors.acc, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (rowMedia.size < 3) {
                        repeat(3 - rowMedia.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Bottom Target Action
        if (uiState.selectedMediaIds.isNotEmpty() && !uiState.isCasting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surf)
            ) {
                HorizontalDivider(color = colors.bord)
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val count = uiState.selectedMediaIds.size
                    Button(
                        onClick = { viewModel.startCasting() },
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
                            text = "📺 " + pluralStringResource(R.plurals.cast_items, count, count),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        } // else block
    }
}

}

