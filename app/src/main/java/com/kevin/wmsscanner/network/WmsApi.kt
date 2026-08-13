package com.kevin.wmsscanner.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.GET
import retrofit2.http.Query
data class PalletLookupResponse(val label: String, val quantity: Int)
data class AssignCheckerRequest(val soNumber: String)

data class SessionSummary(
    val soNumber: String,
    val orderDate: String,
    val status: String
)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val id: Int, val username: String, val roleId: Int)
data class ConfirmInboundRequest(val label: String, val locationCode: String)
data class ShipRequest(val soNumber: String, val label: String, val quantity: Int)
data class ApiError(val error: String)
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

    @GET("api/pallets/lookup-at-location")
    suspend fun lookupPalletAtLocation(
        @Query("label") label: String,
        @Query("locationCode") locationCode: String
    ): Response<PalletLookupResponse>
    data class AssignCheckerRequest(val soNumber: String)

    data class SessionSummary(
        val soNumber: String,
        val orderDate: String,
        val status: String
    )

    // ...inside the existing WmsApi interface, add these two:
    @PATCH("api/sales-orders/assign-checker")
    suspend fun assignChecker(@Body request: AssignCheckerRequest): Response<Unit>

    @GET("api/sales-orders/my-sessions")
    suspend fun getMySessions(): Response<List<SessionSummary>>
}