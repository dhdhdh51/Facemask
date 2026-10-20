package com.jawlinefit.app.media

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaCache(context: Context) {
    private val appContext = context.applicationContext
    val cache = SimpleCache(
        File(appContext.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
        StandaloneDatabaseProvider(appContext)
    )
    val dataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
