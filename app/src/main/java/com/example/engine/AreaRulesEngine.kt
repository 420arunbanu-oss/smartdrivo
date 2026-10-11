package com.example.engine

import androidx.compose.runtime.Immutable
import com.example.model.AreaGroup
import com.example.model.AppSettings
import com.example.model.FilterMode
import com.example.model.RideCandidate

sealed class DecisionResult {
    @Immutable
    data class Accept(val reason: String) : DecisionResult()

    @Immutable
    data class Reject(val reason: String) : DecisionResult()

    @Immutable
    data class Ignore(val reason: String) : DecisionResult()
}

object AreaRulesEngine {

    fun evaluateRide(
        candidate: RideCandidate,
        areas: List<AreaGroup> = emptyList(),
        settings: AppSettings,
        goToAreas: List<AreaGroup> = emptyList(),
        noGoAreas: List<AreaGroup> = emptyList(),
        pickupLocationTextOverride: String? = null,
        isGoToEnabled: Boolean = settings.isGoToEnabled,
        isNoGoEnabled: Boolean = settings.isNoGoEnabled
    ): DecisionResult {
        val pickupText =
            (pickupLocationTextOverride ?: candidate.pickupAddress.orEmpty())
                .trim()
                .lowercase()

        val dropText =
            "${candidate.dropAddress.orEmpty()} ${candidate.dropArea.orEmpty()}"
                .trim()
                .lowercase()

        if (candidate.isBundledOrder && !settings.isBundleOrderEnabled) {
            return DecisionResult.Ignore(
                "Bundle Order OFF  |  Manual action"
            )
        }

        // NO GO: DROP / DESTINATION ONLY.
        // Group name is a label only. Pickup location must NOT trigger No-Go.
        if (isNoGoEnabled) {
            val activeNoGo = noGoAreas.filter {
                it.isEnabled && it.keywords.isNotEmpty()
            }

            for (group in activeNoGo) {
                val words = group.keywords
                    .map { it.trim() }
                    .filter {
                        it.isNotBlank() &&
                        !it.equals(group.name, ignoreCase = true)
                    }

                for (word in words) {
                    if (Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word.lowercase()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(dropText)) {
                        return DecisionResult.Reject(
                            "No-Go Area: destination matches '$word' in '${group.name}'"
                        )
                    }
                }
            }
        }
        // GO TO PRIORITY:
        // 1. Destination/drop can match ANY active Go-To group.
        // 2. Every active group works independently and keeps its own limits.
        // 3. If multiple groups match, accept when ANY matching group passes all 3 limits.
        // 4. Global Fare/Distance/Fastest filters are bypassed while Go-To priority is active.
        if (isGoToEnabled) {
            val activeGoTo = goToAreas.filter { it.isEnabled }

            if (activeGoTo.isNotEmpty()) {
                data class GoToMatch(
                    val group: com.example.model.AreaGroup,
                    val area: com.example.model.AreaEntry
                )

                val matches = mutableListOf<GoToMatch>()

                for (group in activeGoTo) {
                    // New per-area format.
                    if (group.areas.isNotEmpty()) {
                        for (area in group.areas) {
                            val word = area.name.trim()
                            if (
                                word.isNotBlank() &&
                                Regex(
                                    "(?<![\\p{L}\\p{N}])" +
                                        Regex.escape(word) +
                                        "(?![\\p{L}\\p{N}])",
                                    RegexOption.IGNORE_CASE
                                ).containsMatchIn(dropText)
                            ) {
                                matches.add(GoToMatch(group, area))
                            }
                        }
                    } else {
                        // Backward compatibility for old saved groups.
                        for (wordRaw in group.keywords) {
                            val word = wordRaw.trim()
                            if (
                                word.isNotBlank() &&
                                !word.equals(group.name, ignoreCase = true) &&
                                Regex(
                                    "(?<![\\p{L}\\p{N}])" +
                                        Regex.escape(word) +
                                        "(?![\\p{L}\\p{N}])",
                                    RegexOption.IGNORE_CASE
                                ).containsMatchIn(dropText)
                            ) {
                                val oldMinFare =
                                    if (group.maxFare > 0f) group.maxFare
                                    else group.minFare

                                matches.add(
                                    GoToMatch(
                                        group,
                                        com.example.model.AreaEntry(
                                            name = word,
                                            minFare = oldMinFare,
                                            maxPickupKm = group.maxPickupKm,
                                            maxDropKm = group.maxDropKm
                                        )
                                    )
                                )
                            }
                        }
                    }
                }

                if (matches.isEmpty()) {
                    return DecisionResult.Reject(
                        "Go-To Area Filter: destination not found in any active Go-To area"
                    )
                }

                val fare = candidate.fare
                val pickup = candidate.pickupDistKm
                val drop = candidate.dropDistKm
                var firstFailure: String? = null

                for (match in matches) {
                    val area = match.area

                    if (
                        area.minFare > 0f &&
                        fare != null &&
                        fare < area.minFare
                    ) {
                        if (firstFailure == null) {
                            firstFailure =
                                "Go-To '${area.name}': fare ₹${fare.toInt()} is below minimum ₹${area.minFare.toInt()}"
                        }
                        continue
                    }

                    if (
                        area.maxPickupKm > 0f &&
                        pickup != null &&
                        pickup > area.maxPickupKm
                    ) {
                        if (firstFailure == null) {
                            firstFailure =
                                "Go-To '${area.name}': pickup ${pickup}km exceeds max ${area.maxPickupKm}km"
                        }
                        continue
                    }

                    if (
                        area.maxDropKm > 0f &&
                        drop != null &&
                        drop > area.maxDropKm
                    ) {
                        if (firstFailure == null) {
                            firstFailure =
                                "Go-To '${area.name}': drop ${drop}km exceeds max ${area.maxDropKm}km"
                        }
                        continue
                    }

                    return DecisionResult.Accept(
                        "Go-To destination matched '${area.name}' and area limits passed"
                    )
                }

                return DecisionResult.Reject(
                    firstFailure ?: "Go-To Area: matched areas did not pass their limits"
                )
            }
        }

        // RAPIDO_TURBO_FASTEST_V3
        // Fastest / Speed Only mode checks ONLY Maximum Pickup Distance.
        // No-Go and Go-To priority above remain unchanged.
        if (settings.isFastestModeEnabled) {
            val pickup =
                candidate.pickupDistKm

            if (
                pickup != null &&
                (
                    settings.maxPickupDistanceKm <= 0f ||
                    pickup <= settings.maxPickupDistanceKm
                )
            ) {
                return DecisionResult.Accept(
                    "Fastest Mode: pickup ${pickup}km matched"
                )
            }

            return DecisionResult.Reject(
                "Fastest Mode: pickup ${
                    pickup?.let { "${it}km" } ?: "unavailable"
                } exceeds/does not match saved maximum ${
                    settings.maxPickupDistanceKm
                }km"
            )
        }
        // Normal Home filter + independent Filter 2 + Filter 3.
        //
        // Home MATCH OR Filter 2 MATCH OR Filter 3 MATCH = ACCEPT.
        // All active choices FAIL = No condition matched.
        //
        // No-Go / Go-To / Fastest rules above keep their existing priority.

