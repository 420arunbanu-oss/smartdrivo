package com.example

import com.example.engine.AreaRulesEngine
import com.example.engine.AutoPriorityHistoryGate
import com.example.engine.DecisionResult
import com.example.engine.RapidoVerifiedClickGuard
import com.example.model.AppSettings
import com.example.model.FilterMode
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.model.UnlimitedFilterRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDrivoPendingFixRegressionTest {
    private fun ride(fare: Float = 71f, pickup: Float = 0.4f, drop: Float = 2.7f) =
        RideCandidate(
            fare = fare, pickupDistKm = pickup, dropDistKm = drop,
            pickupAddress = null, dropAddress = "Dabirpura", dropArea = "Dabirpura",
            platform = Platform.RAPIDO
        )

    private fun rule(id: String, min: Float, pickup: Float, drop: Float, enabled: Boolean = true) =
        UnlimitedFilterRule(id, enabled, min, pickup, drop)

    @Test fun thirdUnlimitedRowWinsWhenHomeDropFails() {
        val settings = AppSettings(
            filterMode = FilterMode.DISTANCE_ONLY,
            maxPickupDistanceKm = 0.7f, maxDropDistanceKm = 1.7f,
            extraFareRules = listOf(
                rule("first", 90f, 0.5f, 3f),
                rule("second", 20f, 0.4f, 3f, false),
                rule("third", 68f, 0.6f, 3f)
            )
        )
        val decision = AreaRulesEngine.evaluateRide(ride(), settings = settings)
        assertTrue(decision is DecisionResult.Accept)
        assertTrue((decision as DecisionResult.Accept).reason.contains("Unlimited - Filter 3"))
        assertTrue(decision.reason.contains("2.7 km <= 3.0 km"))
    }

    @Test fun unlimitedRequiresAllThreeConditionsAndRulesAreOr() {
        val settings = AppSettings(
            isPrimaryFilterEnabled = false,
            extraFareRules = listOf(
                rule("fare-fails", 72f, 0.6f, 3f),
                rule("pickup-fails", 68f, 0.3f, 3f),
                rule("drop-fails", 68f, 0.6f, 2.6f)
            )
        )
        assertTrue(AreaRulesEngine.evaluateRide(ride(), settings = settings) is DecisionResult.Reject)
        val plusValid = settings.copy(extraFareRules = settings.extraFareRules + rule("valid", 68f, 0.6f, 3f))
        assertTrue(AreaRulesEngine.evaluateRide(ride(), settings = plusValid) is DecisionResult.Accept)
    }

    @Test fun nextVerifiedOrderNotHeldByPreviousRetries() {
        val guard = RapidoVerifiedClickGuard()
        val first = guard.beginNewPopup()
        assertTrue(guard.tryAllow(first, 1, true))
        assertFalse(guard.tryAllow(first, 1, true))
        assertTrue(guard.tryAllow(first, 2, true))
        assertTrue(guard.tryAllow(first, 3, true))

        // A genuinely new popup must get first click, even if the database
        // dedupes/reuses its History record ID or both trips look identical.
        val second = guard.beginNewPopup()
        assertTrue(guard.tryAllow(second, 1, true))
        assertFalse(guard.tryAllow(first, 4, true)) // stale prior retry
        assertFalse(guard.tryAllow(second, 3, true)) // cannot skip attempt
        assertTrue(guard.tryAllow(second, 2, true))
        assertTrue(guard.tryAllow(second, 3, true))
        assertTrue(guard.tryAllow(second, 4, true))
        assertTrue(guard.tryAllow(second, 5, true))
        assertFalse(guard.tryAllow(second, 6, true))
        assertFalse(guard.tryAllow(second, 5, true))
        assertFalse(guard.tryAllow(0L, 1, true))
        assertFalse(guard.tryAllow(guard.beginNewPopup(), 1, false))
    }

    @Test fun newPopupInvalidatesOldVerificationWithoutIgnoringNewOffer() {
        val guard = RapidoVerifiedClickGuard()
        val oldPopup = guard.beginNewPopup()
        assertTrue(guard.tryAllow(oldPopup, 1, true))
        val currentPopup = guard.beginNewPopup()
        assertFalse(guard.isCurrent(oldPopup))
        assertTrue(guard.isCurrent(currentPopup))
        assertTrue(guard.tryAllow(currentPopup, 1, true))
        assertFalse(guard.tryAllow(oldPopup, 2, true))
        assertTrue(guard.tryAllow(currentPopup, 2, true))
    }

    @Test fun priorityCannotSaveEmptyHistory() {
        val valid = ride(164f).copy(dropAddress = "Kachiguda")
        assertTrue(AutoPriorityHistoryGate.hasUsableHistory(valid))
        assertFalse(AutoPriorityHistoryGate.hasUsableHistory(valid.copy(fare = null)))
        assertFalse(AutoPriorityHistoryGate.hasUsableHistory(valid.copy(fare = 0f)))
        assertFalse(AutoPriorityHistoryGate.hasUsableHistory(valid.copy(dropAddress = "Address unavailable")))
    }
}