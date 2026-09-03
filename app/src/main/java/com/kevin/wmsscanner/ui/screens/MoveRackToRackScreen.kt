package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.kevin.wmsscanner.network.MoveRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

private sealed class MoveResult {
    data class Success(val message: String) : MoveResult()
    data class ServerFailure(val message: String, val needsQuantity: Boolean = false) : MoveResult()
    data class NetworkFailure(val message: String) : MoveResult()
}

@Composable
fun MoveRackToRackScreen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var currentLocationInput by remember { mutableStateOf("") }
    var newLocationInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }
    var needsQuantity by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var resultDialog by remember { mutableStateOf<MoveResult?>(null) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val currentLocationFocusRequester = remember { FocusRequester() }
    val newLocationFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            resultDialog = null
            when {
                labelInput.isBlank() -> {
                    labelInput = code
                    currentLocationFocusRequester.requestFocus()
                    keyboardController?.show()
                }
                currentLocationInput.isBlank() -> {
                    currentLocationInput = code
                    newLocationFocusRequester.requestFocus()
                    keyboardController?.show()
                }
                else -> {
                    newLocationInput = code
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
        Text("Rack \u2192 Rack", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = labelInput,
            onValueChange = {
                labelInput = it
                resultDialog = null
            },
            label = { Text("Scan pallet label") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(labelFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                currentLocationFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = currentLocationInput,
            onValueChange = {
                currentLocationInput = it
                resultDialog = null
            },
            label = { Text("Scan current location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(currentLocationFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                newLocationFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = newLocationInput,
            onValueChange = {
                newLocationInput = it
                resultDialog = null
            },
            label = { Text("Scan new location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(newLocationFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = if (needsQuantity) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onNext = {
                quantityFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

        if (needsQuantity) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = quantityInput,
                onValueChange = { quantityInput = it.filter { c -> c.isDigit() } },
                label = { Text("Quantity (default stock, not a tracked pallet)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(quantityFocusRequester)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                resultDialog = null
                loading = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.movePallet(
                            MoveRequest(
                                label = labelInput.trim(),
                                currentLocationCode = currentLocationInput.trim(),
                                newLocationCode = newLocationInput.trim(),
                                quantity = quantityInput.toIntOrNull()
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            resultDialog = MoveResult.Success(
                                "Moved ${labelInput.trim()} to ${newLocationInput.trim()}"
                            )
                            // Fields are cleared only when the user dismisses
                            // the dialog (see below), so the popup can't be
                            // closed out from under them by a side effect.
                        } else {
                            val errBody = response.errorBody()?.string()
                            val json = try { JSONObject(errBody ?: "{}") } catch (e: Exception) { JSONObject() }
                            when (json.optString("matchType")) {
                                "default_needs_quantity" -> {
                                    needsQuantity = true
                                    resultDialog = MoveResult.ServerFailure(
                                        "${json.optString("error")} (${json.optInt("availableQuantity")} available)",
                                        needsQuantity = true
                                    )
                                }
                                "auto_inbound_needs_quantity" -> {
                                    needsQuantity = true
                                    resultDialog = MoveResult.ServerFailure(
                                        json.optString("error"),
                                        needsQuantity = true
                                    )
                                }
                                else -> {
                                    resultDialog = MoveResult.ServerFailure(
                                        json.optString("error", errBody ?: "The server rejected this move (HTTP ${response.code()}).")
                                    )
                                }
                            }
                        }
                    } catch (e: java.io.IOException) {
                        // No response reached the server — likely a dead zone.
                        loading = false
                        resultDialog = MoveResult.NetworkFailure(
                            "Couldn't reach the server. Check your connection and try again. " +
                                    "If this keeps happening, move to an area with better signal " +
                                    "before re-scanning."
                        )
                    } catch (e: Exception) {
                        loading = false
                        resultDialog = MoveResult.ServerFailure("Unexpected error: ${e.message ?: "unknown"}")
                    }
                }
            },
            enabled = !loading && labelInput.isNotBlank() && currentLocationInput.isNotBlank() && newLocationInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Moving..." else "Confirm Move")
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
            is MoveResult.Success -> "Move Successful" to result.message
            is MoveResult.ServerFailure -> "Move Failed" to result.message
            is MoveResult.NetworkFailure -> "Connection Problem" to result.message
        }

        fun dismiss() {
            resultDialog = null
            if (result is MoveResult.Success) {
                labelInput = ""
                currentLocationInput = ""
                newLocationInput = ""
                quantityInput = ""
                needsQuantity = false
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