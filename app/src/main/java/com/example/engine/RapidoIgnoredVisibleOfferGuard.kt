package com.example.engine

import com.example.model.Platform
import com.example.model.RideCandidate
import java.util.Locale
import kotlin.math.abs

/**
 * Remembers one still-visible, already-ignored Rapido offer (Auto Reject OFF).
 * Repeated accessibility events may change its pickup distance as the driver
 * moves. Do not convert those events into separate History records.
 *
 * Clear only on a confirmed Rapido Home transition, master OFF, or an offer
 * that differs in stable details. Never infer a new booking from pickup km.
 */
class RapidoIgnoredVisibleOfferGuard {
    private var lastIgnored: RideCandidate? = null

    @Synchronized
    fun isDuplicateIgnored(candidate: RideCandidate): Boolean {
        val previous = lastIgnored ?: return false
        val duplicate = sameVisibleOffer(previous, candidate)
        if (!duplicate) lastIgnored = null
        return duplicate
    }

    @Synchronized
    fun rememberIgnored(candidate: RideCandidate) {
        if (candidate.platform == Platform.RAPIDO) lastIgnored = candidate
    }

    @Synchronized
    fun clear() {
        lastIgnored = null
    }

    companion object {
        fun sameVisibleOffer(previous: RideCandidate, current: RideCandidate): Boolean {
            if (previous.platform != Platform.RAPIDO || current.platform != Platform.RAPIDO) return false

            val oldId = previous.bookingId?.trim().orEmpty()
            val newId = current.bookingId?.trim().orEmpty()
            if (oldId.isNotEmpty() && newId.isNotEmpty()) {
                return oldId.equals(newId, ignoreCase = true)
            }

            fun close(a: Float?, b: Float?, tolerance: Float): Boolean =
                a != null && b != null && a.isFinite() && b.isFinite() && abs(a - b) <= tolerance

            if (!close(previous.fare, current.fare, 0.01f)) return false
            if (!close(previous.dropDistKm, current.dropDistKm, 0.11f)) return false
            if (previous.isBundledOrder != current.isBundledOrder) return false

            // Keep different base-fare/tip offers distinct when both values exist.
            if (previous.baseFare != null && current.baseFare != null &&
                !close(previous.baseFare, current.baseFare, 0.01f)
            ) return false
            if (previous.tipAmount != null && current.tipAmount != null &&
                !close(previous.tipAmount, current.tipAmount, 0.01f)
            ) return false

            fun cleanAddress(value: String?): String {
                val cleaned = value.orEmpty().trim().lowercase(Locale.ROOT)
                    .replace(Regex("\\s+"), " ")
                return if (cleaned in setOf(
                        "", "address unavailable", "drop location", "detected drop location"
                    )) "" else cleaned
            }

            val oldDrop = cleanAddress(previous.dropAddress)
            val newDrop = cleanAddress(current.dropAddress)
            if (oldDrop.isNotEmpty() && newDrop.isNotEmpty() && oldDrop != newDrop) return false

            // Pickup changes (e.g. 0.8 to 0.7 km) are intentionally ignored.
            return true
        }
    }
}
