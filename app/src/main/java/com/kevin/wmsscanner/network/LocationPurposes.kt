package com.kevin.wmsscanner.network

import com.kevin.wmsscanner.SessionManager

// The receiving and staging locations the PDA's location pickers offer
// (set as "Used for: Receiving / Staging" on the web Locations page), which
// one each picker starts on (Settings → Locations & Marking), and whether
// order marking is on.
//   receiving: Inbound, Move Floor → Rack
//   staging:   Picking, Ship, Cek Outbound
//
// Loaded fresh each time a screen with a picker opens, so changes on the
// web (Used for, defaults, marking) show up the next time the screen opens.
// Only the locations of the warehouse chosen after login (SessionManager).

data class PurposeLocation(val code: String, val name: String, val type: String)
data class PurposeGroup(val locations: List<PurposeLocation>, val defaultCode: String?)
data class PurposeWarehouse(val id: Int, val name: String)
data class LocationPurposesResponse(
    // Null from servers older than warehouses.
    val warehouse: PurposeWarehouse? = null,
    val receiving: PurposeGroup,
    val staging: PurposeGroup,
    // Null from servers older than the marking switch: marking was always on.
    val markingEnabled: Boolean? = null
)

object LocationPurposes {
    // Throws on network errors, like the other API calls.
    suspend fun load(): LocationPurposesResponse {
        var response = NetworkModule.api.getLocationPurposes(SessionManager.warehouseId)
        // The chosen warehouse was deleted on the web: fall back to the
        // default warehouse.
        if (response.code() == 404 && SessionManager.warehouseId != null) {
            SessionManager.setWarehouse(null, null, SessionManager.warehouseCount)
            response = NetworkModule.api.getLocationPurposes(null)
        }
        return when {
            response.isSuccessful && response.body() != null -> response.body()!!
            // Older server without this endpoint (404/405): one receiving
            // and one staging location, by their old fixed codes.
            response.code() == 404 || response.code() == 405 -> LocationPurposesResponse(
                receiving = PurposeGroup(listOf(PurposeLocation("FLOOR", "Floor", "FLOOR")), "FLOOR"),
                staging = PurposeGroup(listOf(PurposeLocation("OUTBOUND_WH", "Outbound WH", "OUTBOUND_WH")), "OUTBOUND_WH"),
                markingEnabled = true
            )
            else -> throw IllegalStateException("Couldn't load locations (${response.code()})")
        }
    }
}

// The code a picker starts on: the default from Settings, else the first
// location (or "" when there are none).
fun PurposeGroup.initialCode(): String = defaultCode ?: locations.firstOrNull()?.code ?: ""

fun LocationPurposesResponse.marking(): Boolean = markingEnabled ?: true
