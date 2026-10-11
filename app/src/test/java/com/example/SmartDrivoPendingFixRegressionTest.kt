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
        assertTrue(guard.tryAllow("order1", 1, true))
        assertFalse(guard.tryAllow("order1", 1, true))
        assertTrue(guard.tryAllow("order1", 2, true))
        assertTrue(guard.tryAllow("order1", 3, true))
        assertTrue(guard.tryAllow("order2", 1, true))
        assertFalse(guard.tryAllow("order2", 3, true))
        assertTrue(guard.tryAllow("order2", 2, true))
        assertFalse(guard.tryAllow("order2", 6, true))
        assertFalse(guard.tryAllow("order3", 1, false))
    }

    @Test fun priorityCannotSaveEmptyHistory() {
        val valid = ride(164f).copy(dropAddress = "Kachiguda")
        assertTrue(AutoPriorityHistoryGate.hasUsableHistory(valid))
        assertFalse(AutoPriorityHistoryGate.hasUsableHistory(valid.copy(fare = null)))
        assertFalse(AutoPriorityHistoryGate.hasUsableHistory(valid.copy(fare = 0f)))
        assertFalse(AutoPriorityHistoryGate.hasUsableHistory(valid.copy(dropAddress = "Address unavailable")))
    }
}