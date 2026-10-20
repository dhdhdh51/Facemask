package com.jawlinefit.app.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

class ContentApiContractTest {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    @Test fun `routes use only exact api contract`() {
        val routes = ContentApi::class.java.declaredMethods.mapNotNull { method ->
            method.getAnnotation(GET::class.java)?.value
                ?: method.getAnnotation(POST::class.java)?.value
                ?: method.getAnnotation(DELETE::class.java)?.value
        }.toSet()
        assertEquals(setOf("api/me", "api/device", "api/nuskhe", "api/routines", "api/routines/{id}", "api/tips", "api/plans", "api/billing/verify"), routes)
        assertFalse(routes.any { it.contains("v1/") || it.contains("home") })
        val meMethod = ContentApi::class.java.declaredMethods.single { it.name == "me" }
        val deleteMethod = ContentApi::class.java.declaredMethods.single { it.name == "deleteAccount" }
        assertEquals("api/me", requireNotNull(meMethod.getAnnotation(GET::class.java)).value)
        assertEquals("api/me", requireNotNull(deleteMethod.getAnnotation(DELETE::class.java)).value)
        assertTrue(meMethod.getAnnotation(DELETE::class.java) == null)
        assertTrue(deleteMethod.getAnnotation(GET::class.java) == null)
    }

    @Test fun `nuskhe query names are exact`() {
        val method = ContentApi::class.java.declaredMethods.single { it.name == "nuskhe" }
        val names = method.parameterAnnotations.flatMap { annotations -> annotations.filterIsInstance<Query>().map(Query::value) }
        assertEquals(listOf("concern", "skin_type", "page"), names)
    }

    @Test fun `request and error envelopes are exact`() {
        assertEquals(
            setOf("product_id", "purchase_token"),
            Json.parseToJsonElement(Json.encodeToString(BillingVerificationRequest("premium_monthly", "private-token"))).jsonObject.keys
        )
        assertEquals("{\"fcm_token\":\"private-token\"}", Json.encodeToString(DeviceTokenRequest("private-token")))
        val error = json.decodeFromString<ApiEnvelope<Me>>("""{"success":false,"error":"Wait"}""")
        assertFalse(error.success)
        assertEquals("Wait", error.error)
    }

    @Test fun `me and billing responses decode exact fields`() {
        val mePayload = """{"success":true,"data":{"uid":"firebase-uid","plan":"premium_monthly","plan_expiry":"2026-12-01T00:00:00Z","is_premium":true}}"""
        val me = json.decodeFromString<ApiEnvelope<Me>>(mePayload).data!!
        assertEquals("firebase-uid", me.uid)
        assertTrue(me.isPremium)

        val billingPayload = """{"success":true,"data":{"is_premium":true,"plan_expiry":"2026-12-01T00:00:00Z"}}"""
        val billing = json.decodeFromString<ApiEnvelope<BillingVerification>>(billingPayload).data!!
        assertTrue(billing.isPremium)
        assertEquals("2026-12-01T00:00:00Z", billing.planExpiry)
    }

    @Test fun `content response objects contain only contract keys`() {
        val nuskhePayload = """{"success":true,"data":[{"id":1,"title":"Routine","concern":"dryness","skin_type":"dry","ingredients":[],"steps":[],"frequency":"daily","warning":null,"is_premium":true,"media_type":"gif","media_url":"https://api.example.test/uploads/a.gif","thumbnail_url":"https://api.example.test/uploads/a.jpg"}]}"""
        val nuskhaObject = Json.parseToJsonElement(nuskhePayload).jsonObject.getValue("data").jsonArray.single().jsonObject
        assertEquals(setOf("id", "title", "concern", "skin_type", "ingredients", "steps", "frequency", "warning", "is_premium", "media_type", "media_url", "thumbnail_url"), nuskhaObject.keys)
        assertEquals(MediaType.GIF, json.decodeFromString<ApiEnvelope<List<Nuskha>>>(nuskhePayload).data!!.single().mediaType)

        val routinePayload = """{"success":true,"data":{"id":2,"title":"Jaw","description":"Controlled movement","duration_min":5,"is_premium":false,"media_type":"video","media_url":"https://api.example.test/uploads/r.mp4","thumbnail_url":"https://api.example.test/uploads/r.jpg","steps":[{"order":0,"title":"Start","duration_sec":30,"instruction":"Move gently","media_type":null,"media_url":null,"thumbnail_url":null}]}}"""
        val routineObject = Json.parseToJsonElement(routinePayload).jsonObject.getValue("data").jsonObject
        assertEquals(setOf("id", "title", "description", "duration_min", "is_premium", "media_type", "media_url", "thumbnail_url", "steps"), routineObject.keys)
        assertEquals(setOf("order", "title", "duration_sec", "instruction", "media_type", "media_url", "thumbnail_url"), routineObject.getValue("steps").jsonArray.single().jsonObject.keys)
        assertEquals(30, json.decodeFromString<ApiEnvelope<Routine>>(routinePayload).data!!.steps.single().durationSec)

        val tipsPayload = """{"success":true,"data":[{"id":3,"category":"Safety","text":"Stop if uncomfortable.","media_type":"image","media_url":"https://api.example.test/uploads/t.jpg","thumbnail_url":"https://api.example.test/uploads/t-thumb.jpg"}]}"""
        assertEquals(setOf("id", "category", "text", "media_type", "media_url", "thumbnail_url"), Json.parseToJsonElement(tipsPayload).jsonObject.getValue("data").jsonArray.single().jsonObject.keys)
        json.decodeFromString<ApiEnvelope<List<Tip>>>(tipsPayload)

        val plansPayload = """{"success":true,"data":[{"id":4,"name":"Monthly","product_id":"premium","base_plan_id":"monthly","duration_days":30}]}"""
        assertEquals(setOf("id", "name", "product_id", "base_plan_id", "duration_days"), Json.parseToJsonElement(plansPayload).jsonObject.getValue("data").jsonArray.single().jsonObject.keys)
        assertEquals(30, json.decodeFromString<ApiEnvelope<List<Plan>>>(plansPayload).data!!.single().durationDays)
    }
}
