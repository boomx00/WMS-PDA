package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.OpenSalesOrder

// Shared by both Picking (by SO) and Shipping — a searchable list of every
// open SO, sorted IN_PROGRESS first, then PENDING. Tapping one navigates
// to whatever destination route pattern the caller provides.
@Composable
fun OpenSoListScreen(
    navController: NavHostController,
    title: String,
    destinationRoutePrefix: String // e.g. "picking_so_list" or "ship_session_v2"
) {
    var search by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf<List<OpenSalesOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.api.getOpenSalesOrders()
            if (response.isSuccessful) {
                orders = response.body() ?: emptyList()
            } else {
                error = "Failed to load sales orders"
            }
        } catch (e: Exception) {
            error = "Couldn't reach server: ${e.message}"
        }
        loading = false
    }

    val filtered = orders.filter { it.soNumber.contains(search.trim(), ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search SO number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        } else if (filtered.isEmpty()) {
            Text(
                "No open sales orders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filtered.forEach { order ->
                val (label, color) = when (order.status) {
                    "IN_PROGRESS" -> "IN PROGRESS" to MaterialTheme.colorScheme.tertiary
                    else -> "PENDING" to MaterialTheme.colorScheme.error
                }
                Card(
                    onClick = { navController.navigate("$destinationRoutePrefix/${order.soNumber}") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(order.soNumber, fontWeight = FontWeight.Medium)
                        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
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