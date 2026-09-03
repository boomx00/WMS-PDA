package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ScanBus
import com.kevin.wmsscanner.network.CreateCustomOpnameRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.OpnameCountRequest
import com.kevin.wmsscanner.network.OpnameLocationRow
import com.kevin.wmsscanner.network.OpnameSession
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import com.kevin.wmsscanner.network.OpnameReportResponse

@Composable
fun StockOpnameScreen(navController: NavHostController) {
    var sessions by remember { mutableStateOf<List<OpnameSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeSession by remember { mutableStateOf<String?>(null) }
    var activeSessionStatus by remember { mutableStateOf<String?>(null) }
    var creatingCustom by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        try {
            val response = NetworkModule.api.getMyOpnameSessions()
            if (response.isSuccessful) {
                sessions = response.body() ?: emptyList()
            } else {
                error = "Failed to load opname sessions"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    if (activeSession != null) {
        OpnameLocationsScreen(
            opnameNumber = activeSession!!,
            initialStatus = activeSessionStatus,
            onBack = {
                activeSession = null
                activeSessionStatus = null
                scope.launch { refresh() }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Stock Opname", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sessions assigned to you",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                creatingCustom = true
                createError = null
                scope.launch {
                    try {
                        val response = NetworkModule.api.createCustomOpname(CreateCustomOpnameRequest())
                        creatingCustom = false
                        if (response.isSuccessful) {
                            val body = response.body()
                            val newOpname = body?.opnameNumber
                            if (newOpname != null) {
                                activeSession = newOpname
                                activeSessionStatus = body.status
                            }
                        } else {
                            createError = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                        }
                    } catch (e: Exception) {
                        creatingCustom = false
                        createError = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !creatingCustom,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (creatingCustom) "Creating..." else "+ Create Custom Opname")
        }

        if (createError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(createError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (sessions.isEmpty()) {
            Text(
                "No opname sessions assigned to you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sessions.forEach { s ->
                val (label, color) = when (s.status) {
                    "DONE" -> "DONE" to MaterialTheme.colorScheme.primary
                    "IN_PROGRESS" -> "IN PROGRESS" to MaterialTheme.colorScheme.tertiary
                    else -> "PENDING" to MaterialTheme.colorScheme.error
                }
                Card(
                    onClick = {
                        activeSession = s.opnameNumber
                        activeSessionStatus = s.status
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(s.opnameNumber, fontWeight = FontWeight.Medium)
                            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "${s.countedLines}/${s.totalLines} counted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun OpnameLocationsScreen(opnameNumber: String, initialStatus: String?, onBack: () -> Unit) {
    var report by remember { mutableStateOf<OpnameReportResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialogState by remember { mutableStateOf<CountDialogState?>(null) }
    // Once an opname is finished, past counts are locked from further edits —
    // location, SKU, and quantity all become read-only. Before that, any
    // saved count can be reopened and corrected (mis-scanned location, wrong
    // SKU, etc.), not just its quantity.
    var isFinished by remember { mutableStateOf(initialStatus == "DONE") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        try {
            val response = NetworkModule.api.getOpnameReport(opnameNumber)
            if (response.isSuccessful) {
                report = response.body()
            } else {
                error = "Failed to load"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(opnameNumber, style = MaterialTheme.typography.headlineMedium)
        if (isFinished) {
            Text(
                "This opname is finished — counts are locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { dialogState = CountDialogState() },
            enabled = !isFinished,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Scan Location to Count")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (report == null || report!!.report.isEmpty()) {
            Text(
                "No locations counted yet — scan one above to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            report!!.report.forEach { loc ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(loc.locationCode, fontWeight = FontWeight.Medium)
                        if (loc.items.isEmpty()) {
                            Text(
                                "No items yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            loc.items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable(enabled = !isFinished) {
                                            dialogState = CountDialogState(
                                                locationCode = loc.locationCode,
                                                sku = item.itemSku,
                                                qty = item.countedQty.toString(),
                                                editing = true
                                            )
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(item.itemSku, style = MaterialTheme.typography.bodyMedium)
                                        Text(item.itemName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${item.countedQty}", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        var finishing by remember { mutableStateOf(false) }
        var finishError by remember { mutableStateOf<String?>(null) }

        Button(
            onClick = {
                finishing = true
                finishError = null
                scope.launch {
                    try {
                        val response = NetworkModule.api.finishOpname(opnameNumber)
                        finishing = false
                        if (response.isSuccessful) {
                            isFinished = true
                            onBack()
                        } else {
                            finishError = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                        }
                    } catch (e: Exception) {
                        finishing = false
                        finishError = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !finishing && !isFinished,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (finishing) "Finishing..." else "Finish Stock Opname")
        }

        if (finishError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(finishError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }

    dialogState?.let { state ->
        OpnameCountDialog(
            opnameNumber = opnameNumber,
            initial = state,
            isFinished = isFinished,
            onDismiss = {
                dialogState = null
                scope.launch { refresh() }
            }
        )
    }
}

data class CountDialogState(
    val locationCode: String = "",
    val sku: String = "",
    val qty: String = "",
    val editing: Boolean = false
)

@Composable
fun OpnameCountDialog(
    opnameNumber: String,
    initial: CountDialogState,
    isFinished: Boolean,
    onDismiss: () -> Unit
) {
    var locationInput by remember { mutableStateOf(initial.locationCode) }
    var scannedInput by remember { mutableStateOf(initial.sku) }
    var countedInput by remember { mutableStateOf(initial.qty) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (isFinished) return@LaunchedEffect
        ScanBus.scans.collect { code ->
            if (locationInput.isBlank()) {
                locationInput = code
            } else {
                scannedInput = code
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (initial.editing) "Edit Count" else "Count",
                    style = MaterialTheme.typography.titleMedium
                )
                if (isFinished) {
                    Text(
                        "This opname is finished — locked from further edits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!initial.editing) {
                    Text(
                        "Scan location, then scan/type SKU, then enter carton amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Noticed a mistake? You can correct the location and SKU here too, " +
                                "not just the quantity — as long as this opname isn't finished yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text("Location") },
                    singleLine = true,
                    readOnly = isFinished,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isFinished) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CameraScanButton(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = scannedInput,
                    onValueChange = { scannedInput = it },
                    label = { Text("SKU") },
                    singleLine = true,
                    readOnly = isFinished,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isFinished) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CameraScanButton(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = countedInput,
                    onValueChange = { countedInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Carton amount") },
                    singleLine = true,
                    readOnly = isFinished,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                if (lastResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(lastResult!!, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!isFinished) {
                        Button(
                            onClick = {
                                val counted = countedInput.toIntOrNull() ?: return@Button
                                submitting = true
                                error = null
                                lastResult = null
                                scope.launch {
                                    try {
                                        val response = NetworkModule.api.submitOpnameCount(
                                            opnameNumber,
                                            OpnameCountRequest(
                                                locationCode = locationInput.trim(),
                                                scanned = scannedInput.trim(),
                                                countedQty = counted,
                                                originalLocationCode = if (initial.editing) initial.locationCode else null,
                                                originalSku = if (initial.editing) initial.sku else null
                                            )
                                        )
                                        submitting = false
                                        if (response.isSuccessful) {
                                            val body = response.body()
                                            lastResult = "Saved ${body?.itemSku} at ${body?.locationCode}: $counted"
                                            if (!initial.editing) {
                                                scannedInput = ""
                                                countedInput = ""
                                            }
                                        } else {
                                            error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                                        }
                                    } catch (e: Exception) {
                                        submitting = false
                                        error = "Couldn't reach server: ${e.message}"
                                    }
                                }
                            },
                            enabled = locationInput.isNotBlank() && scannedInput.isNotBlank() && countedInput.toIntOrNull() != null && !submitting
                        ) {
                            Text(if (submitting) "Saving..." else "Save")
                        }
                    }
                }

                if (!isFinished) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { locationInput = "" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear location")
                        }
                        TextButton(
                            onClick = { scannedInput = "" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear SKU")
                        }
                    }
                }
            }
        }
    }
}