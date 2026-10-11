package com.example.engine

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.model.VehicleType
import java.util.regex.Pattern

object OlaAdapter {

    private const val TAG = "OlaAdapter"

    val OLA_PACKAGES = listOf(
        "com.ola.partner",
        "com.olacabs.oladriver",
        "com.olacabs.driver",
        "com.olacabs.consumer",
        "com.olacabs.customer",
        "ola.cabs"
    )

    // Method A: Resource IDs for Ola accept
    val ACCEPT_IDS = listOf(
        "com.olacabs.oladriver:id/btn_accept",
        "com.olacabs.oladriver:id/accept_btn",
        "com.olacabs.oladriver:id/accept_button",
        "com.olacabs.oladriver:id/accept_ride_button",
        "com.olacabs.oladriver:id/accept_order",
        "com.olacabs.oladriver:id/acceptOrderBtn",
        "com.olacabs.oladriver:id/btnAccept",
        "com.olacabs.oladriver:id/layout_accept",
        "com.olacabs.oladriver:id/action_accept",
        "com.olacabs.oladriver:id/button_accept",
        "com.olacabs.oladriver:id/buttonAccept",
        "com.olacabs.oladriver:id/slide_to_accept",
        "com.olacabs.oladriver:id/swipe_to_accept",
        "com.olacabs.oladriver:id/swipe_button",
        "com.olacabs.oladriver:id/swipe_btn",
        "com.olacabs.oladriver:id/slider_button",
        "com.olacabs.oladriver:id/slider",
        "com.olacabs.oladriver:id/slide_view",
        "com.olacabs.oladriver:id/fl_accept",
        "com.olacabs.oladriver:id/accept_ride",
        "com.olacabs.oladriver:id/accept_trip",
        "com.olacabs.oladriver:id/btn_accept_ride",
        "com.olacabs.oladriver:id/btn_accept_booking",
        "com.olacabs.oladriver:id/accept_booking",
        "com.olacabs.oladriver:id/accept_request",
        "com.olacabs.oladriver:id/accept_duty",
        "com.olacabs.oladriver:id/tv_accept",
        "com.olacabs.oladriver:id/tvAccept",
        "com.olacabs.oladriver:id/acceptTextView",
        "com.olacabs.oladriver:id/accept_container",
        "com.olacabs.oladriver:id/accept_card",
        "com.olacabs.driver:id/btn_accept",
        "com.olacabs.driver:id/accept_btn",
        "com.olacabs.driver:id/accept_button",
        "com.olacabs.driver:id/accept_ride_button",
        "com.olacabs.driver:id/accept_order",
        "com.olacabs.driver:id/acceptOrderBtn",
        "com.olacabs.driver:id/btnAccept",
        "com.olacabs.driver:id/layout_accept",
        "com.olacabs.driver:id/action_accept",
        "com.olacabs.driver:id/button_accept",
        "com.olacabs.driver:id/buttonAccept",
        "com.olacabs.driver:id/slide_to_accept",
        "com.olacabs.driver:id/swipe_to_accept",
        "com.olacabs.driver:id/swipe_button",
        "com.olacabs.driver:id/swipe_btn",
        "com.olacabs.driver:id/slider_button",
        "com.olacabs.driver:id/slider",
        "com.olacabs.driver:id/slide_view",
        "com.olacabs.driver:id/fl_accept",
        "com.olacabs.driver:id/accept_ride",
        "com.olacabs.driver:id/accept_trip",
        "com.olacabs.driver:id/btn_accept_ride",
        "com.olacabs.driver:id/btn_accept_booking",
        "com.olacabs.driver:id/accept_booking",
        "com.olacabs.driver:id/accept_request",
        "com.olacabs.driver:id/accept_duty",
        // Bare IDs
        "btn_accept",
        "accept_btn",
        "accept_button",
        "accept_ride_button",
        "accept_order",
        "acceptOrderBtn",
        "btnAccept",
        "layout_accept",
        "action_accept",
        "button_accept",
        "buttonAccept",
        "slide_to_accept",
        "swipe_to_accept",
        "swipe_button",
        "swipe_btn",
        "slider_button",
        "slider",
        "slide_view",
        "fl_accept",
        "accept_ride",
        "accept_trip",
        "btn_accept_ride",
        "btn_accept_booking",
        "accept_booking",
        "accept_request",
        "accept_duty",
        "tv_accept",
        "tvAccept",
        "acceptTextView",
        "accept_container",
        "accept_card",
        "btn_confirm",
        "confirm_btn",
        "confirm_button",
        "button_confirm",
        "confirm_ride",
        "confirm_booking"
    )

