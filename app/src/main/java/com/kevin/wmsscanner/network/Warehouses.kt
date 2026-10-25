package com.kevin.wmsscanner.network

// Warehouses (parent locations, e.g. Finished Goods and Raw Material), set
// up on the web Locations page → Warehouses. Each has its own receiving /
// staging locations and defaults, so the PDA works in one at a time: it's
// chosen right after login when there's more than one (see
// ChooseWarehouseScreen) and kept in SessionManager.

data class WarehouseResponse(
    val id: Int,
    val name: String,
    val locationCount: Int? = null,
    // Null from servers before warehouses could be deactivated.
    val isActive: Boolean? = null
)

object Warehouses {
    // Every ACTIVE warehouse in display order (the first is the default).
    // Inactive ones aren't offered. Empty for older servers without
    // warehouses. Throws on network errors.
    suspend fun load(): List<WarehouseResponse> {
        val response = NetworkModule.api.getWarehouses()
        return when {
            response.isSuccessful -> response.body().orEmpty().filter { it.isActive != false }
            response.code() == 404 || response.code() == 405 -> emptyList()
            else -> throw IllegalStateException("Couldn't load warehouses (${response.code()})")
        }
    }
}
