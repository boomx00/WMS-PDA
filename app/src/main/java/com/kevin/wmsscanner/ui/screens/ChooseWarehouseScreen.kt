package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.SessionManager
import com.kevin.wmsscanner.network.WarehouseResponse
import com.kevin.wmsscanner.network.Warehouses
import kotlinx.coroutines.launch

// Which warehouse (parent location) to work in — Finished Goods, Raw
// Material, ... The location pickers (Inbound, Pick to, Ship from, ...) then
// only offer that warehouse's locations and start on its defaults.
//
// After login (change = false) it's only shown when there's more than one
// warehouse; with one (or an older server without warehouses) it goes
// straight to the home screen. From the home screen's "Change" (change =
// true) it always shows the list, and Back keeps the current choice.
@Composable
fun ChooseWarehouseScreen(navController: NavHostController, change: Boolean) {
    var warehouses by remember { mutableStateOf<List<WarehouseResponse>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun goHome() {
        if (change) {
            navController.popBackStack()
        } else {
            navController.navigate("home") { popUpTo(0) { inclusive = true } }
        }
    }

    fun choose(warehouse: WarehouseResponse?, count: Int) {
        SessionManager.setWarehouse(warehouse?.id, warehouse?.name, count)
        goHome()
    }

    suspend fun load() {
        error = null
        try {
            val list = Warehouses.load()
            if (list.size <= 1 && !change) {
                choose(list.firstOrNull(), list.size)
            } else {
                warehouses = list
            }
        } catch (e: Exception) {
            // Offline right after login: carry on with the server's default
            // warehouse rather than blocking the scanner.
            if (!change) choose(null, 0) else error = "Couldn't load warehouses: ${e.message}"
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Choose warehouse", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Inbound, picking, shipping and moves use this warehouse's locations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        val list = warehouses
        when {
            error != null -> {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { scope.launch { load() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Retry") }
            }
            list == null -> CircularProgressIndicator()
            else -> list.forEach { warehouse ->
                // The current one (when changing) is the filled button.
                val current = warehouse.id == SessionManager.warehouseId
                val buttonModifier = Modifier.fillMaxWidth().height(64.dp)
                if (current) {
                    Button(onClick = { choose(warehouse, list.size) }, modifier = buttonModifier) {
                        Text("${warehouse.name} ✓", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    OutlinedButton(onClick = { choose(warehouse, list.size) }, modifier = buttonModifier) {
                        Text(warehouse.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (change) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Back") }
        }
    }
}
