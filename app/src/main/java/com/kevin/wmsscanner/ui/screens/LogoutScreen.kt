package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.SessionManager
import com.kevin.wmsscanner.network.NetworkModule
import kotlinx.coroutines.launch

@Composable
fun LogoutScreen(navController: NavHostController) {
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Log Out?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You'll need to sign in again to continue scanning.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                loading = true
                scope.launch {
                    try {
                        NetworkModule.api.logout()
                    } catch (e: Exception) {
                        // Even if the network call fails, still clear local
                        // session state below — no reason to trap the user
                        // logged in on-device just because the request failed.
                    }
                    NetworkModule.clearCookies()
                    SessionManager.clear()
                    loading = false
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (loading) "Logging out..." else "Confirm Log Out")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}