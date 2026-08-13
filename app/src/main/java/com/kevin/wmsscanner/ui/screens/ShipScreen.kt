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
import com.kevin.wmsscanner.network.ShipRequest
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun ShipScreen(navController: NavHostController) {
    var soNumberInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lookupResult by remember { mutableStateOf<String?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val soFocusRequester = remember { FocusRequester() }
    val labelFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        soFocusRequester.requestFocus()
    }
    LaunchedEffect(labelInput) {
        if (labelInput.isBlank()) {
            lookupResult = null
            return@LaunchedEffect
        }
        delay(400)
        lookupLoading = true
        try {
            val response = NetworkModule.api.lookupPalletAtLocation(labelInput.trim(), "OUTBOUND_WH")
            lookupResult = if (response.isSuccessful) {
                "Qty tersedia: ${response.body()?.quantity}"
            } else {
                "Barang tidak ditemukan"
            }
        } catch (e: Exception) {
            lookupResult = null
        }
        lookupLoading = false
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            error = null
            success = null
            when {
                soNumberInput.isBlank() -> {
                    soNumberInput = code
                    labelFocusRequester.requestFocus()
                    keyboardController?.show()
                }
                labelInput.isBlank() -> {
                    labelInput = code
                    quantityFocusRequester.requestFocus()
                    keyboardController?.show()
                }
                else -> {
                    // both filled, camera scan again just overwrites label
                    labelInput = code
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Ship", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Outbound Warehouse \u2192 leaves the warehouse",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = soNumberInput,
            onValueChange = {
                soNumberInput = it
                error = null
                success = null
            },
            label = { Text("Scan sales order barcode") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(soFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                labelFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = labelInput,
            onValueChange = {
                labelInput = it
                error = null
                success = null
            },
            label = { Text("Scan pallet label") },
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

        if (lookupLoading) {
            Text(
                "Checking...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (lookupResult != null) {
            Text(
                lookupResult!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (lookupResult!!.startsWith("Qty"))
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = quantityInput,
            onValueChange = { quantityInput = it.filter { c -> c.isDigit() } },
            label = { Text("Quantity per picking list") },
            singleLine = true,
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
                        val response = NetworkModule.api.shipPallet(
                            ShipRequest(
                                soNumber = soNumberInput.trim(),
                                label = labelInput.trim(),
                                quantity = qty
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Shipped $qty units against ${soNumberInput.trim()}"
                            soNumberInput = ""
                            labelInput = ""
                            quantityInput = ""
                            soFocusRequester.requestFocus()
                        } else {
                            error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                        }
                    } catch (e: Exception) {
                        loading = false
                        error = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !loading && soNumberInput.isNotBlank() && labelInput.isNotBlank() && quantityInput.toIntOrNull() != null,
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
            Text("Back")
        }
    }
}