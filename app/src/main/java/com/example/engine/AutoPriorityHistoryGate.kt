package com.example.engine

import com.example.model.RideCandidate

/** Never save fake ₹0 / address-unavailable Auto Priority accepted cards. */
object AutoPriorityHistoryGate {
    fun hasUsableHistory(candidate: RideCandidate): Boolean {
        val fare = candidate.fare
        val drop = candidate.dropAddress?.trim().orEmpty()
        return fare != null && fare.isFinite() && fare > 0f &&
            drop.isNotBlank() &&
            !drop.equals("Address unavailable", ignoreCase = true) &&
            !drop.equals("Detected Drop Location", ignoreCase = true) &&
            !drop.equals("Drop Location", ignoreCase = true)
    }
}