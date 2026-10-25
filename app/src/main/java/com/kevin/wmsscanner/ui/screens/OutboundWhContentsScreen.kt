package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.network.LocationStockItem
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.initialCode
import com.kevin.wmsscanner.ui.components.LocationPicker
import com.kevin.wmsscanner.ui.components.pickerOptions
import com.kevin.wmsscanner.ui.components.rememberLocationPurposes
import kotlinx.coroutines.launch

@Composable
fun OutboundWhContentsScreen(navController: NavHostController) {
    var search by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf<List<LocationStockItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Which staging location to look in, starting on the default from
    // Settings → Locations & Marking.
    val purposes = rememberLocationPurposes()
    var stagingCode by remember { mutableStateOf("") }

    suspend fun refresh() {
        if (stagingCode.isBlank()) return
        loading = true
        error = null
        try {
            val response = NetworkModule.api.lookupLocationStock(stagingCode)
            if (response.isSuccessful) {
                stock = response.body()?.stock ?: emptyList()
            } else {
                error = "Failed to load $stagingCode contents"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    LaunchedEffect(purposes.data) {
        val data = purposes.data ?: return@LaunchedEffect
        if (stagingCode.isBlank()) stagingCode = data.staging.initialCode()
        if (stagingCode.isBlank()) {
            loading = false
            error = "No staging location is set up — ask an admin to set one on the web Locations page."
        }
    }

    LaunchedEffect(stagingCode) {
        refresh()
    }

    LaunchedEffect(purposes.error) {
        if (purposes.error != null) {
            loading = false
            error = purposes.error
        }
    }

    val filtered = stock.filter {
        it.itemSku.contains(search.trim(), ignoreCase = true) ||
                it.itemName.contains(search.trim(), ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Outbound Warehouse", style = MaterialTheme.typography.headlineMedium)
        Text(
            "All products currently staged for shipping",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LocationPicker(
            label = "Staging location",
            options = purposes.data?.staging?.pickerOptions() ?: emptyList(),
            value = stagingCode,
            onValueChange = { stagingCode = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search SKU or product name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (filtered.isEmpty()) {
            Text(
                if (stock.isEmpty()) "$stagingCode is empty." else "No matches.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "${filtered.size} product(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            filtered.forEach { stockItem ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stockItem.itemSku, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${stockItem.quantity} units",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(stockItem.itemName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${stockItem.palletCartonQty} cartons/pallet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { scope.launch { refresh() } },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}