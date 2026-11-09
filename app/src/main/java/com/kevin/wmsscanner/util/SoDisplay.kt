package com.kevin.wmsscanner.util

import androidx.compose.runtime.mutableStateMapOf

// How a sales order is shown on the PDA: "<doc type> - <doc no>", e.g.
// "SO - 202608000162". The server sends it as `soDisplay` on the SO lists,
// lookups and pick summary; screens that only get the SO number from their
// route (picking / shipping / Tambahan headers) read it from here, since
// the driver always opened them from a list that already filled it in.
// Falls back to the plain SO number (older server, SO with no type).
object SoDisplay {
    // State-backed, so a header recomposes when its SO's text arrives.
    private val bySoNumber = mutableStateMapOf<String, String>()

    fun remember(soNumber: String, soDisplay: String?) {
        if (!soDisplay.isNullOrBlank()) bySoNumber[soNumber] = soDisplay
    }

    // "SO - 202608000162", or the plain SO number if not known.
    fun of(soNumber: String): String = bySoNumber[soNumber] ?: soNumber

    // For headers that used to read "SO: <number>": the type replaces "SO:".
    fun header(soNumber: String): String = bySoNumber[soNumber] ?: "SO: $soNumber"
}
