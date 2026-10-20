package com.jawlinefit.app.data

import com.jawlinefit.app.data.remote.Nuskha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentCacheLogicTest {
    @Test fun `cache keys isolate users and resources`() {
        assertEquals("api-v3:user:user-a:routines:free", scopedCacheKey("user-a", "routines:free"))
        assertTrue(scopedCacheKey("user-a", "tips") != scopedCacheKey("user-b", "tips"))
        assertTrue(scopedCacheKey("user-a", "tips") != scopedCacheKey("user-a", "routines"))
    }

    @Test fun `search and allergy filtering are deterministic`() {
        val items = listOf(
            item(1, "Cooling aloe", listOf("aloe gel")),
            item(2, "Oat mask", listOf("ground oats", "water"))
        )
        assertEquals(listOf(1L), filterNuskhe(items, "aloe", emptySet()).map(Nuskha::id))
        assertEquals(listOf(2L), filterNuskhe(items, null, setOf("aloe")).map(Nuskha::id))
    }

    private fun item(id: Long, title: String, ingredients: List<String>) = Nuskha(
        id = id,
        title = title,
        concern = "dryness",
        skinType = "dry",
        ingredients = ingredients,
        steps = listOf("Patch test first"),
        frequency = "weekly",
        isPremium = false
    )
}
