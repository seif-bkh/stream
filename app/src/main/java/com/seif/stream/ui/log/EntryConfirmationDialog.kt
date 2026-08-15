package com.seif.stream.ui.log

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seif.stream.ui.theme.Accent
import com.seif.stream.ui.theme.Ink
import com.seif.stream.ui.theme.Muted
import com.seif.stream.ui.theme.Paper

@Composable
internal fun EntryConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        shape = RoundedCornerShape(2.dp),
        containerColor = Paper,
        tonalElevation = 0.dp,
    )
}
