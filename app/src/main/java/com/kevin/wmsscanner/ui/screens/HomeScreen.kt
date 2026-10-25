package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.BuildConfig
import com.kevin.wmsscanner.SessionManager
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.ui.components.MenuButton

@Composable
fun HomeScreen(navController: NavHostController) {
    // Pick up permission changes made on the web without logging out.
    // On failure, keep what we already have — the server still enforces.
    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.api.getPdaAccess()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                SessionManager.updatePermissions(body.pdaPermissions)
            }
        } catch (e: Exception) {
            // offline / server down — ignore
        }
    }

    val canInbound = SessionManager.can("pda.inbound")
    val canOutbound = SessionManager.canAny(
        "pda.pick", "pda.pickTambahan", "pda.ship", "pda.shipTambahan", "pda.outboundWh", "pda.checkSo"
    )
    val canMove = SessionManager.canAny("pda.moveIn", "pda.move")
    val canOpname = SessionManager.can("pda.stockOpname")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "WMS Scanner", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Halo ${SessionManager.username ?: ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // The warehouse chosen after login; only when there's more than one.
        if (SessionManager.warehouseCount > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Warehouse: ${SessionManager.warehouseName ?: "Default"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { navController.navigate("choose_warehouse?change=true") }) {
                    Text("Change")
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))

        if (canInbound) {
            MenuButton("INBOUND") { navController.navigate("inbound") }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (canOutbound) {
            MenuButton("OUTBOUND") { navController.navigate("outbound_menu") }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (canMove) {
            MenuButton("MOVE") { navController.navigate("move_menu") }
            Spacer(modifier = Modifier.height(12.dp))
        }
//        MenuButton("CONFIRM INBOUND") { navController.navigate("confirm_inbound") }
        if (canOpname) {
            MenuButton("Stock Opname") { navController.navigate("stock_opname") }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!canInbound && !canOutbound && !canMove && !canOpname) {
            Text(
                "Your role has no PDA menus. Ask a Manager to set them on Permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = { navController.navigate("logout") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
