package com.example

import com.example.engine.DecisionResult
import com.example.engine.RapidoAutoPriorityFilterEngine
import com.example.engine.RapidoAdapter
import com.example.model.AppSettings
import com.example.model.AreaEntry
import com.example.model.AreaGroup
import com.example.model.AreaType
import com.example.model.FilterMode
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.model.UnlimitedFilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidoAutoPriorityFilterEngineTest {
    private fun ride(fare: Float = 142f, pickup: Float = 1.0f, trip: Float = 4.7f,
                     drop: String = "Falaknuma") = RideCandidate(
        fare = fare, pickupDistKm = pickup, dropDistKm = trip,
        pickupAddress = "Address unavailable", dropAddress = drop, dropArea = drop,
        platform = Platform.RAPIDO, tipAmount = 30f
    )
    private val goto = AreaGroup(
        name = "Home", type = AreaType.GO_TO,
        areas = listOf(AreaEntry(name = "Falaknuma", minFare = 120f,
            maxPickupKm = 1.2f, maxDropKm = 5.0f))
    )
    private val nogo = AreaGroup(
        name = "Avoid", type = AreaType.NO_GO,
        keywords = listOf("Falaknuma")
    )

    @Test fun validNormalFilterAcceptsPriority() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(), AppSettings(filterMode = FilterMode.BOTH, minFare = 100f,
                maxPickupDistanceKm = 1.2f, maxDropDistanceKm = 5.0f)
        )
        assertTrue(result is DecisionResult.Accept)
    }
    @Test fun normalFareDropLimitBlocksPriority() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(), AppSettings(filterMode = FilterMode.BOTH, minFare = 100f,
                maxPickupDistanceKm = 1.2f, maxDropDistanceKm = 3.5f)
        )
        assertTrue(result is DecisionResult.Reject)
    }
    @Test fun activeGoToMatchedWithLimitsAccepts() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(), AppSettings(isGoToEnabled = true), goToAreas = listOf(goto)
        )
        assertTrue(result is DecisionResult.Accept)
        assertTrue((result as DecisionResult.Accept).reason.contains("Go-To"))
    }
    @Test fun activeGoToUnmatchedBlocksEvenIfNormalWouldMatch() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(drop = "Charminar"), AppSettings(isGoToEnabled = true),
            goToAreas = listOf(goto)
        )
        assertTrue(result is DecisionResult.Reject)
        assertTrue((result as DecisionResult.Reject).reason.contains("Go-To"))
    }
    @Test fun noGoBlocksEvenIfGoToAndPlusWouldAccept() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(), AppSettings(isGoToEnabled = true, isNoGoEnabled = true,
                isPlusAmountFilterEnabled = true, plusAmountMin = 10f),
            goToAreas = listOf(goto), noGoAreas = listOf(nogo)
        )
        assertTrue(result is DecisionResult.Reject)
        assertTrue((result as DecisionResult.Reject).reason.contains("No-Go"))
    }
    @Test fun goToFareOrDistanceFailureBlocksPriority() {
        val tooDistant = RapidoAutoPriorityFilterEngine.evaluate(
            ride(pickup = 2.0f), AppSettings(isGoToEnabled = true),
            goToAreas = listOf(goto)
        )
        assertTrue(tooDistant is DecisionResult.Reject)
        val tooCheap = RapidoAutoPriorityFilterEngine.evaluate(
            ride(fare = 80f), AppSettings(isGoToEnabled = true),
            goToAreas = listOf(goto)
        )
        assertTrue(tooCheap is DecisionResult.Reject)
    }
    @Test fun unlimitedStrictAndRuleIsAnAlternative() {
        val rule = UnlimitedFilterRule(id = "ok", enabled = true,
            minimum = 140f, maxPickupKm = 1.1f, maxDropKm = 5.0f)
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(), AppSettings(isPrimaryFilterEnabled = false,
                extraFareRules = listOf(rule))
        )
        assertTrue(result is DecisionResult.Accept)
        assertTrue((result as DecisionResult.Accept).reason.contains("Unlimited"))
    }
    @Test fun plusAmountMatchesWhenOtherNormalFiltersDoNot() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(), AppSettings(isPrimaryFilterEnabled = false,
                isPlusAmountFilterEnabled = true, plusAmountMin = 20f,
                plusAmountMaxPickupDistanceKm = 1.2f,
                plusAmountMaxDropDistanceKm = 5.0f)
        )
        assertTrue(result is DecisionResult.Accept)
        assertTrue((result as DecisionResult.Accept).reason.contains("+ Amount Filter"))
    }
    @Test fun plusAmountCannotOverrideActiveGoTo() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride(drop = "Charminar"), AppSettings(isGoToEnabled = true,
                isPrimaryFilterEnabled = false, isPlusAmountFilterEnabled = true,
                plusAmountMin = 10f), goToAreas = listOf(goto)
        )
        assertTrue(result is DecisionResult.Reject)
    }
    @Test fun unknownValuesNeverProduceAutoAccept() {
        val settings = AppSettings()
        assertTrue(RapidoAutoPriorityFilterEngine.evaluate(
            ride().copy(fare = null), settings) is DecisionResult.Ignore)
        assertTrue(RapidoAutoPriorityFilterEngine.evaluate(
            ride().copy(pickupDistKm = null), settings) is DecisionResult.Ignore)
        assertTrue(RapidoAutoPriorityFilterEngine.evaluate(
            ride().copy(dropDistKm = null), settings) is DecisionResult.Ignore)
        assertTrue(RapidoAutoPriorityFilterEngine.evaluate(
            ride(drop = "Address unavailable"), settings) is DecisionResult.Ignore)
    }
    @Test fun bundleOffLeavesPriorityForManualAction() {
        val result = RapidoAutoPriorityFilterEngine.evaluate(
            ride().copy(isBundledOrder = true), AppSettings(isBundleOrderEnabled = false)
        )
        assertTrue(result is DecisionResult.Ignore)
    }
    @Test fun autoPriorityVisibleTextExtractionProvidesFilterValues() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(listOf(
            "Auto Priority", "₹142", "1.0 km", "Trip: 4.7 km", "Drop: Falaknuma"
        ), skipPickupAddress = true)
        assertEquals(142f, parsed.totalFare, 0.01f)
        assertEquals(1.0f, parsed.pickupKm ?: 0f, 0.01f)
        assertEquals(4.7f, parsed.dropKm ?: 0f, 0.01f)
    }
}
