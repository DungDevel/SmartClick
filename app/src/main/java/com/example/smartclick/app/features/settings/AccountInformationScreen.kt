package com.example.smartclick.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInformationScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = SmartClickThemeTokens.colors
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val isPremium = uiState.isPremium

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.account), color = colors.tx1, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.tx1)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.bg
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (user != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surf, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.bord, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(colors.primaryGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(user.name?.take(1)?.uppercase() ?: "U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(user.name ?: stringResource(R.string.user_name_default), color = colors.tx1, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stringResource(R.string.user_email_default), color = colors.tx2, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isPremium) {
                            Box(
                                modifier = Modifier
                                    .background(colors.primaryGradient, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(stringResource(R.string.premium), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(colors.surfEl, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(stringResource(R.string.free), color = colors.tx2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.sign_out), fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(colors.surfEl, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = colors.tx3, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.not_signed_in), color = colors.tx1, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.sign_in_to_sync), color = colors.tx2, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.login() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.acc),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.sign_in), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.login() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.tx1),
                    border = null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.create_account), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

