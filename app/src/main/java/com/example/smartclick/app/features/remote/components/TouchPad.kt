package com.example.smartclick.app.features.remote.components

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.R
import kotlin.math.roundToInt

@Composable
fun TouchPad(
    modifier: Modifier = Modifier,
    onMove: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onTap: () -> Unit = {},
    onLeftClick: () -> Unit = {},
    onRightClick: () -> Unit = {},
    onKeyboardRequest: (() -> Unit)? = null
) {
    val colors = SmartClickThemeTokens.colors
    val shape = RoundedCornerShape(18.dp)

    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val center = Offset(padSize.width / 2f, padSize.height / 2f)
    val dotPos by animateOffsetAsState(
        targetValue = touch ?: center,
        animationSpec = if (touch != null) snap<Offset>() else spring<Offset>(),
        label = "touchDot"
    )
    val dotRadiusPx = with(LocalDensity.current) { 7.dp.toPx() }
    val gridColor = colors.tx2.copy(alpha = 0.25f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape)
                .background(colors.surf)
                .border(1.dp, colors.bord, shape)
                .onSizeChanged { padSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { touch = it },
                        onDragEnd = { touch = null; onDragEnd() },
                        onDragCancel = { touch = null; onDragEnd() },
                        onDrag = { change, drag ->
                            change.consume()
                            touch = Offset(
                                change.position.x.coerceIn(0f, size.width.toFloat()),
                                change.position.y.coerceIn(0f, size.height.toFloat())
                            )
                            onMove(drag.x, drag.y)
                        }
                    )
                }
        ) {
            // Dot grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = 20.dp.toPx()
                var x = spacing
                while (x < size.width) {
                    var y = spacing
                    while (y < size.height) {
                        drawCircle(gridColor, radius = 1.5f, center = Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
            }

            // Cursor dot
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dotPos.x - dotRadiusPx).roundToInt(),
                            (dotPos.y - dotRadiusPx).roundToInt()
                        )
                    }
                    .size(14.dp)
                    .shadow(8.dp, CircleShape, ambientColor = colors.acc, spotColor = colors.acc)
                    .background(colors.acc, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadTextButton(stringResource(R.string.touchpad_left_click), onLeftClick)
            if (onKeyboardRequest != null) {
                PadTextButton(stringResource(R.string.touchpad_keyboard), onKeyboardRequest)
            }
            PadTextButton(stringResource(R.string.touchpad_right_click), onRightClick)
        }
    }
}

@Composable
private fun RowScope.PadTextButton(text: String, onClick: () -> Unit) {
    val colors = SmartClickThemeTokens.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(shape)
            .background(colors.surf)
            .border(1.dp, colors.bord, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.acc,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
