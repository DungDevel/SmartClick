package com.example.smartclick.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.model.ThemeMode
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val settings by viewModel.settings.collectAsState()
    val currentMode = settings?.themeMode ?: ThemeMode.DARK

        val themes = listOf(
        Triple(ThemeMode.DARK, stringResource(R.string.theme_dark), stringResource(R.string.theme_dark_desc)),
        Triple(ThemeMode.LIGHT, stringResource(R.string.theme_light), stringResource(R.string.theme_light_desc)),
        Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_system), stringResource(R.string.theme_system_desc))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.appearance), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.tx1)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.bg
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            themes.forEach { (id, label, desc) ->
                val isSelected = currentMode == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.acDim else colors.surf)
                        .border(1.5.dp, if (isSelected) colors.acc else colors.bord, RoundedCornerShape(16.dp))
                        .clickable { viewModel.setThemeMode(id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                                                text = when (id) {
                            ThemeMode.DARK -> "🌌"
                            ThemeMode.LIGHT -> "☀️"
                            ThemeMode.SYSTEM -> "📱"
                        },
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, color = colors.tx1, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(desc, color = colors.tx2, fontSize = 12.sp)
                    }
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = stringResource(R.string.selected), tint = colors.acc)
                    }
                }
            }
        }
    }
}


