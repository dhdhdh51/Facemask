package com.jawlinefit.app.data

import com.jawlinefit.app.data.local.CacheDao
import com.jawlinefit.app.data.local.CachedContent
import com.jawlinefit.app.data.remote.ApiEnvelope
import com.jawlinefit.app.data.remote.BillingVerification
import com.jawlinefit.app.data.remote.BillingVerificationRequest
import com.jawlinefit.app.data.remote.ContentApi
import com.jawlinefit.app.data.remote.DeviceRegistration
import com.jawlinefit.app.data.remote.DeviceTokenRequest
import com.jawlinefit.app.data.remote.Me
import com.jawlinefit.app.data.remote.Nuskha
import com.jawlinefit.app.data.remote.Plan
import com.jawlinefit.app.data.remote.Routine
import com.jawlinefit.app.data.remote.Tip
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.Response

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T, val fromCache: Boolean = false) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val message: String, val canRetry: Boolean = true, val statusCode: Int? = null) : LoadState<Nothing>
}

/** Keeps API paths/models unchanged while making content reads cache-first and account-scoped. */
class ContentRepository(
    private val api: ContentApi,
    private val cache: CacheDao,
    private val json: Json
) {
    @Volatile private var premium = false
    @Volatile private var entitlementKnown = false
    @Volatile private var accountScope: String? = null

    fun setIdentity(uid: String?) {
        if (accountScope != uid) {
            accountScope = uid
            resetEntitlement()
        }
    }

    suspend fun me(): LoadState<Me> {
        // Every call is authoritative: while it is in flight or failed, premium remains locked.
        premium = false
        entitlementKnown = false
        return request { api.me() }.also { result ->
            if (result is LoadState.Content) {
                setIdentity(result.value.uid)
                premium = result.value.isPremium
                entitlementKnown = true
            }
        }
    }

    suspend fun routines(onCached: (LoadState.Content<List<Routine>>) -> Unit = {}): LoadState<List<Routine>> {
        val tier = if (entitlementKnown && premium) "premium" else "free"
        val loaded = cacheFirst(
            cacheKey("routines:$tier"),
            ListSerializer(Routine.serializer()),
            onCached,
            transformForCache = { values -> if (tier == "premium") values else values.map(::redactPremiumRoutine) }
        ) { api.routines() }
        return if (loaded is LoadState.Content && tier != "premium") {
            LoadState.Content(loaded.value.map(::redactPremiumRoutine), loaded.fromCache)
        } else loaded
    }

    suspend fun routine(id: Long, onCached: (LoadState.Content<Routine>) -> Unit = {}): LoadState<Routine> {
        val tier = if (entitlementKnown && premium) "premium" else "free"
        val safeCached: (LoadState.Content<Routine>) -> Unit = { state ->
            if (!state.value.isPremium || isPremiumConfirmed()) onCached(state)
        }
        val result = cacheFirst(
            cacheKey("routine:$tier:$id"),
            Routine.serializer(),
            safeCached,
            transformForCache = { value -> if (tier == "premium") value else redactPremiumRoutine(value) }
        ) { api.routine(id) }
        return if (result is LoadState.Content && result.value.isPremium && !isPremiumConfirmed()) {
            LoadState.Error("Premium access must be confirmed online before opening this routine.", statusCode = 403)
        } else result
    }

    suspend fun nuskhe(
        query: String?,
        concern: String?,
        skinType: String?,
        page: Int,
        allergens: Set<String>,
        onCached: (LoadState.Content<List<Nuskha>>) -> Unit = {}
    ): LoadState<List<Nuskha>> {
        val safePage = page.coerceAtLeast(1)
        val tier = if (entitlementKnown && premium) "premium" else "free"
        val key = cacheKey("nuskhe:$tier:${concern.orEmpty()}:${skinType.orEmpty()}:$safePage")
        val cachedCallback: (LoadState.Content<List<Nuskha>>) -> Unit = { state ->
            onCached(LoadState.Content(filterNuskhe(state.value, query, allergens), true))
        }
        val loaded = cacheFirst(
            key,
            ListSerializer(Nuskha.serializer()),
            cachedCallback,
            transformForCache = { values -> if (tier == "premium") values else values.map(::redactPremiumNuskha) }
        ) {
            api.nuskhe(concern?.takeIf(String::isNotBlank), skinType?.takeIf(String::isNotBlank), safePage)
        }
        if (loaded !is LoadState.Content) return loaded
        val safeItems = if (isPremiumConfirmed()) loaded.value else loaded.value.map(::redactPremiumNuskha)
        return LoadState.Content(filterNuskhe(safeItems, query, allergens), loaded.fromCache)
    }

    suspend fun tips(onCached: (LoadState.Content<List<Tip>>) -> Unit = {}): LoadState<List<Tip>> =
        cacheFirst(cacheKey("tips"), ListSerializer(Tip.serializer()), onCached) { api.tips() }

    suspend fun plans(): LoadState<List<Plan>> = request { api.plans() }

    suspend fun verifyBilling(productId: String, purchaseToken: String): LoadState<BillingVerification> =
        request { api.verifyBilling(BillingVerificationRequest(productId, purchaseToken)) }

    suspend fun registerDeviceToken(token: String): LoadState<DeviceRegistration> =
        request { api.registerDeviceToken(DeviceTokenRequest(token)) }

    suspend fun deleteAccount(authorization: String): LoadState<JsonElement> = request { api.deleteAccount(authorization) }

    fun resetEntitlement() {
        premium = false
        entitlementKnown = false
    }

    fun isPremiumConfirmed(): Boolean = entitlementKnown && premium

    private fun cacheKey(resource: String): String = scopedCacheKey(accountScope, resource)

    private suspend fun <T> cacheFirst(
        key: String,
        serializer: KSerializer<T>,
        onCached: (LoadState.Content<T>) -> Unit,
        transformForCache: (T) -> T = { it },
        network: suspend () -> Response<ApiEnvelope<T>>
    ): LoadState<T> {
        val stored = runCatching { cache.get(key) }.getOrNull()
        val cachedValue = stored?.let { runCatching { json.decodeFromString(serializer, it.json) }.getOrNull() }
        if (cachedValue != null) onCached(LoadState.Content(cachedValue, fromCache = true))

        val result = request(network)
        if (result is LoadState.Content) {
            runCatching {
                cache.put(CachedContent(key, json.encodeToString(serializer, transformForCache(result.value)), System.currentTimeMillis()))
            }
            return result
        }
        val mayUseStale = result is LoadState.Error && (result.statusCode == null || result.statusCode in 500..599)
        return if (mayUseStale && cachedValue != null) LoadState.Content(cachedValue, fromCache = true) else result
    }

    private suspend fun <T> request(call: suspend () -> Response<ApiEnvelope<T>>): LoadState<T> = try {
        val response = call()
        if (response.code() == 204) {
            LoadState.Empty
        } else {
            val envelope = response.body() ?: parseError(response)
            when {
                response.isSuccessful && envelope.success && envelope.data != null -> LoadState.Content(envelope.data)
                response.isSuccessful && envelope.success -> LoadState.Empty
                else -> httpError(response.code(), envelope.error)
            }
        }
    } catch (_: IOException) {
        LoadState.Error("Network unavailable. Check your connection and try again.")
    } catch (_: Exception) {
        LoadState.Error("The response could not be processed. Please try again.")
    }

    private fun <T> parseError(response: Response<ApiEnvelope<T>>): ApiEnvelope<T> {
        val parsed = response.errorBody()?.string()?.let { body ->
            runCatching { json.decodeFromString(ApiEnvelope.serializer(JsonElement.serializer()), body) }.getOrNull()
        }
        return ApiEnvelope(success = false, error = parsed?.error)
    }

    private fun httpError(status: Int, error: String?): LoadState.Error {
        val fallback = when (status) {
            401 -> "Your session expired. Please sign in again."
            403 -> "This content requires access that is not active on your account."
            429 -> "Too many requests. Please wait a moment and try again."
            in 500..599 -> "The service is temporarily unavailable. Please try again later."
            else -> "The request could not be completed."
        }
        val safeServerMessage = error?.takeIf { it.isNotBlank() && status in 400..499 && status !in setOf(401, 403, 429) }
        return LoadState.Error(safeServerMessage ?: fallback, canRetry = status != 401, statusCode = status)
    }
}

internal fun scopedCacheKey(uid: String?, resource: String): String =
    "api-v3:user:${uid?.takeIf(String::isNotBlank) ?: "signed-out"}:$resource"

internal fun filterNuskhe(items: List<Nuskha>, query: String?, allergens: Set<String>): List<Nuskha> {
    val search = query.orEmpty().trim()
    val blocked = allergens.map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
    return items.filter { item ->
        (search.isBlank() || item.title.contains(search, true) || item.concern.contains(search, true) ||
            item.frequency.contains(search, true) || item.ingredients.any { it.contains(search, true) }) &&
            item.ingredients.none { ingredient -> blocked.any { ingredient.contains(it, true) } }
    }
}

private fun redactPremiumRoutine(item: Routine): Routine = if (!item.isPremium) item else item.copy(
    description = "",
    mediaUrl = null,
    thumbnailUrl = null,
    steps = emptyList()
)

private fun redactPremiumNuskha(item: Nuskha): Nuskha = if (!item.isPremium) item else item.copy(
    ingredients = emptyList(),
    steps = emptyList(),
    warning = null,
    mediaUrl = null,
    thumbnailUrl = null
)
