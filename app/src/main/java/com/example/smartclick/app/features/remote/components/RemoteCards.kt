package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

@Composable
fun RowScope.RemoteStepperCard(
    title: String,
    onMinus: () -> Unit = {},
    onPlus: () -> Unit = {}
) {
    val colors = SmartClickThemeTokens.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .background(colors.surf, RoundedCornerShape(14.dp))
            .border(1.dp, colors.bord, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = colors.tx2,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onMinus,
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfEl),
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.tx1) }
            Button(
                onClick = onPlus,
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfEl),
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.tx1) }
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
            .background(colors.surf, RoundedCornerShape(12.dp))
            .border(1.dp, colors.bord, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}