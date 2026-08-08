package com.kevin.wmsscanner

object SessionManager {
    var isLoggedIn: Boolean = false
    var username: String? = null
    var userId: Int? = null

    fun setSession(id: Int, name: String) {
        userId = id
        username = name
        isLoggedIn = true
    }

    fun clear() {
        userId = null
        username = null
        isLoggedIn = false
    }
}