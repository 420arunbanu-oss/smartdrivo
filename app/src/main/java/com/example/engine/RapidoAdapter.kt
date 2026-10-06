package com.example.engine

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

data class DetectedButton(
    val node: AccessibilityNodeInfo,
    val buttonId: String,
    val packageName: String,
    val method: String = "Unknown"
)

data class RapidoOrderData(
    val baseFare: Float = 0f,
    val tipAmount: Float = 0f,
    val totalFare: Float = 0f,
    val pickupKm: Float? = null,
    val dropKm: Float? = null,
    val pickupAddress: String? = null,
    val dropAddress: String? = null,
    val bookingId: String? = null,

    // RAPIDO_TURBO_DROP_ONLY_V4
    val hasNearby: Boolean = false,
    val isBundledOrder: Boolean = false
)

data class RapidoPopupValidation(
    val isValid: Boolean,
    val fare: Float? = null,
    val pickupDistKm: Float? = null,
    val hasNearby: Boolean = false,
    val acceptButton: DetectedButton? = null,
    val failureReason: String? = null,

    // RAPIDO_TURBO_DROP_ONLY_V4
    // Reuse this exact parsed order in SmartDrivoAccessibilityService.
    val orderData: RapidoOrderData? = null
)

object RapidoAdapter {

    private const val TAG = "RapidoAdapter"

    val HOME_SCREEN_KEYWORDS = listOf(
        "today's earnings",
        "todays earnings",
        "today's earning",
        "todays earning",
        "today earnings",
        "today earning",
        "on duty",
        "blue performance"
    )

