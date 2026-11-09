package com.example.smartclick.app.features.remote.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.usecase.VoiceCommandResult
import com.example.smartclick.app.domain.voice.SpeechState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Bảng "Voice Search" trượt từ dưới lên (theo thiết kế Figma):
 * Listening -> Processing -> Result (dấu tick xanh), có thêm trạng thái lỗi.
 *
 * [outcome] là kết quả xử lý lệnh trên TV (từ RemoteViewModel.voiceOutcome):
 * null = đang xử lý, SUCCESS = tick xanh, còn lại = hiện lỗi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSearchSheet(
    state: SpeechState,
    outcome: VoiceCommandResult?,
    onCancel: () -> Unit,
    onFinished: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = SmartClickThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // StateFlow giữ giá trị của lần nói trước. Bỏ qua trạng thái cũ đó khi vừa mở bảng.
    val initialState = remember { state }
    // Giữ trạng thái gần nhất khác Idle, để khi engine tự reset về Idle thì bảng không nhảy lại "Listening".
    var display by remember { mutableStateOf<SpeechState?>(null) }
    LaunchedEffect(state) {
        val stale = state === initialState &&
            (state is SpeechState.Result || state is SpeechState.Error)
        if (state !is SpeechState.Idle && !stale) display = state
    }
    val shown = display

    val isSuccess = shown is SpeechState.Result && outcome == VoiceCommandResult.SUCCESS
    val isCommandFailure = shown is SpeechState.Result && outcome != null && outcome != VoiceCommandResult.SUCCESS
    val isFailure = shown is SpeechState.Error || isCommandFailure

    // Thành công: hiện tick một lúc rồi tự đóng
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            delay(1800)
            onFinished()
        }
    }

    val failureText: String = when {
        shown is SpeechState.Error -> stringResource(R.string.voice_error_display, shown.message)
        outcome == VoiceCommandResult.NOT_CONNECTED -> stringResource(R.string.command_not_connected)
        outcome == VoiceCommandResult.NOT_SUPPORTED -> stringResource(R.string.command_not_supported)
        outcome == VoiceCommandResult.UNRECOGNIZED -> stringResource(R.string.voice_unrecognized)
        else -> stringResource(R.string.command_failed)
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = colors.surf,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.voice_action),
                    color = colors.tx1,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.surfEl, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCancel
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = colors.tx2,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    isFailure -> VoiceErrorContent(failureText)
                    isSuccess -> VoiceResultContent((shown as SpeechState.Result).text)
                    // Đã nhận dạng xong, đang chờ gửi lệnh sang TV
                    shown is SpeechState.Result -> VoiceProcessingContent("")
                    shown is SpeechState.Processing -> VoiceProcessingContent(shown.partialText)
                    else -> VoiceListeningContent()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isFailure) {
                    VoicePillButton(stringResource(R.string.voice_sheet_try_again), filled = true, onClick = onRetry)
                }
                if (!isSuccess) {
                    VoicePillButton(stringResource(R.string.cancel), filled = false, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun VoiceMicOrb(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF365D89), Color(0xFF102B4E)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.microphone),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.38f)
        )
    }
}

@Composable
private fun VoiceListeningContent() {
    val colors = SmartClickThemeTokens.colors
    val transition = rememberInfiniteTransition(label = "voice_listening")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "voice_pulse"
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "voice_wave"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(pulse)
                .background(colors.acc.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(2f - pulse)
                .background(colors.acc.copy(alpha = 0.14f), CircleShape)
        )
        VoiceMicOrb(76.dp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(17) { i ->
            val h = 6f + 22f * abs(sin(phase + i * 0.55f))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(colors.acc.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.voice_listening),
        color = colors.tx1,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.voice_sheet_say_command),
        color = colors.tx2,
        fontSize = 12.sp
    )
}

@Composable
private fun VoiceProcessingContent(partialText: String) {
    val colors = SmartClickThemeTokens.colors
    val transition = rememberInfiniteTransition(label = "voice_processing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "voice_dots"
    )

    VoiceMicOrb(60.dp)
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a = 0.3f + 0.7f * ((sin(phase - i * 1.2f) + 1f) / 2f)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.acc.copy(alpha = a), CircleShape)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.voice_sheet_processing),
        color = colors.tx1,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (partialText.isBlank()) stringResource(R.string.voice_sheet_please_wait) else partialText,
        color = colors.tx2,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun VoiceResultContent(text: String) {
    val colors = SmartClickThemeTokens.colors
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF34D27B), Color(0xFF16A34A)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.voice_sheet_quote, text),
        color = colors.tx1,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.voice_sheet_done),
        color = colors.tx2,
        fontSize = 12.sp
    )
}

@Composable
private fun VoiceErrorContent(message: String) {
    val colors = SmartClickThemeTokens.colors
    Box(modifier = Modifier.alpha(0.5f)) { VoiceMicOrb(60.dp) }
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = message,
        color = colors.tx1,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun VoicePillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val colors = SmartClickThemeTokens.colors
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(colors.acc, CircleShape)
                else Modifier.border(1.dp, colors.acc.copy(alpha = 0.35f), CircleShape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else colors.acc,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
