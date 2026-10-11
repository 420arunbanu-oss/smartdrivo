package com.example

import com.example.engine.AreaRulesEngine
import com.example.engine.DecisionResult
import com.example.model.AppSettings
import com.example.model.FilterMode
import com.example.model.RideCandidate
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderFilter3Test {

    private fun ride(
        fare: Float,
        pickup: Float,
        drop: Float
    ) = RideCandidate(
        fare = fare,
        pickupDistKm = pickup,
        dropDistKm = drop,
        pickupAddress = "Pickup",
        dropAddress = "Destination",
        dropArea = "Destination"
    )

    @Test
    fun homeAndFilter2Fail_filter3Passes_accept() {

        val settings =
            AppSettings(
                filterMode = FilterMode.BOTH,

                minFare = 150f,
                maxFare = 250f,
                maxPickupDistanceKm = 0.2f,
                maxDropDistanceKm = 1.5f,

                isSecondaryBothFilterEnabled = true,
                secondaryBothMinFare = 100f,
                secondaryBothMaxPickupDistanceKm = 0.3f,
                secondaryBothMaxDropDistanceKm = 2.0f,

                isTertiaryBothFilterEnabled = true,
                tertiaryBothMinFare = 70f,
                tertiaryBothMaxPickupDistanceKm = 0.6f,
                tertiaryBothMaxDropDistanceKm = 3.5f,

                isGoToEnabled = false,
                isNoGoEnabled = false
            )

        val result =
            AreaRulesEngine.evaluateRide(
                candidate = ride(
                    fare = 75f,
                    pickup = 0.5f,
                    drop = 3.0f
                ),
                settings = settings
            )

        assertTrue(
            result is DecisionResult.Accept
        )
    }

    @Test
    fun allThreeFail_noConditionMatch() {

        val settings =
            AppSettings(
                filterMode = FilterMode.BOTH,

                minFare = 150f,
                maxPickupDistanceKm = 0.2f,
                maxDropDistanceKm = 1.5f,

                isSecondaryBothFilterEnabled = true,
                secondaryBothMinFare = 100f,
                secondaryBothMaxPickupDistanceKm = 0.3f,
                secondaryBothMaxDropDistanceKm = 2.0f,

                isTertiaryBothFilterEnabled = true,
                tertiaryBothMinFare = 90f,
                tertiaryBothMaxPickupDistanceKm = 0.4f,
                tertiaryBothMaxDropDistanceKm = 2.5f,

                isGoToEnabled = false,
                isNoGoEnabled = false
            )

        val result =
            AreaRulesEngine.evaluateRide(
                candidate = ride(
                    fare = 75f,
                    pickup = 0.5f,
                    drop = 3.0f
                ),
                settings = settings
            )

        assertTrue(
            result is DecisionResult.Reject
        )

        assertTrue(
            (result as DecisionResult.Reject)
                .reason
                .contains(
                    "Filter 3 failed",
                    ignoreCase = true
                )
        )
    }
}