    /**
     * Requirement 3: Check if the root node contains "Today's Earnings" or "ON DUTY" or "Blue Performance"
     * -> this is the HOME SCREEN, skip completely.
     */
    fun isRapidoHomeScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val textList = mutableListOf<String>()
        collectAllNodeTextsForHomeCheck(root, textList)
        return isRapidoHomeScreenTexts(textList)
    }

    fun isRapidoHomeScreenTexts(texts: List<String>): Boolean {
        if (texts.isEmpty()) return false
        for (t in texts) {
            val lower = t.lowercase().trim()
            for (keyword in HOME_SCREEN_KEYWORDS) {
                if (lower.contains(keyword)) {
                    logD(TAG, "🏠 Rapido home screen keyword detected in item: '$keyword' in '$t'")
                    return true
                }
            }
        }
        val combined = texts.joinToString(" ").lowercase()
        for (keyword in HOME_SCREEN_KEYWORDS) {
            if (combined.contains(keyword)) {
                logD(TAG, "🏠 Rapido home screen keyword detected in combined: '$keyword'")
                return true
            }
        }
        return false
    }

    private fun collectAllNodeTextsForHomeCheck(node: AccessibilityNodeInfo?, outList: MutableList<String>, depth: Int = 0) {
        if (node == null || depth > 15 || outList.size > 80) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) outList.add(text)
        if (!desc.isNullOrEmpty()) outList.add(desc)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllNodeTextsForHomeCheck(child, outList, depth + 1)
            }
        }
    }

    /**
     * Checks whether a ₹ amount is located within an "Earnings", "Today's Earnings",
     * or "Performance" text context.
     */
    fun isEarningsContext(texts: List<String>, index: Int): Boolean {
        val current = texts[index].lowercase()
        if (current.contains("earning") || current.contains("today's") || current.contains("todays") ||
            current.contains("today earning") || current.contains("performance") || current.contains("on duty") ||
            current.contains("balance") || current.contains("wallet")
        ) {
            return true
        }
        if (index > 0) {
            val prev = texts[index - 1].lowercase().trim()
            if (prev.contains("earning") || prev.contains("today's") || prev.contains("todays") ||
                prev.contains("today earning") || prev.contains("performance") || prev.contains("balance") ||
                prev.contains("wallet") || prev == "today"
            ) {
                return true
            }
        }
        if (index > 1) {
            val prev2 = texts[index - 2].lowercase().trim()
            if (prev2.contains("today's") || prev2.contains("todays") || prev2.contains("earning") ||
                prev2.contains("performance") || prev2 == "today"
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether an accessibility node is inside a "Today's Earnings" or "Earnings" container.
     */
    fun isNodeInsideEarningsContainer(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val selfText = (node.text?.toString() ?: "").lowercase()
        val selfDesc = (node.contentDescription?.toString() ?: "").lowercase()
        val selfId = (node.viewIdResourceName ?: "").lowercase()

        if (selfText.contains("earning") || selfDesc.contains("earning") || selfId.contains("earning") ||
            selfText.contains("today's") || selfDesc.contains("today's") || selfText.contains("todays") ||
            selfText.contains("performance") || selfDesc.contains("performance") || selfId.contains("performance") ||
            selfText.contains("on duty") || selfDesc.contains("on duty")
        ) {
            return true
        }

        var curr = node.parent
        var depth = 0
        while (curr != null && depth < 3) {
            val pText = (curr.text?.toString() ?: "").lowercase()
            val pDesc = (curr.contentDescription?.toString() ?: "").lowercase()
            val pId = (curr.viewIdResourceName ?: "").lowercase()
            if (pText.contains("earning") || pDesc.contains("earning") || pId.contains("earning") ||
                pText.contains("today's") || pDesc.contains("today's") || pText.contains("todays") ||
                pText.contains("performance") || pDesc.contains("performance") || pId.contains("performance") ||
                pText.contains("on duty") || pDesc.contains("on duty")
            ) {
                return true
            }
            for (i in 0 until curr.childCount) {
                val sib = curr.getChild(i)
                if (sib != null) {
                    val sText = (sib.text?.toString() ?: "").lowercase()
                    val sDesc = (sib.contentDescription?.toString() ?: "").lowercase()
                    val sId = (sib.viewIdResourceName ?: "").lowercase()
                    val isEarning = sText.contains("earning") || sDesc.contains("earning") || sId.contains("earning") ||
                        sText.contains("today's") || sDesc.contains("today's") || sText.contains("todays") ||
                        sText.contains("performance") || sDesc.contains("performance") ||
                        sText.contains("on duty") || sDesc.contains("on duty")
                    if (isEarning) return true
                }
            }
            curr = curr.parent
            depth++
        }
        return false
    }

    private fun logI(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    val KM_REGEX = Regex("""([0-9]+(?:\.[0-9]+)?)\s*km(?!\s*/\s*h|\s*ph|\s*/\s*hr)""", RegexOption.IGNORE_CASE)
    val RUPEE_AMOUNT_REGEX = Pattern.compile("₹\\s*([0-9]+(?:\\.[0-9]+)?)")

    val SMARTDRIVO_UI_BLOCKLIST = listOf(
        "Accepted",
        "Auto-Accept Orders",
        "Max Pickup",
        "Max Drop",
        "ADDRESS",
        "Fare",
        "Distance",
        "Filter",
        "Settings",
        "Home",
        "History",
        "Areas"
    )

    fun matchesBlocklist(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.trim().lowercase()
        return SMARTDRIVO_UI_BLOCKLIST.any { blocked ->
            lower.contains(blocked.lowercase())
        }
    }

    fun isSmartDrivoPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val p = pkg.lowercase().trim()
        return p == "com.smartdrivo.app" ||
               p == "com.example" ||
               p.contains("smartdrivo")
    }

    val FORBIDDEN_ADDRESS_TEXTS = setOf(
        "auto", "services", "nearby",
        "view", "go to", "home", "credit", "orders", "surge",
        "accept", "decline", "reject", "pass", "skip",
        "ride", "trip", "captain", "rapido", "bike", "cab",
        "cash", "online", "payment", "collect cash", "collect",
        "drop", "pickup", "pick up", "from", "to", "destination",
        "driver", "captain app", "earning", "today"
    )

    fun isInvalidAddress(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val clean = text.trim()
        if (clean.length < 2) return true
        val lower = clean.lowercase()

        // Never treat Rapido UI counters as pickup/drop addresses.
        if (
            lower.matches(Regex("""^\d+\s+(?:orders?|rides?|requests?)$""")) ||
            lower.matches(Regex("""^(?:orders?|rides?|requests?)\s*:?\s*\d+$"""))
        ) {
            return true
        }

        // Blocklist of SmartDrivo UI strings that should NEVER be saved as addresses
        if (matchesBlocklist(clean)) return true

        // Requirement: Do NOT use "Auto", "Services", "Nearby" as addresses
        if (lower == "auto" || lower == "services" || lower == "nearby") return true
        if (lower.startsWith("auto ") || lower.startsWith("services ") || lower.startsWith("nearby ")) return true

        if (FORBIDDEN_ADDRESS_TEXTS.contains(lower)) return true

        // Cannot contain currency symbol
        if (clean.contains("₹") || lower.contains("rs.") || lower.contains("inr")) return true

        // Cannot contain km distance
        if (KM_REGEX.containsMatchIn(clean)) return true

        // Cannot be just numbers or rating
        if (clean.matches(Regex("^[0-9]+(?:\\.[0-9]+)?$"))) return true

        // Cannot be time duration e.g. "4 mins away", "5 min", "2 mins"
        if (clean.matches(Regex("^[0-9.]+\\s*(?:min|mins|minute|minutes|sec|secs|hr|hrs)(?:\\s+away)?$", RegexOption.IGNORE_CASE))) return true

        return false
    }

    fun cleanRapidoAddress(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var res = text.trim()
        res = res.replace(KM_REGEX, "").trim()
        res = res.replace(RUPEE_AMOUNT_REGEX.toRegex(), "").trim()
        res = res.replace(Regex("""^[•\-\s:]+"""), "").trim()
        res = res.replace(Regex("""^(?:pickup|drop|pick\s*up|destination|from|to)[\s:]+""", RegexOption.IGNORE_CASE), "").trim()
        res = res.replace(Regex("""^[•\-\s:]+"""), "").trim()
        return res
    }

    data class TextNodeEntry(
        val text: String,
        val bounds: Rect,
        val viewId: String?,
        val packageName: String? = null
    )

    private fun collectEntries(node: AccessibilityNodeInfo?, list: MutableList<TextNodeEntry>, inheritedPkg: String? = null) {
        if (node == null) return
        val nodePkg = node.packageName?.toString()
        val effectivePkg = nodePkg ?: inheritedPkg

        // Strict requirement: Never extract from SmartDrivo UI
        if (isSmartDrivoPackage(effectivePkg)) {
            return
        }

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val content = when {
            !text.isNullOrEmpty() -> text
            !desc.isNullOrEmpty() -> desc
            else -> null
        }
        if (!content.isNullOrEmpty()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            list.add(TextNodeEntry(content, bounds, node.viewIdResourceName, effectivePkg))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectEntries(child, list, effectivePkg)
                child.recycle()
            }
        }
    }

    /**
     * FIX 2 - Stats showing wrong data:
     * In RapidoAdapter.kt, extract fare correctly:
     * - Find node containing "₹" followed by numbers
     * - Format "₹83 +₹26" → base=83, tip=26, total=109
     * - Extract pickup km from node with pattern "[0-9.]+\\s*km" (first occurrence = pickup distance)
     * - Extract drop km (second occurrence = trip distance)
     * - Extract pickup address (text under first km node)
     * - Extract drop address (text under second km node)
     * - Do NOT use "Auto", "Services", "Nearby" as addresses
     */
    fun extractOrderData(root: AccessibilityNodeInfo, skipHomeCheck: Boolean = false, skipPickupAddress: Boolean = false): RapidoOrderData {
        val rootPkg = root.packageName?.toString().orEmpty()
        if (isSmartDrivoPackage(rootPkg) || (!skipHomeCheck && isRapidoHomeScreen(root))) {
            return RapidoOrderData(
                pickupAddress = "Address unavailable",
                dropAddress = "Address unavailable"
            )
        }

        val entries = mutableListOf<TextNodeEntry>()
        collectEntries(root, entries, rootPkg)

        // Only extract address text from Rapido app package (com.rapido.passenger), not from SmartDrivo UI
        val validEntries = entries.filter { entry ->
            val pkg = entry.packageName.orEmpty()
            !isSmartDrivoPackage(pkg) && (pkg.isEmpty() || isRapidoPackage(pkg) || pkg == "com.rapido.passenger")
        }

        val textList = validEntries.map { it.text }
        val data = extractOrderDataFromTexts(textList, skipPickupAddress = skipPickupAddress)

        // View ID based address extraction from node tree (only from valid Rapido nodes)
        var pickupFromId: String? = null
        var dropFromId: String? = null
        for (entry in validEntries) {
            val id = entry.viewId?.lowercase().orEmpty()
            if (pickupFromId == null && (id.contains("pickup") || id.contains("source") || id.contains("start_loc") || id.contains("tv_pickup"))) {
                val clean = cleanRapidoAddress(entry.text)
                if (clean.length >= 3 && !isInvalidAddress(clean)) pickupFromId = clean
            }
            if (dropFromId == null && (id.contains("drop") || id.contains("dest") || id.contains("tv_drop"))) {
                val clean = cleanRapidoAddress(entry.text)
                if (clean.length >= 3 && !isInvalidAddress(clean)) dropFromId = clean
            }
        }

        // Booking ID extraction from text entries
        var bookingId: String? = null
        val bookingRegex = Regex("(?:CRN|ID|#|Booking\\s*ID)[:\\s-]*([A-Za-z0-9-]+)", RegexOption.IGNORE_CASE)
        for (entry in validEntries) {
            val match = bookingRegex.find(entry.text)
            if (match != null) {
                bookingId = match.groupValues[1].trim()
                break
            }
        }

        val rawPickup = pickupFromId ?: data.pickupAddress
        val rawDrop = dropFromId ?: data.dropAddress

        // If extracted address matches any blocklist word → save as "Address unavailable" instead
        val finalPickup = if (rawPickup.isNullOrBlank() || matchesBlocklist(rawPickup) || isInvalidAddress(rawPickup)) {
            "Address unavailable"
        } else {
            rawPickup
        }

        val finalDrop = if (rawDrop.isNullOrBlank() || matchesBlocklist(rawDrop) || isInvalidAddress(rawDrop)) {
            "Address unavailable"
        } else {
            rawDrop
        }

        return data.copy(
            pickupAddress = finalPickup,
            dropAddress = finalDrop,
            bookingId = bookingId
        )
    }

    /**
     * Pure text-based extraction for unit testing and reliable order parsing.
     */
    fun extractOrderDataFromTexts(texts: List<String>, skipPickupAddress: Boolean = false): RapidoOrderData {
        val isBundleOrder = texts.any {
            it.contains("Bundle Order", ignoreCase = true) ||
            it.contains("Bundled Order", ignoreCase = true)
        }

        // 1. Fare extraction - RAPIDO_FARE_ACCURACY_V2
        //
        // Rapido may expose Total Fare, Base Fare and Tip/Bonus together.
        // Never blindly use the first ₹ amount.
        // Final fare = highest valid visible total OR verified base+tip sum.
        var baseFare = 0f
        var tipAmount = 0f
        var totalFare = 0f

        val rupeeMatcher =
            Pattern.compile("₹\\s*([0-9]+(?:\\.[0-9]+)?)")

        val pairRegex =
            Regex("""₹\s*([0-9]+(?:\.[0-9]+)?)\s*\+\s*₹?\s*([0-9]+(?:\.[0-9]+)?)""")

        val nextTipRegex =
            Regex("""^\s*\+\s*₹?\s*([0-9]+(?:\.[0-9]+)?)""")

        val visibleAmounts = mutableListOf<Float>()
        val verifiedPairs = mutableListOf<Triple<Float, Float, Float>>()

        for (i in texts.indices) {
            if (isEarningsContext(texts, i)) {
                continue
            }

            val text = texts[i]

            pairRegex.find(text)?.let { match ->
                val base = match.groupValues[1].toFloatOrNull()
                val tip = match.groupValues[2].toFloatOrNull()

                if (base != null && tip != null && base > 0f && tip >= 0f) {
                    verifiedPairs.add(
                        Triple(base, tip, base + tip)
                    )
                }
            }

            val matcher = rupeeMatcher.matcher(text)

            var firstAmount: Float? = null
            var firstEnd = -1

            while (matcher.find()) {
                val amount =
                    matcher.group(1)?.toFloatOrNull()

                if (amount != null && amount > 0f) {
                    visibleAmounts.add(amount)

                    if (firstAmount == null) {
                        firstAmount = amount
                        firstEnd = matcher.end()
                    }
                }
            }

            if (
                firstAmount != null &&
                firstEnd >= 0 &&
                text.substring(firstEnd).trim().isEmpty() &&
                i + 1 < texts.size
            ) {
                val nextTip =
                    nextTipRegex.find(texts[i + 1].trim())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toFloatOrNull()

                if (nextTip != null && nextTip >= 0f) {
                    verifiedPairs.add(
                        Triple(
                            firstAmount,
                            nextTip,
                            firstAmount + nextTip
                        )
                    )
                }
            }
        }

        val highestVisible =
            visibleAmounts.maxOrNull() ?: 0f

        val bestPair =
            verifiedPairs.maxByOrNull { it.third }

        totalFare =
            maxOf(
                highestVisible,
                bestPair?.third ?: 0f
            )

        val pairForTotal =
            verifiedPairs.firstOrNull {
                kotlin.math.abs(it.third - totalFare) < 0.01f
            }

        if (pairForTotal != null) {
            baseFare = pairForTotal.first
            tipAmount = pairForTotal.second
        } else {
            // Never show misleading Base/Tip values.
            baseFare = totalFare
            tipAmount = 0f
        }

        logI(
            TAG,
            "Rapido fare verified: total=₹$totalFare base=₹$baseFare tip=₹$tipAmount"
        )
        // Bundle cards contain a top combined fare plus per-order component fares.
        // Example: ₹104+ with Order 1 ₹52 and Order 2 ₹52+.
        // Use the highest visible ₹ amount as the bundle total; never sum all component fares.
        if (isBundleOrder) {
            val bundleFareAmounts = mutableListOf<Float>()
            for (i in texts.indices) {
                if (isEarningsContext(texts, i)) continue
                val bundleMatcher = rupeeMatcher.matcher(texts[i])
                while (bundleMatcher.find()) {
                    bundleMatcher.group(1)?.toFloatOrNull()?.let {
                        if (it > 0f) bundleFareAmounts.add(it)
                    }
                }
            }

            val bundleTotal = bundleFareAmounts.maxOrNull()
            if (bundleTotal != null && bundleTotal > 0f) {
                baseFare = bundleTotal
                tipAmount = 0f
                totalFare = bundleTotal
                logI(TAG, "📦 Bundle fare parsed from top/maximum visible amount: ₹$bundleTotal")
            }
        }

        // 2. Distance extraction: pattern [0-9.]+\s*km (excluding speed indicators)
        val kmMatches = mutableListOf<Pair<Int, Float>>()
        for (i in texts.indices) {
            val line = texts[i]
            val sanitized = line.replace(Regex("""[0-9]+(?:\.[0-9]+)?\s*(?:km/h|kmph|km\s*/\s*h|km/hr)\b""", RegexOption.IGNORE_CASE), " ")
            KM_REGEX.findAll(sanitized).forEach { m ->
                val kmVal = m.groupValues[1].toFloatOrNull()
                if (kmVal != null && kmVal > 0f) {
                    kmMatches.add(Pair(i, kmVal))
                }
            }
        }

        // Explicit drop/trip distance detection from labels or text
        var explicitDropKm: Float? = null
        for (line in texts) {
            val sanitized = line.replace(Regex("""[0-9]+(?:\.[0-9]+)?\s*(?:km/h|kmph|km\s*/\s*h|km/hr)\b""", RegexOption.IGNORE_CASE), " ")
            val dropMatch = Regex("""(?:(?:drop|trip|distance|destination)[^\n0-9]*([0-9]+(?:\.[0-9]+)?)\s*km)|(?:([0-9]+(?:\.[0-9]+)?)\s*km[^\n]*(?:drop|trip|distance|destination))""", RegexOption.IGNORE_CASE).find(sanitized)
            if (dropMatch != null) {
                val valStr = dropMatch.groupValues[1].ifEmpty { dropMatch.groupValues[2] }
                val parsed = valStr.toFloatOrNull()
                if (parsed != null && parsed > 0f) {
                    explicitDropKm = parsed
                    break
                }
            }
        }

        // BUG 1: If pickup distance is "Nearby" or unknown, do NOT assign a numeric value to pickupKm!
        val hasNearby = texts.any { it.trim().equals("nearby", ignoreCase = true) || it.contains("nearby", ignoreCase = true) }
        var pickupKm: Float?
        var dropKm: Float?

        if (explicitDropKm != null) {
            dropKm = explicitDropKm
            pickupKm = if (hasNearby) null else kmMatches.map { it.second }.firstOrNull { it != explicitDropKm }
        } else if (hasNearby) {
            // Pickup is explicitly "Nearby", so pickup distance is NOT a numeric value
            pickupKm = null
            // Any km match on screen belongs to drop/trip distance
            dropKm = kmMatches.firstOrNull()?.second
        } else {
            pickupKm = kmMatches.getOrNull(0)?.second
            dropKm = kmMatches.getOrNull(1)?.second
        }

        if (dropKm == null) {
            dropKm = OrderDataExtractor.extractDropDistance(texts, texts.joinToString(" "))
        }

        // A Bundle Order can contain multiple pickup/trip distance pairs:
        // Order 1 pickup, Order 1 trip, Order 2 pickup, Order 2 trip...
        // Use the largest pickup and largest trip/drop so a distant bundled leg
        // cannot be accidentally accepted because only Order 1 was inspected.
        if (isBundleOrder && kmMatches.size >= 2) {
            val bundleDistances = kmMatches.map { it.second }
            val bundlePickup =
                bundleDistances.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull()
            val bundleDrop =
                bundleDistances.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull()

            if (bundlePickup != null && bundlePickup > 0f) {
                pickupKm = bundlePickup
            }
            if (bundleDrop != null && bundleDrop > 0f) {
                dropKm = bundleDrop
            }

            logI(
                TAG,
                "📦 Bundle distances parsed: maxPickup=${pickupKm}km, maxDrop=${dropKm}km"
            )
        }

        // 3. Address extraction:
        var pickupAddress: String? = null
        var dropAddress: String? = null

        // Step 3a: Explicit label prefix checks (e.g. "Pickup: Indiranagar", "Drop: Koramangala")
        for (i in texts.indices) {
            val raw = texts[i].trim()
            val lower = raw.lowercase()
            if (!skipPickupAddress && pickupAddress == null && (lower.startsWith("pickup:") || lower.startsWith("from:") || lower.startsWith("pick up:"))) {
                val clean = cleanRapidoAddress(raw.substringAfter(":"))
                if (clean.length >= 3 && !isInvalidAddress(clean)) pickupAddress = clean
            } else if (!skipPickupAddress && pickupAddress == null && (lower == "pickup" || lower == "from" || lower == "pick up")) {
                if (i + 1 < texts.size) {
                    val clean = cleanRapidoAddress(texts[i + 1])
                    if (clean.length >= 3 && !isInvalidAddress(clean)) pickupAddress = clean
                }
            }

            if (dropAddress == null && (lower.startsWith("drop:") || lower.startsWith("to:") || lower.startsWith("destination:"))) {
                val clean = cleanRapidoAddress(raw.substringAfter(":"))
                if (clean.length >= 3 && !isInvalidAddress(clean)) dropAddress = clean
            } else if (dropAddress == null && (lower == "drop" || lower == "to" || lower == "destination")) {
                if (i + 1 < texts.size) {
                    val clean = cleanRapidoAddress(texts[i + 1])
                    if (clean.length >= 3 && !isInvalidAddress(clean)) dropAddress = clean
                }
            }
        }

        // Step 3b: Proximity to km markers
        val firstKmIdx = kmMatches.getOrNull(0)?.first
        val secondKmIdx = kmMatches.getOrNull(1)?.first

        if (!skipPickupAddress && pickupAddress == null && firstKmIdx != null && !hasNearby) {
            val endSearch = secondKmIdx ?: texts.size
            for (i in (firstKmIdx + 1) until endSearch) {
                val clean = cleanRapidoAddress(texts[i])
                if (clean.length >= 3 && !isInvalidAddress(clean)) {
                    pickupAddress = clean
                    break
                }
            }
        }

        if (dropAddress == null && secondKmIdx != null) {
            for (i in (secondKmIdx + 1) until texts.size) {
                val clean = cleanRapidoAddress(texts[i])
                if (clean.length >= 3 && !isInvalidAddress(clean)) {
                    dropAddress = clean
                    break
                }
            }
        }

        // Step 3c: Filter candidate address strings from all texts
        val candidateLines = mutableListOf<String>()
        for (text in texts) {
            val clean = cleanRapidoAddress(text)
            if (clean.length >= 3 && !isInvalidAddress(clean) && !clean.contains("₹") && !KM_REGEX.containsMatchIn(clean)) {
                candidateLines.add(clean)
            }
        }

        if (!skipPickupAddress && pickupAddress == null && candidateLines.isNotEmpty()) {
            pickupAddress = candidateLines[0]
        }
        if (dropAddress == null && candidateLines.size >= 2) {
            dropAddress = candidateLines.firstOrNull { it != pickupAddress } ?: candidateLines[1]
        }

        // Step 3d: Fallback candidate filtering strictly excluding invalid and blocklisted texts
        if (!skipPickupAddress && pickupAddress == null) {
            pickupAddress = texts.map { cleanRapidoAddress(it) }.firstOrNull {
                it.isNotBlank() && !it.contains("₹") && !it.matches(Regex("^[0-9.]+$")) && !isInvalidAddress(it) && !matchesBlocklist(it)
            }
        }
        if (dropAddress == null) {
            dropAddress = texts.map { cleanRapidoAddress(it) }.filter { it != pickupAddress }.firstOrNull {
                it.isNotBlank() && !it.contains("₹") && !it.matches(Regex("^[0-9.]+$")) && !isInvalidAddress(it) && !matchesBlocklist(it)
            }
        }

        // Requirement 2: If extracted address matches any blocklist word → save as "Address unavailable" instead
        val safePickup =
            if (skipPickupAddress) {
                "Address unavailable"
            } else if (
                pickupAddress.isNullOrBlank() ||
                matchesBlocklist(pickupAddress) ||
                isInvalidAddress(pickupAddress)
            ) {
                "Address unavailable"
            } else {
                pickupAddress
            }

        val safeDrop = if (dropAddress.isNullOrBlank() || matchesBlocklist(dropAddress) || isInvalidAddress(dropAddress)) {
            "Address unavailable"
        } else {
            dropAddress
        }

        return RapidoOrderData(
            baseFare = baseFare,
            tipAmount = tipAmount,
            totalFare = totalFare,
            pickupKm = pickupKm,
            dropKm = dropKm,
            pickupAddress = safePickup,
            dropAddress = safeDrop,
            hasNearby = hasNearby,
            isBundledOrder = isBundleOrder
        )
    }

    // 1. Check ALL these Rapido package names
    val RAPIDO_PACKAGES = listOf(
        "com.rapido.captain",
        "com.rapido.rider",
        "com.rapido.passenger"
    )

    // FIX 1: Rapido Accept Resource IDs (exact order from specification)
    val RAPIDO_ACCEPT_RESOURCE_IDS = listOf(
        "com.rapido.rider:id/accept_order",
        "com.rapido.rider:id/accept_button",
        "com.rapido.rider:id/acceptOrderBtn",
        "com.rapido.rider:id/btn_accept_ride",
        "com.rapido.rider:id/accept_ride_button",
        "com.rapido.rider:id/btn_accept",
        "com.rapido.rider:id/cta_accept",
        "com.rapido.rider:id/layout_accept",
        "com.rapido.rider:id/tv_accept",
        "com.rapido.rider:id/action_accept"
    )

    /**
     * STEP 1 - Find Accept node by Resource ID:
     * Try each ID using findAccessibilityNodeInfosByViewId():
     * Stop at first non-null result.
     */
    fun findAcceptNodeByResourceId(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (resId in RAPIDO_ACCEPT_RESOURCE_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            if (!nodes.isNullOrEmpty()) {
                val node = nodes[0]
                for (i in 1 until nodes.size) nodes[i].recycle()
                Log.i(TAG, "✓ Rapido Accept found via Resource ID: $resId")
                return node
            }
        }
        val currentPkg = root.packageName?.toString().orEmpty()
        for (resId in RAPIDO_ACCEPT_RESOURCE_IDS) {
            val bareId = resId.substringAfter(":id/")
            if (currentPkg.isNotEmpty() && currentPkg != "com.rapido.rider") {
                val qualified = "$currentPkg:id/$bareId"
                val nodes = root.findAccessibilityNodeInfosByViewId(qualified)
                if (!nodes.isNullOrEmpty()) {
                    val node = nodes[0]
                    for (i in 1 until nodes.size) nodes[i].recycle()
                    Log.i(TAG, "✓ Rapido Accept found via Resource ID: $qualified")
                    return node
                }
            }
            val bareNodes = root.findAccessibilityNodeInfosByViewId(bareId)
            if (!bareNodes.isNullOrEmpty()) {
                val node = bareNodes[0]
                for (i in 1 until bareNodes.size) bareNodes[i].recycle()
                Log.i(TAG, "✓ Rapido Accept found via bare Resource ID: $bareId")
                return node
            }
        }
        return null
    }

    // METHOD A: Resource IDs to check (bare IDs requested and known variants)
    val BARE_ACCEPT_IDS = listOf(
        "accept_order",
        "accept_button",
        "acceptOrderBtn",
        "btn_accept_ride",
        "accept_ride_button",
        "btn_accept",
        "cta_accept",
        "layout_accept",
        "tv_accept",
        "action_accept",
        "btnAccept",
        "btn_accept_order",
        "button_accept",
        "accept_btn"
    )

    // METHOD B: Texts to match (Exact & keywords)
    val ACCEPT_TEXTS = listOf(
        "Accept",
        "ACCEPT",
        "Accept Ride",
        "Accept Order",
        "स्वीकार करें",
        "स्वीकार",
        "Tap to Accept",
        "Swipe to Accept"
    )

    // METHOD C: Content descriptions to match
    val ACCEPT_CONTENT_DESCRIPTIONS = listOf(
        "Accept ride",
        "Accept order",
        "Accept",
        "ACCEPT",
        "स्वीकार करें",
        "स्वीकार"
    )

    // Reject / Decline button IDs
    val BARE_REJECT_IDS = listOf(
        "declineOrderBtn",
        "btn_decline",
        "btnReject",
        "rejectOrderBtn",
        "btnDecline",
        "btn_reject",
        "button_decline",
        "button_reject",
        "decline_button",
        "layout_decline",
        "layout_reject",
        "reject_button",
        "tv_decline",
        "tv_reject"
    )

    val REJECT_TEXTS = listOf(
        "Decline",
        "DECLINE",
        "Reject",
        "REJECT",
        "Pass",
        "Skip",
        "अस्वीकार करें",
        "अस्वीकार"
    )

    fun isRapidoPackage(pkgName: String?): Boolean {
        if (pkgName == null) return false
        return RAPIDO_PACKAGES.any { pkgName.equals(it, ignoreCase = true) } ||
                pkgName.contains("rapido", ignoreCase = true)
    }

    // Forbidden node texts: Never click nodes with text: "View", "Services", "Go To", "Home", "Credit", "Orders", "Surge"
    val FORBIDDEN_TEXTS = listOf(
        "View",
        "Services",
        "Go To",
        "Home",
        "Credit",
        "Orders",
        "Surge"
    )

    fun isForbiddenText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return FORBIDDEN_TEXTS.any { forbidden ->
            trimmed.equals(forbidden, ignoreCase = true) ||
            trimmed.contains(forbidden, ignoreCase = true)
        }
    }

    /**
     * Checks if a string matches any of the accept text keywords (case-insensitive for English).
     */
    fun isAcceptText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (isForbiddenText(trimmed)) return false
        val lower = trimmed.lowercase()
        if (lower.contains("don't accept") || lower.contains("do not accept") || lower.contains("cannot accept")) {
            return false
        }
        if (trimmed.equals("Accept", ignoreCase = true)) return true
        if (ACCEPT_TEXTS.any { lower.contains(it.lowercase()) }) return true
        if (trimmed.contains("स्वीकार करें") || trimmed.contains("स्वीकार")) return true
        return false
    }

    /**
     * Checks if a string matches any of the accept content descriptions.
     */
    fun isAcceptDescription(desc: String?): Boolean {
        if (desc.isNullOrBlank()) return false
        val trimmed = desc.trim()
        if (isForbiddenText(trimmed)) return false
        val lower = trimmed.lowercase()
        if (trimmed.equals("Accept", ignoreCase = true)) return true
        if (ACCEPT_CONTENT_DESCRIPTIONS.any { lower.contains(it.lowercase()) }) return true
        if (trimmed.contains("स्वीकार करें") || trimmed.contains("स्वीकार")) return true
        return false
    }

    /**
     * Resolves a clickable target for a matching node.
     * If the node itself is clickable, returns it. Otherwise walks up the ancestor chain
     * to find the nearest clickable parent or container. If none is clickable, returns the node.
     */
    fun resolveClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (node.isClickable) return node
        var curr: AccessibilityNodeInfo? = node.parent
        while (curr != null) {
            if (curr.isClickable) {
                val id = curr.viewIdResourceName?.lowercase() ?: ""
                val cls = curr.className?.toString()?.lowercase() ?: ""
                if (id.contains("card") || id.contains("details") || id.contains("container") ||
                    id.contains("sheet") || id.contains("root") || cls.contains("cardview")) {
                    break
                }
                return curr
            }
            val parent = curr.parent
            curr = parent
        }
        return node
    }

    /**
     * Scans and finds the accept button using MULTIPLE methods:
     * 1. METHOD D: Scan ALL Clickable Nodes on Screen for "Accept" text or children text
     * 2. METHOD B: By TEXT content (case-insensitive, includes Hindi "स्वीकार करें")
     * 3. METHOD C: By Content Description ("Accept ride", "Accept order", etc.)
     * 4. METHOD A: By Resource ID (acceptOrderBtn, btn_accept, accept_ride_button, btnAccept, accept_button, btn_accept_ride)
     * 5. Built-in findAccessibilityNodeInfosByText fallback
     */
    fun findAcceptButton(root: AccessibilityNodeInfo, currentPackage: String? = null): DetectedButton? {
        val rootPkg = currentPackage?.takeIf { it.isNotEmpty() } ?: root.packageName?.toString().orEmpty()

        // -------------------------------------------------------------
        // STEP 1 - Primary Resource ID search (exact specification)
        // -------------------------------------------------------------
        val resIdNode = findAcceptNodeByResourceId(root)
        if (resIdNode != null) {
            val target = resolveClickableTarget(resIdNode)
            val id = resIdNode.viewIdResourceName ?: "accept_resource_id"
            val detectedPkg = target.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
            Log.i(TAG, "✓ Rapido Accept DETECTED via [STEP 1: Resource ID] -> ID: '$id', Package: '$detectedPkg'")
            return DetectedButton(
                node = target,
                buttonId = id,
                packageName = detectedPkg,
                method = "RESOURCE_ID"
            )
        }

        // -------------------------------------------------------------
        // METHOD 1 (Scan ALL clickable nodes on screen)
        // -------------------------------------------------------------
        val clickableAccept = scanAllClickableNodesForAccept(root, rootPkg)
        if (clickableAccept != null) {
            Log.i(
                TAG,
                "✓ Rapido Accept DETECTED via [Method: Clickable Node Scan] -> Text: '${clickableAccept.buttonId}', Package: '${clickableAccept.packageName}'"
            )
            return clickableAccept
        }

        // -------------------------------------------------------------
        // METHOD B: By TEXT content (Deep tree scan across all nodes)
        // -------------------------------------------------------------
        val textMatchedNode = findNodeByTextPredicate(root) { isAcceptText(it) }
        if (textMatchedNode != null) {
            val target = resolveClickableTarget(textMatchedNode)
            val matchedText = textMatchedNode.text?.toString()?.trim() ?: "Accept"
            val id = target.viewIdResourceName ?: textMatchedNode.viewIdResourceName ?: "text_accept"
            val detectedPkg = target.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
            Log.i(
                TAG,
                "✓ Rapido Accept DETECTED via [Method B: Text Content Match] -> Text: '$matchedText', Target ID: '$id', Package: '$detectedPkg'"
            )
            return DetectedButton(
                node = target,
                buttonId = "$id (Text: '$matchedText')",
                packageName = detectedPkg,
                method = "TEXT_CONTENT"
            )
        }

        // -------------------------------------------------------------
        // METHOD C: By Content Description
        // -------------------------------------------------------------
        val descMatchedNode = findNodeByContentDescPredicate(root) { isAcceptDescription(it) }
        if (descMatchedNode != null) {
            val target = resolveClickableTarget(descMatchedNode)
            val matchedDesc = descMatchedNode.contentDescription?.toString()?.trim() ?: "Accept"
            val id = target.viewIdResourceName ?: descMatchedNode.viewIdResourceName ?: "desc_accept"
            val detectedPkg = target.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
            Log.i(
                TAG,
                "✓ Rapido Accept DETECTED via [Method C: Content Description Match] -> Desc: '$matchedDesc', Target ID: '$id', Package: '$detectedPkg'"
            )
            return DetectedButton(
                node = target,
                buttonId = "$id (Desc: '$matchedDesc')",
                packageName = detectedPkg,
                method = "CONTENT_DESC"
            )
        }

        // -------------------------------------------------------------
        // METHOD A: By Resource ID (Check all requested & known IDs)
        // -------------------------------------------------------------
        val packagesToCheck = (listOf(rootPkg) + RAPIDO_PACKAGES).filter { it.isNotEmpty() }.distinct()
        for (bareId in BARE_ACCEPT_IDS) {
            // Check fully qualified across packages
            for (pkg in packagesToCheck) {
                val fullResId = "$pkg:id/$bareId"
                val nodes = root.findAccessibilityNodeInfosByViewId(fullResId)
                if (!nodes.isNullOrEmpty()) {
                    val rawNode = nodes[0]
                    val target = resolveClickableTarget(rawNode)
                    val detectedPkg = target.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: pkg
                    for (i in 1 until nodes.size) nodes[i].recycle()
                    Log.i(
                        TAG,
                        "✓ Rapido Accept DETECTED via [Method A: Resource ID] -> Full ID: '$fullResId', Package: '$detectedPkg'"
                    )
                    return DetectedButton(
                        node = target,
                        buttonId = fullResId,
                        packageName = detectedPkg,
                        method = "RESOURCE_ID"
                    )
                }
            }

            // Check bare ID directly
            val bareNodes = root.findAccessibilityNodeInfosByViewId(bareId)
            if (!bareNodes.isNullOrEmpty()) {
                val rawNode = bareNodes[0]
                val target = resolveClickableTarget(rawNode)
                val detectedPkg = target.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
                for (i in 1 until bareNodes.size) bareNodes[i].recycle()
                Log.i(
                    TAG,
                    "✓ Rapido Accept DETECTED via [Method A: Bare Resource ID] -> ID: '$bareId', Package: '$detectedPkg'"
                )
                return DetectedButton(
                    node = target,
                    buttonId = bareId,
                    packageName = detectedPkg,
                    method = "BARE_RESOURCE_ID"
                )
            }
        }

        // -------------------------------------------------------------
        // METHOD 5: System text search fallback
        // -------------------------------------------------------------
        for (query in listOf("Accept", "ACCEPT", "Accept Ride", "Accept Order", "स्वीकार करें", "स्वीकार")) {
            val list = root.findAccessibilityNodeInfosByText(query)
            if (!list.isNullOrEmpty()) {
                val matched = list[0]
                val target = resolveClickableTarget(matched)
                val detectedPkg = target.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
                val id = target.viewIdResourceName ?: "text_query_$query"
                for (i in 1 until list.size) list[i].recycle()
                Log.i(
                    TAG,
                    "✓ Rapido Accept DETECTED via [Method 5: System Text Search] -> Query: '$query', ID: '$id', Package: '$detectedPkg'"
                )
                return DetectedButton(
                    node = target,
                    buttonId = "$id (Query: '$query')",
                    packageName = detectedPkg,
                    method = "SYSTEM_TEXT_SEARCH"
                )
            }
        }

        Log.d(TAG, "Rapido Accept button NOT found after running all detection methods.")
        return null
    }

    /**
     * Finds and returns the first matching accept AccessibilityNodeInfo.
     */
    fun findAcceptNode(root: AccessibilityNodeInfo, currentPackage: String? = null): AccessibilityNodeInfo? {
        return findAcceptButton(root, currentPackage)?.node
    }

    /**
     * Scans ALL clickable nodes on screen to find any node containing "Accept" or "स्वीकार".
     */
    private fun scanAllClickableNodesForAccept(root: AccessibilityNodeInfo, rootPkg: String): DetectedButton? {
        val clickableList = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, clickableList)

        for (node in clickableList) {
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            // Check node's own text or description
            if (isAcceptText(text) || isAcceptDescription(desc)) {
                val id = node.viewIdResourceName ?: "clickable_node"
                val detectedPkg = node.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
                val label = text ?: desc ?: "Accept"
                return DetectedButton(
                    node = node,
                    buttonId = "$id (Text: '$label')",
                    packageName = detectedPkg,
                    method = "CLICKABLE_NODE_TEXT"
                )
            }

            // Check children's text inside this clickable container
            val childTexts = mutableListOf<String>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val cText = child.text?.toString()?.trim()
                    val cDesc = child.contentDescription?.toString()?.trim()
                    if (isAcceptText(cText) || isAcceptDescription(cDesc)) {
                        child.recycle()
                        val id = node.viewIdResourceName ?: child.viewIdResourceName ?: "clickable_container"
                        val detectedPkg = node.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
                        val matched = cText ?: cDesc ?: "Accept"
                        return DetectedButton(
                            node = node,
                            buttonId = "$id (Child: '$matched')",
                            packageName = detectedPkg,
                            method = "CLICKABLE_CONTAINER_CHILD_TEXT"
                        )
                    }
                    if (!cText.isNullOrEmpty()) childTexts.add(cText)
                    if (!cDesc.isNullOrEmpty()) childTexts.add(cDesc)
                    child.recycle()
                }
            }

            val combined = childTexts.joinToString(" ")
            if (isAcceptText(combined)) {
                val id = node.viewIdResourceName ?: "clickable_combined"
                val detectedPkg = node.packageName?.toString()?.takeIf { it.isNotEmpty() } ?: rootPkg
                return DetectedButton(
                    node = node,
                    buttonId = "$id (Combined: '$combined')",
                    packageName = detectedPkg,
                    method = "CLICKABLE_CONTAINER_COMBINED"
                )
            }
        }
        return null
    }

    /**
     * Tries reject button IDs and text in order.
     */
    fun findRejectNode(root: AccessibilityNodeInfo, currentPackage: String? = null): AccessibilityNodeInfo? {
        val rootPkg = currentPackage?.takeIf { it.isNotEmpty() } ?: root.packageName?.toString().orEmpty()

        // 1. Text & Desc check
        val textNode = findNodeByTextPredicate(root) { text ->
            REJECT_TEXTS.any { text.contains(it, ignoreCase = true) }
        }
        if (textNode != null) {
            val target = resolveClickableTarget(textNode)
            Log.i(TAG, "✓ Rapido Reject DETECTED via text match: '${textNode.text}'")
            return target
        }

        // 2. Resource IDs check
        val packagesToCheck = (listOf(rootPkg) + RAPIDO_PACKAGES).filter { it.isNotEmpty() }.distinct()
        for (bareId in BARE_REJECT_IDS) {
            for (pkg in packagesToCheck) {
                val fullResId = "$pkg:id/$bareId"
                val list = root.findAccessibilityNodeInfosByViewId(fullResId)
                if (!list.isNullOrEmpty()) {
                    val target = resolveClickableTarget(list[0])
                    for (i in 1 until list.size) list[i].recycle()
                    Log.i(TAG, "✓ Rapido Reject DETECTED via ID: '$fullResId'")
                    return target
                }
            }
            val bareList = root.findAccessibilityNodeInfosByViewId(bareId)
            if (!bareList.isNullOrEmpty()) {
                val target = resolveClickableTarget(bareList[0])
                for (i in 1 until bareList.size) bareList[i].recycle()
                Log.i(TAG, "✓ Rapido Reject DETECTED via bare ID: '$bareId'")
                return target
            }
        }
        return null
    }

    /**
     * Collects all clickable nodes in the subtree.
     */
    private fun collectClickableNodes(node: AccessibilityNodeInfo?, outList: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) {
            outList.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectClickableNodes(child, outList)
            }
        }
    }

    /**
     * Finds first node satisfying text predicate.
     */
    private fun findNodeByTextPredicate(node: AccessibilityNodeInfo?, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString()?.trim()
        if (!txt.isNullOrEmpty() && predicate(txt)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val matched = findNodeByTextPredicate(child, predicate)
            if (matched != null) return matched
            child?.recycle()
        }
        return null
    }

    /**
     * Finds first node satisfying contentDescription predicate.
     */
    private fun findNodeByContentDescPredicate(node: AccessibilityNodeInfo?, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && predicate(desc)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val matched = findNodeByContentDescPredicate(child, predicate)
            if (matched != null) return matched
            child?.recycle()
        }
        return null
    }

    /**
     * Requirement 4: Helper to summarize all clickable button texts and descriptions found on screen.
     */
    fun collectClickableButtonSummaries(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val list = mutableListOf<String>()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isClickable) {
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val viewId = node.viewIdResourceName?.substringAfterLast(":id/") ?: node.viewIdResourceName
                val childTexts = mutableListOf<String>()
                for (i in 0 until node.childCount) {
                    val c = node.getChild(i)
                    if (c != null) {
                        c.text?.toString()?.trim()?.let { if (it.isNotEmpty()) childTexts.add(it) }
                        c.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) childTexts.add(it) }
                        c.recycle()
                    }
                }
                val label = when {
                    !text.isNullOrEmpty() -> "'$text'"
                    childTexts.isNotEmpty() -> "'${childTexts.joinToString(" ")}'"
                    !desc.isNullOrEmpty() -> "desc:'$desc'"
                    !viewId.isNullOrEmpty() -> "id:$viewId"
                    else -> node.className?.toString()?.substringAfterLast(".") ?: "View"
                }
                val idSuffix = if (!viewId.isNullOrEmpty() && !label.contains("id:")) " [id:$viewId]" else ""
                list.add("$label$idSuffix")
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    walk(child)
                    child.recycle()
                }
            }
        }
        walk(root)
        return list
    }

    /**
     * Requirement 1, 2, 3, 4:
     * Validates that a genuine Rapido order popup is visible on screen.
     * Must have ALL of these together:
     * 1. A fare amount (₹ symbol with number) that is NOT inside "Today's Earnings" or "Earnings" container
     * 2. A pickup distance in km (e.g. "0.4 km") OR "Nearby"
     * 3. An "Accept" or "ACCEPT" button visible on screen
     *
     * In addition:
     * - If the root node contains "Today's Earnings" or "ON DUTY" or "Blue Performance" -> HOME SCREEN, skip completely.
     */
    fun validateRapidoOrderPopup(root: AccessibilityNodeInfo?): RapidoPopupValidation {
        if (root == null) {
            return RapidoPopupValidation(isValid = false, failureReason = "Null root node")
        }
        val pkg = root.packageName?.toString().orEmpty()
        if (isSmartDrivoPackage(pkg)) {
            return RapidoPopupValidation(isValid = false, failureReason = "SmartDrivo UI package")
        }

        // Requirement 3: Check if root contains "Today's Earnings" or "ON DUTY" or "Blue Performance"
        if (isRapidoHomeScreen(root)) {
            return RapidoPopupValidation(
                isValid = false,
                failureReason = "Home screen detected ('Today's Earnings' / 'ON DUTY' / 'Blue Performance')"
            )
        }

        // RAPIDO_TURBO_DROP_ONLY_V4
        // Parse only once. Pickup street/address is intentionally skipped.
        val orderData =
            extractOrderData(
                root = root,
                skipHomeCheck = true,
                skipPickupAddress = true
            )

        // Requirement 1a: Fare amount outside earnings container
        if (orderData.totalFare <= 0f) {
            return RapidoPopupValidation(
                isValid = false,
                failureReason = "Missing valid fare amount outside earnings container",
                orderData = orderData
            )
        }

        // Requirement 1b: A pickup distance in km OR "Nearby"
        // Reuse the parsed snapshot instead of scanning the tree again.
        val hasNearby =
            orderData.hasNearby

        val hasPickupKm =
            orderData.pickupKm != null &&
                orderData.pickupKm > 0f

        if (!hasPickupKm && !hasNearby) {
            return RapidoPopupValidation(
                isValid = false,
                fare = orderData.totalFare,
                failureReason = "Missing pickup distance in km or 'Nearby'",
                orderData = orderData
            )
        }

        // Requirement 1c: An "Accept" or "ACCEPT" button visible on screen
        val acceptBtn = findAcceptButton(root)
        if (acceptBtn == null) {
            return RapidoPopupValidation(
                isValid = false,
                fare = orderData.totalFare,
                pickupDistKm = orderData.pickupKm,
                hasNearby = hasNearby,
                failureReason = "Missing 'Accept' or 'ACCEPT' button",
                orderData = orderData
            )
        }

        val bounds = Rect()
        acceptBtn.node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return RapidoPopupValidation(
                isValid = false,
                fare = orderData.totalFare,
                pickupDistKm = orderData.pickupKm,
                hasNearby = hasNearby,
                failureReason = "Accept button is not visible on screen (zero dimensions)",
                orderData = orderData
            )
        }

        return RapidoPopupValidation(
            isValid = true,
            fare = orderData.totalFare,
            pickupDistKm = orderData.pickupKm,
            hasNearby = hasNearby,
            acceptButton = acceptBtn,
            orderData = orderData
        )
    }

    /**
     * Text-based validation helper for testing.
     */
    fun validateRapidoOrderPopupFromTexts(texts: List<String>, hasAcceptButton: Boolean): RapidoPopupValidation {
        if (texts.isEmpty()) {
            return RapidoPopupValidation(isValid = false, failureReason = "Empty text list")
        }

        // Requirement 3: Check if contains "Today's Earnings" or "ON DUTY" or "Blue Performance"
        if (isRapidoHomeScreenTexts(texts)) {
            return RapidoPopupValidation(
                isValid = false,
                failureReason = "Home screen detected ('Today's Earnings' / 'ON DUTY' / 'Blue Performance')"
            )
        }

        val orderData = extractOrderDataFromTexts(texts)

        // Requirement 1a: Fare amount outside earnings container
        if (orderData.totalFare <= 0f) {
            return RapidoPopupValidation(
                isValid = false,
                failureReason = "Missing valid fare amount outside earnings container",
                orderData = orderData
            )
        }

        // Requirement 1b: A pickup distance in km OR "Nearby"
        val hasNearby = texts.any { it.trim().equals("nearby", ignoreCase = true) || it.contains("nearby", ignoreCase = true) }
        val hasPickupKm = orderData.pickupKm != null && orderData.pickupKm > 0f

        if (!hasPickupKm && !hasNearby) {
            return RapidoPopupValidation(
                isValid = false,
                fare = orderData.totalFare,
                failureReason = "Missing pickup distance in km or 'Nearby'",
                orderData = orderData
            )
        }

        // Requirement 1c: "Accept" or "ACCEPT" button visible on screen
        if (!hasAcceptButton) {
            return RapidoPopupValidation(
                isValid = false,
                fare = orderData.totalFare,
                pickupDistKm = orderData.pickupKm,
                hasNearby = hasNearby,
                failureReason = "Missing 'Accept' or 'ACCEPT' button",
                orderData = orderData
            )
        }

        return RapidoPopupValidation(
            isValid = true,
            fare = orderData.totalFare,
            pickupDistKm = orderData.pickupKm,
            hasNearby = hasNearby
        )
    }
}