    val ACCEPT_TEXTS = listOf(
        "Accept",
        "ACCEPT",
        "Accept Ride",
        "Accept Order",
        "Accept Booking",
        "Accept Trip",
        "Accept Duty",
        "Tap to Accept",
        "Confirm",
        "CONFIRM",
        "Confirm Ride",
        "Confirm Booking",
        "Confirm Trip",
        "Swipe to Accept",
        "Slide to Accept",
        "SLIDE TO ACCEPT",
        "SWIPE TO ACCEPT",
        "TAP TO ACCEPT",
        "Slide to accept",
        "Swipe to accept",
        "Tap to accept",
        // Hindi
        "स्वीकार करें",
        "स्वीकार",
        "राइड स्वीकार करें",
        "ट्रिप स्वीकार करें",
        // Regional
        "ಸ್ವೀಕರಿಸಿ",
        "ஏற்றுக்கொள்",
        "ஏற்கவும்",
        "అంగీకరించు",
        "స్వీకరించు",
        "स्वीकारा",
        "গ্রহণ করুন"
    )

    val ACCEPT_CONTENT_DESCRIPTIONS = listOf(
        "Accept ride",
        "Accept order",
        "Accept booking",
        "Accept trip",
        "Accept",
        "ACCEPT",
        "Slide to accept",
        "Swipe to accept",
        "Tap to accept",
        "स्वीकार करें",
        "स्वीकार",
        "ಸ್ವೀಕರಿಸಿ",
        "ஏற்றுக்கொள்",
        "అంగీకరించు"
    )


    // =========================================================
    // OLA_REFERENCE_SCANNER_V1
    //
    // Derived from observed Ola behaviour in the reference APK:
    // - scan the full Accessibility tree
    // - pickup/green + drop/red positional association
    // - preserve partial data for 2 seconds because Ola may
    //   publish fare / distances / addresses in separate events
    // =========================================================

    private const val OLA_CACHE_WINDOW_MS = 2_000L

    private data class OlaTextNode(
        val text: String,
        val centerY: Int
    )

    private data class OlaDistanceNode(
        val km: Float,
        val centerY: Int
    )

    private data class OlaFareParts(
        val base: Float?,
        val tip: Float?,
        val total: Float?
    )

    private data class OlaTransientCache(
        val baseFare: Float? = null,
        val tip: Float? = null,
        val pickupKm: Float? = null,
        val dropKm: Float? = null,
        val pickupAddress: String? = null,
        val dropAddress: String? = null,
        val bookingId: String? = null,
        val timestamp: Long = 0L
    )

    private val olaCacheLock = Any()

    private var olaTransientCache =
        OlaTransientCache()

    private val OLA_RUPEE_REGEX =
        Regex("""₹\s*([0-9]+(?:\.[0-9]+)?)""")

    private val OLA_KM_REGEX =
        Regex(
            """\b([0-9]+(?:\.[0-9]+)?)\s*km\b""",
            RegexOption.IGNORE_CASE
        )

    private val OLA_METER_REGEX =
        Regex(
            """\b([0-9]+(?:\.[0-9]+)?)\s*m\b""",
            RegexOption.IGNORE_CASE
        )

    fun clearTransientCache() {
        synchronized(olaCacheLock) {
            olaTransientCache =
                OlaTransientCache()
        }
    }

