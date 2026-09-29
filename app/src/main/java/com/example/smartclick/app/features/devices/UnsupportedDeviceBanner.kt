package com.example.smartclick.app.features.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens

@Composable
fun UnsupportedDeviceBanner(
    unsupportedCount: Int,
    onManualIpSubmit: (String) -> Unit,
    onRequestSupport: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf("") }
    val colors = SmartClickThemeTokens.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = colors.surfEl),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info, 
                contentDescription = null, 
                tint = colors.tx2,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                if (unsupportedCount > 0) {
                    Text(
                        stringResource(id = com.example.smartclick.R.string.unsupported_device_count, unsupportedCount),
                        color = colors.tx1,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    stringResource(id = com.example.smartclick.R.string.cant_find_tv_tap),
                    color = colors.tx2,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(id = com.example.smartclick.R.string.tv_not_found_question)) },
            text = {
                Column {
                    Text(stringResource(id = com.example.smartclick.R.string.tv_not_found_desc))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text(stringResource(id = com.example.smartclick.R.string.ip_address_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ipInput.isNotBlank()) {
                        onManualIpSubmit(ipInput)
                        showDialog = false
                    }
                }) {
                    Text(stringResource(id = com.example.smartclick.R.string.connect_ip))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { 
                        onRequestSupport()
                        showDialog = false 
                    }) {
                        Text(stringResource(id = com.example.smartclick.R.string.request_support))
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(id = com.example.smartclick.R.string.cancel))
                    }
                }
            }
        )
    }
}
