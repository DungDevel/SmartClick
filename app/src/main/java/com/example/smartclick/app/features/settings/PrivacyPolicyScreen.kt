package com.example.smartclick.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val colors = SmartClickThemeTokens.colors
    val sections = listOf(
        Pair(stringResource(R.string.legal_privacy_collect_title), stringResource(R.string.legal_privacy_collect_body)),
        Pair(stringResource(R.string.legal_privacy_use_title), stringResource(R.string.legal_privacy_use_body)),
        Pair(stringResource(R.string.legal_privacy_sharing_title), stringResource(R.string.legal_privacy_sharing_body)),
        Pair(stringResource(R.string.legal_privacy_retention_title), stringResource(R.string.legal_privacy_retention_body)),
        Pair(stringResource(R.string.legal_privacy_rights_title), stringResource(R.string.legal_privacy_rights_body)),
        Pair(stringResource(R.string.legal_privacy_contact_title), stringResource(R.string.legal_privacy_contact_body))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.privacy_policy), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sections.forEach { (title, body) ->
                Column {
                    Text(title, color = colors.tx1, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(body, color = colors.tx2, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}
