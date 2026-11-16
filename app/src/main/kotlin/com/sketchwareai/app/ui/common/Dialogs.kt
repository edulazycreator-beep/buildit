package com.sketchwareai.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** Confirmation for destructive or irreversible actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

data class FieldSpec(
    val label: String,
    val initial: String,
    val validate: (String) -> String?,
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
)

/** A dialog with one or more validated text fields. Confirm is enabled only when all are valid. */
@Composable
fun TextFieldsDialog(
    title: String,
    confirmLabel: String,
    fields: List<FieldSpec>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var values by rememberSaveable { mutableStateOf(fields.map { it.initial }) }
    val errors = fields.mapIndexed { i, f -> f.validate(values[i]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fields.forEachIndexed { i, f ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { v -> values = values.toMutableList().also { it[i] = v } },
                        label = { Text(f.label) },
                        singleLine = true,
                        isError = errors[i] != null,
                        supportingText = errors[i]?.let { e -> { Text(e) } },
                        keyboardOptions = KeyboardOptions(capitalization = f.capitalization),
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = errors.all { it == null }, onClick = { onConfirm(values.map { it.trim() }); onDismiss() }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
