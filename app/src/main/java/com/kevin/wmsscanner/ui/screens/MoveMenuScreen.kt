package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.ui.components.MenuButton

@Composable
fun MoveMenuScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Move", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(40.dp))

        MenuButton("MOVE IN (Floor \u2192 Rack)") { navController.navigate("move_in") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("MOVE OUT (Rack \u2192 Floor)") { navController.navigate("move_out") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("RACK \u2192 RACK") { navController.navigate("move_rack_to_rack") }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to menu")
        }
    }
}