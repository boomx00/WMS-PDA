package com.kevin.wmsscanner.ui.components

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kevin.wmsscanner.ScanBus
import kotlinx.coroutines.launch

@Composable
fun CameraScanButton(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scope.launch {
                ScanBus.emit(result.contents)
            }
        }
    }

    OutlinedButton(
        onClick = {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(
                    ScanOptions.CODE_128,
                    ScanOptions.QR_CODE
                )
                setOrientationLocked(false)
                setBeepEnabled(true)
                setPrompt("Scan barcode or QR code")
            }
            scanLauncher.launch(options)
        },
        modifier = modifier
    ) {
        Text("Scan with Camera")
    }
}