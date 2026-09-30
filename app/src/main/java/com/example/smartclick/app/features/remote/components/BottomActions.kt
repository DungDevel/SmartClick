package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import androidx.compose.ui.res.stringResource

@Composable
fun BottomActions() {
    val colors = SmartClickThemeTokens.colors
    

    // Delete / Shot / Config
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surf, RoundedCornerShape(14.dp))
            .border(1.dp, colors.bord, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val context = LocalContext.current
        listOf(
            stringResource(R.string.delete) to R.drawable.trash,
            stringResource(R.string.shot) to R.drawable.camera,
            stringResource(R.string.config) to R.drawable.settings
        ).forEach { (label, iconRes) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        Toast.makeText(context, context.getString(R.string.feature_coming_soon, label), Toast.LENGTH_SHORT).show()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = colors.tx2,
                    modifier = Modifier.size(25.dp)
                )
                Text(
                    text = label,
                    color = colors.tx2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Voice Search is hidden until P9 is implemented
    /*
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.acc.copy(alpha = 0.15f))
            .border(1.dp, colors.acc, RoundedCornerShape(12.dp))
            .clickable {  },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.microphone),
            contentDescription = stringResource(R.string.voice_search),
            tint = colors.acc,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.voice_search),
            color = colors.acc,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    */
    Spacer(modifier = Modifier.height(24.dp))
}
