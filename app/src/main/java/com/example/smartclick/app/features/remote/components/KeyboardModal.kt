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
import com.example.smartclick.app.domain.model.RemoteCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardModal(
    onDismissRequest: () -> Unit,
    onSendText: (RemoteCommand) -> Unit,
    dictatedText: String = "",
    onDictate: () -> Unit = {}
) {
    val colors = SmartClickThemeTokens.colors
    var draft by remember { mutableStateOf(KeyboardTextDraft()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(dictatedText) { if (dictatedText.isNotEmpty()) draft = draft.edit(dictatedText) }

    fun submitDraft() {
        draft.submit(onSendText)
        draft = KeyboardTextDraft()
    }

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
            TextButton(onClick = onDictate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dictate_text)) }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = draft.text,
                onValueChange = { newValue -> draft = draft.edit(newValue) },
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
                    onSend = { submitDraft() }
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = { submitDraft() },
                enabled = draft.text.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.submit))
            }
        }
    }
}
