package com.example.smartclick.app.features.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.raisedSurface
import com.example.smartclick.app.features.onboarding.DeviceFoundScreen
import com.example.smartclick.app.features.remote.RemoteScreen

sealed class AppTab(val titleResId: Int, val icon: Int) {
    object Remote : AppTab(R.string.tab_remote, R.drawable.lists)
    object Apps : AppTab(R.string.tab_channels, R.drawable.application)
    object Cast : AppTab(R.string.tab_cast, R.drawable.upload)
    object Settings : AppTab(R.string.tab_settings, R.drawable.settings)
}

@Composable
fun MainAppScreen(onNavigateToPremium: () -> Unit = {}) {
    var currentTabName by rememberSaveable { mutableStateOf(AppTab.Remote.javaClass.simpleName) }
    var showDeviceFound by remember { mutableStateOf(false) }
    val colors = SmartClickThemeTokens.colors

    val tabs = listOf(
        AppTab.Remote,
        AppTab.Apps,
        AppTab.Cast,
        AppTab.Settings
    )

    val currentTab = tabs.find { it.javaClass.simpleName == currentTabName } ?: AppTab.Remote

    Box(modifier = Modifier.fillMaxSize()) {
        // Base Content Layer (Main Tabs)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
        ) {
            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    AppTab.Remote -> RemoteScreen(
                        onNavigateToDevices = { showDeviceFound = true },
                        onNavigateToPremium = onNavigateToPremium
                    )
                    AppTab.Apps -> com.example.smartclick.app.features.channels.ChannelsScreen(onNavigateToDevices = { showDeviceFound = true })
                    AppTab.Cast -> com.example.smartclick.app.features.cast.CastScreen(onNavigateToDevices = { showDeviceFound = true }, onNavigateToPremium = onNavigateToPremium)
                    AppTab.Settings -> com.example.smartclick.app.features.settings.SettingsScreen()
                }
            }

            // Bottom Navigation Bar: thanh nổi hình viên thuốc, có bóng đổ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .raisedSurface(
                            shape = RoundedCornerShape(28.dp),
                            pressable = false,
                            fill = SolidColor(colors.surf),
                            shadowTint = Color(0xFF102B4E).copy(alpha = 0.14f),
                            innerShadow = false
                        )
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { tab ->
                        val isSelected = currentTab == tab
                        val color = if (isSelected) colors.acc else colors.tx2

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .then(
                                    if (isSelected) Modifier.background(colors.acDim, RoundedCornerShape(22.dp))
                                    else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { currentTabName = tab.javaClass.simpleName },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = tab.icon),
                                contentDescription = stringResource(tab.titleResId),
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(color)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(tab.titleResId),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                                maxLines = 1
                            )
                            // Gạch dưới ngắn cho tab đang chọn (giữ chỗ để các tab không nhảy lên xuống)
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(2.dp)
                                    .background(
                                        if (isSelected) colors.acc else Color.Transparent,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Overlay Layer (Device Scan Modal)
        AnimatedVisibility(
            visible = showDeviceFound,
            enter = slideInVertically(initialOffsetY = { it }), // Slide up from bottom
            exit = slideOutVertically(targetOffsetY = { it })   // Slide down to bottom
        ) {
            // Catch clicks behind the screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {}
            ) {
                DeviceFoundScreen(
                    onClose = { showDeviceFound = false }
                )
            }
        }
    }
}

@Composable
fun CenterText(text: String) {
    val colors = SmartClickThemeTokens.colors
    Box(modifier = Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
        Text(text = text, color = colors.tx1, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}
