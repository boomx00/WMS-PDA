package com.kevin.wmsscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Logging everything since we have no official iData docs — this
        // shows the EXACT action + extra names in Logcat when you press
        // the trigger, so we can confirm or correct our guess below.
        Log.d("ScanReceiver", "Received action: ${intent.action}")
        intent.extras?.keySet()?.forEach { key ->
            Log.d("ScanReceiver", "  extra: $key = ${intent.extras?.get(key)}")
        }

        val code = intent.getStringExtra("value")
        if (!code.isNullOrBlank()) {
            CoroutineScope(Dispatchers.Main).launch {
                ScanBus.emit(code)
            }
        }
    }
}