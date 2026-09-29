package com.example.smartclick.app.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.Typography

data class FeatureItem(val icon: String, val title: String, val desc: String)

@Composable
fun WelcomeScreen(
    onNext: () -> Unit
) {
    val colors = SmartClickThemeTokens.colors
    val scrollState = rememberScrollState()

    val features = listOf(
        FeatureItem("🎯", stringResource(R.string.universal_control), stringResource(R.string.works_with_brands)),
        FeatureItem("🎙️", stringResource(R.string.voice_commands), stringResource(R.string.hands_free_nav)),
        FeatureItem("📸", stringResource(R.string.cast_media), stringResource(R.string.share_media))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 40.dp, start = 28.dp, end = 28.dp, bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TV Icon Header
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(30.dp),
                            ambientColor = colors.acGlow,
                            spotColor = colors.acGlow
                        )
                        .background(
                            brush = colors.primaryGradient,
                            shape = RoundedCornerShape(30.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📺",
                        fontSize = 50.sp
                    )
                }

                // Title & Subtitle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.welcome_title),
                        color = colors.tx1,
                        style = Typography.headlineLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.control_tv_desc),
                        color = colors.tx2,
                        style = Typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

                // Features List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    features.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surf, RoundedCornerShape(16.dp))
                                .border(1.dp, colors.bord, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = feature.icon, fontSize = 26.sp)
                            Column {
                                Text(
                                    text = feature.title,
                                    color = colors.tx1,
                                    style = Typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = feature.desc,
                                    color = colors.tx2,
                                    style = Typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Button
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.acc,
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.get_started),
                    style = Typography.titleMedium
                )
            }
        }
    }
}
