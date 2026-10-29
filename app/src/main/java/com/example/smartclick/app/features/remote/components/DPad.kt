package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

@Composable
fun DPad(
    modifier: Modifier = Modifier,
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onOk: () -> Unit = {},
    enabled: Boolean = true
) {
    val colors = SmartClickThemeTokens.colors
    val okDescription = stringResource(R.string.ok_btn)
    val armWidth = 68.dp
    val armLength = 100.dp
    val corner = armWidth / 2

    Box(
        modifier = modifier.size(220.dp).alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        // Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(listOf(colors.acc.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape
                )
        )

        Box(modifier = Modifier.size(200.dp)) {
            DPadArm(
                modifier = Modifier.align(Alignment.TopCenter).size(armWidth, armLength),
                shape = RoundedCornerShape(topStart = corner, topEnd = corner),
                iconAlignment = Alignment.TopCenter,
                rotation = 0f,
                description = stringResource(R.string.dir_up),
                onClick = onUp,
                enabled = enabled,
                testTag = "navigation_pad_up"
            )
            DPadArm(
                modifier = Modifier.align(Alignment.BottomCenter).size(armWidth, armLength),
                shape = RoundedCornerShape(bottomStart = corner, bottomEnd = corner),
                iconAlignment = Alignment.BottomCenter,
                rotation = 180f,
                description = stringResource(R.string.dir_down),
                onClick = onDown,
                enabled = enabled,
                testTag = "navigation_pad_down"
            )
            DPadArm(
                modifier = Modifier.align(Alignment.CenterStart).size(armLength, armWidth),
                shape = RoundedCornerShape(topStart = corner, bottomStart = corner),
                iconAlignment = Alignment.CenterStart,
                rotation = 270f,
                description = stringResource(R.string.dir_left),
                onClick = onLeft,
                enabled = enabled,
                testTag = "navigation_pad_left"
            )
            DPadArm(
                modifier = Modifier.align(Alignment.CenterEnd).size(armLength, armWidth),
                shape = RoundedCornerShape(topEnd = corner, bottomEnd = corner),
                iconAlignment = Alignment.CenterEnd,
                rotation = 90f,
                description = stringResource(R.string.dir_right),
                onClick = onRight,
                enabled = enabled,
                testTag = "navigation_pad_right"
            )

            // OK
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(lerp(colors.acc, Color.Black, 0.3f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .semantics { contentDescription = okDescription }
                    .testTag("navigation_pad_ok")
                    .clickable(enabled = enabled, onClick = onOk),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ok_btn),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun DPadArm(
    modifier: Modifier,
    shape: Shape,
    iconAlignment: Alignment,
    rotation: Float,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String
) {
    val colors = SmartClickThemeTokens.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.acc)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .semantics { contentDescription = description }
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = iconAlignment
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier
                .padding(12.dp)
                .size(22.dp)
                .rotate(rotation)
        )
    }
}
