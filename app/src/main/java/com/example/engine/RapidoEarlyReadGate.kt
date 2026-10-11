package com.example.engine

/**
 * Bounded read-only recovery when Rapido sends a partially populated offer.
 * No click is authorized by this gate. Acceptance still requires the normal
 * validated Rapido popup, settings, strict button and per-offer click guard.
 */
object RapidoEarlyReadGate {
    const val MAX_FAST_RETRIES = 2

    fun retryDelayMs(retryNumber: Int): Long = when (retryNumber) {
        1 -> 30L
        2 -> 65L
        else -> 0L
    }

    fun mayRetry(partialOfferSeen: Boolean, completedRetries: Int, enabled: Boolean): Boolean =
        partialOfferSeen && enabled && completedRetries in 0 until MAX_FAST_RETRIES

    fun hasPartialOrderSignal(
        fare: Float?, pickupKm: Float?, tripKm: Float?, strictAcceptFound: Boolean
    ): Boolean {
        fun positive(v: Float?): Boolean = v != null && v.isFinite() && v > 0f
        return (positive(fare) &&
            (positive(pickupKm) || positive(tripKm) || strictAcceptFound)) ||
            (strictAcceptFound && (positive(pickupKm) || positive(tripKm)))
    }

    // Underlying Rapido Home texts may coexist with an overlay order card.
    // Only override a Home hint when a COMPLETE popup + Accept target exists.
    fun rejectHomeOverlay(
        homeDetected: Boolean,
        fare: Float?, pickupKm: Float?, tripKm: Float?, strictAcceptFound: Boolean
    ): Boolean {
        if (!homeDetected) return false
        fun positive(v: Float?): Boolean = v != null && v.isFinite() && v > 0f
        return !(positive(fare) && positive(pickupKm) &&
            positive(tripKm) && strictAcceptFound)
    }
}