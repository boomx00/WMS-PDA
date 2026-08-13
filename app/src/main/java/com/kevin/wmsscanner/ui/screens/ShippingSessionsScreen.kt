package com.kevin.wmsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.network.AssignCheckerRequest
import com.kevin.wmsscanner.network.NetworkModule
import com.kevin.wmsscanner.network.SessionSummary
import kotlinx.coroutines.launch

@Composable
fun ShippingSessionsScreen(navController: NavHostController) {
    var soNumberInput by remember { mutableStateOf("") }
    var assigning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var loadingSessions by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun refreshSessions() {
        loadingSessions = true
        try {
            val response = NetworkModule.api.getMySessions()
            if (response.isSuccessful) {
                sessions = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
        }
        loadingSessions = false
    }

    LaunchedEffect(Unit) {
        refreshSessions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Shipping (Session)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Assign yourself to an SO", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = soNumberInput,
            onValueChange = {
                soNumberInput = it
                error = null
            },
            label = { Text("Scan or type SO number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                assigning = true
                error = null
                scope.launch {
                    try {
                        val response = NetworkModule.api.assignChecker(
                            AssignCheckerRequest(soNumberInput.trim())
                        )
                        assigning = false
                        if (response.isSuccessful) {
                            val assignedSo = soNumberInput.trim()
                            soNumberInput = ""
                            refreshSessions()
                            navController.navigate("ship_session/$assignedSo")
                        } else {
                            error = "Failed: ${response.errorBody()?.string() ?: "unknown error"}"
                        }
                    } catch (e: Exception) {
                        assigning = false
                        error = "Couldn't reach server: ${e.message}"
                    }
                }
            },
            enabled = !assigning && soNumberInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (assigning) "Assigning..." else "Assign Checker")
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Your recent SOs", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (loadingSessions) {
            CircularProgressIndicator()
        } else if (sessions.isEmpty()) {
            Text(
                "None yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sessions.forEach { s ->
                SessionRow(
                    session = s,
                    onClick = {
                        if (s.status != "DONE") {
                            navController.navigate("ship_session/${s.soNumber}")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    val (label, color) = when (session.status) {
        "DONE" -> "DONE" to MaterialTheme.colorScheme.primary
        "IN_PROGRESS" -> "IN PROGRESS" to MaterialTheme.colorScheme.tertiary
        else -> "PENDING" to MaterialTheme.colorScheme.error
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(session.soNumber, fontWeight = FontWeight.Medium)
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}