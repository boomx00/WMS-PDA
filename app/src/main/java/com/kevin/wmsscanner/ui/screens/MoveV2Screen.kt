package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ScanBus
import com.kevin.wmsscanner.network.BarcodeItemLookupResponse
import com.kevin.wmsscanner.network.LocationStockItem
import com.kevin.wmsscanner.network.MoveV2Request
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MoveV2Screen(navController: NavHostController) {
    var sourceInput by remember { mutableStateOf("") }
    var stockList by remember { mutableStateOf<List<LocationStockItem>>(emptyList()) }
    var selectedTrackedItem by remember { mutableStateOf<LocationStockItem?>(null) }
    var sourceLooked by remember { mutableStateOf(false) }

    var barcodeInput by remember { mutableStateOf("") }
    var barcodeItem by remember { mutableStateOf<BarcodeItemLookupResponse?>(null) }
    var barcodeError by remember { mutableStateOf<String?>(null) }

    var destinationInput by remember { mutableStateOf("") }
    var palletCountInput by remember { mutableStateOf("") }
    var cartonQtyInput by remember { mutableStateOf("") }

    var loadingLookup by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sourceFocusRequester = remember { FocusRequester() }
    val barcodeFocusRequester = remember { FocusRequester() }
    val destinationFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        sourceFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            if (!sourceLooked) {
                sourceInput = code
            } else if (stockList.isEmpty() && barcodeItem == null) {
                barcodeInput = code
            } else if (destinationInput.isBlank()) {
                destinationInput = code
            }
        }
    }

    LaunchedEffect(sourceInput) {
        sourceLooked = false
        stockList = emptyList()
        selectedTrackedItem = null
        barcodeInput = ""
        barcodeItem = null
        barcodeError = null
        destinationInput = ""
        palletCountInput = ""
        cartonQtyInput = ""
        error = null
        success = null

        if (sourceInput.isBlank()) return@LaunchedEffect

        delay(400)
        loadingLookup = true
        try {
            val response = NetworkModule.api.lookupLocationStock(sourceInput.trim())
            loadingLookup = false
            sourceLooked = true
            if (response.isSuccessful) {
                val body = response.body()
                stockList = body?.stock ?: emptyList()
                if (stockList.size == 1) selectedTrackedItem = stockList[0]
                if (stockList.isEmpty()) barcodeFocusRequester.requestFocus()
            } else {
                error = "Location not found"
            }
        } catch (e: Exception) {
            loadingLookup = false
            sourceLooked = true
            error = "Couldn't reach server: ${e.message}"
        }
    }

    LaunchedEffect(barcodeInput) {
        barcodeItem = null
        barcodeError = null
        if (barcodeInput.isBlank()) return@LaunchedEffect

        delay(400)
        try {
            val response = NetworkModule.api.lookupItemByBarcode(barcodeInput.trim())
            if (response.isSuccessful) {
                barcodeItem = response.body()
                destinationFocusRequester.requestFocus()
            } else {
                barcodeError = "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            barcodeError = "Couldn't reach server: ${e.message}"
        }
    }

    fun resetForm() {
        sourceInput = ""
        stockList = emptyList()
        selectedTrackedItem = null
        sourceLooked = false
        barcodeInput = ""
        barcodeItem = null
        barcodeError = null
        destinationInput = ""
        palletCountInput = ""
        cartonQtyInput = ""
    }

    val activeItemSku = selectedTrackedItem?.itemSku ?: barcodeItem?.sku
    val activePalletCartonQty = selectedTrackedItem?.palletCartonQty ?: barcodeItem?.palletCartonQty

    val palletValue = palletCountInput.toIntOrNull()
    val cartonValue = cartonQtyInput.toIntOrNull()

    val resolvedQty: Int? = when {
        palletCountInput.isNotBlank() && palletValue != null && activePalletCartonQty != null ->
            palletValue * activePalletCartonQty
        cartonQtyInput.isNotBlank() && cartonValue != null -> cartonValue
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Move (v2)", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Rack \u2192 Rack \u2014 by SKU, pallet, or carton qty",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = sourceInput,
            onValueChange = { sourceInput = it },
            label = { Text("Scan current (source) location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(sourceFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        if (loadingLookup) {
            Text("Checking...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (sourceLooked && stockList.size > 1) {
            Text("Multiple products here \u2014 pick one:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            stockList.forEach { stockItem ->
                Card(
                    onClick = { selectedTrackedItem = stockItem },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${stockItem.itemSku} — ${stockItem.itemName}")
                        Text("${stockItem.quantity}")
                    }
                }
            }
        }

        selectedTrackedItem?.let { item ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(item.itemSku, style = MaterialTheme.typography.titleMedium)
            Text(item.itemName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Available: ${item.quantity} (${item.palletCartonQty}/pallet)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (sourceLooked && stockList.isEmpty() && !loadingLookup) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No stock recorded here yet \u2014 scan barcode or input SKU to identify it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = barcodeInput,
                onValueChange = { barcodeInput = it },
                label = { Text("Scan barcode or input SKU") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(barcodeFocusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(8.dp))
            CameraScanButton(modifier = Modifier.fillMaxWidth())

            if (barcodeError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(barcodeError!!, color = MaterialTheme.colorScheme.error)
            }

            barcodeItem?.let { bi ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(bi.sku, style = MaterialTheme.typography.titleMedium)
                Text(bi.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${bi.palletCartonQty} cartons/pallet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (activeItemSku != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = destinationInput,
                onValueChange = { destinationInput = it },
                label = { Text("Scan destination location") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(destinationFocusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(8.dp))
            CameraScanButton(modifier = Modifier.fillMaxWidth())
        }

        if (activeItemSku != null && activePalletCartonQty != null && destinationInput.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pallet atau Carton Qty", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = palletCountInput,
                    onValueChange = { newValue ->
                        palletCountInput = newValue.filter { c -> c.isDigit() }
                        if (palletCountInput.isNotEmpty()) {
                            cartonQtyInput = ""
                        }
                    },
                    label = { Text("Pallet") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cartonQtyInput,
                    onValueChange = { newValue ->
                        cartonQtyInput = newValue.filter { c -> c.isDigit() }
                        if (cartonQtyInput.isNotEmpty()) {
                            palletCountInput = ""
                        }
                    },
                    label = { Text("Carton Qty") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$activePalletCartonQty cartons/pallet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            resolvedQty?.let { qty ->
                Text(
                    "= $qty units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val qty = resolvedQty ?: return@Button
                    val isDefaultMove = selectedTrackedItem == null

                    submitting = true
                    error = null
                    success = null
                    scope.launch {
                        try {
                            val response = NetworkModule.api.moveV2(
                                MoveV2Request(
                                    sourceLocationCode = sourceInput.trim(),
                                    destinationLocationCode = destinationInput.trim(),
                                    itemSku = activeItemSku,
                                    quantity = qty,
                                    sourceUntracked = if (isDefaultMove) true else null
                                )
                            )
                            submitting = false
                            if (response.isSuccessful) {
                                success = "Moved $qty units of $activeItemSku"
                                resetForm()
                                sourceFocusRequester.requestFocus()
                            } else {
                                error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                            }
                        } catch (e: Exception) {
                            submitting = false
                            error = "Couldn't reach server: ${e.message}"
                        }
                    }
                },
                enabled = !submitting && resolvedQty != null,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (submitting) "Moving..." else "Confirm Move")
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        if (success != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(success!!, color = MaterialTheme.colorScheme.primary)
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