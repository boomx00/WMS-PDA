package com.kevin.wmsscanner.util

data class ParsedLabel(
    val sku: String,
    val palletSeq: String,
    val quantity: Int,
    val workOrderNumber: String
)

// Parses "*SKU*palletSeq*qty*workOrder" (leading * is a barcode start
// delimiter, stripped if present).
fun parseLabel(raw: String): ParsedLabel? {
    val cleaned = raw.trim().removePrefix("*")
    val parts = cleaned.split("*")
    if (parts.size != 4) return null
    val (sku, palletSeq, qtyStr, workOrderNumber) = parts
    val quantity = qtyStr.toIntOrNull() ?: return null
    if (sku.isBlank() || palletSeq.isBlank() || workOrderNumber.isBlank()) return null
    return ParsedLabel(sku, palletSeq, quantity, workOrderNumber)
}