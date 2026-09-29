package com.example.smartclick.app.features.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.model.BrandProfile

/**
 * FW-2: Icon là mối lo trình bày, tra theo id hãng; dữ liệu kết nối nằm ở `BrandProfile`.
 */
private fun brandIcon(brandId: String): String = when (brandId.lowercase()) {
    "samsung", "sony", "hisense", "panasonic", "sharp", "xiaomi", "apple_tv", "chromecast" -> "📺"
    "others" -> "❓"
    else -> "🖥"
}

@Composable
fun BrandScreen(
    onNext: (String) -> Unit,
    viewModel: BrandViewModel = hiltViewModel()
) {
    val brands by viewModel.brands.collectAsState()
    var selectedBrandId by remember { mutableStateOf<String?>(null) }
    val colors = SmartClickThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.select_tv_brand),
            color = colors.tx1,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.what_brand_tv),
            color = colors.tx2,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(brands, key = { it.id }) { brand ->
                val isSelected = selectedBrandId == brand.id

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isSelected) colors.acc else colors.bord
                    ),
                    color = if (isSelected) colors.surfEl else colors.surf,
                    modifier = Modifier
                        .clickable { selectedBrandId = brand.id }
                        .then(
                            if (isSelected) Modifier.shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = colors.acDim,
                                ambientColor = colors.acDim
                            ) else Modifier
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp)
                    ) {
                        Text(text = brandIcon(brand.id), fontSize = 28.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Text(
                            text = brand.displayName,
                            color = colors.tx1,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                // FW-2/FW-3: truyền brand id để orchestrator tra BrandProfile.probePlan.
                onNext(selectedBrandId ?: "others")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.acc,
                contentColor = Color.White
            )
        ) {
            Text(
                text = if (selectedBrandId != null) stringResource(R.string.connect_tv) else stringResource(R.string.skip),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
