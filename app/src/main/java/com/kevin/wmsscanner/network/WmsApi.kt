package com.kevin.wmsscanner.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path
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
data class OpnameLocationRow(val locationCode: String, val total: Int, val counted: Int, val done: Boolean)
data class OpnameCountRequest(val locationCode: String, val scanned: String, val countedQty: Int)
data class OpnameCountResponse(
    val itemSku: String,
    val itemName: String,
    val countedQty: Int,
    val difference: Int? = null,
    val locationCode: String? = null
)
data class LocationStockLookupResponse(val locationCode: String, val locationType: String, val stock: List<LocationStockItem>)
data class PickResponse(val locationCode: String, val itemSku: String, val quantityPicked: Int)
data class ShipV2Request(val soNumber: String, val label: String, val quantity: Int)
data class ShipV2Response(val itemSku: String, val quantityShipped: Int, val remainingOnOrder: Int)
data class BarcodeItemLookupResponse(val sku: String, val name: String, val palletCartonQty: Int)
data class ClaimRequest(val itemSku: String, val quantity: Int)

data class LabelStockLookupResponse(
    val itemSku: String,
    val itemName: String,
    val quantity: Int,
    val palletCartonQty: Int? = null,
    val orderedQty: Int? = null,
    val alreadyShipped: Int? = null,
    val remaining: Int? = null,
    val availableToShip: Int? = null,
    val unclaimedInOutboundWh: Int? = null
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

data class MoveV2Request(
    val sourceLocationCode: String,
    val destinationLocationCode: String,
    val itemSku: String,
    val quantity: Int,
    val sourceUntracked: Boolean? = null
)
data class OpenSalesOrder(val soNumber: String, val orderDate: String, val status: String)
data class MoveInV2Request(val label: String, val destinationLocationCode: String, val quantity: Int)
data class SettingsResponse(
    val allowDefaultCodeTransactions: Boolean,
    val automaticInbound: Boolean,
    val automaticInboundFromRack: Boolean,
    val allowUntrackedOutbound: Boolean,
    val allowDefaultPicking: Boolean,
    val allowNegativeFloorStock: Boolean,
    val allowNegativeRackStock: Boolean
)
data class OpnameSession(
    val opnameNumber: String,
    val notes: String?,
    val status: String,
    val totalLines: Int,
    val countedLines: Int
)

data class OpnameLine(
    val id: Int,
    val locationCode: String,
    val itemSku: String,
    val itemName: String,
    val systemQty: Int,
    val countedQty: Int?,
    val difference: Int?,
    val countedAt: String?
)

data class OpnameDetailResponse(
    val opnameNumber: String,
    val notes: String?,
    val lines: List<OpnameLine>
)

data class CountRequest(val locationCode: String, val itemSku: String, val countedQty: Int)

data class PickSummaryResponse(val soNumber: String, val items: List<PickSummaryLine>)

data class CreateCustomOpnameRequest(val notes: String? = null)
data class OpnameReportItem(
    val itemSku: String,
    val itemName: String,
    val countedQty: Int,
    val countedAt: String?,
    val countedByUsername: String?
)
data class OpnameReportLocation(val locationCode: String, val counted: Boolean, val items: List<OpnameReportItem>)
data class OpnameReportResponse(
    val opnameNumber: String,
    val notes: String?,
    val assignedToUsername: String?,
    val totalLocations: Int,
    val countedLocations: Int,
    val report: List<OpnameReportLocation>
)

interface WmsApi {
    @GET("api/stock-opname/{opnameNumber}/report")
    suspend fun getOpnameReport(@Path("opnameNumber") opnameNumber: String): Response<OpnameReportResponse>
    @PATCH("api/stock-opname/{opnameNumber}/finish")
    suspend fun finishOpname(@Path("opnameNumber") opnameNumber: String): Response<Unit>
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

    @PATCH("api/location-stock/move")
    suspend fun moveV2(@Body request: MoveV2Request): Response<Unit>


    @PATCH("api/location-stock/move-in")
    suspend fun moveInV2(@Body request: MoveInV2Request): Response<Unit>

    @GET("api/settings")
    suspend fun getSettings(): Response<SettingsResponse>

    @GET("api/stock-opname/my-sessions")
    suspend fun getMyOpnameSessions(): Response<List<OpnameSession>>

    @GET("api/stock-opname/{opnameNumber}/locations")
    suspend fun getOpnameLocations(@Path("opnameNumber") opnameNumber: String): Response<List<OpnameLocationRow>>

    @PATCH("api/stock-opname/{opnameNumber}/count")
    suspend fun submitOpnameCount(
        @Path("opnameNumber") opnameNumber: String,
        @Body request: OpnameCountRequest
    ): Response<OpnameCountResponse>
    @POST("api/stock-opname/custom")
    suspend fun createCustomOpname(@Body request: CreateCustomOpnameRequest): Response<OpnameSession>

    @POST("api/sales-orders/{soNumber}/claim")
    suspend fun claimStock(
        @Path("soNumber") soNumber: String,
        @Body request: ClaimRequest
    ): Response<Unit>
}