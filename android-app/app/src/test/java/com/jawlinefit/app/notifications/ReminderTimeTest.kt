package com.jawlinefit.app.notifications

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimeTest {
    @Test fun `next civil time is tomorrow after selected time passes`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 20, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = millisUntilNextLocalTime(19, 0, now.timeInMillis)
        assertTrue(delay > 22L * 60L * 60L * 1000L)
        assertTrue(delay <= 24L * 60L * 60L * 1000L)
    }

    @Test fun `next civil time keeps minute precision`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 20, 18, 59, 30)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(30_000L, millisUntilNextLocalTime(19, 0, now.timeInMillis))
    }
}
