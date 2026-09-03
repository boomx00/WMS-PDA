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

private sealed class MoveInResult {
    data class Success(val message: String) : MoveInResult()
    data class ServerFailure(val message: String) : MoveInResult()
    data class NetworkFailure(val message: String) : MoveInResult()
}

@Composable
fun MoveInV2Screen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var lookupItem by remember { mutableStateOf<BarcodeItemLookupResponse?>(null) }
    var lookupLoading by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    var destinationInput by remember { mutableStateOf("") }
    var palletCountInput by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    // Result dialog state: null = hidden. Distinguishing NetworkFailure from
    // ServerFailure matters because connectivity is spotty in some areas of
    // the warehouse — the driver needs to know whether to just retry, or
    // whether the server actually rejected the scan.
    var resultDialog by remember { mutableStateOf<MoveInResult?>(null) }
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
        resultDialog = null

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
                resultDialog = null
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

        Button(
            onClick = {
                val count = palletCountInput.toIntOrNull() ?: return@Button
                val item = lookupItem ?: return@Button
                val qty = count * item.palletCartonQty

                resultDialog = null
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
                            resultDialog = MoveInResult.Success(
                                "Moved $qty cartons ($count pallet(s)) to ${destinationInput.trim()}"
                            )
                            // Fields are cleared only when the user dismisses
                            // the dialog (see below) — clearing labelInput
                            // here would re-trigger the label LaunchedEffect,
                            // which resets resultDialog and closes the popup
                            // before the user can read it.
                        } else {
                            resultDialog = MoveInResult.ServerFailure(
                                response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                                    ?: "The server rejected this move (HTTP ${response.code()})."
                            )
                        }
                    } catch (e: java.io.IOException) {
                        // Covers UnknownHostException, SocketTimeoutException,
                        // ConnectException, etc. — no response reached the
                        // server, most likely a dead zone in the warehouse.
                        loading = false
                        resultDialog = MoveInResult.NetworkFailure(
                            "Couldn't reach the server. Check your connection and try again. " +
                                    "If this keeps happening, move to an area with better signal " +
                                    "before re-scanning."
                        )
                    } catch (e: Exception) {
                        loading = false
                        resultDialog = MoveInResult.ServerFailure(
                            "Unexpected error: ${e.message ?: "unknown"}"
                        )
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

    resultDialog?.let { result ->
        val (title, message) = when (result) {
            is MoveInResult.Success -> "Move In Successful" to result.message
            is MoveInResult.ServerFailure -> "Move In Failed" to result.message
            is MoveInResult.NetworkFailure -> "Connection Problem" to result.message
        }

        fun dismiss() {
            resultDialog = null
            if (result is MoveInResult.Success) {
                // Safe to reset the form now — the dialog is already gone,
                // so the label LaunchedEffect firing on labelInput = "" has
                // nothing left to close.
                labelInput = ""
                lookupItem = null
                destinationInput = ""
                palletCountInput = ""
                labelFocusRequester.requestFocus()
            }
        }

        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { dismiss() }) {
                    Text("OK")
                }
            }
        )
    }
}