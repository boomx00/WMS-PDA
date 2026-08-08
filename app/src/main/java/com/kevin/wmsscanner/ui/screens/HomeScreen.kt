package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ui.components.MenuButton

@Composable
fun HomeScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "WMS Scanner", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(40.dp))

        MenuButton("IN Handpallet") { navController.navigate("inbound") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("OUTBOUND") { navController.navigate("outbound") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("MOVE") { navController.navigate("move_menu") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("CONFIRM SISA") { navController.navigate("confirm_inbound") }
    }
}