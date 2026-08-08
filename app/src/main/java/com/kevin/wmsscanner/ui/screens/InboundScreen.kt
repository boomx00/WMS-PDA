package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.kevin.wmsscanner.network.InboundRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.LabelRow
import com.kevin.wmsscanner.util.parseLabel
import kotlinx.coroutines.launch
import com.kevin.wmsscanner.ui.components.CameraScanButton
@Composable
fun InboundScreen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var manualQuantity by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val labelFocusRequester = remember { FocusRequester() }
    val qtyFocusRequester = remember { FocusRequester() }

    val parsed = remember(labelInput) { parseLabel(labelInput) }
    val manualQty = manualQuantity.toIntOrNull()

    LaunchedEffect(Unit) {
        labelFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            labelInput = code
            error = null
            success = null
            qtyFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            },
            label = { Text("Scan product label") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(labelFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { qtyFocusRequester.requestFocus() })
        )
        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))

        if (parsed != null) {
            Column {
                LabelRow("SKU", parsed.sku)
                LabelRow("Pallet Seq", parsed.palletSeq)
                LabelRow("Work Order", parsed.workOrderNumber)
            }
        } else if (labelInput.isNotBlank()) {
            Text(
                "Label format not recognized",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = manualQuantity,
            onValueChange = {
                manualQuantity = it.filter { c -> c.isDigit() }
                error = null
                success = null
            },
            label = { Text("Actual quantity") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(qtyFocusRequester)
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
                val p = parsed ?: return@Button
                val qty = manualQty ?: return@Button
                error = null
                success = null
                loading = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.inbound(
                            InboundRequest(
                                label = labelInput.trim(),
                                sku = p.sku,
                                workOrderNumber = p.workOrderNumber,
                                quantity = qty
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Scanned in: ${labelInput.trim()} — qty $qty"
                            labelInput = ""
                            manualQuantity = ""
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
            enabled = !loading && parsed != null && manualQty != null && manualQty > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Scanning in..." else "Scan In")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to menu")
        }
    }
}