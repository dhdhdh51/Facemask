package com.jawlinefit.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingContractTest {
    @Test fun `plan duration days match Play billing periods`() {
        assertEquals(7, billingPeriodDays("P1W"))
        assertEquals(30, billingPeriodDays("P1M"))
        assertEquals(90, billingPeriodDays("P3M"))
        assertEquals(365, billingPeriodDays("P1Y"))
        assertEquals(360, billingPeriodDays("P12M"))
        assertNull(billingPeriodDays("P0.5Y"))
        assertNull(billingPeriodDays("monthly"))
    }
}
