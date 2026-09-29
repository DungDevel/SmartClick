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
fun TermsAndConditionScreen(onBack: () -> Unit) {
    val colors = SmartClickThemeTokens.colors
    val terms = listOf(
        Pair("1. Acceptance of Terms", "By accessing and using SmartClick, you accept and agree to be bound by the terms and provision of this agreement."),
        Pair("2. License Use", "We grant you a personal, non-exclusive, non-transferable license to use the app for personal and non-commercial purposes."),
        Pair("3. Restrictions", "You may not reverse engineer, decompile, or disassemble the app, except and only to the extent that such activity is expressly permitted by applicable law."),
        Pair("4. User Accounts", "You are responsible for maintaining the confidentiality of your account and password and for restricting access to your device."),
        Pair("5. Termination", "We may terminate or suspend access to our service immediately, without prior notice or liability, for any reason whatsoever.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.terms_and_conditions), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            terms.forEach { (title, body) ->
                Column {
                    Text(title, color = colors.tx1, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(body, color = colors.tx2, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}
