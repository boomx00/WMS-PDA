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

// One row in the "your sessions" list.
data class SkuOpnameSession(
    val opnameNumber: String,
    val skuCount: Int,
    // Sum of every SKU's locked system quantity.
    val systemQty: Int,
    // Sum of everything counted so far.
    val countedQty: Int,
    val status: String
)

data class SkuOpnameLine(
    val locationCode: String,
    val systemQty: Int,
    val countedQty: Int,
    val difference: Int
)

// One SKU inside a session. systemQty is locked at the moment it was added.
data class SkuOpnameSkuEntry(
    val skuCode: String,
    val skuName: String,
    val systemQty: Int,
    val countedQty: Int,
    val difference: Int,
    val lines: List<SkuOpnameLine>
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

object SkuOpnameApi {
    val api: WmsSkuOpnameApi by lazy { NetworkModule.retrofit.create(WmsSkuOpnameApi::class.java) }
}