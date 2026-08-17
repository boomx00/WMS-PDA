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
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.ShipV2Request
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ShipSessionV2Screen(navController: NavHostController, soNumber: String) {
    var labelInput by remember { mutableStateOf("") }

    var lookupSku by remember { mutableStateOf<String?>(null) }
    var lookupName by remember { mutableStateOf<String?>(null) }
    var lookupQty by remember { mutableStateOf<Int?>(null) }
    var lookupPalletCartonQty by remember { mutableStateOf<Int?>(null) }
    var lookupOrderedQty by remember { mutableStateOf<Int?>(null) }
    var lookupAlreadyShipped by remember { mutableStateOf<Int?>(null) }
    var lookupRemaining by remember { mutableStateOf<Int?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    var palletQtyInput by remember { mutableStateOf("") }
    var cartonQtyInput by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val palletFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            labelInput = code
            palletFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(labelInput) {
        lookupSku = null
        lookupName = null
        lookupQty = null
        lookupPalletCartonQty = null
        lookupOrderedQty = null
        lookupAlreadyShipped = null
        lookupRemaining = null
        lookupError = null
        error = null
        success = null
        palletQtyInput = ""
        cartonQtyInput = ""

        if (labelInput.isBlank()) return@LaunchedEffect

        delay(400)
        lookupLoading = true
        try {
            val response = NetworkModule.api.lookupStockByLabel(labelInput.trim(), soNumber)
            lookupLoading = false
            if (response.isSuccessful) {
                val body = response.body()
                lookupSku = body?.itemSku
                lookupName = body?.itemName
                lookupQty = body?.quantity
                lookupPalletCartonQty = body?.palletCartonQty
                lookupOrderedQty = body?.orderedQty
                lookupAlreadyShipped = body?.alreadyShipped
                lookupRemaining = body?.remaining
            } else {
                lookupError = "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            lookupLoading = false
            lookupError = "Couldn't reach server: ${e.message}"
        }
    }

    val palletCartonQty = lookupPalletCartonQty ?: 0
    val palletFilled = palletQtyInput.isNotBlank()
    val cartonFilled = cartonQtyInput.isNotBlank()

    val palletValue = palletQtyInput.toIntOrNull()
    val cartonValue = cartonQtyInput.toIntOrNull()

    // One ship action can only ever represent AT MOST one physical pallet.
    val quantityError: String? = when {
        palletFilled && cartonFilled -> "Isi salah satu saja, jangan keduanya"
        palletFilled && palletValue != null && palletValue > 1 ->
            "Maksimal 1 pallet per pengiriman"
        cartonFilled && cartonValue != null && palletCartonQty > 0 && cartonValue > palletCartonQty ->
            "Maksimal $palletCartonQty carton (1 pallet penuh)"
        else -> null
    }

    val resolvedQty: Int? = when {
        quantityError != null -> null
        palletFilled && !cartonFilled && palletValue != null -> palletValue * palletCartonQty
        cartonFilled && !palletFilled && cartonValue != null -> cartonValue
        else -> null
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
                palletFocusRequester.requestFocus()
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
        } else if (lookupSku != null) {
            Text("$lookupSku — $lookupName", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Qty tersedia di Outbound WH: $lookupQty (${lookupPalletCartonQty ?: "?"} carton/pallet)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (lookupOrderedQty != null) {
                Text(
                    "Pesanan: $lookupOrderedQty · Sudah terkirim: ${lookupAlreadyShipped ?: 0} · Sisa: $lookupRemaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        if (lookupSku != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pilih salah satu kuantitas (maks 1 pallet)", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = palletQtyInput,
                    onValueChange = { palletQtyInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Pallet") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(palletFocusRequester)
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

        lookupAlreadyShipped?.let { shipped ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Already shipped for this SO: $shipped",
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
                val qty = resolvedQty ?: return@Button
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
                            palletQtyInput = ""
                            cartonQtyInput = ""
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
            enabled = !loading && labelInput.isNotBlank() && resolvedQty != null && quantityError == null,
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
}