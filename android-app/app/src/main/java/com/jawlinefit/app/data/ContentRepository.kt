package com.jawlinefit.app.data

import com.jawlinefit.app.data.local.CacheDao
import com.jawlinefit.app.data.local.CachedContent
import com.jawlinefit.app.data.remote.ContentApi
import com.jawlinefit.app.data.remote.HomeContent
import com.jawlinefit.app.data.remote.NuskhaListResponse
import com.jawlinefit.app.data.remote.Routine
import com.jawlinefit.app.data.remote.RoutineListResponse
import com.jawlinefit.app.data.remote.TipListResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import retrofit2.Response

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T, val fromCache: Boolean = false) : LoadState<T>
    data object Empty : LoadState<Nothing>
    data class Error(val message: String, val canRetry: Boolean = true) : LoadState<Nothing>
}

class ContentRepository(
    private val api: ContentApi,
    private val cache: CacheDao,
    private val json: Json
) {
    suspend fun home(language: String) = cached("home:$language", HomeContent.serializer()) { api.home(language) }
    suspend fun routines(language: String) = cached("routines:$language", RoutineListResponse.serializer()) { api.routines(language) }
    suspend fun routine(id: String, language: String): LoadState<Routine> =
        cached("routine:$language:$id", Routine.serializer()) { api.routine(id, language) }
    suspend fun nuskhe(language: String, query: String?, category: String?, allergens: Set<String>): LoadState<NuskhaListResponse> {
        val key = "nuskhe:$language:${query.orEmpty()}:${category.orEmpty()}:${allergens.sorted().joinToString(",")}" 
        return cached(key, NuskhaListResponse.serializer()) { api.nuskhe(language, query?.takeIf(String::isNotBlank), category, allergens.toList()) }
    }
    suspend fun tips(language: String) = cached("tips:$language", TipListResponse.serializer()) { api.tips(language) }

    private suspend fun <T> cached(key: String, serializer: KSerializer<T>, network: suspend () -> Response<T>): LoadState<T> {
        val result = request(network)
        if (result is LoadState.Content) {
            cache.put(CachedContent(key, json.encodeToString(serializer, result.value), System.currentTimeMillis()))
            return result
        }
        val stored = cache.get(key) ?: return result
        return runCatching { LoadState.Content(json.decodeFromString(serializer, stored.json), fromCache = true) }
            .getOrElse { result }
    }

    private suspend fun <T> request(call: suspend () -> Response<T>): LoadState<T> = try {
        val response = call()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> LoadState.Content(body)
            response.code() == 204 -> LoadState.Empty
            response.code() == 401 -> LoadState.Error("Please sign in again.", false)
            else -> LoadState.Error("Content could not be loaded (${response.code()}).")
        }
    } catch (_: java.io.IOException) {
        LoadState.Error("You appear to be offline. Check your connection and try again.")
    } catch (_: Exception) {
        LoadState.Error("Something went wrong while loading content.")
    }
}
