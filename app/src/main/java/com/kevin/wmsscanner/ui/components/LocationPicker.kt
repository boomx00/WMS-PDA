package com.kevin.wmsscanner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kevin.wmsscanner.network.LocationPurposes
import com.kevin.wmsscanner.network.LocationPurposesResponse
import com.kevin.wmsscanner.network.PurposeGroup
import com.kevin.wmsscanner.network.PurposeLocation
import com.kevin.wmsscanner.network.ShipStagingLocation

// One option in a LocationPicker: the location code, plus an optional
// detail shown next to it in the list (e.g. "20 for SO-1").
data class PickerOption(val code: String, val detail: String? = null)

// A location field with a dropdown of the allowed locations (receiving or
// staging, from the web). The dropdown only shows when there's more than
// one to choose from; with one, the field just shows it. allowTyping lets
// the worker type or scan a code instead of picking from the list (the
// server still checks it's an allowed location).
@Composable
fun LocationPicker(
    label: String,
    options: List<PickerOption>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowTyping: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val hasChoice = options.size > 1
    val selectedDetail = options.firstOrNull { it.code.equals(value, ignoreCase = true) }?.detail

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (allowTyping) onValueChange(it) },
            readOnly = !allowTyping,
            enabled = enabled,
            label = { Text(label) },
            supportingText = if (selectedDetail != null) {
                { Text(selectedDetail) }
            } else {
                null
            },
            singleLine = true,
            trailingIcon = if (hasChoice && enabled) {
                { TextButton(onClick = { expanded = true }) { Text("▼") } }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option.detail != null) "${option.code} — ${option.detail}" else option.code) },
                    onClick = {
                        onValueChange(option.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

// The receiving / staging locations for a screen's pickers, loaded once
// when the screen opens.
class PurposesState {
    var data by mutableStateOf<LocationPurposesResponse?>(null)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun rememberLocationPurposes(): PurposesState {
    val state = remember { PurposesState() }
    LaunchedEffect(Unit) {
        try {
            state.data = LocationPurposes.load()
        } catch (e: Exception) {
            state.error = "Couldn't load locations: ${e.message}"
        }
    }
    return state
}

fun PurposeGroup.pickerOptions(): List<PickerOption> = locations.map { PickerOption(it.code) }

// Ship screens: every staging location, those holding stock this order can
// ship listed first with the amount ("20 for SO-1 · from A.1.1", or "20 in
// stock" with marking off). The others stay listed so ecer stock there can
// still be claimed.
fun shipPickerOptions(
    shippable: List<ShipStagingLocation>,
    allStaging: List<PurposeLocation>,
    markingOn: Boolean,
    orderLabel: String
): List<PickerOption> {
    val withStock = shippable.map { loc ->
        val amount = if (markingOn) "${loc.quantity} for $orderLabel" else "${loc.quantity} in stock"
        val from = loc.pickedFrom.orEmpty()
        PickerOption(loc.code, if (from.isEmpty()) amount else "$amount · from ${from.joinToString(", ")}")
    }
    val others = allStaging
        .filter { s -> shippable.none { it.code.equals(s.code, ignoreCase = true) } }
        .map { PickerOption(it.code) }
    return withStock + others
}
