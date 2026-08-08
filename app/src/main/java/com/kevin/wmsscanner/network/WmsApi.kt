package com.kevin.wmsscanner.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PATCH

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val id: Int, val username: String, val roleId: Int)
data class ConfirmInboundRequest(val label: String, val locationCode: String)

data class ApiError(val error: String)
data class MoveRequest(
    val label: String,
    val currentLocationCode: String,
    val newLocationCode: String,
    val quantity: Int? = null
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
}