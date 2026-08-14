package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ScanBus
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.PickRequest
import com.kevin.wmsscanner.network.PickSummaryLine
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SoPickingListScreen(navController: NavHostController, soNumber: String) {
    var lines by remember { mutableStateOf<List<PickSummaryLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeLine by remember { mutableStateOf<PickSummaryLine?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        try {
            val response = NetworkModule.api.getPickSummary(soNumber)
            if (response.isSuccessful) {
                lines = response.body()?.items ?: emptyList()
            } else {
                error = "Failed to load SO"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("SO: $soNumber", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else {
            lines.forEach { line ->
                val done = line.remaining <= 0
                Card(
                    onClick = { if (!done) activeLine = line },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(line.itemSku)
                            Text(if (done) "DONE" else "${line.pickedQty}/${line.orderedQty}")
                        }
                        Text(line.itemName, style = MaterialTheme.typography.bodySmall)
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

    activeLine?.let { line ->
        PickPopup(
            soNumber = soNumber,
            line = line,
            onDismiss = { activeLine = null },
            onSuccess = {
                activeLine = null
                scope.launch { refresh() }
            }
        )
    }
}

@Composable
fun PickPopup(
    soNumber: String,
    line: PickSummaryLine,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var locationInput by remember { mutableStateOf("") }
    var locationLooked by remember { mutableStateOf(false) }
    var foundAtLocation by remember { mutableStateOf(false) }

    // Identification step — only needed if the scanned location comes back
    // untracked for this SKU.
    var identifyInput by remember { mutableStateOf("") }
    var identifyError by remember { mutableStateOf<String?>(null) }
    var identifyConfirmed by remember { mutableStateOf(false) }

    var palletCountInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            if (!locationLooked) {
                locationInput = code
            } else if (!identifyConfirmed) {
                identifyInput = code
            }
        }
    }

    LaunchedEffect(locationInput) {
        locationLooked = false
        foundAtLocation = false
        identifyConfirmed = false
        identifyInput = ""
        identifyError = null
        error = null

        if (locationInput.isBlank()) return@LaunchedEffect
        delay(400)

        try {
            val response = NetworkModule.api.lookupLocationStock(locationInput.trim())
            locationLooked = true
            if (response.isSuccessful) {
                val stock = response.body()?.stock ?: emptyList()
                foundAtLocation = stock.any { it.itemSku == line.itemSku }
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
    }

    LaunchedEffect(identifyInput) {
        identifyError = null
        identifyConfirmed = false
        if (identifyInput.isBlank()) return@LaunchedEffect
        delay(400)

        try {
            val response = NetworkModule.api.lookupItemByBarcode(identifyInput.trim())
            if (response.isSuccessful && response.body()?.sku == line.itemSku) {
                identifyConfirmed = true
            } else if (response.isSuccessful) {
                identifyError = "This is ${response.body()?.sku}, expected ${line.itemSku}"
            } else {
                identifyError = "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            identifyError = "Couldn't reach server: ${e.message}"
        }
    }

    val readyForQuantity = locationLooked && (foundAtLocation || identifyConfirmed)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(line.itemSku, style = MaterialTheme.typography.titleMedium)
                Text(line.itemName, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Remaining: ${line.remaining}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text("Scan or type location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                CameraScanButton(modifier = Modifier.fillMaxWidth())

                if (locationLooked && !foundAtLocation) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Not tracked at this location \u2014 scan barcode or input SKU to confirm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = identifyInput,
                        onValueChange = { identifyInput = it },
                        label = { Text("Scan barcode or input SKU") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CameraScanButton(modifier = Modifier.fillMaxWidth())

                    if (identifyError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(identifyError!!, color = MaterialTheme.colorScheme.error)
                    }
                    if (identifyConfirmed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Confirmed", color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (readyForQuantity) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = palletCountInput,
                        onValueChange = { palletCountInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Pallet count") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val count = palletCountInput.toIntOrNull()
                    if (count != null) {
                        Text(
                            "= ${count * line.palletCartonQty} units",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val count = palletCountInput.toIntOrNull() ?: return@Button
                            val qty = count * line.palletCartonQty
                            submitting = true
                            error = null
                            scope.launch {
                                try {
                                    val response = NetworkModule.api.pickFromLocation(
                                        PickRequest(
                                            locationCode = locationInput.trim(),
                                            itemSku = line.itemSku,
                                            quantity = qty,
                                            sourceUntracked = if (!foundAtLocation) true else null,
                                            soNumber = soNumber
                                        )
                                    )
                                    submitting = false
                                    if (response.isSuccessful) {
                                        onSuccess()
                                    } else {
                                        error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                                    }
                                } catch (e: Exception) {
                                    submitting = false
                                    error = "Couldn't reach server: ${e.message}"
                                }
                            }
                        },
                        enabled = readyForQuantity && !submitting && palletCountInput.toIntOrNull() != null
                    ) {
                        Text(if (submitting) "Picking..." else "Confirm")
                    }
                }
            }
        }
    }
}