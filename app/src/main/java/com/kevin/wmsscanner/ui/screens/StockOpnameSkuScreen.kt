package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ScanBus
import com.kevin.wmsscanner.network.AddSkuToOpnameRequest
import com.kevin.wmsscanner.network.ItemSearchResult
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.OpnameCountRequest
import com.kevin.wmsscanner.network.SkuOpnameApi
import com.kevin.wmsscanner.network.SkuOpnameDetail
import com.kevin.wmsscanner.network.SkuOpnameLine
import com.kevin.wmsscanner.network.SkuOpnameSession
import com.kevin.wmsscanner.network.SkuLocationRow
import com.kevin.wmsscanner.ui.components.CameraScanButton
import com.kevin.wmsscanner.ui.components.picking.InfoBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Stock Opname — By SKU
//
// Create New makes an empty session (SOSKU-<name>-<n>). Inside it, SKUs are
// added one at a time and shown as cards; adding a SKU locks that SKU's total
// system quantity on the server at that moment. Tapping a SKU card opens its
// locations: enter a location and a quantity for each place it's found, and
// tap any line to correct it.
// ---------------------------------------------------------------------------

// Server errors come back as {"error":"..."} — show just the message.
private fun readApiError(raw: String?): String {
    if (raw.isNullOrBlank()) return "unknown error"
    val match = Regex("\"error\"\\s*:\\s*\"(.*?)\"").find(raw)
    return match?.groupValues?.get(1) ?: raw
}

private fun signed(value: Int): String = if (value > 0) "+$value" else "$value"

// ---------------------------------------------------------------------------
// 1. Session list
// ---------------------------------------------------------------------------

