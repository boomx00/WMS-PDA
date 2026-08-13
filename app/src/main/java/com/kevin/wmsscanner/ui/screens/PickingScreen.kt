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
import com.kevin.wmsscanner.network.MoveRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun PickingScreen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }
    var needsQuantity by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val locationFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            error = null
            success = null
            if (labelInput.isBlank()) {
                labelInput = code
                locationFocusRequester.requestFocus()
                keyboardController?.show()
            } else {
                locationInput = code
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Picking", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Rack/Floor \u2192 Outbound Warehouse",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

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
                locationFocusRequester.requestFocus()
                keyboardController?.show()
            })
        )

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = locationInput,
            onValueChange = {
                locationInput = it
                error = null
                success = null
            },
            label = { Text("Scan current location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(locationFocusRequester),
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
                label = { Text("Quantity") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(quantityFocusRequester)
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
                error = null
                success = null
                loading = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.movePallet(
                            MoveRequest(
                                label = labelInput.trim(),
                                currentLocationCode = locationInput.trim(),
                                newLocationCode = "OUTBOUND_WH",
                                quantity = quantityInput.toIntOrNull()
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Moved ${labelInput.trim()} to Outbound Warehouse"
                            labelInput = ""
                            locationInput = ""
                            quantityInput = ""
                            needsQuantity = false
                            labelFocusRequester.requestFocus()
                        } else {
                            val errBody = response.errorBody()?.string()
                            val json = try { JSONObject(errBody ?: "{}") } catch (e: Exception) { JSONObject() }
                            when (json.optString("matchType")) {
                                "default_needs_quantity" -> {
                                    needsQuantity = true
                                    error = "${json.optString("error")} (${json.optInt("availableQuantity")} available)"
                                }
                                "auto_inbound_needs_quantity" -> {
                                    needsQuantity = true
                                    error = json.optString("error")
                                }
                                "already_exists_elsewhere" -> {
                                    error = "\u26A0\uFE0F ${json.optString("error")}"
                                }
                                else -> {
                                    error = "Failed: ${json.optString("error", errBody ?: "unknown error")}"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        loading = false
                        error = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !loading && labelInput.isNotBlank() && locationInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Moving..." else "Confirm Picking")
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