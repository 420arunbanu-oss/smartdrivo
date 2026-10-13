package com.example

import com.example.engine.RapidoAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidoOfferParserRegressionTest {
    @Test fun offIsNeverSavedAsDestination() {
        assertTrue(RapidoAdapter.isInvalidAddress("OFF"))
        assertTrue(RapidoAdapter.isInvalidAddress("ON"))
        assertFalse(RapidoAdapter.isInvalidAddress("Shamshabad - Ismail Nagar, Hyderabad"))
    }

    @Test fun duplicatePickupKmNodesDoNotReplaceTripKm() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹129", "0.9 km", "0.9 km", "3.7 km",
                "Milan Function Hall - Limra Mission School, Shah Gunj, Hyderabad"),
            skipPickupAddress = true
        )
        assertEquals(0.9f, parsed.pickupKm ?: -1f, 0.001f)
        assertEquals(3.7f, parsed.dropKm ?: -1f, 0.001f)
    }

    @Test fun tripLabelWinsEvenWhenExtraKmExists() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹163", "Pickup 0.2 km", "Trip 10.3 km", "5.0 km",
                "OFF", "Shamshabad - Ismail Nagar, Chandrayangutta, Hyderabad"),
            skipPickupAddress = true
        )
        assertEquals(0.2f, parsed.pickupKm ?: -1f, 0.001f)
        assertEquals(10.3f, parsed.dropKm ?: -1f, 0.001f)
        assertEquals("Shamshabad - Ismail Nagar, Chandrayangutta, Hyderabad", parsed.dropAddress)
    }

    @Test fun reversedTextOrderUsesVerifiedPickupAndTripIds() {
        val guessed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹128", "6.5 km", "0.9 km", "Shamshabad, Hyderabad"),
            skipPickupAddress = true
        )
        // The raw Accessibility list came in reverse order, but node IDs
        // identify which distance belongs to each field.
        val corrected = RapidoAdapter.reconcileKmByViewId(
            guessed,
            listOf(
                "6.5 km" to "com.rapido.rider:id/tv_trip_distance",
                "0.9 km" to "com.rapido.rider:id/tv_pickup_distance"
            )
        )
        assertEquals(0.9f, corrected.pickupKm ?: -1f, 0.001f)
        assertEquals(6.5f, corrected.dropKm ?: -1f, 0.001f)
    }

    @Test fun oneVerifiedTripIdCanRepairSwappedPair() {
        val guessed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹128", "6.5 km", "0.9 km", "Dabirpura, Hyderabad"),
            skipPickupAddress = true
        )
        val corrected = RapidoAdapter.reconcileKmByViewId(
            guessed,
            listOf("6.5 km" to "com.rapido.rider:id/drop_distance")
        )
        assertEquals(0.9f, corrected.pickupKm ?: -1f, 0.001f)
        assertEquals(6.5f, corrected.dropKm ?: -1f, 0.001f)
    }

    @Test fun unknownViewIdsCannotRewriteDistances() {
        val guessed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹128", "0.9 km", "6.5 km"), skipPickupAddress = true
        )
        val unchanged = RapidoAdapter.reconcileKmByViewId(
            guessed, listOf("6.5 km" to "com.rapido.rider:id/offer_card")
        )
        assertEquals(guessed.pickupKm, unchanged.pickupKm)
        assertEquals(guessed.dropKm, unchanged.dropKm)
    }

    @Test fun conflictingTaggedDistancesNeverBecomeFalseKnownMatch() {
        val guessed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹128", "0.9 km", "6.5 km"), skipPickupAddress = true
        )
        val uncertain = RapidoAdapter.reconcileKmByViewId(
            guessed,
            listOf(
                "6.5 km" to "com.rapido.rider:id/drop_distance",
                "7.2 km" to "com.rapido.rider:id/tv_drop_distance"
            )
        )
        assertEquals(null, uncertain.dropKm)
    }

    @Test fun separateTripAndDistanceLinesStillParse() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("â‚¹130", "Pickup", "0.6 km", "Trip", "3.7 km", "OFF",
                "Dhoolpet - Hara Darwaza, Hyderabad"),
            skipPickupAddress = true
        )
        assertEquals(0.6f, parsed.pickupKm ?: -1f, 0.001f)
        assertEquals(3.7f, parsed.dropKm ?: -1f, 0.001f)
        assertEquals("Dhoolpet - Hara Darwaza, Hyderabad", parsed.dropAddress)
    }

    @Test fun conflictingFarePairsDuringBurstAreNotInventedAsSingleOffer() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("₹104 +₹22", "₹51 +₹9", "Pickup 0.7 km", "Trip 2.7 km"),
            skipPickupAddress = true
        )
        assertEquals(0f, parsed.totalFare, 0.001f)
    }

    @Test fun distinctUnlabeledKmFromOverlappingCardsAreNotGuessed() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("₹126", "0.7 km", "2.7 km", "1.3 km",
                "Falaknuma, Hyderabad"), skipPickupAddress = true
        )
        assertEquals(null, parsed.pickupKm)
        assertEquals(null, parsed.dropKm)
    }

    @Test fun conflictingPickupLabelsAreNotReadAsOneDistance() {
        val parsed = RapidoAdapter.extractOrderDataFromTexts(
            listOf("₹60", "Pickup 0.3 km", "Pickup 0.7 km",
                "Trip 1.7 km", "Charminar, Hyderabad"), skipPickupAddress = true
        )
        assertEquals(null, parsed.pickupKm)
        assertEquals(1.7f, parsed.dropKm ?: -1f, 0.001f)
    }
}
