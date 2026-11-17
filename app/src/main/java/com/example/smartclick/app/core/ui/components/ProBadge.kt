package com.example.smartclick.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

/** Small orange "PRO" pill shown next to a screen title for premium users. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    val colors = SmartClickThemeTokens.colors
    Text(
        text = stringResource(R.string.pro_badge),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        modifier = modifier
            .background(colors.orange, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
