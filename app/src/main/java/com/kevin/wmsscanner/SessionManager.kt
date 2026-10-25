package com.kevin.wmsscanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SessionManager {
    var isLoggedIn: Boolean = false
    var username: String? = null
    var userId: Int? = null

    // PDA features this user's role may use, e.g. "pda.ship". Set on login
    // and refreshed each time the home screen opens. Compose state, so menus
    // redraw when it changes. Keys match lib/pdaFeatures.ts on the server.
    // UI only — the server enforces the same rules on every scan.
    var pdaPermissions by mutableStateOf<Set<String>>(emptySet())
        private set

    // The warehouse (parent location) this session works in, chosen after
    // login when there's more than one. Null = the server's default
    // warehouse (single warehouse, or an older server). Compose state, so
    // the home screen redraws when it changes.
    var warehouseId by mutableStateOf<Int?>(null)
        private set
    var warehouseName by mutableStateOf<String?>(null)
        private set
    // How many warehouses there were at login — "Change" only shows when
    // there's more than one.
    var warehouseCount by mutableStateOf(0)
        private set

    fun setWarehouse(id: Int?, name: String?, count: Int) {
        warehouseId = id
        warehouseName = name
        warehouseCount = count
    }

    fun setSession(id: Int, name: String, permissions: List<String> = emptyList()) {
        userId = id
        username = name
        isLoggedIn = true
        pdaPermissions = permissions.toSet()
    }

    fun updatePermissions(permissions: List<String>) {
        pdaPermissions = permissions.toSet()
    }

    fun can(key: String): Boolean = key in pdaPermissions

    fun canAny(vararg keys: String): Boolean = keys.any { it in pdaPermissions }

    fun clear() {
        userId = null
        username = null
        isLoggedIn = false
        pdaPermissions = emptySet()
        warehouseId = null
        warehouseName = null
        warehouseCount = 0
    }
}
