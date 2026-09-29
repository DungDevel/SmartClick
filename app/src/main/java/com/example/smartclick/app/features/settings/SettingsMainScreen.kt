package com.example.smartclick.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.domain.model.ThemeMode
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@Composable
fun SettingsMainScreen(
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()

    val user = uiState.user
    val isPremium = uiState.isPremium
    val settings = uiState.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp)
    ) {
        // Profile card
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                .fillMaxWidth()
                .background(colors.surf, RoundedCornerShape(20.dp))
                .border(1.dp, colors.bord, RoundedCornerShape(20.dp))
                .clickable { onNavigate("account") }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.primaryGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                if (user != null) {
                    // Online dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(colors.green, CircleShape)
                            .border(2.dp, colors.surf, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (user != null) {
                    Text(user.name ?: stringResource(R.string.user_name_default), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (isPremium) {
                        Text(stringResource(R.string.premium_member), color = colors.tx2, fontSize = 12.sp)
                    } else {
                        Text(stringResource(R.string.user_email_default), color = colors.tx2, fontSize = 12.sp)
                    }
                } else {
                    Text(stringResource(R.string.guest), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(stringResource(R.string.sign_in_to_backup), color = colors.tx2, fontSize = 12.sp)
                }
            }
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .background(colors.primaryGradient, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.pro), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Settings items
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(colors.surf, RoundedCornerShape(16.dp))
                .border(1.dp, colors.bord, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            SettingsRowItem(
                icon = Icons.Default.Info,
                label = stringResource(R.string.account_information),
                sub = stringResource(R.string.manage_your_profile),
                onClick = { onNavigate("account") }
            )
            HorizontalDivider(color = colors.bord, modifier = Modifier.padding(start = 64.dp))
            SettingsRowItem(
                icon = Icons.Default.Place,
                label = stringResource(R.string.language),
                sub = settings?.languageTag ?: stringResource(R.string.system_default),
                onClick = { onNavigate("language") }
            )
            HorizontalDivider(color = colors.bord, modifier = Modifier.padding(start = 64.dp))
            SettingsRowItem(
                icon = Icons.Default.Lock,
                label = stringResource(R.string.privacy_policy),
                sub = stringResource(R.string.data_and_privacy),
                onClick = { onNavigate("privacy") }
            )
            HorizontalDivider(color = colors.bord, modifier = Modifier.padding(start = 64.dp))
            SettingsRowItem(
                icon = Icons.Default.Info,
                label = stringResource(R.string.terms_and_conditions),
                sub = stringResource(R.string.legal_information),
                onClick = { onNavigate("terms") }
            )
            HorizontalDivider(color = colors.bord, modifier = Modifier.padding(start = 64.dp))
            SettingsRowItem(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.appearance),
                sub = settings?.themeMode?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.system),
                onClick = { onNavigate("appearance") }
            )
            HorizontalDivider(color = colors.bord, modifier = Modifier.padding(start = 64.dp))
            SettingsRowItem(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.haptic_feedback),
                sub = if (settings?.hapticEnabled == true) stringResource(R.string.on_text) else stringResource(R.string.off_text),
                onClick = { onNavigate("haptic") }
            )
        }

        if (!isPremium) {
            // Upgrade Banner
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = colors.acGlow)
                    .background(colors.primaryGradient, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.upgrade_to_premium), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.unlimited_devices), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onNavigate("premium") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                ) {
                    Text(stringResource(R.string.update_now), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Text(
            text = stringResource(R.string.app_version_info),
            color = colors.tx3,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colors = SmartClickThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.surfEl, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.tx2,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = colors.tx1, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (sub != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(sub, color = colors.tx2, fontSize = 12.sp)
            }
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(colors.acDim, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(badge, color = colors.acc, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.go),
                tint = colors.tx3
            )
        }
    }
}

