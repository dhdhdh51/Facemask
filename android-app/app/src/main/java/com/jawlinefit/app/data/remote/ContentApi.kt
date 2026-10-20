package com.jawlinefit.app.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ContentApi {
    @GET("api/me")
    suspend fun me(): Response<ApiEnvelope<Me>>

    @POST("api/device")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Response<ApiEnvelope<DeviceRegistration>>

    @GET("api/nuskhe")
    suspend fun nuskhe(
        @Query("concern") concern: String? = null,
        @Query("skin_type") skinType: String? = null,
        @Query("page") page: Int
    ): Response<ApiEnvelope<List<Nuskha>>>

    @GET("api/routines")
    suspend fun routines(): Response<ApiEnvelope<List<Routine>>>

    @GET("api/routines/{id}")
    suspend fun routine(@Path("id") id: Long): Response<ApiEnvelope<Routine>>

    @GET("api/tips")
    suspend fun tips(): Response<ApiEnvelope<List<Tip>>>

    @GET("api/plans")
    suspend fun plans(): Response<ApiEnvelope<List<Plan>>>

    @POST("api/billing/verify")
    suspend fun verifyBilling(@Body request: BillingVerificationRequest): Response<ApiEnvelope<BillingVerification>>

    @DELETE("api/me")
    suspend fun deleteAccount(@Header("Authorization") authorization: String? = null): Response<ApiEnvelope<JsonElement>>
}
