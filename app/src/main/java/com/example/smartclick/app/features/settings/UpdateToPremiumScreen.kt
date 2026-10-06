package com.example.smartclick.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import com.example.smartclick.app.domain.model.PricingPlan
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateToPremiumScreen(
    onBack: () -> Unit,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val premiumPerks = listOf(
        stringResource(R.string.perk_unlimited_devices),
        stringResource(R.string.perk_voice_commands),
        stringResource(R.string.perk_media_casting),
        stringResource(R.string.perk_no_ads)
    )
    val uiState by viewModel.uiState.collectAsState()
    val billingMessage by viewModel.billingMessages.collectAsState(initial = null)
    var selectedPlanId by remember { mutableStateOf("annual") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(billingMessage) {
        billingMessage?.let { msg ->
            if (msg.isNotEmpty()) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.tx1)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.primaryGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⭐", fontSize = 32.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.smartclick_premium), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.unlock_all_features), color = colors.tx2, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(32.dp))

            // Pricing Plans - lấy từ ViewModel (repository), không hardcode giá
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(32.dp),
                        color = colors.acc
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = stringResource(R.string.failed_to_load_plans),
                        color = colors.tx2,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(32.dp)
                            .clickable { viewModel.retry() }
                    )
                }
                else -> {
                    PricingPlansRow(
                        plans = uiState.plans,
                        selectedPlanId = selectedPlanId,
                        onSelectPlan = { selectedPlanId = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Perks
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                premiumPerks.forEach { perk ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFF22C55E).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(perk, color = colors.tx1, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    (context as? android.app.Activity)?.let { activity ->
                        viewModel.onStartFreeTrial(activity, selectedPlanId)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).shadow(8.dp, RoundedCornerShape(14.dp), spotColor = colors.acGlow),
                colors = ButtonDefaults.buttonColors(containerColor = colors.acc),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.start_free_trial), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.restore_purchase_terms),
                color = colors.tx2,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { viewModel.onRestorePurchase() }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Row hiển thị các gói đăng ký, giá lấy từ repository. */
@Composable
private fun PricingPlansRow(
    plans: List<PricingPlan>,
    selectedPlanId: String,
    onSelectPlan: (String) -> Unit
) {
    val colors = SmartClickThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        plans.forEach { plan ->
            val isSelected = selectedPlanId == plan.id
            val alpha = if (isSelected) 1f else 0.6f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                // Main card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isSelected) Modifier.shadow(24.dp, RoundedCornerShape(16.dp), spotColor = colors.acGlow) else Modifier)
                        .background(
                            if (isSelected) colors.primaryGradient else androidx.compose.ui.graphics.SolidColor(colors.surfEl),
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) Color.Transparent else colors.bord,
                            RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectPlan(plan.id) }
                        .padding(vertical = 16.dp, horizontal = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val textColor = if (isSelected) Color.White else colors.tx1.copy(alpha = alpha)
                        Text(plan.displayName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(plan.price, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(plan.period, color = if (isSelected) Color.White.copy(alpha = 0.7f) else colors.tx2.copy(alpha = alpha), fontSize = 11.sp)
                    }
                }

                // Badge overlay
                if (plan.isBestValue) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-8).dp)
                            .background(colors.orange, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.best_value), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
