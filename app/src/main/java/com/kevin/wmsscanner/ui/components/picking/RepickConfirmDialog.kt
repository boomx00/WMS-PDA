package com.kevin.wmsscanner.ui.components.picking

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.kevin.wmsscanner.network.PickSummaryLine

// Shown before opening the pick popup on a line that's already fully picked.
@Composable
fun RepickConfirmDialog(
    soNumber: String,
    line: PickSummaryLine,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfirmasi") },
        text = {
            Text(
                "Apakah anda yakin mau picking lagi? ${line.itemName} untuk $soNumber " +
                        "sudah terpicking sebanyak ${line.pickedQty}/${line.orderedQty}"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Ya, picking lagi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}