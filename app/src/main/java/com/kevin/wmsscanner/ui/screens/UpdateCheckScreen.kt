package com.kevin.wmsscanner.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.kevin.wmsscanner.BuildConfig
import com.kevin.wmsscanner.network.AppVersionResponse
import com.kevin.wmsscanner.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun UpdateCheckScreen(navController: NavHostController) {
    var checking by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<AppVersionResponse?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) } // 0f..1f
    var downloadedMb by remember { mutableStateOf(0.0) }
    var totalMb by remember { mutableStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val response = NetworkModule.api.getAppVersion()
            if (response.isSuccessful) {
                val info = response.body()
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                    updateInfo = info
                } else {
                    navController.navigate("home") { popUpTo(0) { inclusive = true } }
                }
            } else {
                navController.navigate("home") { popUpTo(0) { inclusive = true } }
            }
        } catch (e: Exception) {
            navController.navigate("home") { popUpTo(0) { inclusive = true } }
        }
        checking = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (checking) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Checking for updates...")
        } else if (updateInfo != null) {
            Text("Update Available", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version ${updateInfo!!.versionName}")
            if (!updateInfo!!.releaseNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(updateInfo!!.releaseNotes!!, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(32.dp))

            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (totalMb > 0)
                        "%.1f MB / %.1f MB (%d%%)".format(downloadedMb, totalMb, (progress * 100).toInt())
                    else
                        "%.1f MB downloaded".format(downloadedMb),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    downloading = true
                    progress = 0f
                    downloadedMb = 0.0
                    totalMb = 0.0
                    error = null
                    scope.launch {
                        try {
                            val apkFile = withContext(Dispatchers.IO) {
                                val file = File(
                                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                                    "wms-update.apk"
                                )

                                val connection = URL(updateInfo!!.apkUrl).openConnection() as HttpURLConnection
                                connection.connectTimeout = 15000
                                connection.readTimeout = 15000
                                connection.instanceFollowRedirects = true
                                connection.connect()

                                if (connection.responseCode !in 200..299) {
                                    throw Exception("Server returned ${connection.responseCode} ${connection.responseMessage}")
                                }

                                val contentLength = connection.contentLength // -1 if unknown
                                var bytesRead = 0L

                                connection.inputStream.use { input ->
                                    file.outputStream().use { output ->
                                        val buffer = ByteArray(8 * 1024)
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            output.write(buffer, 0, read)
                                            bytesRead += read

                                            withContext(Dispatchers.Main) {
                                                downloadedMb = bytesRead / (1024.0 * 1024.0)
                                                if (contentLength > 0) {
                                                    totalMb = contentLength / (1024.0 * 1024.0)
                                                    progress = (bytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                                                }
                                            }
                                        }
                                    }
                                }

                                file
                            }

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                apkFile
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            downloading = false
                            error = "Download failed: ${e.message}"
                        }
                    }
                },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (downloading) "Downloading..." else "Download & Install")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { navController.navigate("home") { popUpTo(0) { inclusive = true } } },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}