package com.kevin.wmsscanner.network

// Which locations hold the Receiving and Outbound staging roles (set on the
// web Locations page). Move In / Move Out go through the Receiving
// location, Picking / Ship / Outbound contents through the Outbound one —
// instead of the fixed codes "FLOOR" and "OUTBOUND_WH".
//
// Cached for a few minutes so screens don't ask on every scan.

data class RoleLocation(val code: String, val name: String, val type: String)
data class LocationRolesResponse(val receiving: RoleLocation?, val outbound: RoleLocation?)

object LocationRoles {
    const val MISSING_RECEIVING = "No Receiving location is set — assign one on the web Locations page."
    const val MISSING_OUTBOUND = "No Outbound staging location is set — assign one on the web Locations page."

    private const val CACHE_MS = 5 * 60 * 1000L
    private var cached: LocationRolesResponse? = null
    private var cachedAt = 0L

    // Receiving location code, or null if no location holds the role.
    // Throws on network errors, like the other API calls.
    suspend fun receivingCode(): String? = load().receiving?.code

    // Outbound staging location code, or null if no location holds the role.
    suspend fun outboundCode(): String? = load().outbound?.code

    fun clear() {
        cached = null
    }

    private suspend fun load(): LocationRolesResponse {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_MS) return it }

        val response = NetworkModule.api.getLocationRoles()
        val roles = when {
            response.isSuccessful -> response.body() ?: LocationRolesResponse(null, null)
            // Older server without this endpoint (404/405): the codes it
            // still uses.
            response.code() == 404 || response.code() == 405 -> LocationRolesResponse(
                receiving = RoleLocation("FLOOR", "Floor", "FLOOR"),
                outbound = RoleLocation("OUTBOUND_WH", "Outbound WH", "OUTBOUND_WH")
            )
            else -> throw IllegalStateException("Couldn't load location roles (${response.code()})")
        }
        cached = roles
        cachedAt = now
        return roles
    }
}

// How to treat a looked-up location, from its type's rules. Falls back to
// the old type-code checks when talking to a server that doesn't send the
// rules yet.

// Only one SKU may be stored there (e.g. a rack cell).
fun LocationStockLookupResponse.isSingleSku(): Boolean =
    allowsMultipleSku?.not() ?: (locationType == "RACK")

// Pickable and holds many SKUs (e.g. the floor): SKUs are typed/scanned
// rather than picked from a list, and Default Picking applies.
fun LocationStockLookupResponse.isMultiSkuPickArea(): Boolean =
    if (allowsMultipleSku != null && pickable != null) allowsMultipleSku && pickable
    else locationType == "FLOOR"
