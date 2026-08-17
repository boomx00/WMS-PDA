package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.BuildConfig
import com.kevin.wmsscanner.SessionManager
import com.kevin.wmsscanner.ui.components.MenuButton

@Composable
fun HomeScreen(navController: NavHostController) {
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
        Spacer(modifier = Modifier.height(40.dp))

        MenuButton("INBOUND") { navController.navigate("inbound") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("OUTBOUND") { navController.navigate("outbound_menu") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("MOVE") { navController.navigate("move_menu") }
        Spacer(modifier = Modifier.height(12.dp))
//        MenuButton("CONFIRM INBOUND") { navController.navigate("confirm_inbound") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("Stock Opname") {navController.navigate("stock_opname") }

        OutlinedButton(
            onClick = { navController.navigate("logout") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}