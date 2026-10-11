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
}
