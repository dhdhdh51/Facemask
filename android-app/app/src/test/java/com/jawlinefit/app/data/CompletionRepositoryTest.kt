package com.jawlinefit.app.data

import com.jawlinefit.app.data.local.CompletionDao
import com.jawlinefit.app.data.local.RoutineCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionRepositoryTest {
    private val repository = CompletionRepository(object : CompletionDao {
        override fun observeAll(): Flow<List<RoutineCompletion>> = emptyFlow()
        override suspend fun insert(completion: RoutineCompletion) = Unit
        override suspend fun deleteAll() = Unit
    })

    @Test fun `streak counts consecutive unique days including today`() {
        val items = listOf(100L, 99L, 99L, 98L).mapIndexed { index, day -> RoutineCompletion("$index", 1, day, 0) }
        assertEquals(3, repository.currentStreak(items, 100L))
    }

    @Test fun `streak permits today not yet completed`() {
        val items = listOf(99L, 98L).map { day -> RoutineCompletion("$day", 1, day, 0) }
        assertEquals(2, repository.currentStreak(items, 100L))
    }

    @Test fun `streak stops at missed day`() {
        val items = listOf(100L, 98L).map { day -> RoutineCompletion("$day", 1, day, 0) }
        assertEquals(1, repository.currentStreak(items, 100L))
    }
}
