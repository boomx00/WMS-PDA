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
import com.kevin.wmsscanner.network.ClaimRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.ShipV2Request
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
@Composable
fun ShipSessionV2Screen(navController: NavHostController, soNumber: String, expectedSku: String) {
    var labelInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }

    var lookupSku by remember { mutableStateOf<String?>(null) }
    var lookupName by remember { mutableStateOf<String?>(null) }
    var lookupUnclaimed by remember { mutableStateOf<Int?>(null) }
    var lookupOrderedQty by remember { mutableStateOf<Int?>(null) }
    var lookupAlreadyShipped by remember { mutableStateOf<Int?>(null) }
    var lookupRemaining by remember { mutableStateOf<Int?>(null) }
    var lookupAvailableToShip by remember { mutableStateOf<Int?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var skuMismatch by remember { mutableStateOf(false) }
    var claimAmountInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var claiming by remember { mutableStateOf(false) }
    var claimError by remember { mutableStateOf<String?>(null) }
    var showClaimDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }

    suspend fun runLookup(value: String) {
        lookupSku = null
        lookupName = null
        lookupUnclaimed = null
        lookupOrderedQty = null
        lookupAlreadyShipped = null
        lookupRemaining = null
        lookupAvailableToShip = null
        lookupError = null
        skuMismatch = false
        claimAmountInput = ""
        if (value.isBlank()) return

        lookupLoading = true
        try {
            val response = NetworkModule.api.lookupStockByLabel(value.trim(), soNumber)
            lookupLoading = false
            if (response.isSuccessful) {
                val body = response.body()
                lookupSku = body?.itemSku
                lookupName = body?.itemName
                lookupUnclaimed = body?.quantity
                lookupOrderedQty = body?.orderedQty
                lookupAlreadyShipped = body?.alreadyShipped
                lookupRemaining = body?.remaining
                lookupAvailableToShip = body?.availableToShip

                if (lookupSku != null && lookupSku != expectedSku) {
                    skuMismatch = true
                }
            } else {
                lookupError = "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            lookupLoading = false
            lookupError = "Couldn't reach server: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            labelInput = code
            quantityFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(labelInput) {
        error = null
        success = null
        delay(400)
        runLookup(labelInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Ship (v2)", style = MaterialTheme.typography.headlineMedium)
        Text(
            "SO: $soNumber",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Expected: $expectedSku",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = labelInput,
            onValueChange = { labelInput = it },
            label = { Text("Scan pallet label or type SKU") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(labelFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                quantityFocusRequester.requestFocus()
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
        } else if (skuMismatch) {
            Text(
                "SKU tidak sesuai — diharapkan $expectedSku, discan $lookupSku",
                color = MaterialTheme.colorScheme.error
            )
        } else if (lookupSku != null) {
            Text("$lookupSku — $lookupName", style = MaterialTheme.typography.bodyMedium)

            lookupAvailableToShip?.let { available ->
                Text(
                    "Tersedia untuk SO ini: $available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if ((lookupUnclaimed ?: 0) > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ecer (belum diklaim) di Outbound WH: $lookupUnclaimed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                TextButton(onClick = { showClaimDialog = true }) {
                    Text("Klaim Ecer")
                }
            }

            if (lookupOrderedQty != null) {
                Text(
                    "Ordered: $lookupOrderedQty · Already shipped: ${lookupAlreadyShipped ?: 0} · Remaining: $lookupRemaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = quantityInput,
            onValueChange = { quantityInput = it.filter { c -> c.isDigit() } },
            label = { Text("Quantity per picking list") },
            singleLine = true,
            enabled = !skuMismatch,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(quantityFocusRequester)
        )

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
                        val response = NetworkModule.api.shipV2(
                            ShipV2Request(soNumber = soNumber, label = labelInput.trim(), quantity = qty)
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Shipped $qty units"
                            labelInput = ""
                            quantityInput = ""
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
            enabled = !loading && !skuMismatch && labelInput.isNotBlank() && quantityInput.toIntOrNull() != null,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Shipping..." else "Confirm Ship")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Shipping")
        }
    }
    if (showClaimDialog) {
        ClaimEcerDialog(
            soNumber = soNumber,
            itemSku = lookupSku ?: "",
            maxAmount = lookupUnclaimed ?: 0,
            onDismiss = { showClaimDialog = false },
            onConfirmed = {
                showClaimDialog = false
                scope.launch {
                    runLookup(labelInput)
                    quantityFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        )
    }
}

@Composable
fun ClaimEcerDialog(
    soNumber: String,
    itemSku: String,
    maxAmount: Int,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val amount = amountInput.toIntOrNull()
    val exceedsMax = amount != null && amount > maxAmount

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Klaim Ecer", style = MaterialTheme.typography.titleMedium)
                Text(itemSku, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Maksimal: $maxAmount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                if (exceedsMax) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tidak boleh lebih dari $maxAmount",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amount ?: return@Button
                            submitting = true
                            error = null
                            scope.launch {
                                try {
                                    val response = NetworkModule.api.claimStock(
                                        soNumber,
                                        ClaimRequest(itemSku, amt)
                                    )
                                    submitting = false
                                    if (response.isSuccessful) {
                                        onConfirmed()
                                    } else {
                                        error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                                    }
                                } catch (e: Exception) {
                                    submitting = false
                                    error = "Couldn't reach server: ${e.message}"
                                }
                            }
                        },
                        enabled = !submitting && amount != null && amount > 0 && !exceedsMax
                    ) {
                        Text(if (submitting) "Claiming..." else "Konfirmasi")
                    }
                }
            }
        }
    }
}