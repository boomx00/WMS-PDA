package com.kevin.wmsscanner.ui.components.picking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.PickSummaryLine
import com.kevin.wmsscanner.network.SkuLocationRow

// Small round "i" button shown beside each picking card.
@Composable
fun InfoBadge(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "i",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun SkuLocationsDialog(line: PickSummaryLine, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf<List<SkuLocationRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(line.itemSku) {
        try {
            val response = NetworkModule.api.getLocationsBySku(line.itemSku)
            if (response.isSuccessful) {
                rows = response.body()?.locations ?: emptyList()
            } else {
                error = "Gagal memuat lokasi"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(line.itemSku, style = MaterialTheme.typography.titleMedium)
                Text(line.itemName, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        rows.isEmpty() -> Text(
                            "Tidak ada stok di lokasi manapun.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(row.locationCode, fontWeight = FontWeight.Medium)
                                val pallets = if (line.palletCartonQty > 0)
                                    " (${"%.1f".format(row.quantity.toDouble() / line.palletCartonQty)} plt)"
                                else ""
                                Text("${row.quantity}$pallets")
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
            }
        }
    }
}