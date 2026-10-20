package com.jawlinefit.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

@Serializable
data class Me(
    val uid: String,
    val plan: String? = null,
    @SerialName("plan_expiry") val planExpiry: String? = null,
    @SerialName("is_premium") val isPremium: Boolean
)

@Serializable
data class HomeContent(
    val featuredRoutine: Routine? = null,
    val featuredTip: Tip? = null,
    val completionStreak: Int = 0
)

@Serializable
enum class MediaType {
    @SerialName("image") IMAGE,
    @SerialName("gif") GIF,
    @SerialName("video") VIDEO
}

@Serializable
data class Routine(
    val id: Long,
    val title: String,
    val description: String,
    @SerialName("duration_min") val durationMin: Int,
    @SerialName("is_premium") val isPremium: Boolean,
    @SerialName("media_type") val mediaType: MediaType? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val steps: List<RoutineStep> = emptyList()
)

@Serializable
data class RoutineStep(
    val order: Int,
    val title: String,
    @SerialName("duration_sec") val durationSec: Int,
    val instruction: String,
    @SerialName("media_type") val mediaType: MediaType? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class Nuskha(
    val id: Long,
    val title: String,
    val concern: String,
    @SerialName("skin_type") val skinType: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val frequency: String,
    val warning: String? = null,
    @SerialName("is_premium") val isPremium: Boolean,
    @SerialName("media_type") val mediaType: MediaType? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class Tip(
    val id: Long,
    val category: String,
    val text: String,
    @SerialName("media_type") val mediaType: MediaType? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class Plan(
    val id: Long,
    val name: String,
    @SerialName("product_id") val productId: String,
    @SerialName("base_plan_id") val basePlanId: String,
    @SerialName("duration_days") val durationDays: Int
)

@Serializable
data class BillingVerificationRequest(
    @SerialName("product_id") val productId: String,
    @SerialName("purchase_token") val purchaseToken: String
)

@Serializable
data class BillingVerification(
    @SerialName("is_premium") val isPremium: Boolean,
    @SerialName("plan_expiry") val planExpiry: String? = null
)

@Serializable
data class DeviceTokenRequest(@SerialName("fcm_token") val fcmToken: String)

@Serializable
data class DeviceRegistration(@SerialName("fcm_token") val fcmToken: String)