    private fun collectOlaTextNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<OlaTextNode>
    ) {
        if (node == null) return

        val rect = Rect()

        try {
            node.getBoundsInScreen(rect)
        } catch (_: Exception) {
        }

        val centerY =
            if (!rect.isEmpty)
                rect.centerY()
            else
                0

        val text =
            node.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (text.isNotBlank()) {
            out +=
                OlaTextNode(
                    text = text,
                    centerY = centerY
                )
        }

        val desc =
            node.contentDescription
                ?.toString()
                ?.trim()
                .orEmpty()

        if (
            desc.isNotBlank() &&
            !desc.equals(text, ignoreCase = false)
        ) {
            out +=
                OlaTextNode(
                    text = desc,
                    centerY = centerY
                )
        }

        for (i in 0 until node.childCount) {
            val child =
                try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }

            if (child != null) {
                collectOlaTextNodes(
                    child,
                    out
                )

                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun collectAllNodeTexts(
        root: AccessibilityNodeInfo
    ): List<String> {
        val nodes =
            mutableListOf<OlaTextNode>()

        collectOlaTextNodes(
            root,
            nodes
        )

        return nodes
            .map { it.text }
            .distinct()
    }

    fun scoreOrderRoot(
        root: AccessibilityNodeInfo
    ): Int {
        val texts =
            collectAllNodeTexts(root)

        if (texts.isEmpty()) {
            return Int.MIN_VALUE
        }

        val fullText =
            texts.joinToString(" \n ")

        val lower =
            fullText.lowercase()

        var score = 0

        if (fullText.contains("₹")) {
            score += 100
        }

        val kmCount =
            OLA_KM_REGEX
                .findAll(fullText)
                .count()

        if (kmCount >= 2) {
            score += 80
        } else if (kmCount == 1) {
            score += 30
        }

        if (
            texts.any {
                isAcceptText(it) ||
                    isAcceptDesc(it)
            } ||
            lower.contains("confirm")
        ) {
            score += 120
        }

        if (
            lower.contains("pickup") ||
            lower.contains("green")
        ) {
            score += 20
        }

        if (
            lower.contains("drop") ||
            lower.contains("destination") ||
            lower.contains("red")
        ) {
            score += 20
        }

        return score
    }

    private fun extractOlaFare(
        nodes: List<OlaTextNode>
    ): OlaFareParts {

        var baseFare: Float? = null
        var tip: Float? = null

        for (node in nodes) {

            val raw =
                node.text.trim()

            val lower =
                raw.lowercase()

            if (!raw.contains("₹")) {
                continue
            }

            // Ignore price-per-km/rate text.
            if (
                lower.contains("/km") ||
                lower.contains("per km") ||
                lower.contains("₹/km") ||
                lower.contains("discount")
            ) {
                continue
            }

            val amounts =
                OLA_RUPEE_REGEX
                    .findAll(raw)
                    .mapNotNull {
                        it.groupValues
                            .getOrNull(1)
                            ?.toFloatOrNull()
                    }
                    .toList()

            if (amounts.isEmpty()) {
                continue
            }

            if (lower.contains("tip")) {

                val foundTip =
                    amounts.sum()

                tip =
                    maxOf(
                        tip ?: 0f,
                        foundTip
                    )

                continue
            }

            if (
                raw.contains("+") &&
                amounts.size >= 2
            ) {

                val candidateBase =
                    amounts.first()

                val candidateTip =
                    amounts.drop(1).sum()

                baseFare =
                    maxOf(
                        baseFare ?: 0f,
                        candidateBase
                    )

                tip =
                    maxOf(
                        tip ?: 0f,
                        candidateTip
                    )

            } else {

                val candidateBase =
                    amounts.first()

                baseFare =
                    maxOf(
                        baseFare ?: 0f,
                        candidateBase
                    )
            }
        }

        val total =
            when {
                baseFare != null ->
                    baseFare + (tip ?: 0f)

                tip != null ->
                    tip

                else ->
                    null
            }

        return OlaFareParts(
            base = baseFare,
            tip = tip,
            total = total
        )
    }

    private fun extractOlaDistance(
        text: String
    ): Float? {

        val lower =
            text.lowercase()

        if (
            lower.contains("km/h") ||
            lower.contains("kmph") ||
            lower.contains("km/hr")
        ) {
            return null
        }

        val km =
            OLA_KM_REGEX
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()

        if (
            km != null &&
            km >= 0f
        ) {
            return km
        }

        val meters =
            OLA_METER_REGEX
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()

        if (
            meters != null &&
            meters >= 0f
        ) {
            return meters / 1000f
        }

        return null
    }

    private fun isOlaAddressCandidate(
        text: String
    ): Boolean {

        val clean =
            text.trim()

        if (clean.length < 4) {
            return false
        }

        val lower =
            clean.lowercase()

        if (
            clean.contains("₹") ||
            OLA_KM_REGEX.containsMatchIn(clean) ||
            OLA_METER_REGEX.containsMatchIn(clean)
        ) {
            return false
        }

        if (
            Regex(
                """\b[0-9]+\s*(?:min|mins|minute|minutes)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(clean)
        ) {
            return false
        }

        val blocked =
            listOf(
                "pickup",
                "pick up",
                "drop",
                "destination",
                "accept",
                "confirm",
                "pass",
                "skip",
                "close",
                "dismiss",
                "cash",
                "payment",
                "pay online",
                "online",
                "mini",
                "prime",
                "sedan",
                "sniper",
                "logs",
                "discount",
                "away"
            )

        if (
            blocked.any {
                lower == it ||
                    lower.startsWith("$it:") ||
                    lower.startsWith("$it ")
            }
        ) {
            return false
        }

        if (
            lower == "x" ||
            lower == "✕"
        ) {
            return false
        }

        // Address should contain at least one letter.
        return clean.any {
            it.isLetter()
        }
    }

    private fun nearestIndex(
        values: List<Pair<Int, Int>>,
        targetY: Int?,
        excluded: Set<Int> = emptySet()
    ): Int? {

        val candidates =
            values.filter {
                it.first !in excluded
            }

        if (candidates.isEmpty()) {
            return null
        }

        if (
            targetY == null ||
            targetY <= 0
        ) {
            return candidates
                .minByOrNull { it.second }
                ?.first
        }

        return candidates
            .minByOrNull {
                kotlin.math.abs(
                    it.second - targetY
                )
            }
            ?.first
    }

    fun extractCandidate(
        root: AccessibilityNodeInfo,
        defaultVehicle: VehicleType =
            VehicleType.AUTO
    ): RideCandidate {

        val detectedAt =
            System.currentTimeMillis()

        val nodes =
            mutableListOf<OlaTextNode>()

        collectOlaTextNodes(
            root,
            nodes
        )

        val textLines =
            nodes
                .map { it.text }
                .distinct()

        val fullText =
            textLines.joinToString(" \n ")

        val fareParts =
            extractOlaFare(nodes)

        val pickupLabelY =
            nodes.firstOrNull {
                val lower =
                    it.text.lowercase()

                lower.contains("pickup") ||
                    lower.contains("pick up") ||
                    lower == "green" ||
                    lower.contains("green pin")
            }?.centerY

        val dropLabelY =
            nodes.firstOrNull {
                val lower =
                    it.text.lowercase()

                lower.contains("drop") ||
                    lower.contains("destination") ||
                    lower == "red" ||
                    lower.contains("red pin")
            }?.centerY

        val distanceNodes =
            nodes.mapNotNull { node ->

                val km =
                    extractOlaDistance(
                        node.text
                    )
                        ?: return@mapNotNull null

                OlaDistanceNode(
                    km = km,
                    centerY = node.centerY
                )
            }
                .distinctBy {
                    "${it.centerY}:${"%.3f".format(it.km)}"
                }
                .sortedBy {
                    it.centerY
                }

        val distancePositions =
            distanceNodes.mapIndexed {
                    index,
                    node ->

                index to node.centerY
            }

        val pickupDistanceIndex =
            nearestIndex(
                values = distancePositions,
                targetY = pickupLabelY
            )

        val dropDistanceIndex =
            nearestIndex(
                values = distancePositions,
                targetY = dropLabelY,
                excluded =
                    pickupDistanceIndex
                        ?.let { setOf(it) }
                        ?: emptySet()
            )

        var pickupKm =
            pickupDistanceIndex
                ?.let {
                    distanceNodes
                        .getOrNull(it)
                        ?.km
                }

        var dropKm =
            dropDistanceIndex
                ?.let {
                    distanceNodes
                        .getOrNull(it)
                        ?.km
                }

        // When labels aren't exposed, Ola normally displays
        // pickup first and trip/drop second.
        if (
            pickupKm == null &&
            distanceNodes.isNotEmpty()
        ) {
            pickupKm =
                distanceNodes[0].km
        }

        if (
            dropKm == null &&
            distanceNodes.size >= 2
        ) {
            dropKm =
                distanceNodes[1].km
        }

        val addressNodes =
            nodes.filter {
                isOlaAddressCandidate(
                    it.text
                )
            }

        val addressPositions =
            addressNodes.mapIndexed {
                    index,
                    node ->

                index to node.centerY
            }

        val pickupAddressIndex =
            nearestIndex(
                values = addressPositions,
                targetY = pickupLabelY
            )

        val dropAddressIndex =
            nearestIndex(
                values = addressPositions,
                targetY = dropLabelY,
                excluded =
                    pickupAddressIndex
                        ?.let { setOf(it) }
                        ?: emptySet()
            )

        var pickupAddress =
            pickupAddressIndex
                ?.let {
                    addressNodes
                        .getOrNull(it)
                        ?.text
                }

        var dropAddress =
            dropAddressIndex
                ?.let {
                    addressNodes
                        .getOrNull(it)
                        ?.text
                }

        if (
            pickupAddress.isNullOrBlank() &&
            addressNodes.isNotEmpty()
        ) {
            pickupAddress =
                addressNodes.first().text
        }

        if (
            dropAddress.isNullOrBlank() &&
            addressNodes.size >= 2
        ) {
            dropAddress =
                addressNodes
                    .firstOrNull {
                        it.text != pickupAddress
                    }
                    ?.text
        }

        val bookingId =
            OrderDataExtractor
                .extractBookingId(fullText)

        var baseFare =
            fareParts.base

        var tip =
            fareParts.tip

        var totalFare =
            fareParts.total

        // -----------------------------------------------------
        // 2-second partial-data cache.
        // Ola can update parts of the card in separate
        // TYPE_WINDOW_CONTENT_CHANGED events.
        // -----------------------------------------------------
        synchronized(olaCacheLock) {

            val old =
                olaTransientCache

            val cacheFresh =
                detectedAt -
                    old.timestamp <=
                    OLA_CACHE_WINDOW_MS

            // Reuse cache only for positively identified same booking.
            val sameOrder =
                !old.bookingId.isNullOrBlank() &&
                !bookingId.isNullOrBlank() &&
                old.bookingId == bookingId

            if (
                cacheFresh &&
                sameOrder
            ) {

                baseFare =
                    baseFare
                        ?: old.baseFare

                tip =
                    tip
                        ?: old.tip

                if (totalFare == null) {
                    totalFare =
                        when {
                            baseFare != null ->
                                baseFare +
                                    (tip ?: 0f)

                            else ->
                                null
                        }
                }

                pickupKm =
                    pickupKm
                        ?: old.pickupKm

                dropKm =
                    dropKm
                        ?: old.dropKm

                pickupAddress =
                    pickupAddress
                        ?: old.pickupAddress

                dropAddress =
                    dropAddress
                        ?: old.dropAddress
            }

            val currentHasUsefulData =
                fareParts.total != null ||
                    distanceNodes.isNotEmpty() ||
                    !pickupAddress.isNullOrBlank() ||
                    !dropAddress.isNullOrBlank()

            if (currentHasUsefulData) {

                olaTransientCache =
                    OlaTransientCache(
                        baseFare = baseFare,
                        tip = tip,
                        pickupKm = pickupKm,
                        dropKm = dropKm,
                        pickupAddress =
                            pickupAddress,
                        dropAddress =
                            dropAddress,
                        bookingId = bookingId,
                        timestamp =
                            detectedAt
                    )
            }
        }

        val safePickup =
            pickupAddress
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Address unavailable"

        val safeDrop =
            dropAddress
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Address unavailable"

        val dropArea =
            if (
                safeDrop !=
                "Address unavailable"
            ) {
                safeDrop
                    .substringBefore(",")
                    .trim()
                    .ifBlank {
                        "Main City Area"
                    }
            } else {
                "Main City Area"
            }

        val vehicle =
            OrderDataExtractor
                .detectVehicleType(
                    fullText,
                    defaultVehicle
                )

        return RideCandidate(
            fare = totalFare,
            pickupDistKm = pickupKm,
            dropDistKm = dropKm,
            pickupAddress = safePickup,
            dropAddress = safeDrop,
            dropArea = dropArea,
            platform = Platform.OLA,
            vehicleType = vehicle,
            bookingId = bookingId,
            baseFare = baseFare,
            tipAmount = tip,
            detectionTimeMs = detectedAt
        )
    }
    private val ACCEPT_REGEX = Pattern.compile(
        "(?:tap\\s*to\\s*|swipe\\s*to\\s*|slide\\s*to\\s*)?accept(?:\\s*(?:ride|order|booking|trip|duty))?",
        Pattern.CASE_INSENSITIVE
    )
    private val REJECT_REGEX = Pattern.compile(
        "^(?:tap\\s*to\\s*|swipe\\s*to\\s*)?(?:reject|decline|pass|skip)(?:\\s*(?:ride|order|booking|trip))?$",
        Pattern.CASE_INSENSITIVE
    )

    fun isAcceptText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.trim().lowercase()
        if (lower.contains("don't accept") || lower.contains("do not accept") || lower.contains("cannot accept")) return false
        if (lower.contains("reject") || lower.contains("decline") || lower.contains("cancel") || lower.contains("skip")) return false

        if (lower.contains("accept") || lower.contains("slide to") || lower.contains("swipe to") || lower.contains("tap to accept")) {
            return true
        }
        if (ACCEPT_TEXTS.any { lower.contains(it.lowercase()) }) return true
        if (text.contains("स्वीकार") || text.contains("ಸ್ವೀಕರಿಸಿ") || text.contains("ஏற்க") || text.contains("అంగీకరించు") || text.contains("स्वीकारा") || text.contains("গ্রহণ")) return true
        if (ACCEPT_REGEX.matcher(text.trim()).find()) return true
        return false
    }

    fun isAcceptDesc(desc: String?): Boolean {
        if (desc.isNullOrBlank()) return false
        val lower = desc.trim().lowercase()
        if (lower.contains("don't accept") || lower.contains("do not accept") || lower.contains("cannot accept")) return false
        if (lower.contains("reject") || lower.contains("decline") || lower.contains("cancel") || lower.contains("skip")) return false

        if (lower.contains("accept") || lower.contains("slide to") || lower.contains("swipe to") || lower.contains("tap to accept")) {
            return true
        }
        if (ACCEPT_CONTENT_DESCRIPTIONS.any { lower.contains(it.lowercase()) }) return true
        if (desc.contains("स्वीकार") || desc.contains("ಸ್ವೀಕರಿಸಿ") || desc.contains("ஏற்க") || desc.contains("అంగీకరించు") || desc.contains("स्वीकारा")) return true
        return false
    }

    fun isSlideOrSwipe(node: AccessibilityNodeInfo): Boolean {
        val txt = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        val combined = "$txt $desc $id".lowercase()
        return combined.contains("slide") || combined.contains("swipe") || combined.contains("slider")
    }

    fun resolveClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (node.isClickable) return node
        var curr: AccessibilityNodeInfo? = node.parent
        while (curr != null) {
            if (curr.isClickable) return curr
            val parent = curr.parent
            curr = parent
        }
        return node
    }

    fun findAcceptNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Direct text search queries for accept
        for (q in listOf(
            "Accept", "ACCEPT", "Accept Ride", "Accept Booking", "Accept Order", "Accept Trip",
            "Tap to Accept",
        "Confirm",
        "CONFIRM",
        "Confirm Ride",
        "Confirm Booking",
        "Confirm Trip", "Swipe to Accept", "Slide to Accept",
            "स्वीकार करें", "स्वीकार", "राइड स्वीकार करें"
        )) {
            val list = root.findAccessibilityNodeInfosByText(q)
            if (!list.isNullOrEmpty()) {
                val target = resolveClickableTarget(list[0])
                for (i in 1 until list.size) list[i].recycle()
                Log.i(TAG, "✓ Ola Accept detected via text query '$q'")
                return target
            }
        }

        // 2. Resource IDs
        for (resId in ACCEPT_IDS) {
            val list = root.findAccessibilityNodeInfosByViewId(resId)
            if (!list.isNullOrEmpty()) {
                val target = resolveClickableTarget(list[0])
                for (i in 1 until list.size) list[i].recycle()
                Log.i(TAG, "✓ Ola Accept detected via Resource ID: '$resId'")
                return target
            }
        }

        // 3. Scan clickable nodes for accept text in self or any subtree child
        val clickableAccept = scanClickableNodesForAccept(root)
        if (clickableAccept != null) {
            Log.i(TAG, "✓ Ola Accept detected via Clickable Node Scan")
            return clickableAccept
        }

        // 4. Recursive text match across all nodes
        val textMatched = searchNode(root) { isAcceptText(it) }
        if (textMatched != null) {
            val target = resolveClickableTarget(textMatched)
            Log.i(TAG, "✓ Ola Accept detected via Text Match: '${textMatched.text}'")
            return target
        }

        // 5. Recursive content description match across all nodes
        val descMatched = searchNodeByDesc(root) { isAcceptDesc(it) }
        if (descMatched != null) {
            val target = resolveClickableTarget(descMatched)
            Log.i(TAG, "✓ Ola Accept detected via Content Desc: '${descMatched.contentDescription}'")
            return target
        }

        return null
    }

    private fun scanClickableNodesForAccept(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val clickableList = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, clickableList)

        for (node in clickableList) {
            val txt = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            if (isAcceptText(txt) || isAcceptDesc(desc)) {
                return node
            }
            val childTexts = mutableListOf<String>()
            collectChildTexts(node, childTexts)
            val combined = childTexts.joinToString(" ")
            if (isAcceptText(combined)) {
                return node
            }
        }
        return null
    }

    private fun collectChildTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            child.text?.toString()?.trim()?.let { if (it.isNotEmpty()) outList.add(it) }
            child.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) outList.add(it) }
            collectChildTexts(child, outList)
            child.recycle()
        }
    }

    fun findRejectNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return searchNode(root) { text -> REJECT_REGEX.matcher(text).find() }
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo?, outList: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) outList.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) collectClickableNodes(child, outList)
        }
    }

    private fun searchNode(node: AccessibilityNodeInfo?, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString()?.trim().orEmpty()
        if (txt.isNotEmpty() && predicate(txt)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val matched = searchNode(child, predicate)
            if (matched != null) return matched
            child?.recycle()
        }
        return null
    }

    private fun searchNodeByDesc(node: AccessibilityNodeInfo?, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && predicate(desc)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val matched = searchNodeByDesc(child, predicate)
            if (matched != null) return matched
            child?.recycle()
        }
        return null
    }
}

