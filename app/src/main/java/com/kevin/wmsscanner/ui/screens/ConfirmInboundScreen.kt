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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ScanBus
import com.kevin.wmsscanner.network.ConfirmInboundRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch

@Composable
fun ConfirmInboundScreen(navController: NavHostController) {
    var labelInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val labelFocusRequester = remember { FocusRequester() }
    val locationFocusRequester = remember { FocusRequester() }

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
        Text("Confirm Inbound", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Scan a pending pallet's label, then where you placed it",
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
            label = { Text("Scan pending pallet label") },
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
            label = { Text("Scan actual location") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(locationFocusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
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
                error = null
                success = null
                loading = true
                scope.launch {
                    try {
                        val response = NetworkModule.api.confirmInbound(
                            ConfirmInboundRequest(
                                label = labelInput.trim(),
                                locationCode = locationInput.trim()
                            )
                        )
                        loading = false
                        if (response.isSuccessful) {
                            success = "Confirmed ${labelInput.trim()} at ${locationInput.trim()}"
                            labelInput = ""
                            locationInput = ""
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
            enabled = !loading && labelInput.isNotBlank() && locationInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Confirming..." else "Confirm Inbound")
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