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
fun OutboundMenuScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Outbound", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(40.dp))
        MenuButton("PICKING (v2 \u2014 by SO)") { navController.navigate("picking_so_entry") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("SHIP SESSION (v2)") { navController.navigate("shipping_sessions_v2") }
//        MenuButton("PICKING (Rack/Floor \u2192 Outbound WH)") { navController.navigate("picking") }
//        Spacer(modifier = Modifier.height(12.dp))
//        MenuButton("SHIP (Outbound WH \u2192 Gone)") { navController.navigate("ship") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("CEK OUTBOUND WH") { navController.navigate("outbound_wh_contents") }
        Spacer(modifier = Modifier.height(12.dp))
        MenuButton("CHECK SO") { navController.navigate("check_so") }
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to menu")
        }
    }
}