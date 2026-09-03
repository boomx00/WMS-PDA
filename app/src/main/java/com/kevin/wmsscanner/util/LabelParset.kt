package com.kevin.wmsscanner.util

data class ParsedLabel(
    val sku: String,
    val palletSeq: String,
    val quantity: Int,
    val workOrderNumber: String
)

// Format: "*SKU*palletSeq*qty*workOrder" (leading * is a barcode start
// delimiter, stripped if present). Example: *14013024102*0004*5000*MO007449
//
// SKU length varies by product — it isn't checked here, it's validated
// against the item database server-side. palletSeq and qty are
// fixed-width, but the work order's digit count varies (e.g. "MO0001" and
// "MO007449" are both valid) — only the "MO" prefix and the optional
// 2-letter suffix are fixed:
//   - palletSeq : exactly 4 digits   (e.g. "0004")
//   - qty       : exactly 4 digits   (e.g. "5000")
//   - workOrder : "MO" + 1-10 digits, optionally followed by a 2-letter
//                 suffix (e.g. "MO0001", "MO007449", "MO007449-DY")
private val PALLET_SEQ_REGEX = Regex("^\\d{4}$")
private val QTY_REGEX = Regex("^\\d{4}$")
private val WORK_ORDER_REGEX = Regex("^MO\\d{1,10}(-[A-Za-z]{2})?$")

fun parseLabel(raw: String): ParsedLabel? {
    val cleaned = raw.trim().removePrefix("*")
    val parts = cleaned.split("*")
    if (parts.size != 4) return null
    val (sku, palletSeq, qtyStr, workOrderNumber) = parts

    if (sku.isBlank()) return null
    if (!PALLET_SEQ_REGEX.matches(palletSeq)) return null
    if (!QTY_REGEX.matches(qtyStr)) return null
    if (!WORK_ORDER_REGEX.matches(workOrderNumber)) return null

    val quantity = qtyStr.toIntOrNull() ?: return null
    return ParsedLabel(sku, palletSeq, quantity, workOrderNumber)
}