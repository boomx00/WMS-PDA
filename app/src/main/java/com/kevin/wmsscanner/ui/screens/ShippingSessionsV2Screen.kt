package com.kevin.wmsscanner.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun ShippingSessionsV2Screen(navController: NavHostController) {
    OpenSoListScreen(navController, "Shipping (v2)", "ship_session_v2")
}