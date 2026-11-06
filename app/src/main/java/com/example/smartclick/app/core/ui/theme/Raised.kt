package com.example.smartclick.app.core.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// Tô kín một Outline bằng brush (thay cho drawOutline để khỏi phụ thuộc import)
private fun DrawScope.fillOutline(outline: Outline, brush: Brush) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = Offset(outline.rect.left, outline.rect.top),
            size = Size(outline.rect.width, outline.rect.height)
        )
        is Outline.Rounded -> {
            val r = outline.roundRect
            drawRoundRect(
                brush = brush,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                cornerRadius = r.topLeftCornerRadius
            )
        }
        is Outline.Generic -> drawPath(outline.path, brush)
    }
}

@Composable
fun Modifier.raisedSurface(
    shape: Shape,
    pressable: Boolean = true,
    fill: Brush? = null,
    shadowTint: Color? = null,
    innerShadow: Boolean = true
): Modifier {
    val isDark = SmartClickThemeTokens.colors.bg.luminance() < 0.5f
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressable && pressed) 1f else 0f, label = "raised_press")

    // Giá trị sáng lấy từ Figma. Giá trị tối là ước lượng (chưa có thiết kế).
    val baseColor = if (isDark) Color(0xFF192B43) else Color(0xFFEDF3F7)
    val shadowColor = shadowTint ?: if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.20f)
    val innerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF0F2B48).copy(alpha = 0.13f)
    val fillBrush = fill ?: SolidColor(baseColor)
    val innerBrush = SolidColor(innerColor)

    return this
        .graphicsLayer { scaleX = 1f - 0.03f * press; scaleY = 1f - 0.03f * press }
        // Drop shadow: X0 Y2 blur8 (khi nhấn: bóng co lại, nút như lún xuống)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val radius = (6.dp.toPx() * (1f - 0.75f * press)).coerceAtLeast(0.5f)
            val dy = 2.dp.toPx() * (1f - press)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.FILL
                    color = baseColor.toArgb()
                    setShadowLayer(radius, 0f, dy, shadowColor.toArgb())
                }
                val nc = canvas.nativeCanvas
                when (outline) {
                    is Outline.Rectangle -> {
                        val r = outline.rect
                        nc.drawRect(r.left, r.top, r.right, r.bottom, paint)
                    }
                    is Outline.Rounded -> {
                        val r = outline.roundRect
                        nc.drawRoundRect(
                            r.left, r.top, r.right, r.bottom,
                            r.topLeftCornerRadius.x, r.topLeftCornerRadius.y,
                            paint
                        )
                    }
                    is Outline.Generic -> nc.drawPath(outline.path.asAndroidPath(), paint)
                }
            }
        }
        .clip(shape)
        // Nền + inner shadow: dải 1px ở mép trên (X0 Y1 blur0), dày lên khi nhấn
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            fillOutline(outline, fillBrush)
            if (innerShadow) {
                fillOutline(outline, innerBrush)
                translate(top = 1.dp.toPx() * (1f + press)) { fillOutline(outline, fillBrush) }
            }
        }
        .then(
            if (pressable) Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    pressed = false
                }
            } else Modifier
        )
}
