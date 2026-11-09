package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.raisedSurface
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.usecase.SendCommandResult

/** Thẻ nền trắng, không viền, chỉ có bóng đổ rất nhẹ (giống thiết kế Figma). */
@Composable
fun Modifier.raisedCard(): Modifier {
    val colors = SmartClickThemeTokens.colors
    return this.raisedSurface(
        shape = RoundedCornerShape(18.dp),
        pressable = false,
        fill = SolidColor(colors.surf),
        shadowTint = Color(0xFF102B4E).copy(alpha = 0.10f),
        innerShadow = false
    )
}

@Composable
fun RowScope.RemoteStepperCard(
    title: String,
    onMinus: () -> Unit = {},
    onPlus: () -> Unit = {},
    enabled: Boolean = true,
    minusCommand: RemoteCommand? = null,
    plusCommand: RemoteCommand? = null,
    onHoldCommand: (suspend (RemoteCommand) -> SendCommandResult)? = null,
    onPressStart: () -> Unit = {}
) {
    val colors = SmartClickThemeTokens.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.4f)
            .raisedCard()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = colors.tx2,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            arrayOf(Triple("-", minusCommand, onMinus), Triple("+", plusCommand, onPlus)).forEach { (label, command, onClick) ->
                val repeatModifier = if (command != null && onHoldCommand != null) {
                    Modifier.heldCommandInput(command, enabled, onPressStart, onHoldCommand)
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = onClick
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).height(40.dp)
                        .raisedSurface(RoundedCornerShape(14.dp))
                        .then(repeatModifier)
                        .semantics { contentDescription = title + label },
                    contentAlignment = Alignment.Center
                ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.tx1) }
            }
        }
    }
}

@Composable
fun RowScope.RemoteSquareButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colors = SmartClickThemeTokens.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .raisedSurface(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(colors.tx2)
        )
    }
}