        val fare = candidate.fare
        val pickup = candidate.pickupDistKm
        val drop = candidate.dropDistKm

        val homeFareMatches =
            when {
                settings.filterMode == FilterMode.DISTANCE_ONLY ->
                    true

                fare == null || fare <= 0f ->
                    true

                settings.minFare > 0f &&
                    fare < settings.minFare ->
                    false

                settings.maxFare > 0f &&
                    fare > settings.maxFare ->
                    false

                else ->
                    true
            }

        val homeDistanceMatches =
            when {
                settings.filterMode == FilterMode.FARE_ONLY ->
                    true

                pickup != null &&
                    settings.maxPickupDistanceKm > 0f &&
                    pickup > settings.maxPickupDistanceKm ->
                    false

                drop != null &&
                    settings.maxDropDistanceKm > 0f &&
                    drop > settings.maxDropDistanceKm ->
                    false

                else ->
                    true
            }

        // ORDER_FILTER_1_RUNTIME_V1
        val homeMatched =
            settings.isPrimaryFilterEnabled &&
                homeFareMatches &&
                homeDistanceMatches

        val secondaryMatched =
            settings.isSecondaryBothFilterEnabled &&
                fare != null &&
                fare > 0f &&
                pickup != null &&
                drop != null &&
                (
                    settings.secondaryBothMinFare <= 0f ||
                        fare >= settings.secondaryBothMinFare
                ) &&
                (
                    settings.secondaryBothMaxPickupDistanceKm <= 0f ||
                        pickup <= settings.secondaryBothMaxPickupDistanceKm
                ) &&
                (
                    settings.secondaryBothMaxDropDistanceKm <= 0f ||
                        drop <= settings.secondaryBothMaxDropDistanceKm
                )

