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
        // After login (via update_check), and from Home's "Change".
        composable(
            "choose_warehouse?change={change}",
            arguments = listOf(navArgument("change") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            ChooseWarehouseScreen(navController, backStackEntry.arguments?.getBoolean("change") ?: false)
        }
        composable("inbound") { InboundScreen(navController) }
        composable("outbound_menu") { OutboundMenuScreen(navController) }
        composable("picking") { PickingScreen(navController) }
        composable("move_menu") { MoveMenuScreen(navController) }
        composable("move_in") { MoveInScreen(navController) }
        composable("move_out") { MoveOutScreen(navController) }
        composable("move_rack_to_rack") { MoveRackToRackScreen(navController) }
        composable("confirm_inbound") { ConfirmInboundScreen(navController) }
        composable("logout") { LogoutScreen(navController) }
        composable("ship") { ShipScreen(navController) }
        composable("update_check") { UpdateCheckScreen(navController) }
        composable("check_so") { CheckSoScreen(navController) }
        composable("shipping_sessions") { ShippingSessionsScreen(navController) }
        composable("picking_v2") { PickingV2Screen(navController) }
        composable("shipping_sessions_v2") { ShippingSessionsV2Screen(navController) }
        composable("picking_so_entry") { PickingSoEntryScreen(navController) }
        composable("stock_opname") { StockOpnameMenuScreen(navController) }
        composable("stock_opname_location") { StockOpnameScreen(navController) }
        composable("stock_opname_sku") { StockOpnameSkuScreen(navController) }
        composable(
            "picking_to/{soNumber}?edit={edit}",
            arguments = listOf(
                navArgument("soNumber") { type = NavType.StringType },
                navArgument("edit") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            val edit = backStackEntry.arguments?.getBoolean("edit") ?: false
            PickToScreen(navController, soNumber, edit)
        }
        composable(
            "picking_so_list/{soNumber}?staging={staging}",
            arguments = listOf(
                navArgument("soNumber") { type = NavType.StringType },
                navArgument("staging") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            val staging = backStackEntry.arguments?.getString("staging") ?: ""
            SoPickingListScreen(navController, soNumber, staging)
        }
        composable(
            "ship_so_items/{soNumber}",
            arguments = listOf(navArgument("soNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            ShipSoItemListScreen(navController, soNumber)
        }
        composable(
            "ship_session_v2/{soNumber}/{expectedSku}",
            arguments = listOf(
                navArgument("soNumber") { type = NavType.StringType },
                navArgument("expectedSku") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            val expectedSku = backStackEntry.arguments?.getString("expectedSku") ?: ""
            ShipSessionV2Screen(navController, soNumber, expectedSku)
        }
        composable(
            "ship_session/{soNumber}",
            arguments = listOf(navArgument("soNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            ShipSessionScreen(navController, soNumber)
        }

        composable("move_v2") { MoveV2Screen(navController) }

        composable("move_in_v2") { MoveInV2Screen(navController) }
        composable("outbound_wh_contents") { OutboundWhContentsScreen(navController) }
        composable(
            "tambahan_picking/{soNumber}?staging={staging}",
            arguments = listOf(
                navArgument("soNumber") { type = NavType.StringType },
                navArgument("staging") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            val staging = backStackEntry.arguments?.getString("staging") ?: ""
            TambahanPickingScreen(navController, soNumber, staging)
        }
        composable(
            "tambahan_ship/{soNumber}/{expectedSku}",
            arguments = listOf(
                navArgument("soNumber") { type = NavType.StringType },
                navArgument("expectedSku") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val soNumber = backStackEntry.arguments?.getString("soNumber") ?: ""
            val expectedSku = backStackEntry.arguments?.getString("expectedSku") ?: ""
            TambahanShipScreen(navController, soNumber, expectedSku)
        }
    }

}