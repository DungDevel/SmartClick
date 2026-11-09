package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import com.example.smartclick.app.domain.voice.SpeechState

@Composable
fun BottomActions(
    speechState: SpeechState = SpeechState.Idle,
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {}
) {
    val colors = SmartClickThemeTokens.colors


    // Delete / Shot / Config (thẻ không viền, chỉ có bóng nhẹ)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .raisedCard()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val context = LocalContext.current
        listOf(
            stringResource(R.string.delete) to R.drawable.trash,
            stringResource(R.string.shot) to R.drawable.camera,
            stringResource(R.string.config) to R.drawable.settings
        ).forEach { (label, iconRes) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        Toast.makeText(context, context.getString(R.string.feature_coming_soon, label), Toast.LENGTH_SHORT).show()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = colors.tx2,
                    modifier = Modifier.size(25.dp)
                )
                Text(
                    text = label,
                    color = colors.tx2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Trạng thái nghe giờ hiện trong bảng VoiceSearchSheet, nút chỉ đóng vai trò mở bảng đó
    val isListening = false
    val voiceTextStr = stringResource(R.string.voice_action)

    val boxColor = if (isListening) colors.acc.copy(alpha = 0.25f) else colors.acc.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(boxColor)
            .border(1.dp, colors.acc.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onStartVoice() })
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.microphone),
            contentDescription = stringResource(R.string.voice_action),
            tint = colors.acc,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = voiceTextStr,
            color = colors.acc,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}
