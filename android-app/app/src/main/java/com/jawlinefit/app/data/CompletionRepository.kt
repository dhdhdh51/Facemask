package com.jawlinefit.app.data

import com.jawlinefit.app.data.local.CompletionDao
import com.jawlinefit.app.data.local.RoutineCompletion
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CompletionRepository(private val dao: CompletionDao) {
    val completions: Flow<List<RoutineCompletion>> = dao.observeAll()
    val streak: Flow<Int> = completions.map { currentStreak(it) }

    suspend fun complete(routineId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val epochDay = localEpochDay(nowMillis)
        dao.insert(RoutineCompletion("$routineId:$epochDay", routineId, epochDay, nowMillis))
    }

    suspend fun deleteAll() = dao.deleteAll()

    internal fun currentStreak(items: List<RoutineCompletion>, todayEpochDay: Long = localEpochDay(System.currentTimeMillis())): Int {
        val days = items.map { it.completedEpochDay }.toSet()
        var cursor = todayEpochDay
        if (cursor !in days && cursor - 1 in days) cursor--
        var count = 0
        while (cursor in days) { count++; cursor-- }
        return count
    }

    private fun localEpochDay(timeMillis: Long): Long {
        val offsetMillis = TimeZone.getDefault().getOffset(timeMillis).toLong()
        return Math.floorDiv(timeMillis + offsetMillis, 86_400_000L)
    }
}
