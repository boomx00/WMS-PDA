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
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.PickRequest
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PickingV2Screen(navController: NavHostController) {
    var locationInput by remember { mutableStateOf("") }
    var stockList by remember { mutableStateOf<List<LocationStockItem>>(emptyList()) }
    var selectedTrackedItem by remember { mutableStateOf<LocationStockItem?>(null) }
    var locationLooked by remember { mutableStateOf(false) } // has a lookup for this location completed?

    // "Default picking" path — location came back empty, identify via carton barcode.
    var barcodeInput by remember { mutableStateOf("") }
    var barcodeItem by remember { mutableStateOf<BarcodeItemLookupResponse?>(null) }
    var barcodeError by remember { mutableStateOf<String?>(null) }

    var palletCountInput by remember { mutableStateOf("") }
    var loadingLookup by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val locationFocusRequester = remember { FocusRequester() }
    val barcodeFocusRequester = remember { FocusRequester() }
    val palletFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        locationFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            // Whichever field is currently "active" for scanning gets it —
            // if we're still on the location step, fill that; if we've
            // moved into the barcode-identification step, fill that instead.
            if (!locationLooked) {
                locationInput = code
            } else if (stockList.isEmpty() && barcodeItem == null) {
                barcodeInput = code
            }
        }
    }

    LaunchedEffect(locationInput) {
        locationLooked = false
        stockList = emptyList()
        selectedTrackedItem = null
        barcodeInput = ""
        barcodeItem = null
        barcodeError = null
        palletCountInput = ""
        error = null
        success = null

        if (locationInput.isBlank()) return@LaunchedEffect

        delay(400)
        loadingLookup = true
        try {
            val response = NetworkModule.api.lookupLocationStock(locationInput.trim())
            loadingLookup = false
            locationLooked = true
            if (response.isSuccessful) {
                val body = response.body()
                stockList = body?.stock ?: emptyList()
                if (stockList.size == 1) selectedTrackedItem = stockList[0]
                if (stockList.isEmpty()) {
                    barcodeFocusRequester.requestFocus()
                }
            } else {
                error = "Location not found"
            }
        } catch (e: Exception) {
            loadingLookup = false
            locationLooked = true
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
                palletFocusRequester.requestFocus()
            } else {
                barcodeError = "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            barcodeError = "Couldn't reach server: ${e.message}"
        }
    }

    fun resetForm() {
        locationInput = ""
        stockList = emptyList()
        selectedTrackedItem = null
        locationLooked = false
        barcodeInput = ""
        barcodeItem = null
        barcodeError = null
        palletCountInput = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Picking (v2)", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Scan location \u2014 pallet count, not carton qty",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = locationInput,
            onValueChange = { locationInput = it },
            label = { Text("Scan or type location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(locationFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        if (loadingLookup) {
            Text("Checking...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Case: multiple SKUs at this location (e.g. Floor) — pick one.
        if (locationLooked && stockList.size > 1) {
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

        // Case: location already has a tracked product — just confirm + enter pallets.
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

        // Case: location is empty per the system — identify via carton barcode.
        if (locationLooked && stockList.isEmpty() && !loadingLookup) {
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

        // Pallet count input — shown once we know the product, either way.
        val activePalletCartonQty = selectedTrackedItem?.palletCartonQty ?: barcodeItem?.palletCartonQty
        if (activePalletCartonQty != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = palletCountInput,
                onValueChange = { palletCountInput = it.filter { c -> c.isDigit() } },
                label = { Text("Pallet count") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(palletFocusRequester)
            )

            val palletCount = palletCountInput.toIntOrNull()
            if (palletCount != null) {
                Text(
                    "= ${palletCount * activePalletCartonQty} units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val count = palletCountInput.toIntOrNull() ?: return@Button
                    val qty = count * activePalletCartonQty
                    val sku = selectedTrackedItem?.itemSku ?: barcodeItem?.sku ?: return@Button
                    val isDefaultPick = selectedTrackedItem == null

                    submitting = true
                    error = null
                    success = null
                    scope.launch {
                        try {
                            val response = NetworkModule.api.pickFromLocation(
                                PickRequest(
                                    locationCode = locationInput.trim(),
                                    itemSku = sku,
                                    quantity = qty,
                                    sourceUntracked = if (isDefaultPick) true else null
                                )
                            )
                            submitting = false
                            if (response.isSuccessful) {
                                success = if (isDefaultPick) {
                                    "Default Picking: $count pallet(s) ($qty units) of $sku"
                                } else {
                                    "Picked $count pallet(s) ($qty units) of $sku"
                                }
                                resetForm()
                                locationFocusRequester.requestFocus()
                            } else {
                                error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                            }
                        } catch (e: Exception) {
                            submitting = false
                            error = "Couldn't reach server: ${e.message}"
                        }
                    }
                },
                enabled = !submitting &&
                        palletCountInput.toIntOrNull() != null &&
                        (selectedTrackedItem == null || (palletCountInput.toInt() * activePalletCartonQty) <= (selectedTrackedItem?.quantity ?: Int.MAX_VALUE)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (submitting) "Picking..." else "Confirm Pick")
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