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
import com.kevin.wmsscanner.network.InboundRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Extracts just the SKU segment from a scanned label (ignores pallet seq,
// embedded qty, and work order — those aren't reliable/relevant here).
private fun extractSkuDisplay(raw: String): String {
    val cleaned = raw.trim().removePrefix("*")
    return cleaned.split("*").firstOrNull()?.trim() ?: raw
}

private fun parseLabel(raw: String): Triple<String, String, String>? {
    val cleaned = raw.trim().removePrefix("*")
    val parts = cleaned.split("*")
    if (parts.size != 4) return null
    val (sku, _, _, workOrder) = parts
    return Triple(sku, workOrder, cleaned)
}

@Composable
fun InboundScreen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var skuInput by remember { mutableStateOf("") }
    var workOrderInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }

    var lookupPalletCartonQty by remember { mutableStateOf<Int?>(null) }
    var lookupItemName by remember { mutableStateOf<String?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }

    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            labelInput = code
            error = null
            success = null

            val parsed = parseLabel(code)
            if (parsed != null) {
                skuInput = parsed.first
                workOrderInput = parsed.second
            } else {
                skuInput = extractSkuDisplay(code)
            }
        }
    }

    // Live lookup on whatever SKU is currently resolved (from a scan, or
    // typed manually into the SKU field) — auto-fills quantity to a full
    // pallet's worth, capped by the item's master data going forward.
    LaunchedEffect(skuInput) {
        lookupPalletCartonQty = null
        lookupItemName = null

        if (skuInput.isBlank()) return@LaunchedEffect
        delay(400)

        lookupLoading = true
        try {
            val response = NetworkModule.api.lookupItemByBarcode(skuInput.trim())
            lookupLoading = false
            if (response.isSuccessful) {
                val body = response.body()
                lookupPalletCartonQty = body?.palletCartonQty
                lookupItemName = body?.name
                // Auto-fill quantity to a full pallet by default.
                if (body?.palletCartonQty != null) {
                    quantityInput = body.palletCartonQty.toString()
                }
            }
        } catch (e: Exception) {
            lookupLoading = false
        }
    }

    val quantityExceedsMax = quantityInput.toIntOrNull()?.let { qty ->
        lookupPalletCartonQty != null && qty > lookupPalletCartonQty!!
    } ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Inbound", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = labelInput,
            onValueChange = {
                labelInput = it
                error = null
                success = null
                val parsed = parseLabel(it)
                if (parsed != null) {
                    skuInput = parsed.first
                    workOrderInput = parsed.second
                } else {
                    skuInput = extractSkuDisplay(it)
                }
            },
            label = { Text("Scan product label") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(labelFocusRequester)
        )

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = skuInput,
            onValueChange = { skuInput = it },
            label = { Text("SKU") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (lookupLoading) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Checking...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (lookupItemName != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$lookupItemName — ${lookupPalletCartonQty ?: "?"} cartons/pallet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = workOrderInput,
            onValueChange = { workOrderInput = it },
            label = { Text("Work Order") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = quantityInput,
            onValueChange = { quantityInput = it.filter { c -> c.isDigit() } },
            label = { Text("Quantity") },
            singleLine = true,
            isError = quantityExceedsMax,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        if (quantityExceedsMax) {
            Text(
                "Cannot exceed $lookupPalletCartonQty (one full pallet)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
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
                val qty = quantityInput.toIntOrNull() ?: return@Button
                error = null
                success = null
                loading = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.inbound(
                            InboundRequest(
                                label = labelInput.trim(),
                                sku = skuInput.trim(),
                                workOrderNumber = workOrderInput.trim(),
                                quantity = qty
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Pallet ${labelInput.trim()} scanned in at Floor."
                            labelInput = ""
                            skuInput = ""
                            workOrderInput = ""
                            quantityInput = ""
                            lookupPalletCartonQty = null
                            lookupItemName = null
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
            enabled = !loading &&
                    labelInput.isNotBlank() &&
                    skuInput.isNotBlank() &&
                    workOrderInput.isNotBlank() &&
                    quantityInput.toIntOrNull() != null &&
                    !quantityExceedsMax,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Scanning in..." else "Scan in at Floor")
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