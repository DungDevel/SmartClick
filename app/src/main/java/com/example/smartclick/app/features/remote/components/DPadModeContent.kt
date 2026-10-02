package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.model.RemoteCommand
import com.example.smartclick.app.domain.model.TvCapability
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha

@Composable
fun DPadModeContent(dispatchCommand: (RemoteCommand) -> Unit, capabilities: Set<TvCapability>) {
    val colors = SmartClickThemeTokens.colors

    // Nav row
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
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(colors.surf, CircleShape)
                        .border(1.dp, colors.bord, CircleShape)
                        .clickable { dispatchCommand(command) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
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
            onOk = { dispatchCommand(RemoteCommand.Ok) }
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
            enabled = capabilities.contains(TvCapability.VOLUME)
        )
        RemoteStepperCard(
            title = stringResource(R.string.ch),
            onMinus = { dispatchCommand(RemoteCommand.ChannelDown) },
            onPlus = { dispatchCommand(RemoteCommand.ChannelUp) },
            enabled = capabilities.contains(TvCapability.CHANNEL_CONTROL)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Media Controls
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surf, RoundedCornerShape(14.dp))
            .border(1.dp, colors.bord, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = command.javaClass.simpleName, // Fixed empty content description
                modifier = Modifier
                    .size(24.dp)
                    .clickable { dispatchCommand(command) }
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Extra icon grid (2 rows x 4)
    
    val extraRows = listOf(
        listOf(
            stringResource(R.string.btn_first) to R.drawable.backward,
            stringResource(R.string.back) to R.drawable.left_arrow,
            stringResource(R.string.btn_headphones) to R.drawable.podcast,
            stringResource(R.string.btn_mic) to R.drawable.microphone
        ),
        listOf(
            stringResource(R.string.btn_repeat) to R.drawable.repost,
            stringResource(R.string.btn_chat) to R.drawable.chat,
            stringResource(R.string.btn_favorite) to R.drawable.star,
            stringResource(R.string.btn_volume) to R.drawable.volume
        )
    )
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        extraRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (desc, icon) ->
                    RemoteSquareButton(
                        icon = icon,
                        contentDescription = desc,
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.feature_coming_soon, desc), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
