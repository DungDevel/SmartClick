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
        Pair("Data We Collect", "We collect device identifiers, network info, and usage analytics."),
        Pair("How We Use Data", "Data is used to provide TV connectivity and improve features."),
        Pair("Data Sharing", "We do not sell your personal data. Anonymized analytics only."),
        Pair("Data Retention", "Your data is retained for 90 days after account deletion."),
        Pair("Your Rights", "You can access, correct, or delete your data at any time."),
        Pair("Contact Us", "privacy@smartclick.app")
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
