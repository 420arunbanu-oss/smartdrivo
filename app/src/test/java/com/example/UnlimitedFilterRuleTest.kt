package com.example

import com.example.model.UnlimitedFilterRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlimitedFilterRuleTest {
    private val filter = UnlimitedFilterRule("f1", true, 80f, 0.6f, 3.5f)

    @Test fun strictAndRequiresAllValues() {
        assertTrue(filter.matches(80f, 0.6f, 3.5f))
        assertFalse(filter.matches(79f, 0.6f, 3.5f))
        assertFalse(filter.matches(80f, 0.7f, 3.5f))
        assertFalse(filter.matches(80f, 0.6f, 3.6f))
        assertFalse(filter.matches(80f, null, 3.5f))
        assertFalse(filter.matches(null, 0.6f, 3.5f))
    }

    @Test fun disabledRulesCannotMatch() {
        assertFalse(filter.copy(enabled = false).matches(100f, 0.1f, 1f))
    }

    @Test fun independentRowsAreOr() {
        val second = UnlimitedFilterRule("f2", true, 100f, 1.5f, 5f)
        assertTrue(listOf(filter, second).any { it.matches(110f, 1.4f, 4.9f) })
        assertFalse(listOf(filter, second).any { it.matches(40f, 1.4f, 4.9f) })
    }
}