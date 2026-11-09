package com.kevin.wmsscanner.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.PickToRequest
import com.kevin.wmsscanner.network.initialCode
import com.kevin.wmsscanner.ui.components.LocationPicker
import com.kevin.wmsscanner.ui.components.pickerOptions
import com.kevin.wmsscanner.ui.components.rememberLocationPurposes
import kotlinx.coroutines.launch
import com.kevin.wmsscanner.util.SoDisplay

// Picking step 2 (after choosing the SO): where this SO's picks go — a
// staging location. The choice is remembered for the SO on the server, so
// once it's been chosen, opening the SO again goes straight to the picking
// list with it (change it with Edit there, which opens this screen with
// edit = true). With only one staging location there's nothing to choose.
@Composable
fun PickToScreen(navController: NavHostController, soNumber: String, edit: Boolean = false) {
    val purposes = rememberLocationPurposes()
    var stagingCode by remember { mutableStateOf("") }
    // null until the server answers what this SO was last picked to
    var savedCode by remember { mutableStateOf<String?>(null) }
    var savedLoaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun open(code: String) {
        navController.navigate("picking_so_list/$soNumber?staging=${Uri.encode(code)}") {
            // Back from the picking list returns to the SO list, not here
            // (nor to the list this Edit was opened from).
            popUpTo(if (edit) "picking_so_list/{soNumber}?staging={staging}" else "picking_to/{soNumber}?edit={edit}") {
                inclusive = true
            }
        }
    }

    suspend fun saveAndOpen(code: String) {
        saving = true
        error = null
        try {
            val response = NetworkModule.api.setPickTo(soNumber, PickToRequest(code))
            saving = false
            if (response.isSuccessful) {
                open(code)
            } else {
                error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
            }
        } catch (e: Exception) {
            saving = false
            error = "Couldn't reach server: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.api.getPickTo(soNumber)
            if (response.isSuccessful) savedCode = response.body()?.code
        } catch (e: Exception) {
            // Not fatal: just ask for the location.
        }
        savedLoaded = true
    }

    LaunchedEffect(purposes.data, savedLoaded) {
        val data = purposes.data ?: return@LaunchedEffect
        if (!savedLoaded) return@LaunchedEffect
        val staging = data.staging.locations
        val saved = savedCode?.takeIf { code -> staging.any { it.code.equals(code, ignoreCase = true) } }

        if (stagingCode.isBlank()) stagingCode = saved ?: data.staging.initialCode()

        // Already chosen for this SO: straight to the picking list.
        if (!edit && saved != null) {
            open(saved)
        } else if (!edit && staging.size == 1) {
            saveAndOpen(staging[0].code)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(SoDisplay.header(soNumber), style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pilih lokasi staging untuk picking",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        val data = purposes.data
        when {
            purposes.error != null -> Text(purposes.error!!, color = MaterialTheme.colorScheme.error)
            data == null || !savedLoaded -> CircularProgressIndicator()
            data.staging.locations.isEmpty() -> Text(
                "No staging location is set up — ask an admin to set one on the web Locations page.",
                color = MaterialTheme.colorScheme.error
            )
            else -> {
                LocationPicker(
                    label = "Pick to",
                    options = data.staging.pickerOptions(),
                    value = stagingCode,
                    onValueChange = { stagingCode = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { scope.launch { saveAndOpen(stagingCode) } },
                    enabled = stagingCode.isNotBlank() && !saving,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (saving) "Saving..." else "Lanjut")
                }
            }
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
