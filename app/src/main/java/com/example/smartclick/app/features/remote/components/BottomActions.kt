package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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


    // Delete / Shot / Config
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surf, RoundedCornerShape(14.dp))
            .border(1.dp, colors.bord, RoundedCornerShape(14.dp))
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
                    .clickable {
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

    val isListening = speechState is SpeechState.Listening || speechState is SpeechState.Processing
    val voiceTextStr = when (speechState) {
        is SpeechState.Idle -> stringResource(R.string.voice_search)
        is SpeechState.Listening -> "Listening..."
        is SpeechState.Processing -> "Processing: ${speechState.partialText}"
        is SpeechState.Result -> "Result: ${speechState.text}"
        is SpeechState.Error -> "Error: ${speechState.message}"
    }

    val boxColor = if (isListening) colors.acc.copy(alpha = 0.3f) else colors.acc.copy(alpha = 0.15f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(boxColor)
            .border(1.dp, colors.acc, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onStartVoice()
                        tryAwaitRelease()
                        onStopVoice()
                    }
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.microphone),
            contentDescription = stringResource(R.string.voice_search),
            tint = colors.acc,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = voiceTextStr,
            color = colors.tx1,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}
