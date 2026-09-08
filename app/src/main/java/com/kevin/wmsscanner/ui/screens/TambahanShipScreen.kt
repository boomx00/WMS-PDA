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
import com.kevin.wmsscanner.network.TambahanShipRequest
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch

@Composable
fun TambahanShipScreen(navController: NavHostController, soNumber: String, expectedSku: String) {
    var labelInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultIsError by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Ship Tambahan", style = MaterialTheme.typography.headlineMedium)
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

        Spacer(modifier = Modifier.height(8.dp))
        CameraScanButton(modifier = Modifier.fillMaxWidth())

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

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val qty = quantityInput.toIntOrNull() ?: return@Button
                submitting = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.shipTambahan(
                            TambahanShipRequest(soNumber = soNumber, label = labelInput.trim(), quantity = qty)
                        )
                        submitting = false
                        if (response.isSuccessful) {
                            resultMessage = "Shipped $qty. Remaining: ${response.body()?.remainingToShip}"
                            resultIsError = false
                        } else {
                            resultMessage = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                            resultIsError = true
                        }
                    } catch (e: Exception) {
                        submitting = false
                        resultMessage = "Couldn't reach server: ${e.message}"
                        resultIsError = true
                    }
                    showResultDialog = true
                }
            },
            enabled = !submitting && labelInput.isNotBlank() && quantityInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (submitting) "Shipping..." else "Confirm Ship")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { /* require explicit OK — no dismiss-on-outside-tap */ },
            title = { Text(if (resultIsError) "Ship Failed" else "Ship Successful") },
            text = { Text(resultMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showResultDialog = false
                    if (!resultIsError) {
                        labelInput = ""
                        quantityInput = ""
                        labelFocusRequester.requestFocus()
                    }
                }) {
                    Text("OK")
                }
            }
        )
    }
}