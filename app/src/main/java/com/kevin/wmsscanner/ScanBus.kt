package com.kevin.wmsscanner

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScanBus {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scans = _scans.asSharedFlow()

    suspend fun emit(code: String) {
        _scans.emit(code)
    }
}