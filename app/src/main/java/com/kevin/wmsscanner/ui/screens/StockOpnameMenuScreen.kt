package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// Entry point for Stock Opname: pick whether to count by location (the
// existing flow) or by SKU (per-SKU sessions, SOSKU-...).
@Composable
fun StockOpnameMenuScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Stock Opname", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose how you want to count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { navController.navigate("stock_opname_location") },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("By Location")
        }
        Text(
            "Count location by location — scan a location, then the SKU and quantity found there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { navController.navigate("stock_opname_sku") },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("By SKU")
        }
        Text(
            "Count one SKU — its system quantity is locked, then enter the quantity per location.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}