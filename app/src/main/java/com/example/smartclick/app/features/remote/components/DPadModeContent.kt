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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.raisedSurface
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import com.example.smartclick.app.domain.usecase.SendCommandResult
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext

@Composable
fun DPadModeContent(
    dispatchCommand: (RemoteCommand) -> Unit,
    capabilities: Set<TvCapability>,
    connected: Boolean = true,
    navigationEnabled: Boolean = TvCapability.DPAD in capabilities,
    onHoldCommand: (suspend (RemoteCommand) -> SendCommandResult)? = null,
    onPressStart: () -> Unit = {},
    controlsEnabled: Boolean = true,
    onKeyboard: () -> Unit = {},
    onDictate: () -> Unit = {},
    showKeyboard: Boolean = false,
    showDictation: Boolean = false
) {
    val colors = SmartClickThemeTokens.colors

    // Nav row: MENU / BACK / HOME / INPUT (vuông bo góc, không viền, có bóng)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val navButtons = listOf(
            stringResource(R.string.btn_menu) to Pair(R.drawable.menu, RemoteCommand.Menu),
            stringResource(R.string.back) to Pair(R.drawable.left_arrow, RemoteCommand.Back),
            stringResource(R.string.btn_home) to Pair(R.drawable.home, RemoteCommand.Home),
            stringResource(R.string.btn_input) to Pair(R.drawable.map, RemoteCommand.Input)
        )
        navButtons.forEach { (label, data) ->
            val iconRes = data.first
            val command = data.second
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .raisedSurface(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { dispatchCommand(command) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(colors.tx2)
                    )
                }
                Text(
                    text = label.uppercase(),
                    color = colors.tx2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.sp
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))

    // D-Pad
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DPad(
            onUp = { dispatchCommand(RemoteCommand.DPadUp) },
            onDown = { dispatchCommand(RemoteCommand.DPadDown) },
            onLeft = { dispatchCommand(RemoteCommand.DPadLeft) },
            onRight = { dispatchCommand(RemoteCommand.DPadRight) },
            onOk = { dispatchCommand(RemoteCommand.Ok) },
            enabled = navigationEnabled,
            onHoldCommand = onHoldCommand,
            onPressStart = onPressStart
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // VOL / CH
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RemoteStepperCard(
            title = stringResource(R.string.vol),
            onMinus = { dispatchCommand(RemoteCommand.VolumeDown) },
            onPlus = { dispatchCommand(RemoteCommand.VolumeUp) },
            minusCommand = RemoteCommand.VolumeDown,
            plusCommand = RemoteCommand.VolumeUp,
            enabled = controlsEnabled && (!connected || capabilities.contains(TvCapability.VOLUME)),
            onHoldCommand = onHoldCommand,
            onPressStart = onPressStart
        )
        RemoteStepperCard(
            title = stringResource(R.string.ch),
            onMinus = { dispatchCommand(RemoteCommand.ChannelDown) },
            onPlus = { dispatchCommand(RemoteCommand.ChannelUp) },
            minusCommand = RemoteCommand.ChannelDown,
            plusCommand = RemoteCommand.ChannelUp,
            enabled = controlsEnabled && (!connected || capabilities.contains(TvCapability.CHANNEL_CONTROL)),
            onHoldCommand = onHoldCommand,
            onPressStart = onPressStart
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Media Controls: thẻ không viền, mỗi nút là một ô nổi
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .raisedCard()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val mediaIcons = listOf(
            R.drawable.backward to RemoteCommand.Rewind,
            R.drawable.multimedia_controls_1 to RemoteCommand.Previous,
            R.drawable.pause to RemoteCommand.PlayPause,
            R.drawable.multimedia_controls to RemoteCommand.Next,
            R.drawable.next_button to RemoteCommand.FastForward
        )
        mediaIcons.forEach { (iconRes, command) ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .raisedSurface(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { dispatchCommand(command) },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = command.javaClass.simpleName,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.tx2)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Keyboard / Search / Dictate: ba nút nổi riêng, không có thẻ bao ngoài
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val canInput = capabilities.contains(TvCapability.KEYBOARD_TEXT)
        val hasSearchUi = capabilities.contains(TvCapability.OPEN_SEARCH_UI)
        val context = LocalContext.current
        val searchNotSupportedMsg = stringResource(R.string.remote_search_not_supported)

        val extraButtons = listOf(
            Triple(R.drawable.keyboard, stringResource(R.string.keyboard), canInput to onKeyboard),
            Triple(R.drawable.search, stringResource(R.string.search), true to {
                if (hasSearchUi) {
                    dispatchCommand(RemoteCommand.OpenSearchUi)
                } else {
                    android.widget.Toast.makeText(context, searchNotSupportedMsg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }),
            Triple(R.drawable.microphone, stringResource(R.string.dictate_text), canInput to onDictate)
        )
        extraButtons.forEach { (iconRes, label, state) ->
            val (enabled, onClick) = state
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .alpha(if (enabled) 1f else 0.4f)
                    .raisedSurface(RoundedCornerShape(16.dp))
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(colors.tx2)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}
