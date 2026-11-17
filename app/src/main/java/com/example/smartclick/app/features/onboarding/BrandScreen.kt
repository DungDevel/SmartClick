package com.example.smartclick.app.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.R
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.core.ui.theme.onboardingBackground

/**
 * Maps a brand ID to its corresponding brand image.
 *
 * Logos are drawn with ContentScale.Fit inside a uniform white tile, so
 * differently sized drawables no longer get cropped or look inconsistent.
 */
private fun brandLogo(brandId: String): Int? = when (brandId.lowercase()) {
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
    "amazon" -> R.drawable.amazon_logo
    "chromecast" -> R.drawable.chromecastlogo
    "other", "others" -> R.drawable.tvicon
    else -> null
}

/** Extra zoom for logos whose drawable has a lot of empty margin. */
private fun brandZoom(brandId: String): Float = when (brandId.lowercase()) {
    "samsung" -> 1.6f
    "amazon" -> 1.6f
    "chromecast" -> 1.3f
    else -> 1f
}

@Composable
fun BrandScreen(
    onNext: (String) -> Unit,
    viewModel: BrandViewModel = hiltViewModel()
) {
    val brands by viewModel.brands.collectAsState()
    var selectedBrandId by remember { mutableStateOf<String?>(null) }

    val colors = SmartClickThemeTokens.colors
    val cardShape = RoundedCornerShape(18.dp)
    val logoShape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onboardingBackground()
            .padding(
                top = 56.dp,
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
            fontSize = 26.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = stringResource(R.string.what_brand_tv),
            color = colors.tx2,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ─────────────────────────────────────────────
        // Brand grid
        // ─────────────────────────────────────────────

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = brands,
                key = { it.id }
            ) { brand ->

                val isSelected = selectedBrandId == brand.id

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.04f else 1f,
                    label = "brandScale"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) colors.acc else Color.Transparent,
                    label = "brandBorder"
                )

                Surface(
                    shape = cardShape,
                    color = Color.White,
                    border = BorderStroke(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) borderColor else colors.bord
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.7f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 14.dp,
                                    shape = cardShape,
                                    spotColor = colors.acDim,
                                    ambientColor = colors.acDim
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(cardShape)
                        .clickable { selectedBrandId = brand.id }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val logoRes = brandLogo(brand.id)
                        if (logoRes != null) {
                            Image(
                                painter = painterResource(id = logoRes),
                                contentDescription = brand.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .clip(logoShape)
                                    .graphicsLayer {
                                        scaleX = brandZoom(brand.id)
                                        scaleY = brandZoom(brand.id)
                                    }
                            )
                        } else {
                            Text(
                                text = brand.displayName,
                                color = Color(0xFF1B2540),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        if (isSelected) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(22.dp)
                                    .background(colors.acc, CircleShape)
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
                .padding(top = 16.dp)
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}
