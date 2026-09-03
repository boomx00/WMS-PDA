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
import com.kevin.wmsscanner.network.TambahanPickRequest
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TambahanPickingScreen(navController: NavHostController, soNumber: String) {
    var locationInput by remember { mutableStateOf("") }
    var locationType by remember { mutableStateOf<String?>(null) }
    var stockList by remember { mutableStateOf<List<LocationStockItem>>(emptyList()) }
    var selectedTrackedItem by remember { mutableStateOf<LocationStockItem?>(null) }
    var locationLooked by remember { mutableStateOf(false) }

    // Used only when locationType == "FLOOR" — manual SKU entry instead of
    // a tappable list, since Floor can hold many different SKUs at once.
    var skuInput by remember { mutableStateOf("") }

    var barcodeInput by remember { mutableStateOf("") }
    var barcodeItem by remember { mutableStateOf<BarcodeItemLookupResponse?>(null) }
    var barcodeError by remember { mutableStateOf<String?>(null) }

    var palletCountInput by remember { mutableStateOf("") }
    var cartonQtyInput by remember { mutableStateOf("") }

    var loadingLookup by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val locationFocusRequester = remember { FocusRequester() }
    val skuFocusRequester = remember { FocusRequester() }
    val barcodeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        locationFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            if (!locationLooked) {
                locationInput = code
            } else if (locationType == "FLOOR" && selectedTrackedItem == null && barcodeItem == null) {
                skuInput = code
            } else if (stockList.isEmpty() && barcodeItem == null) {
                barcodeInput = code
            }
        }
    }

    LaunchedEffect(locationInput) {
        locationLooked = false
        stockList = emptyList()
        selectedTrackedItem = null
        locationType = null
        skuInput = ""
        barcodeInput = ""
        barcodeItem = null
        barcodeError = null
        palletCountInput = ""
        cartonQtyInput = ""
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
                locationType = body?.locationType

                if (locationType != "FLOOR" && stockList.size == 1) {
                    selectedTrackedItem = stockList[0]
                }

                if (stockList.isEmpty()) {
                    barcodeFocusRequester.requestFocus()
                } else if (locationType == "FLOOR") {
                    skuFocusRequester.requestFocus()
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

    // Floor-only: match typed/scanned SKU against this location's stock
    // list first; if it's not tracked here yet, fall back to a general
    // item lookup so Default Picking can still tag the right product.
    LaunchedEffect(skuInput) {
        if (locationType != "FLOOR") return@LaunchedEffect
        selectedTrackedItem = null
        barcodeItem = null
        barcodeError = null
        if (skuInput.isBlank()) return@LaunchedEffect

        delay(300)
        val typed = skuInput.trim()
        val exactMatch = stockList.find { it.itemSku.equals(typed, ignoreCase = true) }
        if (exactMatch != null) {
            selectedTrackedItem = exactMatch
            return@LaunchedEffect
        }

        try {
            val response = NetworkModule.api.lookupItemByBarcode(typed)
            if (response.isSuccessful) {
                barcodeItem = response.body()
            } else {
                barcodeError = "SKU tidak ditemukan di lokasi ini maupun di database"
            }
        } catch (e: Exception) {
            barcodeError = "Couldn't reach server: ${e.message}"
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
        locationType = null
        skuInput = ""
        barcodeInput = ""
        barcodeItem = null
        barcodeError = null
        palletCountInput = ""
        cartonQtyInput = ""
    }

    val activePalletCartonQty = selectedTrackedItem?.palletCartonQty ?: barcodeItem?.palletCartonQty
    val activeSku = selectedTrackedItem?.itemSku ?: barcodeItem?.sku
    val activeName = selectedTrackedItem?.itemName ?: barcodeItem?.name
    val activeQtyAtLocation = selectedTrackedItem?.quantity

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
        Text("Tambahan Picking", style = MaterialTheme.typography.headlineMedium)
        Text(
            "SO: $soNumber",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Scan or type location",
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
            CircularProgressIndicator()
        }

        if (locationLooked) {
            if (locationType == "FLOOR" && stockList.isNotEmpty()) {
                // Floor holds many SKUs — typing/scanning is faster and less
                // error-prone than scrolling a long tappable list.
                OutlinedTextField(
                    value = skuInput,
                    onValueChange = { skuInput = it },
                    label = { Text("Enter or scan SKU") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(skuFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(modifier = Modifier.height(8.dp))
                CameraScanButton(modifier = Modifier.fillMaxWidth())
                if (barcodeError != null && selectedTrackedItem == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(barcodeError!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (stockList.size > 1) {
                Text("Pilih produk", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                stockList.forEach { stockItem ->
                    Card(
                        onClick = { selectedTrackedItem = stockItem },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stockItem.itemSku)
                                Text(stockItem.itemName, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${stockItem.quantity}")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (stockList.isEmpty()) {
                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = { barcodeInput = it },
                    label = { Text("Scan barcode / SKU") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(barcodeFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                if (barcodeError != null) {
                    Text(barcodeError!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (selectedTrackedItem != null || barcodeItem != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(activeSku ?: "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        activeName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeQtyAtLocation != null) {
                        Text(
                            "Qty di lokasi ini: $activeQtyAtLocation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = palletCountInput,
                    onValueChange = { palletCountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Pallet") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cartonQtyInput,
                    onValueChange = { cartonQtyInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Carton") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (success != null) {
                Text(success!!, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val qty = resolvedQty ?: return@Button
                    val sku = activeSku ?: return@Button
                    submitting = true
                    error = null
                    success = null
                    scope.launch {
                        try {
                            val response = NetworkModule.api.pickTambahan(
                                TambahanPickRequest(
                                    locationCode = locationInput.trim(),
                                    itemSku = sku,
                                    quantity = qty,
                                    soNumber = soNumber
                                )
                            )
                            submitting = false
                            if (response.isSuccessful) {
                                success = "Picked $qty of $sku for ${response.body()?.tambahanNumber}"
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
                enabled = resolvedQty != null && !submitting,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (submitting) "Picking..." else "Confirm Pick")
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