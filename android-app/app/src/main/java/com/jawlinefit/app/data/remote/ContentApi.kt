package com.jawlinefit.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ContentApi {
    @GET("v1/home") suspend fun home(@Query("language") language: String): Response<HomeContent>
    @GET("v1/routines") suspend fun routines(@Query("language") language: String): Response<RoutineListResponse>
    @GET("v1/routines/{id}") suspend fun routine(@Path("id") id: String, @Query("language") language: String): Response<Routine>
    @GET("v1/nuskhe") suspend fun nuskhe(
        @Query("language") language: String,
        @Query("query") query: String? = null,
        @Query("category") category: String? = null,
        @Query("exclude_allergens") excludeAllergens: List<String> = emptyList()
    ): Response<NuskhaListResponse>
    @GET("v1/tips") suspend fun tips(@Query("language") language: String): Response<TipListResponse>
    @POST("v1/subscriptions/verify") suspend fun verifySubscription(@Body request: SubscriptionVerificationRequest): Response<SubscriptionStatus>
    @GET("v1/subscriptions/status") suspend fun subscriptionStatus(): Response<SubscriptionStatus>
    @POST("v1/devices/fcm-token") suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Response<ApiMessage>
    @DELETE("v1/account") suspend fun deleteAccount(@Body request: DeleteAccountRequest): Response<ApiMessage>
}
