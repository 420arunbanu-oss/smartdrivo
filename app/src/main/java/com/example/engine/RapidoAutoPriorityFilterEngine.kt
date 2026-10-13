package com.example.engine

import com.example.model.AppSettings
import com.example.model.AreaGroup
import com.example.model.RideCandidate

/**
 * Auto Priority is a special Rapido popup, NOT permission to bypass saved
 * filters. No-Go always blocks; enabled Go-To takes its usual priority;
 * otherwise Filter 1/2/3/Unlimited are OR alternatives, and each row is AND.
 * +Amount is an additional OR filter but never overrides No-Go or Go-To.
 */
object RapidoAutoPriorityFilterEngine {
    fun evaluate(
        candidate: RideCandidate,
        settings: AppSettings,
        goToAreas: List<AreaGroup> = emptyList(),
        noGoAreas: List<AreaGroup> = emptyList()
    ): DecisionResult {
        val fare = candidate.fare
        val pickup = candidate.pickupDistKm
        val trip = candidate.dropDistKm
        if (fare == null || !fare.isFinite() || fare <= 0f ||
            pickup == null || !pickup.isFinite() || pickup <= 0f ||
            trip == null || !trip.isFinite() || trip <= 0f
        ) {
            return DecisionResult.Ignore(
                "Auto Priority: incomplete fare/pickup/trip - waiting for full order"
            )
        }
        val drop = candidate.dropAddress?.trim().orEmpty()
        if (drop.isBlank() ||
            drop.equals("Address unavailable", ignoreCase = true) ||
            drop.equals("Drop Location", ignoreCase = true) ||
            drop.equals("Detected Drop Location", ignoreCase = true) ||
            RapidoAdapter.isInvalidAddress(drop)
        ) {
            return DecisionResult.Ignore(
                "Auto Priority: incomplete destination - waiting for full order"
            )
        }

        val normal = AreaRulesEngine.evaluateRide(
            candidate = candidate,
            areas = goToAreas + noGoAreas,
            settings = settings,
            goToAreas = goToAreas,
            noGoAreas = noGoAreas,
            pickupLocationTextOverride = "",
            isGoToEnabled = settings.isGoToEnabled,
            isNoGoEnabled = settings.isNoGoEnabled
        )
        if (normal is DecisionResult.Accept || normal is DecisionResult.Ignore) return normal
        val rejected = normal as? DecisionResult.Reject ?: return normal

        // Only regular 'No condition matched' can be rescued by a matching
        // +Amount row. Never override No-Go, Go-To, Fastest or Bundle gates.
        if (!rejected.reason.startsWith("No condition matched")) return rejected

        val tip = candidate.tipAmount
        val legacyPlusMatched =
            settings.isPlusAmountFilterEnabled &&
                tip != null && tip.isFinite() && tip > 0f &&
                tip >= settings.plusAmountMin &&
                pickup <= settings.plusAmountMaxPickupDistanceKm &&
                trip <= settings.plusAmountMaxDropDistanceKm
        if (legacyPlusMatched) {
            return DecisionResult.Accept(
                "Mode: + Amount Filter | +₹$tip >= +₹${settings.plusAmountMin}" +
                    " | Pickup ${pickup}km <= ${settings.plusAmountMaxPickupDistanceKm}km" +
                    " | Trip ${trip}km <= ${settings.plusAmountMaxDropDistanceKm}km"
            )
        }
        val extraIndex = settings.extraPlusAmountRules.indexOfFirst {
            it.matches(tip, pickup, trip)
        }
        if (extraIndex >= 0) {
            return DecisionResult.Accept(
                "Mode: +Amount Unlimited - Filter ${extraIndex + 1} matched"
            )
        }
        return rejected
    }
}
