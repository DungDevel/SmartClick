package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@Composable
fun NumPad(
    modifier: Modifier = Modifier,
    canDelete: Boolean = true,
    onDigit: (String) -> Unit = {},
    onExit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val rows = listOf(
        listOf("1", "2", "3", "EXIT"),
        listOf("4", "5", "6", "0"),
        listOf("7", "8", "9", "DEL")
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    val isEnabled = if (key == "DEL") canDelete else true
                    NumKey(
                        key = key,
                        modifier = Modifier.alpha(if (isEnabled) 1f else 0.4f),
                        onClick = {
                            if (!isEnabled) return@NumKey
                            when (key) {
                                "EXIT" -> onExit()
                                "DEL" -> onDelete()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.NumKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = SmartClickThemeTokens.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(shape)
            .background(colors.surfEl)
            .border(1.dp, colors.bord, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "EXIT" -> Text(stringResource(R.string.exit), color = colors.acc, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            "DEL" -> BackspaceIcon(
                color = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            else -> Text(key, color = colors.tx1, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Icon backspace tự vẽ bằng Canvas, không cần material-icons-extended */
@Composable
private fun BackspaceIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        val strokeWidth = 2f * s

        val outline = Path().apply {
            moveTo(8f * s, 4f * s)
            lineTo(21f * s, 4f * s)
            lineTo(21f * s, 20f * s)
            lineTo(8f * s, 20f * s)
            lineTo(2f * s, 12f * s)
            close()
        }
        drawPath(
            path = outline,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(12f * s, 9f * s),
            end = Offset(17f * s, 15f * s),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(17f * s, 9f * s),
            end = Offset(12f * s, 15f * s),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}