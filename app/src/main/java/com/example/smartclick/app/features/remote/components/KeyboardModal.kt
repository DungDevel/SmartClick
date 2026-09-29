package com.example.smartclick.app.features.remote.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smartclick.app.core.ui.theme.SmartClickThemeTokens
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.example.smartclick.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardModal(
    onDismissRequest: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnter: () -> Unit,
    onBackspace: () -> Unit
) {
    val colors = SmartClickThemeTokens.colors
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100) // allow bottom sheet to settle
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = colors.bg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.send_text_to_tv), color = colors.tx1, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    if (newValue.length < text.length) {
                        onBackspace()
                    } else if (newValue.length > text.length) {
                        val delta = newValue.substring(text.length)
                        onTextChange(delta)
                    }
                    text = newValue
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.tx1,
                    unfocusedTextColor = colors.tx1,
                    focusedBorderColor = colors.acc,
                    unfocusedBorderColor = colors.bord,
                    cursorColor = colors.acc
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onEnter()
                        text = ""
                    }
                ),
                singleLine = true
            )
        }
    }
}
