package com.kevin.wmsscanner.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Per-SKU stock opname (SOSKU-...). Kept in its own file so WmsApi.kt stays
// untouched. Counting a location's quantity, finishing, history and item
// search all reuse the existing endpoints in WmsApi.

data class AddSkuToOpnameRequest(val sku: String)

// One row in the "your sessions" list. Totals are the same as the SKU cards:
// Total Stock locations only. systemTotal / countedTotal include loose packs
// as cartons (decimal); null from servers older than that.
data class SkuOpnameSession(
    val opnameNumber: String,
    val skuCount: Int,
    // Sum of every SKU's locked system quantity (cartons).
    val systemQty: Int,
    // Sum of everything counted so far (cartons).
    val countedQty: Int,
    val status: String,
    val systemTotal: Double? = null,
    val countedTotal: Double? = null
)

// unit "PACKS": a packs location — the line's quantities are loose packs.
// inTotalStock false: the location isn't counted in Total Stock, so the line
// is listed but left out of the SKU's totals (null from older servers).
data class SkuOpnameLine(
    val locationCode: String,
    val systemQty: Int,
    val countedQty: Int,
    val difference: Int,
    val unit: String? = null,
    val inTotalStock: Boolean? = null
)

// One SKU inside a session. systemQty is locked at the moment it was added —
// Total Stock locations only, like the web summary (lockedQty: every
// location).
// systemPacks / countedPacks: loose packs on top of the cartons (0 from
// servers older than loose packs).
data class SkuOpnameSkuEntry(
    val skuCode: String,
    val skuName: String,
    val systemQty: Int,
    val countedQty: Int,
    val difference: Int,
    val lines: List<SkuOpnameLine>,
    val systemPacks: Int = 0,
    val countedPacks: Int = 0,
    // Packs in one carton (Items page); 0 when not set.
    val packsPerCarton: Int = 0,
    val lockedQty: Int? = null,
    val lockedPacks: Int? = null
)

data class SkuOpnameDetail(
    val opnameNumber: String,
    val finished: Boolean,
    val skus: List<SkuOpnameSkuEntry>
)

interface WmsSkuOpnameApi {
    // Creates an empty session — SKUs are added to it afterwards.
    @POST("api/stock-opname/sku")
    suspend fun createSkuOpname(): Response<SkuOpnameSession>

    @GET("api/stock-opname/sku")
    suspend fun getMySkuOpnameSessions(): Response<List<SkuOpnameSession>>

    @GET("api/stock-opname/sku/{opnameNumber}")
    suspend fun getSkuOpnameDetail(@Path("opnameNumber") opnameNumber: String): Response<SkuOpnameDetail>

    // Adds a SKU to the session and locks its current system quantity.
    @POST("api/stock-opname/sku/{opnameNumber}/skus")
    suspend fun addSkuToOpname(
        @Path("opnameNumber") opnameNumber: String,
        @Body request: AddSkuToOpnameRequest
    ): Response<SkuOpnameSkuEntry>

    // Where the SKU sits in the system right now — every location with a
    // non-zero quantity, pickable or not (all=1). Same rows the server sums
    // when it locks a SKU's system qty. Reuses SkuLocationsResponse from
    // WmsApi.kt; picking keeps calling the pickable-only version there.
    @GET("api/location-stock/by-sku")
    suspend fun getAllLocationsBySku(
        @Query("sku") sku: String,
        @Query("all") all: Int
    ): Response<SkuLocationsResponse>
}

// A line counted in loose packs (a packs location).
fun SkuOpnameLine.isPacks(): Boolean = unit == "PACKS"

// Counted in Total Stock (true when the server doesn't say).
fun SkuOpnameLine.countsInTotal(): Boolean = inTotalStock != false

object SkuOpnameApi {
    val api: WmsSkuOpnameApi by lazy { NetworkModule.retrofit.create(WmsSkuOpnameApi::class.java) }
}