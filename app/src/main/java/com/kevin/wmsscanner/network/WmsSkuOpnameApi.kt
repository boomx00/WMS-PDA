package com.kevin.wmsscanner.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
}

object SkuOpnameApi {
    val api: WmsSkuOpnameApi by lazy { NetworkModule.retrofit.create(WmsSkuOpnameApi::class.java) }
}