package com.example.engine

/**
 * One independent session per verified live Rapido popup.
 * History record ID/fingerprint is NOT a safe live popup identity.
 * A new popup gets a fresh first attempt even when History dedupes its ID;
 * an older popup's asynchronous retries cannot click a newer popup.
 * Does not bypass strict Rapido package/Accept node checks at call sites.
 */
class RapidoVerifiedClickGuard {
    private var session = 0L
    private var lastAttempt = 0

    @Synchronized
    fun beginNewPopup(): Long {
        session = if (session == Long.MAX_VALUE) 1L else session + 1L
        lastAttempt = 0
        return session
    }

    @Synchronized
    fun tryAllow(
        popupSession: Long,
        attemptNumber: Int,
        autoAcceptEnabled: Boolean
    ): Boolean {
        if (!autoAcceptEnabled || popupSession <= 0L ||
            popupSession != session || attemptNumber !in 1..5 ||
            attemptNumber != lastAttempt + 1
        ) return false
        lastAttempt = attemptNumber
        return true
    }

    @Synchronized
    fun isCurrent(popupSession: Long): Boolean =
        popupSession > 0L && popupSession == session
}
