package com.example.smartclick.app.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.Typography
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

data class LanguageItem(val code: String, val nameRes: Int, val flag: String)

val LANGUAGES = listOf(
    LanguageItem("sys", R.string.system_default, "📱"),
    LanguageItem("en", R.string.language_english, "🇺🇸"),
    LanguageItem("vi", R.string.language_vietnamese, "🇻🇳"),
    LanguageItem("es", R.string.language_spanish, "🇪🇸"),
    LanguageItem("pt-BR", R.string.language_portuguese_brazil, "🇧🇷"),
    LanguageItem("de", R.string.language_german, "🇩🇪")
)

@Composable
fun LanguageScreen(
    currentLanguage: String = "en",
    onSelectAndNext: (String) -> Unit
) {
    val colors = SmartClickThemeTokens.colors
    var selectedLanguageCode by remember(currentLanguage) { mutableStateOf(currentLanguage) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.choose_language),
                color = colors.tx1,
                style = Typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.choose_language_desc),
                color = colors.tx2,
                style = Typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Language List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LANGUAGES) { language ->
                    val isSelected = selectedLanguageCode == language.code

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .run {
                                if (isSelected) {
                                    shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        ambientColor = colors.acGlow,
                                        spotColor = colors.acGlow
                                    )
                                } else this
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) colors.acDim else colors.surf)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) colors.acc else colors.bord,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedLanguageCode = language.code }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(text = language.flag, fontSize = 24.sp)

                        Text(
                            text = stringResource(id = language.nameRes),
                            color = colors.tx1,
                            style = Typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.selected),
                                tint = colors.acc
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Continue Button
            Button(
                onClick = { onSelectAndNext(selectedLanguageCode) },
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
                    text = stringResource(R.string.continue_btn),
                    style = Typography.titleMedium
                )
            }
        }
    }
}
