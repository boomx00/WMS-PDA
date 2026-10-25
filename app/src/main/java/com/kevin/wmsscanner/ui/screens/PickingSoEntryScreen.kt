package com.kevin.wmsscanner.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun PickingSoEntryScreen(navController: NavHostController) {
    // Choose the SO, then where to pick to (PickToScreen), then the list.
    OpenSoListScreen(navController, "Picking (by SO)", "picking_to", forPicking = true)
}