@Composable
fun StockOpnameSkuScreen(navController: NavHostController) {
    var sessions by remember { mutableStateOf<List<SkuOpnameSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeSession by remember { mutableStateOf<SkuOpnameSession?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = null
        try {
            val response = SkuOpnameApi.api.getMySkuOpnameSessions()
            if (response.isSuccessful) {
                sessions = response.body() ?: emptyList()
            } else {
                error = "Failed to load SKU opname sessions"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val current = activeSession
    if (current != null) {
        SkuOpnameSessionScreen(
            session = current,
            onBack = {
                activeSession = null
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
        Text("Stock Opname — By SKU", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your per-SKU sessions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                creating = true
                createError = null
                scope.launch {
                    try {
                        val response = SkuOpnameApi.api.createSkuOpname()
                        creating = false
                        val body = response.body()
                        if (response.isSuccessful && body != null) {
                            activeSession = body
                        } else {
                            createError = "Failed: ${readApiError(response.errorBody()?.string())}"
                        }
                    } catch (e: Exception) {
                        creating = false
                        createError = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !creating,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (creating) "Creating..." else "+ Create New")
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
                "No SKU opname sessions yet — tap Create New to start one.",
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
                    onClick = { activeSession = s },
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
                            "${s.skuCount} SKU · System (locked): ${s.systemQty} · Counted: ${s.countedQty}",
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

// ---------------------------------------------------------------------------
// 2. Inside a session — SKU cards
// ---------------------------------------------------------------------------

@Composable
fun SkuOpnameSessionScreen(session: SkuOpnameSession, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<SkuOpnameDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isFinished by remember { mutableStateOf(session.status == "DONE") }
    var showAddDialog by remember { mutableStateOf(false) }
    var activeSkuCode by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(showSpinner: Boolean = true) {
        if (showSpinner) loading = true
        error = null
        try {
            val response = SkuOpnameApi.api.getSkuOpnameDetail(session.opnameNumber)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                detail = body
                isFinished = body.finished
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

    // Open one SKU's locations screen.
    val openCode = activeSkuCode
    val openEntry = detail?.skus?.firstOrNull { it.skuCode == openCode }
    if (openCode != null && openEntry != null) {
        SkuLocationsScreen(
            opnameNumber = session.opnameNumber,
            skuCode = openEntry.skuCode,
            skuName = openEntry.skuName,
            systemQty = openEntry.systemQty,
            isFinished = isFinished,
            onBack = {
                activeSkuCode = null
                scope.launch { refresh(showSpinner = false) }
            }
        )
        return
    }

    val skus = detail?.skus ?: emptyList()
    val totalLocked = skus.sumOf { it.systemQty }
    val totalCounted = skus.sumOf { it.countedQty }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(session.opnameNumber, style = MaterialTheme.typography.headlineMedium)
        if (isFinished) {
            Text(
                "This opname is finished — counts are locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Add the SKUs you want to count, then tap a SKU to enter its locations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (skus.isNotEmpty()) {
            Text(
                "${skus.size} SKU · System (locked): $totalLocked · Counted: $totalCounted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = { showAddDialog = true },
            enabled = !isFinished,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("+ Add SKU")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (skus.isEmpty()) {
            Text(
                "No SKU added yet — tap Add SKU to start.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            skus.forEach { sku ->
                Card(
                    onClick = { activeSkuCode = sku.skuCode },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sku.skuCode, fontWeight = FontWeight.Medium)
                            Text(
                                if (sku.difference == 0) "Match" else signed(sku.difference),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sku.difference == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            sku.skuName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "System (locked): ${sku.systemQty} · Counted: ${sku.countedQty} · ${sku.lines.size} location(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                finishing = true
                finishError = null
                scope.launch {
                    try {
                        val response = NetworkModule.api.finishOpname(session.opnameNumber)
                        finishing = false
                        if (response.isSuccessful) {
                            isFinished = true
                            onBack()
                        } else {
                            finishError = "Failed: ${readApiError(response.errorBody()?.string())}"
                        }
                    } catch (e: Exception) {
                        finishing = false
                        finishError = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !finishing && !isFinished,
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

    if (showAddDialog) {
        AddSkuDialog(
            opnameNumber = session.opnameNumber,
            existingSkuCodes = skus.map { it.skuCode.uppercase() }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdded = {
                showAddDialog = false
                scope.launch { refresh(showSpinner = false) }
            }
        )
    }
}

// Pick a SKU to add to the session. Saving locks that SKU's system quantity
// on the server at that moment.
@Composable
fun AddSkuDialog(
    opnameNumber: String,
    existingSkuCodes: Set<String>,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    var skuInput by remember { mutableStateOf("") }
    var resolved by remember { mutableStateOf<ItemSearchResult?>(null) }
    var suggestions by remember { mutableStateOf<List<ItemSearchResult>>(emptyList()) }
    var lookupLoading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            skuInput = code
        }
    }

    // Same live search the by-location count dialog uses: resolves the
    // product once the typed/scanned SKU matches exactly, otherwise offers a
    // pick list. A scanned pallet label (SKU*seq*qty*WO) is reduced to its
    // SKU segment for the search.
    LaunchedEffect(skuInput) {
        resolved = null
        suggestions = emptyList()
        error = null
        val query = skuInput.trim().trimStart('*').substringBefore('*').trim()
        if (query.length < 2) return@LaunchedEffect
        delay(350)

        lookupLoading = true
        try {
            val response = NetworkModule.api.searchItems(query)
            lookupLoading = false
            if (response.isSuccessful) {
                val results = response.body() ?: emptyList()
                val exact = results.firstOrNull { it.sku.equals(query, ignoreCase = true) }
                if (exact != null) {
                    resolved = exact
                } else {
                    suggestions = results
                }
            }
        } catch (e: Exception) {
            lookupLoading = false
        }
    }

    val alreadyAdded = resolved?.let { existingSkuCodes.contains(it.sku.uppercase()) } ?: false

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Add SKU", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Its system quantity is locked the moment you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = skuInput,
                    onValueChange = { skuInput = it },
                    label = { Text("Scan or type SKU") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                CameraScanButton(modifier = Modifier.fillMaxWidth())

                if (lookupLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Checking...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (resolved != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${resolved!!.sku} — ${resolved!!.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (alreadyAdded) {
                        Text(
                            "SKU ini sudah ada di sesi ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Did you mean:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    suggestions.take(8).forEach { result ->
                        Card(
                            onClick = { skuInput = result.sku },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(result.sku, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    result.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val sku = resolved?.sku ?: return@Button
                            submitting = true
                            error = null
                            scope.launch {
                                try {
                                    val response = SkuOpnameApi.api.addSkuToOpname(
                                        opnameNumber,
                                        AddSkuToOpnameRequest(sku)
                                    )
                                    submitting = false
                                    if (response.isSuccessful) {
                                        onAdded()
                                    } else {
                                        error = "Failed: ${readApiError(response.errorBody()?.string())}"
                                    }
                                } catch (e: Exception) {
                                    submitting = false
                                    error = "Couldn't reach server: ${e.message}"
                                }
                            }
                        },
                        enabled = resolved != null && !alreadyAdded && !submitting
                    ) {
                        Text(if (submitting) "Saving..." else "Save")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. One SKU's locations
// ---------------------------------------------------------------------------

data class SkuCountDialogState(
    val locationCode: String = "",
    val qty: String = "",
    val editing: Boolean = false
)

@Composable
fun SkuLocationsScreen(
    opnameNumber: String,
    skuCode: String,
    skuName: String,
    systemQty: Int,
    isFinished: Boolean,
    onBack: () -> Unit
) {
    var lines by remember { mutableStateOf<List<SkuOpnameLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialogState by remember { mutableStateOf<SkuCountDialogState?>(null) }
    var historyLocation by remember { mutableStateOf<String?>(null) }
    var showSystemLocations by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(showSpinner: Boolean = true) {
        if (showSpinner) loading = true
        error = null
        try {
            val response = SkuOpnameApi.api.getSkuOpnameDetail(opnameNumber)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                lines = body.skus.firstOrNull { it.skuCode == skuCode }?.lines ?: emptyList()
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

    val countedTotal = lines.sumOf { it.countedQty }
    val difference = countedTotal - systemQty
    val existingQtyByLocation = lines.associate { it.locationCode.uppercase() to it.countedQty }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(opnameNumber, style = MaterialTheme.typography.titleMedium)
        if (isFinished) {
            Text(
                "This opname is finished — counts are locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(skuCode, style = MaterialTheme.typography.titleMedium)
                        Text(
                            skuName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Where this SKU is in the system right now.
                    InfoBadge(onClick = { showSystemLocations = true })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("System qty (locked)")
                    Text("$systemQty", fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Counted so far")
                    Text("$countedTotal", fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Difference")
                    Text(
                        signed(difference),
                        fontWeight = FontWeight.Medium,
                        color = if (difference == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { dialogState = SkuCountDialogState() },
            enabled = !isFinished,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Add Location Count")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (lines.isEmpty()) {
            Text(
                "No locations counted yet — tap Add Location Count to start.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            lines.forEach { line ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isFinished) {
                                    dialogState = SkuCountDialogState(
                                        locationCode = line.locationCode,
                                        qty = line.countedQty.toString(),
                                        editing = true
                                    )
                                },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(line.locationCode, fontWeight = FontWeight.Medium)
                            Text("${line.countedQty}", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { historyLocation = line.locationCode },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("View History")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }

    dialogState?.let { state ->
        SkuCountDialog(
            opnameNumber = opnameNumber,
            skuCode = skuCode,
            skuName = skuName,
            initial = state,
            existingQtyByLocation = existingQtyByLocation,
            onSaved = { scope.launch { refresh(showSpinner = false) } },
            onDismiss = { dialogState = null }
        )
    }

    // Reuses the by-location history dialog — a SKU session stores its
    // counts the same way, so the per-line history works unchanged.
    historyLocation?.let { locationCode ->
        CountHistoryDialog(
            opnameNumber = opnameNumber,
            locationCode = locationCode,
            sku = skuCode,
            onDismiss = { historyLocation = null }
        )
    }

    if (showSystemLocations) {
        SkuSystemLocationsDialog(
            skuCode = skuCode,
            skuName = skuName,
            lockedSystemQty = systemQty,
            countedQtyByLocation = existingQtyByLocation,
            onDismiss = { showSystemLocations = false }
        )
    }
}

// "i" on the SKU card: every location holding this SKU in the system right
// now (live location_stock, pickable or not). The locked system qty was
// taken from these same rows when the SKU was added, so the live total is
// shown next to it — if they differ, stock moved after the lock. Each row
// also shows what's been counted there in this session, if anything.
@Composable
fun SkuSystemLocationsDialog(
    skuCode: String,
    skuName: String,
    lockedSystemQty: Int,
    countedQtyByLocation: Map<String, Int>,
    onDismiss: () -> Unit
) {
    var rows by remember { mutableStateOf<List<SkuLocationRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(skuCode) {
        try {
            val response = SkuOpnameApi.api.getAllLocationsBySku(skuCode, 1)
            if (response.isSuccessful) {
                rows = response.body()?.locations ?: emptyList()
            } else {
                error = "Gagal memuat lokasi: ${readApiError(response.errorBody()?.string())}"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    val liveTotal = rows.sumOf { it.quantity }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Lokasi di sistem", style = MaterialTheme.typography.titleMedium)
                Text(skuCode, style = MaterialTheme.typography.bodyMedium)
                Text(
                    skuName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (!loading && error == null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("System qty now", style = MaterialTheme.typography.bodySmall)
                        Text("$liveTotal", fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("System qty (locked)", style = MaterialTheme.typography.bodySmall)
                        Text("$lockedSystemQty", fontWeight = FontWeight.Medium)
                    }
                    if (liveTotal != lockedSystemQty) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Stok sudah berubah sejak SKU ini ditambahkan (${signed(liveTotal - lockedSystemQty)}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Location",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "System / Counted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        rows.isEmpty() -> Text(
                            "Tidak ada stok di lokasi manapun.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> rows.forEach { row ->
                            val counted = countedQtyByLocation[row.locationCode.uppercase()]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.locationCode, fontWeight = FontWeight.Medium)
                                    Text(
                                        row.locationType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${row.quantity}",
                                    fontWeight = FontWeight.Medium,
                                    color = if (row.quantity < 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    " / ${counted ?: "–"}",
                                    color = when {
                                        counted == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        counted == row.quantity -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
            }
        }
    }
}

// Add or correct one location's quantity for this SKU. The SKU is fixed —
// only the location and quantity are entered.
//
// RACK (or location not yet resolved): type the total once and Save. A
// location that already has a line must be corrected via EDIT instead.
// FLOOR (any non-RACK) location: tally mode, same as the by-location count
// dialog — type an amount, tap "+ Add" or "− Subtract" to move the running
// total, each tap saves immediately. The total starts from whatever is
// already recorded for that location, so reopening a line keeps tallying.
@Composable
fun SkuCountDialog(
    opnameNumber: String,
    skuCode: String,
    skuName: String,
    initial: SkuCountDialogState,
    existingQtyByLocation: Map<String, Int>,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    var locationInput by remember { mutableStateOf(initial.locationCode) }
    var qtyInput by remember { mutableStateOf(initial.qty) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    // Locations saved during THIS dialog session — the list behind the dialog
    // only refreshes asynchronously, so these are tracked here too to keep
    // the duplicate check accurate while entering several in a row.
    var savedThisSession by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Last location saved by this dialog; used so renaming a just-saved line
    // cleans up the old row instead of leaving a duplicate.
    var lastCommittedLocation by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ScanBus.scans.collect { code ->
            locationInput = code
        }
    }

    // Resolve the location type so FLOOR locations switch to tally mode.
    var locationType by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(locationInput) {
        locationType = null
        val loc = locationInput.trim()
        if (loc.isBlank()) return@LaunchedEffect
        delay(350)
        try {
            val response = NetworkModule.api.lookupLocationStock(loc)
            if (response.isSuccessful) {
                locationType = response.body()?.locationType
            }
        } catch (e: Exception) {
            // Falls back to the normal single-shot Save flow.
        }
    }

    val trimmedLocation = locationInput.trim()
    val upperLocation = trimmedLocation.uppercase()
    val useTally = locationType != null && locationType != "RACK" && trimmedLocation.isNotBlank()

    // Running total for the current location. Re-baselines whenever the
    // location changes, starting from what is already recorded there.
    var tally by remember(upperLocation) {
        mutableStateOf(existingQtyByLocation[upperLocation] ?: 0)
    }
    var tallyInput by remember { mutableStateOf("") }

    // The row this line used to be saved under, if the location is being
    // corrected (see the server's originalLocationCode handling).
    val effectiveOriginalLocation: String? = when {
        lastCommittedLocation != null ->
            if (trimmedLocation.equals(lastCommittedLocation, ignoreCase = true)) lastCommittedLocation else null
        initial.editing -> initial.locationCode
        else -> null
    }

    // A location that already has a line for this SKU must be corrected via
    // EDIT, not re-entered — saving it again would silently overwrite that
    // line's quantity. Doesn't apply in tally mode, where continuing the
    // running total of an existing line is exactly the point.
    val locationChanged = !initial.editing ||
            !trimmedLocation.equals(initial.locationCode.trim(), ignoreCase = true)
    val isDuplicate = !useTally && locationChanged &&
            trimmedLocation.isNotBlank() &&
            (existingQtyByLocation.containsKey(upperLocation) || savedThisSession.contains(upperLocation))

    val qtyValue = qtyInput.toIntOrNull()
    val canSave = trimmedLocation.isNotBlank() && qtyValue != null && !isDuplicate && !submitting

    fun submit(qty: Int, onDone: (Boolean) -> Unit) {
        submitting = true
        error = null
        lastResult = null
        scope.launch {
            try {
                val response = NetworkModule.api.submitOpnameCount(
                    opnameNumber,
                    OpnameCountRequest(
                        locationCode = trimmedLocation,
                        scanned = skuCode,
                        countedQty = qty,
                        originalLocationCode = effectiveOriginalLocation,
                        originalSku = if (effectiveOriginalLocation != null) skuCode else null
                    )
                )
                submitting = false
                if (response.isSuccessful) {
                    lastCommittedLocation = trimmedLocation
                    savedThisSession = savedThisSession + upperLocation
                    onSaved()
                    onDone(true)
                } else {
                    error = "Failed: ${readApiError(response.errorBody()?.string())}"
                    onDone(false)
                }
            } catch (e: Exception) {
                submitting = false
                error = "Couldn't reach server: ${e.message}"
                onDone(false)
            }
        }
    }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (initial.editing) "Edit Count" else "Add Location Count",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(skuCode, style = MaterialTheme.typography.bodyMedium)
                Text(
                    skuName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (initial.editing)
                        "Noticed a mistake? Correct the location and/or quantity below."
                    else
                        "Scan the location, then enter the carton amount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = {
                        locationInput = it
                        lastResult = null
                    },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                CameraScanButton(modifier = Modifier.fillMaxWidth())

                if (isDuplicate) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Lokasi $trimmedLocation sudah tercatat untuk SKU ini. " +
                                "Jika mau di rubah, silahkan EDIT dari daftar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (useTally) {
                    Text(
                        "Current total for $skuCode: $tally cartons",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tallyInput,
                        onValueChange = { tallyInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = {
                            qtyInput = it.filter { c -> c.isDigit() }
                            lastResult = null
                        },
                        label = { Text("Carton amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                if (lastResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(lastResult!!, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (useTally) {
                    val amount = tallyInput.toIntOrNull() ?: 0
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val newTotal = (tally - amount).coerceAtLeast(0)
                                submit(newTotal) { ok ->
                                    if (ok) {
                                        tally = newTotal
                                        tallyInput = ""
                                        lastResult = "Total for $skuCode now $newTotal"
                                    }
                                }
                            },
                            enabled = !submitting && amount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (submitting) "..." else "− Subtract")
                        }
                        Button(
                            onClick = {
                                val newTotal = tally + amount
                                submit(newTotal) { ok ->
                                    if (ok) {
                                        tally = newTotal
                                        tallyInput = ""
                                        lastResult = "Total for $skuCode now $newTotal"
                                    }
                                }
                            },
                            enabled = !submitting && amount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (submitting) "..." else "+ Add")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !submitting) { Text("Close") }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !submitting) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val qty = qtyValue ?: return@Button
                                // Confirm with a pop-up; its OK closes the dialog so the
                                // same location can't be entered twice.
                                submit(qty) { ok ->
                                    if (ok) successMessage =
                                        "Location: $trimmedLocation\nSKU: $skuCode\nQty: $qty"
                                }
                            },
                            enabled = canSave
                        ) {
                            Text(if (submitting) "Saving..." else "Save")
                        }
                    }
                }
            }
        }
    }

    successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {
                successMessage = null
                onDismiss()
            },
            title = { Text("Saved successfully") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    successMessage = null
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        )
    }
}