        val tertiaryMatched =
            settings.isTertiaryBothFilterEnabled &&
                fare != null &&
                fare > 0f &&
                pickup != null &&
                drop != null &&
                (
                    settings.tertiaryBothMinFare <= 0f ||
                        fare >= settings.tertiaryBothMinFare
                ) &&
                (
                    settings.tertiaryBothMaxPickupDistanceKm <= 0f ||
                        pickup <= settings.tertiaryBothMaxPickupDistanceKm
                ) &&
                (
                    settings.tertiaryBothMaxDropDistanceKm <= 0f ||
                        drop <= settings.tertiaryBothMaxDropDistanceKm
                )

        // Unlimited rows are OR with Filter 1/2/3; each row needs all 3 values.
        val unlimitedMatched = settings.extraFareRules.any {
            it.matches(fare, pickup, drop)
        }

        if (
            homeMatched ||
            secondaryMatched ||
            tertiaryMatched ||
            unlimitedMatched
        ) {
            val matched =
                mutableListOf<String>()

            if (homeMatched) {
                matched +=
                    "Order Filter 1"
            }

            if (secondaryMatched) {
                matched += "Filter 2"
            }

            if (tertiaryMatched) {
                matched += "Filter 3"
            }
            if (unlimitedMatched) {
                matched += "Filter Unlimited"
            }

            return DecisionResult.Accept(
                matched.joinToString(" + ") +
                    " matched"
            )
        }

        return DecisionResult.Reject(
            buildString {
                append("No condition matched")

                append(
                    if (settings.isPrimaryFilterEnabled) {
                        "  |  Order Filter 1 failed"
                    } else {
                        "  |  Order Filter 1 OFF"
                    }
                )

                append(
                    if (settings.isSecondaryBothFilterEnabled) {
                        "  |  Filter 2 failed"
                    } else {
                        "  |  Filter 2 OFF"
                    }
                )

                append(
                    if (settings.isTertiaryBothFilterEnabled) {
                        "  |  Filter 3 failed"
                    } else {
                        "  |  Filter 3 OFF"
                    }
                )
            }
        )
    }
    @JvmName("evaluateRideWithStrings")
    fun evaluateRide(
        candidate: RideCandidate,
        areas: List<AreaGroup> = emptyList(),
        settings: AppSettings,
        goToAreas: List<String>,
        noGoAreas: List<String>,
        pickupLocationTextOverride: String? = null,
        isGoToEnabled: Boolean = settings.isGoToEnabled,
        isNoGoEnabled: Boolean = settings.isNoGoEnabled
    ): DecisionResult = evaluateRide(
        candidate = candidate,
        areas = areas,
        settings = settings,
        goToAreas = goToAreas.map {
            AreaGroup(
                name = "Go-To",
                keywords = listOf(it)
            )
        },
        noGoAreas = noGoAreas.map {
            AreaGroup(
                name = "No-Go",
                keywords = listOf(it),
                type = com.example.model.AreaType.NO_GO
            )
        },
        pickupLocationTextOverride = pickupLocationTextOverride,
        isGoToEnabled = isGoToEnabled,
        isNoGoEnabled = isNoGoEnabled
    )
}
