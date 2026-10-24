package com.kevin.wmsscanner.ui.screens

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
import com.kevin.wmsscanner.network.CreateCustomOpnameRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.OpnameCountRequest
import com.kevin.wmsscanner.network.OpnameLocationRow
import com.kevin.wmsscanner.network.OpnameSession
import com.kevin.wmsscanner.network.ItemSearchResult
import com.kevin.wmsscanner.network.OpnameCountHistoryEntry
import com.kevin.wmsscanner.ui.components.CameraScanButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    // (locationCode, sku) of the line whose addition/reduction history is
    // being viewed — null when no history dialog is open. sku is "" for a
    // confirmed-empty location's history.
    var historyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Once an opname is finished, past counts are locked from further edits —
    // location, SKU, and quantity all become read-only. Before that, any
    // saved count can be reopened and corrected (mis-scanned location, wrong
    // SKU, etc.), not just its quantity.
    var isFinished by remember { mutableStateOf(initialStatus == "DONE") }
    val scope = rememberCoroutineScope()

    // Snapshot of what's already recorded per location, from the last
    // report fetch — passed into the dialog so a brand-new ("Scan Location
    // to Count") entry can warn before creating an accidental duplicate at
    // a location that already has data, instead of silently allowing a
    // second row to exist for the same location.
    val existingItemsByLocation = remember(report) {
        report?.report?.associate { it.locationCode.uppercase() to it.items } ?: emptyMap()
    }

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
            // One card PER ITEM rather than one card per location — a FLOOR
            // location can hold several different SKUs, and bundling them
            // into a single card made them read as one blob instead of
            // distinct, individually-tappable lines.
            report!!.report.forEach { loc ->
                if (loc.items.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(loc.locationCode, fontWeight = FontWeight.Medium)
                            Text(
                                "No items yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    loc.items.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isFinished) {
                                            dialogState = CountDialogState(
                                                locationCode = loc.locationCode,
                                                sku = item.itemSku ?: "",
                                                qty = if (item.itemSku == null) "" else item.countedQty.toString(),
                                                editing = true
                                            )
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(loc.locationCode, fontWeight = FontWeight.Medium)
                                        if (item.itemSku != null) {
                                            Text(item.itemSku, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text(
                                            item.itemName,
                                            style = if (item.itemSku != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (item.itemSku != null) {
                                        Text("${item.countedQty}", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { historyTarget = loc.locationCode to (item.itemSku ?: "") },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("View History")
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
            existingItemsByLocation = existingItemsByLocation,
            onDismiss = {
                dialogState = null
                scope.launch { refresh() }
            }
        )
    }

    historyTarget?.let { (locationCode, sku) ->
        CountHistoryDialog(
            opnameNumber = opnameNumber,
            locationCode = locationCode,
            sku = sku,
            onDismiss = { historyTarget = null }
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
    existingItemsByLocation: Map<String, List<com.kevin.wmsscanner.network.OpnameReportItem>> = emptyMap(),
    onDismiss: () -> Unit
) {
    var locationInput by remember { mutableStateOf(initial.locationCode) }
    var scannedInput by remember { mutableStateOf(initial.sku) }
    var countedInput by remember { mutableStateOf(initial.qty) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    // Set when a brand-new (not-editing) entry targets a location that
    // already has data and would create an accidental duplicate instead
    // of correcting the existing line — see duplicateWarningMessage below.
    var duplicateWarningMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Live SKU lookup/search — resolves the product name once the typed or
    // scanned SKU matches an item exactly, and otherwise offers a LIKE-style
    // pick list (matches by SKU or product name, e.g. typing "XL38" also
    // surfaces names written as "XL 38" or "XL38+4").
    var resolvedItemName by remember { mutableStateOf<String?>(null) }
    var skuSuggestions by remember { mutableStateOf<List<ItemSearchResult>>(emptyList()) }
    var skuLookupLoading by remember { mutableStateOf(false) }

    LaunchedEffect(scannedInput) {
        resolvedItemName = null
        skuSuggestions = emptyList()
        val query = scannedInput.trim()
        if (isFinished || query.length < 2) return@LaunchedEffect
        delay(350)

        skuLookupLoading = true
        try {
            val response = NetworkModule.api.searchItems(query)
            skuLookupLoading = false
            if (response.isSuccessful) {
                val results = response.body() ?: emptyList()
                val exact = results.firstOrNull { it.sku.equals(query, ignoreCase = true) }
                if (exact != null) {
                    resolvedItemName = exact.name
                } else {
                    skuSuggestions = results
                }
            }
        } catch (e: Exception) {
            skuLookupLoading = false
        }
    }

    // FLOOR locations can hold several SKUs and get counted in passes —
    // walking the floor, tallying what's found a stack at a time — rather
    // than in one single total like a RACK. So once the location resolves
    // to FLOOR (or any other non-RACK type) and a SKU is present, the
    // quantity UI switches from "type the total once" to "type an amount,
    // then tap + or − to add/remove it from a running total", and each tap
    // saves immediately.
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
            // Leave locationType null — falls back to the normal single-shot
            // Save flow rather than blocking on a lookup failure.
        }
    }

    val trimmedLocation = locationInput.trim()
    val trimmedSku = scannedInput.trim()
    val useFloorTally = locationType != null && locationType != "RACK" && trimmedSku.isNotBlank()

    // Running total for the location+SKU currently in the fields.
    // Re-baselines to 0 whenever EITHER the location or the SKU changes —
    // keyed on both, not just the SKU, so tallying "111" at B.1.1 then
    // typing a different location for the same SKU starts a fresh total
    // instead of carrying over B.1.1's count. Only pre-fills from the
    // existing counted qty when the fields still match exactly what this
    // dialog was opened to edit.
    var floorTally by remember(trimmedLocation, trimmedSku) {
        mutableStateOf(
            if (initial.editing &&
                trimmedLocation.equals(initial.locationCode.trim(), ignoreCase = true) &&
                trimmedSku.equals(initial.sku.trim(), ignoreCase = true)
            )
                (initial.qty.toIntOrNull() ?: 0)
            else 0
        )
    }
    var tallyInput by remember { mutableStateOf("") }
    var tallySubmitting by remember { mutableStateOf(false) }

    // Tracks the location+SKU THIS DIALOG SESSION has itself already
    // successfully saved to, if any — set only on a successful submit,
    // never reset by merely typing. This is what lets effectiveOriginal*
    // below tell "still correcting the line I opened, haven't saved yet"
    // apart from "already saved something, now typing something new".
    var lastCommittedLocation by remember { mutableStateOf<String?>(null) }
    var lastCommittedSku by remember { mutableStateOf<String?>(null) }

    // Identifies the row this line USED TO be saved under, for the backend
    // to find and clean up if the location/SKU is being corrected —
    // null means this submission isn't renaming/replacing any other row.
    //
    // - Before this session has saved anything: if the dialog was opened
    //   to EDIT an existing line, the original is always that line's
    //   pre-edit location+SKU — regardless of what's since been typed
    //   into the fields, because nothing committed yet invalidates that.
    //   This is what makes "reopen C.9.1 (111/25), change SKU to 000,
    //   Save" replace the old row instead of leaving both behind: while
    //   you're still typing the correction, "original" stays locked to
    //   111, not reset the moment the SKU field changes.
    // - Once this session HAS saved something, the original is only
    //   meaningful if the CURRENT fields still match what was just saved
    //   (continuing to tally or re-save that same line) — the moment the
    //   fields diverge from that, it's a fresh, unrelated entry and
    //   there's nothing to clean up. This is what stops a brand-new
    //   B.1.2 from deleting an already-saved, unrelated B.1.1.
    //
    // Known limitation: correcting the SAME line's SKU/location a SECOND
    // time within one still-open dialog session (without closing it in
    // between) only cleans up the immediately-preceding row, not earlier
    // ones further back in that session — close and reopen the card
    // between corrections if that ever comes up.
    val effectiveOriginalLocation: String? = when {
        lastCommittedLocation != null ->
            if (trimmedLocation.equals(lastCommittedLocation, ignoreCase = true) &&
                trimmedSku.equals(lastCommittedSku ?: "", ignoreCase = true)
            ) lastCommittedLocation else null
        initial.editing -> initial.locationCode
        else -> null
    }
    val effectiveOriginalSku: String? = when {
        lastCommittedLocation != null ->
            if (trimmedLocation.equals(lastCommittedLocation, ignoreCase = true) &&
                trimmedSku.equals(lastCommittedSku ?: "", ignoreCase = true)
            ) lastCommittedSku else null
        initial.editing -> initial.sku
        else -> null
    }

    // Only applies to a brand-new entry ("Scan Location to Count"), never
    // to editing an existing line — reopening a card and correcting it is
    // exactly the case this check exists to steer people toward, not
    // something it should block.
    //
    // RACK locations hold exactly one SKU, so ANY existing data there
    // means a second submission is a mistake, whatever SKU is typed.
    // FLOOR (and other non-RACK) locations legitimately hold several
    // different SKUs, so only warn there if the SKU being typed matches
    // one that's ALREADY recorded — a genuinely different SKU is a normal
    // additional item, not a duplicate.
    val duplicateCheckTriggered: Boolean = run {
        if (initial.editing || locationType == null) return@run false
        val existingHere = existingItemsByLocation[trimmedLocation.uppercase()] ?: emptyList()
        if (existingHere.isEmpty()) return@run false
        if (locationType == "RACK") return@run true
        existingHere.any { it.itemSku != null && it.itemSku.equals(trimmedSku, ignoreCase = true) }
    }

    fun checkForDuplicateBeforeSubmit(): Boolean {
        if (!duplicateCheckTriggered) return false
        val existingHere = existingItemsByLocation[trimmedLocation.uppercase()] ?: emptyList()
        val existingDesc = existingHere.joinToString(", ") { it.itemSku ?: "(empty confirmation)" }
        duplicateWarningMessage =
            "Lokasi $trimmedLocation sudah tercatat dengan SKU $existingDesc . " +
                    "Jika mau di rubah, silahkan EDIT!!!."
        return true
    }

    fun submitFloorTally(delta: Int) {
        if (delta == 0) return
        if (checkForDuplicateBeforeSubmit()) return
        val newTotal = (floorTally + delta).coerceAtLeast(0)
        tallySubmitting = true
        error = null
        scope.launch {
            try {
                val response = NetworkModule.api.submitOpnameCount(
                    opnameNumber,
                    OpnameCountRequest(
                        locationCode = locationInput.trim(),
                        scanned = scannedInput.trim(),
                        countedQty = newTotal,
                        originalLocationCode = effectiveOriginalLocation,
                        originalSku = effectiveOriginalSku
                    )
                )
                tallySubmitting = false
                if (response.isSuccessful) {
                    floorTally = newTotal
                    tallyInput = ""
                    lastResult = "Total for $trimmedSku now $newTotal"
                    lastCommittedLocation = locationInput.trim()
                    lastCommittedSku = scannedInput.trim()
                } else {
                    error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                }
            } catch (e: Exception) {
                tallySubmitting = false
                error = "Couldn't reach server: ${e.message}"
            }
        }
    }

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
                        "Scan location, then scan/type SKU, then enter carton amount. " +
                                "If the location is empty, leave SKU and carton amount blank.",
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

                if (skuLookupLoading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Checking...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (resolvedItemName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        resolvedItemName!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (skuSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Did you mean:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        skuSuggestions.take(8).forEach { result ->
                            Card(
                                onClick = {
                                    scannedInput = result.sku
                                    resolvedItemName = result.name
                                    skuSuggestions = emptyList()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
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
                }

                if (!isFinished) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CameraScanButton(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (useFloorTally) {
                    // FLOOR (or any non-RACK) location with a SKU chosen — tally
                    // mode: type an amount, tap + or − to add/remove it from the
                    // running total, saved immediately on each tap.
                    Text(
                        "Current total for $trimmedSku: $floorTally cartons",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tallyInput,
                        onValueChange = { tallyInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount") },
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

                    Spacer(modifier = Modifier.height(12.dp))

                    val tallyAmount = tallyInput.toIntOrNull() ?: 0
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { submitFloorTally(-tallyAmount) },
                            enabled = !isFinished && !tallySubmitting && tallyAmount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (tallySubmitting) "..." else "− Subtract")
                        }
                        Button(
                            onClick = { submitFloorTally(tallyAmount) },
                            enabled = !isFinished && !tallySubmitting && tallyAmount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (tallySubmitting) "..." else "+ Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                } else {
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
                            // Two valid states: SKU + qty both filled in (a normal
                            // count), or both left blank (the location was checked
                            // and found empty). Anything half-filled is blocked so
                            // a stray tap can't submit a bogus qty=0-for-a-real-SKU
                            // line by accident.
                            val markingEmpty = scannedInput.isBlank() && countedInput.isBlank()
                            val isValid = locationInput.isNotBlank() &&
                                    (markingEmpty || (scannedInput.isNotBlank() && countedInput.toIntOrNull() != null))

                            Button(
                                onClick = {
                                    if (checkForDuplicateBeforeSubmit()) return@Button
                                    val counted = if (markingEmpty) 0 else (countedInput.toIntOrNull() ?: return@Button)
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
                                                    originalLocationCode = effectiveOriginalLocation,
                                                    originalSku = effectiveOriginalSku
                                                )
                                            )
                                            submitting = false
                                            if (response.isSuccessful) {
                                                val body = response.body()
                                                lastResult = if (body?.itemSku != null) {
                                                    "Saved ${body.itemSku} at ${body.locationCode}: $counted"
                                                } else {
                                                    "Marked ${body?.locationCode ?: locationInput.trim()} as empty"
                                                }
                                                lastCommittedLocation = locationInput.trim()
                                                lastCommittedSku = scannedInput.trim()
                                                // Confirm with a pop-up; its OK closes this dialog
                                                // (so the same location can't be entered twice) and
                                                // onDismiss refreshes the list behind it.
                                                successMessage = if (body?.itemSku != null) {
                                                    "Location: ${body.locationCode}\nSKU: ${body.itemSku}\nQty: $counted"
                                                } else {
                                                    "Location: ${body?.locationCode ?: locationInput.trim()}\nMarked as empty"
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
                                enabled = isValid && !submitting
                            ) {
                                Text(if (submitting) "Saving..." else if (markingEmpty) "Mark Empty" else "Save")
                            }
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

    // Blocks a brand-new entry from silently duplicating a location that
    // already has data — see duplicateCheckTriggered above. "OK" closes
    // the whole count dialog (not just this alert), since the intended
    // next step is tapping the existing card in the list, not continuing
    // to type in this one.
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

    duplicateWarningMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { duplicateWarningMessage = null },
            title = { Text("Location already counted") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    duplicateWarningMessage = null
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        )
    }
}
// Shows every individual commit to one location+SKU line — a full Save,
// an empty-confirmation, or (most commonly on a FLOOR location) a single
// +/- tally tap — newest first, with who made it and what it changed the
// total to. This is "what numbers were actually typed in", separate from
// just the current total shown on the report.
@Composable
fun CountHistoryDialog(
    opnameNumber: String,
    locationCode: String,
    sku: String,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<OpnameCountHistoryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.api.getOpnameCountHistory(opnameNumber, locationCode, sku)
            loading = false
            if (response.isSuccessful) {
                entries = response.body() ?: emptyList()
            } else {
                error = "Failed to load history"
            }
        } catch (e: Exception) {
            loading = false
            error = "Couldn't reach server: ${e.message}"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("History", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (sku.isNotBlank()) "$locationCode · $sku" else "$locationCode (empty confirmation)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (loading) {
                    CircularProgressIndicator()
                } else if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else if (entries.isEmpty()) {
                    Text(
                        "No changes recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    if (entry.delta > 0) "+${entry.delta}" else "${entry.delta}",
                                    fontWeight = FontWeight.Medium,
                                    color = if (entry.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "by ${entry.username ?: "unknown"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("→ ${entry.resultingTotal}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    entry.createdAt.replace("T", " ").take(16),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}