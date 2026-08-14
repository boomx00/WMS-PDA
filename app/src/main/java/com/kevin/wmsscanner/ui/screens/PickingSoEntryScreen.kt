package com.kevin.wmsscanner.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
fun PickingSoEntryScreen(navController: NavHostController) {
    OpenSoListScreen(navController, "Picking (by SO)", "picking_so_list")
}