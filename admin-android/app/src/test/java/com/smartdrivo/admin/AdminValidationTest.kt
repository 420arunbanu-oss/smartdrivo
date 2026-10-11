package com.smartdrivo.admin

import org.junit.Assert.*
import org.junit.Test

class AdminValidationTest {
    @Test fun adminRequiresBothFlags() {
        assertTrue(AdminValidation.isAuthorizedAdmin(true, true))
        assertFalse(AdminValidation.isAuthorizedAdmin(true, false))
        assertFalse(AdminValidation.isAuthorizedAdmin(true, null))
        assertFalse(AdminValidation.isAuthorizedAdmin(false, true))
    }

    @Test fun utrMustHaveExactlyTwelveDigits() {
        assertTrue(AdminValidation.isValidUtr("123456789012"))
        assertFalse(AdminValidation.isValidUtr("12345678901"))
        assertFalse(AdminValidation.isValidUtr("ABC456789012"))
    }

    @Test fun planExpiryExtendsExistingMembership() {
        val day = 86_400_000L
        assertEquals(17 * day, AdminValidation.membershipExpiry(10 * day, 14 * day, "3DAYS"))
        assertEquals(17 * day, AdminValidation.membershipExpiry(10 * day, 5 * day, "7DAYS"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownPlanMustNotAutoApprove() {
        AdminValidation.planDays("UNKNOWN")
    }
}
