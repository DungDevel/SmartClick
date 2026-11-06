package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.usecase.SendCommandResult

@Composable
fun DPad(
    modifier: Modifier = Modifier,
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onOk: () -> Unit = {},
    enabled: Boolean = true,
    onHoldCommand: (suspend (RemoteCommand) -> SendCommandResult)? = null,
    onPressStart: () -> Unit = {}
) {
    val colors = SmartClickThemeTokens.colors
    val okDescription = stringResource(R.string.ok_btn)

    // Kích thước: Figma là 174 (thanh rộng 58). Đã phóng to khoảng 15%.
    // Muốn to/nhỏ hơn thì chỉ cần đổi padSize, các kích thước khác tự theo tỉ lệ.
    val padSize = 200.dp
    val outerSize = padSize + 40.dp
    val armWidth = padSize / 3
    val armLength = padSize / 2
    val corner = armWidth / 2
    val barShape = RoundedCornerShape(corner)

    val isDark = colors.bg.luminance() < 0.5f
    val shadowColor = if (isDark) Color(0xFF050F1C).copy(alpha = 0.52f)
    else Color(0xFF102B4E).copy(alpha = 0.24f)
    val barBrush = Brush.linearGradient(
        0f to Color(0xFF365D89),
        0.52f to Color(0xFF173A60),
        1f to Color(0xFF102B4E)
    )
    val outline = Color.White.copy(alpha = 0.12f)

    Box(
        modifier = modifier.size(outerSize).alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(padSize)) {
            // Hai thanh nền giao nhau (chỉ để vẽ, không nhận bấm)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(padSize, armWidth)
                    .shadow(18.dp, barShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .background(barBrush, barShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(armWidth, padSize)
                    .shadow(10.dp, barShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .background(barBrush, barShape)
            )
            // Viền trong mờ
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(padSize - 4.dp, armWidth - 4.dp)
                    .border(1.dp, outline, RoundedCornerShape(corner - 2.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(armWidth - 4.dp, padSize - 4.dp)
                    .border(1.dp, outline, RoundedCornerShape(corner - 2.dp))
            )

            DPadArm(
                modifier = Modifier.align(Alignment.TopCenter).size(armWidth, armLength),
                shape = RoundedCornerShape(topStart = corner, topEnd = corner),
                iconAlignment = Alignment.TopCenter,
                rotation = 0f,
                description = stringResource(R.string.dir_up),
                command = RemoteCommand.DPadUp,
                onClick = onUp,
                enabled = enabled,
                onHoldCommand = onHoldCommand,
                onPressStart = onPressStart,
                testTag = "navigation_pad_up"
            )
            DPadArm(
                modifier = Modifier.align(Alignment.BottomCenter).size(armWidth, armLength),
                shape = RoundedCornerShape(bottomStart = corner, bottomEnd = corner),
                iconAlignment = Alignment.BottomCenter,
                rotation = 180f,
                description = stringResource(R.string.dir_down),
                command = RemoteCommand.DPadDown,
                onClick = onDown,
                enabled = enabled,
                onHoldCommand = onHoldCommand,
                onPressStart = onPressStart,
                testTag = "navigation_pad_down"
            )
            DPadArm(
                modifier = Modifier.align(Alignment.CenterStart).size(armLength, armWidth),
                shape = RoundedCornerShape(topStart = corner, bottomStart = corner),
                iconAlignment = Alignment.CenterStart,
                rotation = 270f,
                description = stringResource(R.string.dir_left),
                command = RemoteCommand.DPadLeft,
                onClick = onLeft,
                enabled = enabled,
                onHoldCommand = onHoldCommand,
                onPressStart = onPressStart,
                testTag = "navigation_pad_left"
            )
            DPadArm(
                modifier = Modifier.align(Alignment.CenterEnd).size(armLength, armWidth),
                shape = RoundedCornerShape(topEnd = corner, bottomEnd = corner),
                iconAlignment = Alignment.CenterEnd,
                rotation = 90f,
                description = stringResource(R.string.dir_right),
                command = RemoteCommand.DPadRight,
                onClick = onRight,
                enabled = enabled,
                onHoldCommand = onHoldCommand,
                onPressStart = onPressStart,
                testTag = "navigation_pad_right"
            )

            // OK
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(armWidth)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.22f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .semantics { contentDescription = okDescription }
                    .testTag("navigation_pad_ok")
                    .clickable(enabled = enabled, onClick = onOk),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ok_btn),
                    color = Color.White,
                    fontSize = 18.sp,
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
    command: RemoteCommand,
    onClick: () -> Unit,
    enabled: Boolean,
    onHoldCommand: (suspend (RemoteCommand) -> SendCommandResult)?,
    onPressStart: () -> Unit,
    testTag: String
) {
    // Nền và viền do hai thanh phía sau vẽ; cánh chỉ giữ vùng bấm và mũi tên
    Box(
        modifier = modifier
            .clip(shape)
            .semantics { contentDescription = description }
            .testTag(testTag)
            .then(
                if (onHoldCommand == null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier.heldCommandInput(command, enabled, onPressStart, onHoldCommand)
            ),
        contentAlignment = iconAlignment
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = description,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier
                .padding(14.dp)
                .size(26.dp)
                .rotate(rotation)
        )
    }
}
