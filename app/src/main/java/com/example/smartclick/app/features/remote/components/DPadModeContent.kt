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
import androidx.compose.ui.res.stringResource

@Composable
fun DPadModeContent(dispatchCommand: (RemoteCommand) -> Unit) {
    val colors = SmartClickThemeTokens.colors

    // Nav row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val navButtons = listOf(
            "Menu" to Pair(R.drawable.menu, RemoteCommand.Menu),
            stringResource(R.string.back) to Pair(R.drawable.left_arrow, RemoteCommand.Back),
            "Home" to Pair(R.drawable.home, RemoteCommand.Home),
            "Input" to Pair(R.drawable.map, RemoteCommand.Input)
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
            onPlus = { dispatchCommand(RemoteCommand.VolumeUp) }
        )
        RemoteStepperCard(
            title = stringResource(R.string.ch),
            onMinus = { dispatchCommand(RemoteCommand.Previous) },
            onPlus = { dispatchCommand(RemoteCommand.Next) }
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
    val context = LocalContext.current
    val extraRows = listOf(
        listOf(
            "First" to R.drawable.backward,
            stringResource(R.string.back) to R.drawable.left_arrow,
            "Headphones" to R.drawable.podcast,
            "Mic" to R.drawable.microphone
        ),
        listOf(
            "Repeat" to R.drawable.repost,
            "Chat" to R.drawable.chat,
            "Favorite" to R.drawable.star,
            "Volume" to R.drawable.volume
        )
    )
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
                        onClick = { Toast.makeText(context, "Unmapped", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }
}
