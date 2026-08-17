package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ScanBus
import com.kevin.wmsscanner.network.BarcodeItemLookupResponse
import com.kevin.wmsscanner.network.MoveInV2Request
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MoveInV2Screen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var lookupItem by remember { mutableStateOf<BarcodeItemLookupResponse?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    var destinationInput by remember { mutableStateOf("") }
    var palletCountInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val destinationFocusRequester = remember { FocusRequester() }
    val palletFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            if (labelInput.isBlank()) {
                labelInput = code
            } else {
                destinationInput = code
            }
        }
    }

    // Live lookup — works with a scanned barcode, a real pallet label
    // (SKU segment gets extracted), or a bare SKU typed directly.
    LaunchedEffect(labelInput) {
        lookupItem = null
        lookupError = null
        error = null
        success = null

        if (labelInput.isBlank()) return@LaunchedEffect

        delay(400)
        lookupLoading = true
        try {
            val cleaned = labelInput.trim().removePrefix("*").split("*").firstOrNull() ?: labelInput.trim()
            val response = NetworkModule.api.lookupItemByBarcode(cleaned)
            lookupLoading = false
            if (response.isSuccessful) {
                lookupItem = response.body()
                destinationFocusRequester.requestFocus()
            } else {
                lookupError = "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            lookupLoading = false
            lookupError = "Couldn't reach server: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Move In (v2)", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Floor \u2192 destination \u2014 by SKU total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = labelInput,
            onValueChange = { labelInput = it },
            label = { Text("Scan pallet label or input SKU") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(labelFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                destinationFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(8.dp))

        if (lookupLoading) {
            Text("Checking...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (lookupError != null) {
            Text(lookupError!!, color = MaterialTheme.colorScheme.error)
        } else if (lookupItem != null) {
            Text(lookupItem!!.sku, style = MaterialTheme.typography.titleMedium)
            Text(lookupItem!!.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${lookupItem!!.palletCartonQty} cartons/pallet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = destinationInput,
            onValueChange = {
                destinationInput = it
                error = null
                success = null
            },
            label = { Text("Scan destination location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(destinationFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                palletFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

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
        if (palletCount != null && lookupItem != null) {
            Text(
                "= ${palletCount * lookupItem!!.palletCartonQty} cartons",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (success != null) {
            Text(success!!, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                val count = palletCountInput.toIntOrNull() ?: return@Button
                val item = lookupItem ?: return@Button
                val qty = count * item.palletCartonQty

                error = null
                success = null
                loading = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.moveInV2(
                            MoveInV2Request(
                                label = labelInput.trim(),
                                destinationLocationCode = destinationInput.trim(),
                                quantity = qty
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Moved $qty cartons ($count pallet(s)) to ${destinationInput.trim()}"
                            labelInput = ""
                            lookupItem = null
                            destinationInput = ""
                            palletCountInput = ""
                            labelFocusRequester.requestFocus()
                        } else {
                            error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                        }
                    } catch (e: Exception) {
                        loading = false
                        error = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !loading && lookupItem != null && destinationInput.isNotBlank() && palletCountInput.toIntOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Moving..." else "Confirm Move In")
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