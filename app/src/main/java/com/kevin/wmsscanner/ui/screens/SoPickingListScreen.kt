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
    var locationChecked by remember { mutableStateOf(false) }
    var loadingLookup by remember { mutableStateOf(false) }

    var locationStatus by remember { mutableStateOf<String?>(null) }
    var matchedQty by remember { mutableStateOf(0) }

    var palletCountInput by remember { mutableStateOf("") }
    var cartonQtyInput by remember { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code -> locationInput = code }
    }

    LaunchedEffect(locationInput) {
        locationChecked = false
        locationStatus = null
        matchedQty = 0
        error = null

        if (locationInput.isBlank()) return@LaunchedEffect
        delay(400)

        loadingLookup = true
        try {
            val response = NetworkModule.api.lookupLocationStock(locationInput.trim())
            loadingLookup = false
            locationChecked = true
            if (response.isSuccessful) {
                val body = response.body()
                val stock = body?.stock ?: emptyList()
                val locationType = body?.locationType
                val match = stock.find { it.itemSku == line.itemSku }
                val occupiedByOther = stock.any { it.itemSku != line.itemSku }

                // Only RACK cells are single-SKU — occupied-by-other only
                // counts as a genuine mismatch there. Floor and other
                // multi-SKU locations just fall through to EMPTY (Default
                // Picking territory) when the target SKU isn't among what's
                // already there.
                locationStatus = when {
                    match != null -> "MATCH"
                    occupiedByOther && locationType == "RACK" -> "MISMATCH"
                    else -> "EMPTY"
                }
                matchedQty = match?.quantity ?: 0
            } else {
                error = "Location not found"
            }
        } catch (e: Exception) {
            loadingLookup = false
            error = "Couldn't reach server: ${e.message}"
        }
    }

    // Resolve the final quantity to submit + validation state, based on
    // exactly one of the two fields being filled.
    val palletFilled = palletCountInput.isNotBlank()
    val cartonFilled = cartonQtyInput.isNotBlank()

    val quantityError: String? = when {
        palletFilled && cartonFilled -> "Isi salah satu saja, jangan keduanya"
        !palletFilled && !cartonFilled -> null // no error yet, just not ready — don't show until they try
        else -> null
    }

    val resolvedQty: Int? = when {
        palletFilled && !cartonFilled -> palletCountInput.toIntOrNull()?.times(line.palletCartonQty)
        cartonFilled && !palletFilled -> cartonQtyInput.toIntOrNull()
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
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

                if (loadingLookup) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Checking...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                when (locationStatus) {
                    "MATCH" -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Confirmed — $matchedQty available here",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    "MISMATCH" -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "This location has a different product",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    "EMPTY" -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No stock recorded here — will be logged as Default Picking",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (locationStatus == "MATCH" || locationStatus == "EMPTY") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pilih salah satu kuantitas", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = palletCountInput,
                            onValueChange = { palletCountInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Pallet") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cartonQtyInput,
                            onValueChange = { cartonQtyInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Carton Qty") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${line.palletCartonQty} cartons/pallet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (quantityError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(quantityError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else if (resolvedQty != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "= $resolvedQty units",
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
                            val qty = resolvedQty ?: return@Button
                            submitting = true
                            error = null
                            scope.launch {
                                try {
                                    val response = NetworkModule.api.pickFromLocation(
                                        PickRequest(
                                            locationCode = locationInput.trim(),
                                            itemSku = line.itemSku,
                                            quantity = qty,
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
                        enabled = (locationStatus == "MATCH" || locationStatus == "EMPTY") &&
                                resolvedQty != null && quantityError == null && !submitting
                    ) {
                        Text(if (submitting) "Picking..." else "Confirm")
                    }
                }
            }
        }
    }
}