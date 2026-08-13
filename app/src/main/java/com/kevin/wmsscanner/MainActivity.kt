package com.kevin.wmsscanner
import android.os.Build
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kevin.wmsscanner.ui.screens.*
import androidx.navigation.navArgument
import androidx.navigation.NavType


class MainActivity : ComponentActivity() {
    private val scanReceiver = ScanReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WmsApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("android.intent.action.SCANRESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(scanReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(scanReceiver)
    }
}

@Composable
fun WmsApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("inbound") { InboundScreen(navController) }
        composable("outbound_menu") { OutboundMenuScreen(navController) }
        composable("picking") { PickingScreen(navController) }
        composable("move_menu") { MoveMenuScreen(navController) }
        composable("move_in") { MoveInScreen(navController) }
        composable("move_out") { MoveOutScreen(navController) }
        composable("move_rack_to_rack") { MoveRackToRackScreen(navController) }
        composable("confirm_inbound") { ConfirmInboundScreen(navController) }
        composable("logout") { LogoutScreen(navController) }
        composable("shipping_sessions") { ShippingSessionsScreen(navController) }
        composable(
            "ship_session/{soNumber}",
            arguments = listOf(navArgument("soNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            ShipSessionScreen(navController, soNumber)
        }
        composable("update_check") { UpdateCheckScreen(navController) }
        composable("check_so") { CheckSoScreen(navController) }
    }
}