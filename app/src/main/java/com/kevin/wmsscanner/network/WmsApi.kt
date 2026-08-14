package com.kevin.wmsscanner.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.GET
import retrofit2.http.Query

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val id: Int, val username: String, val roleId: Int)
data class ConfirmInboundRequest(val label: String, val locationCode: String)
data class ShipRequest(val soNumber: String, val label: String, val quantity: Int)
data class ApiError(val error: String)
data class PalletLookupResponse(val label: String, val quantity: Int)
data class AssignCheckerRequest(val soNumber: String)
data class LocationStockItem(
    val itemId: Int,
    val itemSku: String,
    val itemName: String,
    val palletCartonQty: Int,
    val quantity: Int
)
data class LocationStockLookupResponse(val locationCode: String, val locationType: String, val stock: List<LocationStockItem>)
data class PickResponse(val locationCode: String, val itemSku: String, val quantityPicked: Int)
data class ShipV2Request(val soNumber: String, val label: String, val quantity: Int)
data class ShipV2Response(val itemSku: String, val quantityShipped: Int, val remainingOnOrder: Int)
data class BarcodeItemLookupResponse(val sku: String, val name: String, val palletCartonQty: Int)
data class LabelStockLookupResponse(
    val itemSku: String,
    val itemName: String,
    val quantity: Int,
    val orderedQty: Int? = null,
    val alreadyShipped: Int? = null,
    val remaining: Int? = null
)
data class SessionSummary(
    val soNumber: String,
    val orderDate: String,
    val status: String
)
data class MoveRequest(
    val label: String,
    val currentLocationCode: String,
    val newLocationCode: String,
    val quantity: Int? = null
)

data class AppVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String?
)
data class RemoveRequest(
    val label: String,
    val locationCode: String,
    val quantity: Int? = null
)

data class InboundRequest(
    val label: String,
    val sku: String,
    val workOrderNumber: String,
    val quantity: Int
)
data class PickRequest(
    val locationCode: String,
    val itemSku: String,
    val quantity: Int,
    val sourceUntracked: Boolean? = null,
    val soNumber: String? = null
)
data class PalletResponse(
    val id: Int,
    val label: String,
    val quantity: Int,
    val status: String
)

data class SoLookupLine(
    val itemId: Int,
    val quantity: Int,
    val itemSku: String,
    val itemName: String,
    val shipped: Int,
    val remaining: Int,
    val status: String
)

data class SoLookupResponse(
    val soNumber: String,
    val orderDate: String,
    val items: List<SoLookupLine>
)

data class PickSummaryLine(
    val itemId: Int,
    val itemSku: String,
    val itemName: String,
    val palletCartonQty: Int,
    val orderedQty: Int,
    val pickedQty: Int,
    val remaining: Int
)
data class SalesOrder(
    val id: Int,
    val soNumber: String,
    val orderDate: String
)
data class OpenSalesOrder(val soNumber: String, val orderDate: String, val status: String)

data class PickSummaryResponse(val soNumber: String, val items: List<PickSummaryLine>)
interface WmsApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/pallets")
    suspend fun inbound(@Body request: InboundRequest): Response<PalletResponse>

    @PATCH("api/pallets/move")
    suspend fun movePallet(@Body request: MoveRequest): Response<PalletResponse>

    @PATCH("api/pallets/remove")
    suspend fun removePallet(@Body request: RemoveRequest): Response<PalletResponse>

    @PATCH("api/pallets/confirm-inbound")
    suspend fun confirmInbound(@Body request: ConfirmInboundRequest): Response<PalletResponse>
    @PATCH("api/pallets/ship")
    suspend fun shipPallet(@Body request: ShipRequest): Response<PalletResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/app-version")
    suspend fun getAppVersion(): Response<AppVersionResponse>

    @GET("api/sales-orders/lookup")
    suspend fun lookupSalesOrder(@Query("soNumber") soNumber: String): Response<SoLookupResponse>
    @GET("api/sales-orders")
    suspend fun getAllSalesOrders(): Response<List<SalesOrder>>

    @GET("api/pallets/lookup-at-location")
    suspend fun lookupPalletAtLocation(
        @Query("label") label: String,
        @Query("locationCode") locationCode: String
    ): Response<PalletLookupResponse>
  

    // ...inside the existing WmsApi interface, add these two:
    @PATCH("api/sales-orders/assign-checker")
    suspend fun assignChecker(@Body request: AssignCheckerRequest): Response<Unit>

    @GET("api/sales-orders/my-sessions")
    suspend fun getMySessions(): Response<List<SessionSummary>>



    // ...inside the WmsApi interface:
    @GET("api/location-stock/lookup")
    suspend fun lookupLocationStock(@Query("locationCode") locationCode: String): Response<LocationStockLookupResponse>

    @PATCH("api/location-stock/pick")
    suspend fun pickFromLocation(@Body request: PickRequest): Response<PickResponse>

    // ...inside the WmsApi interface:
    @GET("api/location-stock/lookup-by-label")
    suspend fun lookupStockByLabel(@Query("label") label: String): Response<LabelStockLookupResponse>

    @PATCH("api/location-stock/ship")
    suspend fun shipV2(@Body request: ShipV2Request): Response<ShipV2Response>
    @GET("api/items/lookup-by-barcode")
    suspend fun lookupItemByBarcode(@Query("barcode") barcode: String): Response<BarcodeItemLookupResponse>
    @GET("api/location-stock/lookup-by-label")
    suspend fun lookupStockByLabel(
        @Query("label") label: String,
        @Query("soNumber") soNumber: String
    ): Response<LabelStockLookupResponse>
    @GET("api/sales-orders/pick-summary")
    suspend fun getPickSummary(@Query("soNumber") soNumber: String): Response<PickSummaryResponse>
    @GET("api/sales-orders/open-list")
    suspend fun getOpenSalesOrders(): Response<List<OpenSalesOrder>>
}