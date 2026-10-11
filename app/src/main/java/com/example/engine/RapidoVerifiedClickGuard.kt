package com.example.engine

/** Verified Rapido Accept orders bypass unrelated generic click cooldowns,
 * but one order permits only sequential attempts 1 through 5. */
class RapidoVerifiedClickGuard {
    private var orderId: String? = null
    private var lastAttempt = 0

    @Synchronized
    fun tryAllow(orderId: String, attemptNumber: Int, autoAcceptEnabled: Boolean): Boolean {
        if (!autoAcceptEnabled || orderId.isBlank() || attemptNumber !in 1..5) return false
        if (this.orderId != orderId) {
            if (attemptNumber != 1) return false
            this.orderId = orderId
            lastAttempt = 0
        }
        if (attemptNumber != lastAttempt + 1) return false
        lastAttempt = attemptNumber
        return true
    }
}