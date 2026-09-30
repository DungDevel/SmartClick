package com.example.smartclick.app.features.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

/**
 * Maps a brand ID to its corresponding brand image.
 *
 * Each drawable should ideally use the same aspect ratio
 * so that all brand cards have a consistent visual appearance.
 */
private fun brandLogo(brandId: String): Int = when (brandId.lowercase()) {
    "samsung" -> R.drawable.samsunglogo
    "lg" -> R.drawable.lglogo
    "sony" -> R.drawable.sonylogo
    "tcl" -> R.drawable.tcllogo
    "hisense" -> R.drawable.hisense
    "philips" -> R.drawable.philipslogo
    "panasonic" -> R.drawable.panasoniclogo
    "vizio" -> R.drawable.viziologo
    "sharp" -> R.drawable.sharplogo
    "toshiba" -> R.drawable.toshibalogo
    "xiaomi" -> R.drawable.xiaomilogo
    "roku" -> R.drawable.rokulogo
    "appletv" -> R.drawable.appletvlogo
    "amazon" -> R.drawable.amazon_logo
    "chromecast" -> R.drawable.chromecastlogo
    else -> R.drawable.podcast
}

@Composable
fun BrandScreen(
    onNext: (String) -> Unit,
    viewModel: BrandViewModel = hiltViewModel()
) {
    val brands by viewModel.brands.collectAsState()
    var selectedBrandId by remember { mutableStateOf<String?>(null) }

    val colors = SmartClickThemeTokens.colors
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(
                top = 32.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp
            )
    ) {

        // ─────────────────────────────────────────────
        // Header
        // ─────────────────────────────────────────────

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

        // ─────────────────────────────────────────────
        // Brand grid
        // ─────────────────────────────────────────────

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = brands,
                key = { it.id }
            ) { brand ->

                val isSelected = selectedBrandId == brand.id

                Surface(
                    shape = cardShape,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            colors.acc
                        } else {
                            colors.bord
                        }
                    ),
                    color = colors.surf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clickable {
                            selectedBrandId = brand.id
                        }
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 16.dp,
                                    shape = cardShape,
                                    spotColor = colors.acDim,
                                    ambientColor = colors.acDim
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Image(
                        painter = painterResource(
                            id = brandLogo(brand.id)
                        ),
                        contentDescription = brand.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cardShape)
                    )
                }
            }
        }

        // ─────────────────────────────────────────────
        // Continue button
        // ─────────────────────────────────────────────

        Button(
            onClick = {
                // FW-2/FW-3:
                // Pass brand ID to the orchestrator so it can
                // resolve BrandProfile.probePlan.
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
                text = if (selectedBrandId != null) {
                    stringResource(R.string.connect_tv)
                } else {
                    stringResource(R.string.skip)
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
