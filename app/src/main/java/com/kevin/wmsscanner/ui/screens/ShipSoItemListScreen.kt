package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.SoLookupLine
import com.kevin.wmsscanner.network.TambahanItemLine
@Composable
fun ShipSoItemListScreen(navController: NavHostController, soNumber: String) {
    var lines by remember { mutableStateOf<List<SoLookupLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tambahanItems by remember { mutableStateOf<List<TambahanItemLine>>(emptyList()) }
    LaunchedEffect(Unit) {
        loading = true
        try {
            val response = NetworkModule.api.lookupSalesOrder(soNumber)
            if (response.isSuccessful) {
                lines = response.body()?.items ?: emptyList()
                try {
                    val tResponse = NetworkModule.api.getTambahan(soNumber)
                    if (tResponse.isSuccessful) {
                        tambahanItems = tResponse.body()?.items ?: emptyList()
                    }
                } catch (e: Exception) {
                    // Non-fatal
                }
            } else {
                error = "Failed to load SO"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("SO: $soNumber", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pilih produk untuk dikirim",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (lines.isEmpty()) {
            Text(
                "No items on this SO.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            lines.forEach { line ->
                val done = line.remaining <= 0
                val (label, color) = when {
                    done -> "DONE" to MaterialTheme.colorScheme.primary
                    line.shipped > 0 -> "PARTIAL" to MaterialTheme.colorScheme.tertiary
                    else -> "PENDING" to MaterialTheme.colorScheme.error
                }
                Card(
                    onClick = {
                        if (!done) {
                            navController.navigate("ship_session_v2/$soNumber/${line.itemSku}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(line.itemSku, fontWeight = FontWeight.Medium)
                            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(line.itemName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Ordered: ${line.quantity} · Shipped: ${line.shipped} · Remaining: ${line.remaining}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (tambahanItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Tambahan",
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
                Spacer(modifier = Modifier.height(8.dp))

                tambahanItems.forEach { line ->
                    val done = line.shippedQty >= line.pickedQty
                    Card(
                        onClick = {
                            if (!done) {
                                navController.navigate("tambahan_ship/$soNumber/${line.itemSku}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(line.itemSku, fontWeight = FontWeight.Medium)
                                Text("${line.shippedQty}/${line.pickedQty}")
                            }
                            Text(line.itemName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}