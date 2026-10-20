package com.jawlinefit.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class HomeContent(
    val greeting: String = "",
    val headline: String = "",
    val featuredRoutine: Routine? = null,
    val featuredTip: Tip? = null,
    val announcement: String? = null
)

@Serializable
data class Routine(
    val id: String,
    val title: String,
    val summary: String,
    val durationSeconds: Int,
    val safetyNotice: String,
    val thumbnailUrl: String? = null,
    val premium: Boolean = false,
    val steps: List<RoutineStep> = emptyList()
)

@Serializable
data class RoutineStep(
    val id: String,
    val title: String,
    val instruction: String,
    val durationSeconds: Int,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

@Serializable
data class Nuskha(
    val id: String,
    val title: String,
    val summary: String,
    val instructions: String,
    val category: String,
    val ingredients: List<String> = emptyList(),
    val allergyTags: List<String> = emptyList(),
    val patchTestNotice: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null
)

@Serializable
data class Tip(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val mediaUrl: String? = null
)

@Serializable data class RoutineListResponse(val items: List<Routine> = emptyList())
@Serializable data class NuskhaListResponse(val items: List<Nuskha> = emptyList(), val categories: List<String> = emptyList())
@Serializable data class TipListResponse(val items: List<Tip> = emptyList())
@Serializable data class SubscriptionVerificationRequest(val purchaseToken: String, val productId: String)
@Serializable data class SubscriptionStatus(val active: Boolean, val expiresAtEpochMillis: Long? = null, val manageUrl: String? = null)
@Serializable data class DeleteAccountRequest(val confirmation: String = "DELETE")
@Serializable data class ApiMessage(val message: String)
@Serializable data class DeviceTokenRequest(val token: String, val platform: String = "android")
