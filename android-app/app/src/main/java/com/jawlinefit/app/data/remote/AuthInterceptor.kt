package com.jawlinefit.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val auth: FirebaseAuth) : Interceptor {
    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val initialToken = token(forceRefresh = false)
        val authenticated = request.newBuilder().apply {
            if (!initialToken.isNullOrBlank()) header("Authorization", "Bearer $initialToken")
            header("Accept", "application/json")
        }.build()
        val response = chain.proceed(authenticated)
        if (response.code != 401 || initialToken.isNullOrBlank()) return response

        val refreshed = synchronized(refreshLock) { token(forceRefresh = true) }
        if (refreshed.isNullOrBlank()) return response
        response.close()
        return chain.proceed(request.newBuilder()
            .header("Authorization", "Bearer $refreshed")
            .header("Accept", "application/json")
            .build())
    }

    private fun token(forceRefresh: Boolean): String? = runBlocking {
        runCatching { auth.currentUser?.getIdToken(forceRefresh)?.await()?.token }.getOrNull()
    }
}
