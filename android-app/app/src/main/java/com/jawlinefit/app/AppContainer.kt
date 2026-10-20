package com.jawlinefit.app

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.jawlinefit.app.auth.AuthRepository
import com.jawlinefit.app.billing.BillingManager
import com.jawlinefit.app.data.ContentRepository
import com.jawlinefit.app.data.ProgressRepository
import com.jawlinefit.app.data.local.JawlineDatabase
import com.jawlinefit.app.data.local.UserPreferences
import com.jawlinefit.app.data.remote.AuthInterceptor
import com.jawlinefit.app.data.remote.ContentApi
import com.jawlinefit.app.media.MediaCache
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val database = Room.databaseBuilder(appContext, JawlineDatabase::class.java, "jawlinefit.db").build()
    val preferences = UserPreferences(appContext)
    private val firebaseAuth = FirebaseAuth.getInstance()
    val authRepository = AuthRepository(appContext, firebaseAuth)
    private val http = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(firebaseAuth))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        })
        .build()
    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ContentApi::class.java)
    val contentRepository = ContentRepository(api, database.cacheDao(), json)
    val progressRepository = ProgressRepository(appContext, database.progressDao())
    val billingManager = BillingManager(appContext)
    val mediaCache by lazy { MediaCache(appContext) }
    val contentApi: ContentApi = api
}
