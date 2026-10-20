package com.jawlinefit.app

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.google.firebase.auth.FirebaseAuth
import com.jawlinefit.app.auth.AuthRepository
import com.jawlinefit.app.billing.BillingManager
import com.jawlinefit.app.data.CompletionRepository
import com.jawlinefit.app.data.ContentRepository
import com.jawlinefit.app.data.ProgressRepository
import com.jawlinefit.app.data.local.JawlineDatabase
import com.jawlinefit.app.data.local.UserPreferences
import com.jawlinefit.app.data.remote.AuthInterceptor
import com.jawlinefit.app.data.remote.ContentApi
import com.jawlinefit.app.media.MediaCache
import android.os.Build
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val json = Json { ignoreUnknownKeys = false; explicitNulls = false }
    val database = Room.databaseBuilder(appContext, JawlineDatabase::class.java, "jawlinefit.db")
        .addMigrations(JawlineDatabase.MIGRATION_1_2)
        .build()
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
    val completionRepository = CompletionRepository(database.completionDao())
    val billingManager = BillingManager(appContext)
    val imageLoader = ImageLoader.Builder(appContext).components {
        if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
    }.build()
    val mediaCache by lazy { MediaCache(appContext) }
}
