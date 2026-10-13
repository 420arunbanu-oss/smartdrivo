package com.example

import com.example.engine.RapidoEarlyReadGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RapidoEarlyReadGateTest {
    @Test fun trueHomeScreenNeverLooksLikeOffer() {
        assertTrue(RapidoEarlyReadGate.rejectHomeOverlay(
            homeDetected = true, fare = 0f, pickupKm = null,
            tripKm = null, strictAcceptFound = false
        ))
        assertFalse(RapidoEarlyReadGate.hasPartialOrderSignal(0f, null, null, false))
    }

    @Test fun completeVerifiedPopupAboveHomeIsNotRejected() {
        assertFalse(RapidoEarlyReadGate.rejectHomeOverlay(
            homeDetected = true, fare = 98f, pickupKm = 0.2f,
            tripKm = 2.9f, strictAcceptFound = true
        ))
    }

    @Test fun homePlusUnverifiedOrIncompleteOfferStillRejected() {
        assertTrue(RapidoEarlyReadGate.rejectHomeOverlay(true, 98f, 0.2f, 2.9f, false))
        assertTrue(RapidoEarlyReadGate.rejectHomeOverlay(true, 98f, null, 2.9f, true))
        assertTrue(RapidoEarlyReadGate.rejectHomeOverlay(true, 98f, 0.2f, null, true))
        assertFalse(RapidoEarlyReadGate.rejectHomeOverlay(false, null, null, null, false))
    }

    @Test fun partialOfferWithFareAndPickupArmsLimitedRecheck() {
        assertTrue(RapidoEarlyReadGate.hasPartialOrderSignal(98f, 0.2f, null, false))
        assertTrue(RapidoEarlyReadGate.hasPartialOrderSignal(98f, null, 2.9f, false))
        assertFalse(RapidoEarlyReadGate.hasPartialOrderSignal(null, null, null, false))
        assertFalse(RapidoEarlyReadGate.hasPartialOrderSignal(0f, null, null, true))
    }

    @Test fun retriesAreStrictlyBoundedAndKeepMasterOffDisabled() {
        assertEquals(2, RapidoEarlyReadGate.MAX_FAST_RETRIES)
        assertTrue(RapidoEarlyReadGate.mayRetry(true, 0, true))
        assertTrue(RapidoEarlyReadGate.mayRetry(true, 1, true))
        assertFalse(RapidoEarlyReadGate.mayRetry(true, 2, true))
        assertFalse(RapidoEarlyReadGate.mayRetry(true, 0, false))
        assertFalse(RapidoEarlyReadGate.mayRetry(false, 0, true))
        assertEquals(30L, RapidoEarlyReadGate.retryDelayMs(1))
        assertEquals(65L, RapidoEarlyReadGate.retryDelayMs(2))
        assertEquals(0L, RapidoEarlyReadGate.retryDelayMs(3))
    }
}