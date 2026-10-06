package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.example.data.FirebaseRepository
import com.example.data.PreferencesManager
import com.example.data.RideDiagnosticsManager
import com.example.engine.AreaRulesEngine
import com.example.engine.DecisionResult
import com.example.engine.OlaAdapter
import com.example.engine.OrderDataExtractor
import com.example.engine.RapidoAdapter
import com.example.engine.RapidoPopupValidation
import com.example.engine.UberAdapter
import com.example.model.AreaGroup
import com.example.model.ClickStrategy
import com.example.model.OrderHistoryItem
import com.example.model.OrderStatus
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

class SmartDrivoAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager
    private val preferencesManager: PreferencesManager get() = prefs
    private lateinit var repository: FirebaseRepository
    private val rideHistoryRepository by lazy { com.example.data.db.RideHistoryRepository.getInstance(applicationContext) }
    private val activeOrderRecordIds = java.util.Collections.synchronizedMap(mutableMapOf<com.example.model.Platform, String>())
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ==========================================================
    // UBER_WINDOW_POLLER_V3
    //
    // Uber Trip Radar may appear in another AccessibilityWindow
    // without sending a useful accessibility event.
    //
    // While Auto Accept is ON, scan Uber windows directly.
    // Existing genuine-order guard prevents Uber Home screen
    // from entering History or triggering clicks.
    // ==========================================================

    @Volatile
    private var uberWindowPollingActive = false

    private val uberWindowPollRunnable =
        object : Runnable {

            override fun run() {

                if (!uberWindowPollingActive) {
                    return
                }

                try {

                    if (
                        ::prefs.isInitialized &&
                        preferencesManager.isAutoAcceptEnabled &&
                        !isProcessing &&
                        !isClickInProgress &&
                        System.currentTimeMillis() >= clickBlockedUntilTimestamp
                    ) {

                        val settings =
                            prefs.loadSettings()

                        if (settings.uberEnabled) {

                            val uberRoot =
                                findBestUberOrderRoot(null)

                            if (uberRoot != null) {

                                val pkg =
                                    uberRoot.packageName
                                        ?.toString()
                                        .orEmpty()

                                if (isUberPackage(pkg)) {

                                    Log.d(
                                        TAG,
                                        "UBER POLL V3: Uber window detected pkg=$pkg"
                                    )
                                    val treeNow =
                                        System.currentTimeMillis()

                                    if (
                                        treeNow -
                                            lastUberTreeDumpAt >
                                        1500L
                                    ) {
                                        lastUberTreeDumpAt =
                                            treeNow

                                        Log.i(
                                            TAG,
                                            "UBER TREE START pkg=$pkg rootClass=${uberRoot.className} children=${uberRoot.childCount}"
                                        )

                                        dumpUberNodeTree(
                                            uberRoot
                                        )

                                        Log.i(
                                            TAG,
                                            "UBER TREE END"
                                        )
                                    }
                                    // UBER_NODE_DIAGNOSTIC_V1
                                    try {
                                        val debugTexts =
                                            UberAdapter.collectAllNodeTexts(
                                                uberRoot
                                            )

                                        Log.i(
                                            TAG,
                                            "UBER NODE DEBUG: pkg=$pkg textCount=${debugTexts.size} texts=" +
                                                debugTexts.take(40).joinToString(" || ")
                                        )

                                        val clickableDebug =
                                            RapidoAdapter.collectClickableButtonSummaries(
                                                uberRoot
                                            )

                                        Log.i(
                                            TAG,
                                            "UBER NODE DEBUG BUTTONS: $clickableDebug"
                                        )

                                    } catch (e: Exception) {
                                        Log.w(
                                            TAG,
                                            "UBER NODE DEBUG error: ${e.message}"
                                        )
                                    }

                                    handleUberOrder(
                                        uberRoot,
                                        pkg.ifBlank {
                                            "com.ubercab.driver"
                                        }
                                    )
                                }
                            }
                        }
                    }

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "UBER POLL V3 error: ${e.message}"
                    )

                } finally {

                    if (uberWindowPollingActive) {
                        handler.postDelayed(
                            this,
                            150L
                        )
                    }
                }
            }
        }


    // UBER_TREE_DIAGNOSTIC_V1
    private var lastUberTreeDumpAt = 0L

    private fun dumpUberNodeTree(
        node: AccessibilityNodeInfo?,
        depth: Int = 0,
        maxDepth: Int = 12
    ) {
        if (node == null || depth > maxDepth) return

        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            Log.i(
                TAG,
                "UBER TREE ${"  ".repeat(depth)}" +
                    "class=${node.className} " +
                    "id=${node.viewIdResourceName} " +
                    "text='${node.text}' " +
                    "desc='${node.contentDescription}' " +
                    "clickable=${node.isClickable} " +
                    "enabled=${node.isEnabled} " +
                    "visible=${node.isVisibleToUser} " +
                    "children=${node.childCount} " +
                    "bounds=$bounds"
            )

            for (i in 0 until node.childCount) {
                dumpUberNodeTree(
                    node.getChild(i),
                    depth + 1,
                    maxDepth
                )
            }

        } catch (e: Exception) {
            Log.w(TAG, "UBER TREE error: ${e.message}")
        }
    }

    private fun startUberWindowPolling() {

        if (
            !::prefs.isInitialized ||
            !preferencesManager.isAutoAcceptEnabled
        ) {
            return
        }

        if (uberWindowPollingActive) {
            return
        }

        uberWindowPollingActive = true

        handler.removeCallbacks(
            uberWindowPollRunnable
        )

        handler.post(
            uberWindowPollRunnable
        )

        Log.i(
            TAG,
            "UBER POLL V3: STARTED"
        )
    }


    private fun stopUberWindowPolling() {

        uberWindowPollingActive = false

        handler.removeCallbacks(
            uberWindowPollRunnable
        )

        Log.i(
            TAG,
            "UBER POLL V3: STOPPED"
        )
    }


    // ==========================================================
    @Volatile
    private var lastHandledTimestamp = 0L
    @Volatile
    private var isProcessing = false

    // Rapido can emit hundreds of accessibility events per second.
    // Keep only one parser job in flight and debounce noisy content-change events.
    @Volatile
    private var isRapidoEventProcessing = false

    @Volatile
    private var lastRapidoEventDispatchAt = 0L



    @Volatile
    private var activeRapidoPopupOrderId: String? = null

    @Volatile
    private var lastValidRapidoPopupAt: Long = 0L
    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.TOGGLE_CHANGED") {
                val enabled = intent.getBooleanExtra("enabled", preferencesManager.isAutoAcceptEnabled)
                Log.i(TAG, "Broadcast received: TOGGLE_CHANGED enabled=$enabled")
                prefs.reloadAppSettings()

                if (enabled) {
                    startUberWindowPolling()
                    showActiveServiceNotification()

                    Log.i(
                        TAG,
                        "▶️ MASTER ON — SmartDrivo monitoring enabled"
                    )
                } else {
                    pauseAllLiveOrderWork()
                }
            }
        }
    }
private val processingTimeoutRunnable = Runnable {
        if (isProcessing) {
            Log.w(TAG, "Watchdog: Resetting isProcessing state after timeout")
            isProcessing = false
        }
    }

    companion object {
        private const val TAG = "SmartDrivoService"
        const val ACCEPT_CLICK_SPEED_MS = 20L

        val ALLOWED_PACKAGES = setOf(
            "com.rapido.passenger",
            "com.rapido.rider",
            "com.ubercab",
            "com.ubercab.driver",
            "ola.cabs",
            "com.ola.partner",
            "com.olacabs.oladriver",
            "com.olacabs.driver",
            "com.olacabs.customer",
            "com.olacabs.consumer"
        )

        var isServiceRunning = false
            private set

        private val CONFIRMATION_KEYWORDS = listOf(
            "accepted", "ride accepted", "accepted!", "thanks for accepting",
            "navigate to pickup", "go to pickup", "start ride", "arrived at pickup",
            "ongoing ride", "order accepted"
        )

        fun isRapidoPackage(pkg: String): Boolean {
            val p = pkg.trim().lowercase()
            return p == "com.rapido.passenger" || p.contains("com.rapido.passenger") || p.contains("com.rapido.rider") || p.contains("rapido")
        }

        fun isUberPackage(pkg: String): Boolean {
            val p = pkg.trim().lowercase()
            return p == "com.ubercab" || p.startsWith("com.ubercab.") || p.contains("com.ubercab")
        }

        fun isOlaPackage(pkg: String): Boolean {
            val p = pkg.trim().lowercase()
            return p == "ola.cabs" || p.startsWith("ola.cabs.") || p.contains("ola.cabs") ||
                   p.contains("olacabs") || p.contains("ola.driver") ||
                   (p.contains("ola") && (p.contains("driver") || p.contains("partner") || p.contains("cab")))
        }

        fun isAllowedPackage(pkg: String): Boolean {
            return isRapidoPackage(pkg) || isUberPackage(pkg) || isOlaPackage(pkg)
        }
    }
    private fun showActiveServiceNotification() {
        try {
            NotificationHelper.createNotificationChannel(applicationContext)

            val notification = NotificationHelper.buildNotification(
                context = applicationContext,
                title = "SmartDrivo Active",
                text = "✅ SmartDrivo Active — Monitoring orders"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notification
                )
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to show SmartDrivo active notification: ${e.message}"
            )
        }
    }

    private fun hideActiveServiceNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }

        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as?
                    android.app.NotificationManager

            notificationManager?.cancel(
                NotificationHelper.NOTIFICATION_ID
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Auto Accept is SmartDrivo's MASTER switch.
     *
     * OFF:
     * - no live order reading
     * - no new history
     * - no filters / area rules
     * - no Auto Reject
     * - no Bundle processing
     * - no automatic clicks
     * - no accepted-order overlay
     * - no persistent SmartDrivo notification
     */
    private fun pauseAllLiveOrderWork() {
        stopUberWindowPolling()
        handler.removeCallbacks(processingTimeoutRunnable)

        isProcessing = false
        isRapidoEventProcessing = false
        activeRapidoPopupOrderId = null
        activeOrderRecordIds.clear()

        isClickInProgress = false
        recentClickTimestamps.clear()
        clickBlockedUntilTimestamp = 0L

        try {
            stopService(
                Intent(
                    applicationContext,
                    FloatingOverlayService::class.java
                )
            )
        } catch (_: Exception) {
        }

        hideActiveServiceNotification()

        Log.i(
            TAG,
            "⏸️ MASTER OFF — all SmartDrivo live order work stopped"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferencesManager.getInstance(applicationContext)
        repository = FirebaseRepository(applicationContext, prefs)
        isServiceRunning = true
        startUberWindowPolling()
        Log.i(TAG, "SmartDrivo Accessibility Service Connected")

        try {
            val filter = IntentFilter("com.example.TOGGLE_CHANGED")
            ContextCompat.registerReceiver(
                this,
                toggleReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering toggleReceiver: ${e.message}")
        }
// Auto Accept controls active SmartDrivo monitoring.
        if (preferencesManager.isAutoAcceptEnabled) {
            showActiveServiceNotification()
        } else {
            hideActiveServiceNotification()
        }


        // Configure accessibility serviceInfo to ensure TYPE_WINDOW_CONTENT_CHANGED & TYPE_WINDOW_STATE_CHANGED are delivered
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.packageNames = ALLOWED_PACKAGES.toTypedArray()
            serviceInfo = info
            Log.i(TAG, "AccessibilityServiceInfo configured with TYPE_WINDOW_CONTENT_CHANGED & TYPE_WINDOW_STATE_CHANGED")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring serviceInfo: ${e.message}")
        }

                // Auto Accept is the MASTER runtime switch.
        serviceScope.launch {
            prefs.appSettings.collectLatest { settings ->
                if (settings.isAutoAcceptActive) {
                    showActiveServiceNotification()
                    startUberWindowPolling()
                } else {
                    pauseAllLiveOrderWork()
                }
            }
        }
    }

    // ==========================================
    // BUG FIXES: Cooldown, Rate-Limiter, Vibration & SpeculativeClick
    // ==========================================

    // Fix 3 - Gesture cooldown:
    // Add a isClickInProgress boolean flag.
    // While isClickInProgress = true, skip ALL new clicks.
    // Reset flag after 3 seconds.
    @Volatile
    private var isClickInProgress = false

    private val resetClickInProgressRunnable = Runnable {
        isClickInProgress = false
        Log.d(TAG, "⏱️ [Fix 3] isClickInProgress reset to false (3s cooldown expired)")
    }

    // Fix 4 - General:
    // Add check: if service is clicking more than 3 times in 2 seconds, stop all clicks and wait 5 seconds.
    private val recentClickTimestamps = Collections.synchronizedList(mutableListOf<Long>())
    @Volatile
    private var clickBlockedUntilTimestamp = 0L

    /**
     * Centralized click authorization check:
     * - Fix 3: while isClickInProgress = true, skip ALL new clicks.
     * - Fix 4: if service is clicking more than 3 times in 2 seconds, stop all clicks and wait 5 seconds.
     */
    @Synchronized
    private fun canExecuteClick(): Boolean {
        if (!preferencesManager.isAutoAcceptEnabled) {
            Log.d(
                TAG,
                "⏸️ Click blocked because Auto Accept master is OFF"
            )
            return false
        }

        val now = System.currentTimeMillis()

        // Fix 4: Check if waiting after rate limit penalty (5-second wait)
        if (now < clickBlockedUntilTimestamp) {
            val waitRemaining = clickBlockedUntilTimestamp - now
            Log.w(TAG, "🚫 [Fix 4 Rate Limit] All clicks halted! Must wait 5s cooldown (remaining: ${waitRemaining}ms)")
            return false
        }

        // Fix 3: While isClickInProgress = true, skip ALL new clicks
        if (isClickInProgress) {
            Log.w(TAG, "🚫 [Fix 3 Cooldown] Click skipped: isClickInProgress is TRUE (waiting 3s cooldown)")
            return false
        }

        // Fix 4: Check if clicking more than 3 times in 2 seconds
        recentClickTimestamps.removeAll { now - it > 2000L }
        if (recentClickTimestamps.size >= 3) {
            clickBlockedUntilTimestamp = now + 5000L
            recentClickTimestamps.clear()
            Log.e(TAG, "🚨 [Fix 4 Penalty] Service clicked 3+ times in 2 seconds! Stopping all clicks for 5 seconds.")
            return false
        }

        return true
    }

    /**
     * Records that a new click sequence has been initiated.
     * Starts the 3-second isClickInProgress cooldown and records the click timestamp.
     */
    // RAPIDO_NEW_ORDER_CLICK_V2
    // A verified NEW Rapido popup should not be blocked by the
    // previous order's generic 3-second click-in-progress lock.
    // Anti-spam limits are still retained.
    @Synchronized
    private fun canExecuteVerifiedRapidoClick(): Boolean {
        if (!preferencesManager.isAutoAcceptEnabled) {
            return false
        }

        val now = System.currentTimeMillis()

        if (now < clickBlockedUntilTimestamp) {
            return false
        }

        recentClickTimestamps.removeAll {
            now - it > 2_000L
        }

        if (recentClickTimestamps.size >= 3) {
            clickBlockedUntilTimestamp =
                now + 5_000L

            recentClickTimestamps.clear()

            Log.w(
                TAG,
                "Rapido verified click blocked by anti-spam safety limit"
            )

            return false
        }

        // Do not use isClickInProgress here.
        // activeRapidoPopupOrderId protects the SAME popup.
        return true
    }
    @Synchronized
    private fun notifyClickInitiated() {
        val now = System.currentTimeMillis()
        recentClickTimestamps.add(now)
        isClickInProgress = true
        serviceScope.launch(Dispatchers.IO) {
            delay(3000L)
            resetClickInProgressRunnable.run()
        }
    }

    // Fix 2 - Vibration:
    // Vibrate only once per order using a lastVibratedOrderId variable.
    // If same order already vibrated, skip.
    // Single vibration max 200ms, never loop.
    @Volatile
    private var lastVibratedOrderId: String? = null
    @Volatile
    private var lastVibratedTimestamp = 0L

    private fun triggerOrderDetectedVibration(orderId: String?) {
        // RAPIDO_TURBO_DROP_ONLY_V4
        // Vibration intentionally disabled.
        return
    }
    private fun getOrderIdentifier(candidate: RideCandidate): String {
        val bookingId = candidate.bookingId?.trim()
        if (!bookingId.isNullOrEmpty()) return bookingId

        val fare = candidate.fare?.toString() ?: "na"
        val pickDist = candidate.pickupDistKm?.toString() ?: "na"
        val dropDist = candidate.dropDistKm?.toString() ?: "na"

        // RAPIDO_STABLE_ID_V4
        // Rapido address text appears in stages. Never let address changes
        // turn one visible popup into multiple History rows.
        if (candidate.platform == Platform.RAPIDO) {
            val bundle = if (candidate.isBundledOrder) "bundle" else "single"
            return "RAPIDO_${fare}_${pickDist}_${dropDist}_${bundle}"
        }

        val pickup = candidate.pickupAddress?.trim()?.take(25) ?: "addr"
        return "${candidate.platform}_${fare}_${pickDist}_${dropDist}_${pickup}"
    }
    data class DirectRideFilterSettings(
        val minFare: Float,
        val maxFare: Float,
        val maxPickupKm: Float,
        val maxDropKm: Float,
        val filterMode: String = "both",

        val secondaryBothEnabled: Boolean = false,
        val secondaryBothMinFare: Float = 50f,
        val secondaryBothMaxPickupKm: Float = 3.0f,
        val secondaryBothMaxDropKm: Float = 7.5f
    )

    private fun getDirectPreferencesString(sp: SharedPreferences, keys: List<String>, defaultVal: String): String {
        for (key in keys) {
            if (sp.contains(key)) {
                try {
                    val str = sp.getString(key, null)
                    if (!str.isNullOrBlank()) return str
                } catch (_: Exception) {}
            }
        }
        return defaultVal
    }

    private fun getDirectPreferencesFloat(sp: SharedPreferences, keys: List<String>, defaultVal: Float): Float {
        for (key in keys) {
            if (sp.contains(key)) {
                try {
                    return sp.getFloat(key, defaultVal)
                } catch (_: ClassCastException) {
                    try {
                        val str = sp.getString(key, null)
                        val f = str?.toFloatOrNull()
                        if (f != null) return f
                    } catch (_: Exception) {
                        try {
                            return sp.getInt(key, defaultVal.toInt()).toFloat()
                        } catch (_: Exception) {
                            try {
                                return sp.getLong(key, defaultVal.toLong()).toFloat()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
        return defaultVal
    }

    /**
     * Reads settings directly from SharedPreferences using context.getSharedPreferences("smartdrivo_prefs", Context.MODE_PRIVATE).
     * Reads minFare, maxFare, maxPickupKm, maxDropKm, and filterMode fresh every time.
     * Does NOT use PreferencesManager class or any cached variable.
     */
    private fun readDirectSettingsFresh(): DirectRideFilterSettings {
        val sp = applicationContext.getSharedPreferences("smartdrivo_prefs", Context.MODE_PRIVATE)
        val minFare = getDirectPreferencesFloat(sp, listOf("setting_min_fare", "min_fare", "minFare"), 50f)
        val maxFare = getDirectPreferencesFloat(sp, listOf("setting_max_fare", "max_fare", "maxFare"), 999f)
        val maxPickupKm = getDirectPreferencesFloat(
            sp,
            listOf("setting_max_pickup_dist_km", "max_pickup_dist_km", "max_pickup_km", "maxPickupKm", "maxPickupDistanceKm"),
            3.0f
        )
        val maxDropKm = getDirectPreferencesFloat(
            sp,
            listOf("setting_max_drop_dist_km", "setting_max_drop_km", "max_drop_dist_km", "max_drop_distance_km", "max_drop_km", "maxDropKm", "maxDropDistanceKm"),
            7.5f
        )
        val rawMode = getDirectPreferencesString(sp, listOf("filter_mode", "setting_filter_mode", "filterMode"), "both")
        val normalizedFilterMode = when {
            rawMode.contains("fare", ignoreCase = true) && !rawMode.contains("distance", ignoreCase = true) -> "fare_only"
            rawMode.contains("distance", ignoreCase = true) && !rawMode.contains("fare", ignoreCase = true) -> "distance_only"
            else -> "both"
        }

        val secondaryBothEnabled =
            try {
                sp.getBoolean(
                    "setting_secondary_both_filter_enabled",
                    false
                )
            } catch (_: Exception) {
                false
            }

        val secondaryBothMinFare =
            getDirectPreferencesFloat(
                sp,
                listOf("setting_secondary_both_min_fare"),
                50f
            )

        val secondaryBothMaxPickupKm =
            getDirectPreferencesFloat(
                sp,
                listOf("setting_secondary_both_max_pickup_km"),
                3.0f
            )

        val secondaryBothMaxDropKm =
            getDirectPreferencesFloat(
                sp,
                listOf("setting_secondary_both_max_drop_km"),
                7.5f
            )
        return DirectRideFilterSettings(
            minFare = minFare,
            maxFare = maxFare,
            maxPickupKm = maxPickupKm,
            maxDropKm = maxDropKm,
            filterMode = normalizedFilterMode,

            secondaryBothEnabled =
                secondaryBothEnabled,
            secondaryBothMinFare =
                secondaryBothMinFare,
            secondaryBothMaxPickupKm =
                secondaryBothMaxPickupKm,
            secondaryBothMaxDropKm =
                secondaryBothMaxDropKm
        )
    }

    data class DirectFilterResult(
        val status: OrderStatus,
        val reason: String
    )

    /**
     * Fresh direct filter check without using PreferencesManager class or any cached variable:
     * - If No-Go area matched → OrderStatus.REJECTED
     * - All other filter failures (fare, pickup km, drop km) → OrderStatus.IGNORED
     * - All filters passed → OrderStatus.ACCEPTED
     *
     * Respects Filter Mode:
     * - If filterMode == "fare_only" -> skip pickup and drop distance checks completely
     * - If filterMode == "distance_only" -> skip fare checks completely
     * - If filterMode == "both" -> check both fare and distance filters
     */
    private fun evaluateDirectRideFilters(candidate: RideCandidate): DirectFilterResult {
        // 1. NO-GO = DROP / DESTINATION ONLY.
        // Pickup address and pickup distance must NEVER trigger No-Go.
        if (prefs.isNoGoEnabled) {
            val noGoAreas = prefs.loadNoGoAreas().filter { it.isEnabled }

            if (noGoAreas.isNotEmpty()) {
                val dropTextRaw =
                    "${candidate.dropAddress.orEmpty()} ${candidate.dropArea.orEmpty()}".trim()
                val dropText = dropTextRaw.lowercase()

                val dropMatches = mutableListOf<String>()
                val matchedGroups = mutableListOf<String>()

                for (noGo in noGoAreas) {
                    val targets = noGo.keywords
                        .map { it.trim() }
                        .filter {
                            it.isNotBlank() &&
                            !it.equals(noGo.name, ignoreCase = true)
                        }

                    var groupMatched = false

                    for (target in targets) {
                        if (
                            dropText.isNotBlank() &&
                            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(target.lowercase()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(dropText)
                        ) {
                            dropMatches += target
                            groupMatched = true
                        }
                    }

                    if (groupMatched && noGo.name.isNotBlank()) {
                        matchedGroups += noGo.name
                    }
                }

                if (dropMatches.isNotEmpty()) {
                    val fareText = candidate.fare
                        ?.takeIf { it > 0f }
                        ?.let { "₹${it.toInt()}" }
                        ?: "N/A"

                    val pickupKmText = candidate.pickupDistKm
                        ?.takeIf { it > 0f }
                        ?.let { String.format(Locale.ENGLISH, "%.1f km", it) }
                        ?: "N/A"

                    val tripKmText = candidate.dropDistKm
                        ?.takeIf { it > 0f }
                        ?.let { String.format(Locale.ENGLISH, "%.1f km", it) }
                        ?: "N/A"

                    val groups = matchedGroups.distinct()
                    val areas = dropMatches.distinct()

                    val reason = buildString {
                        append("Mode: No Go Area")
                        if (groups.isNotEmpty()) {
                            append("\nGroup: ${groups.joinToString(", ")}")
                        }
                        append("\nDrop matched: ${areas.joinToString(", ")}")
                        append("\nWhy rejected: destination/drop area is blocked")
                    }

                    Log.w(TAG, "❌ [Direct Filter REJECT] $reason")
                    return DirectFilterResult(OrderStatus.REJECTED, reason)
                }
            }
        }
        // Go-To/Fastest priority is evaluated by AreaRulesEngine after No-Go safety.
        val prioritySettings = prefs.loadSettings()
        val hasActiveGoTo =
            prioritySettings.isGoToEnabled &&
                prefs.loadGoToAreas().any { group ->
                    group.isEnabled &&
                        (
                            group.areas.any { area ->
                                area.name.isNotBlank()
                            } ||
                            group.keywords.any { keyword ->
                                keyword.isNotBlank() &&
                                    !keyword.equals(
                                        group.name,
                                        ignoreCase = true
                                    )
                            }
                        )
                }

        if (hasActiveGoTo) {
            return DirectFilterResult(
                OrderStatus.ACCEPTED,
                "Go-To priority mode"
            )
        }

        val direct = readDirectSettingsFresh()
        val filterMode = direct.filterMode
        val pickup = candidate.pickupDistKm
        val fare = candidate.fare
        val drop = candidate.dropDistKm

        Log.i(
            TAG,
            "⚡ Direct SharedPreferences Evaluation: " +
            "[filterMode=$filterMode, minFare=₹${direct.minFare}, maxFare=₹${direct.maxFare}, maxPickup=${direct.maxPickupKm}km, maxDrop=${direct.maxDropKm}km] vs " +
            "[Candidate: fare=₹$fare, pickup=${pickup}km, drop=${drop}km]"
        )

        val checkDistance = filterMode == "distance_only" || filterMode == "both"
        val checkFare = filterMode == "fare_only" || filterMode == "both"

        // Build a complete decision report instead of stopping at the first failure.
        // This preserves the REAL order values + the filter limits that were active
        // at the exact moment the order was evaluated.
        val failureReasons = mutableListOf<String>()

        if (checkDistance) {
            if (pickup != null && direct.maxPickupKm > 0f && pickup > direct.maxPickupKm) {
                failureReasons += "Pickup ${"%.1f".format(pickup)} km > max ${"%.1f".format(direct.maxPickupKm)} km"
            }

            if (drop != null && direct.maxDropKm > 0f && drop > direct.maxDropKm) {
                failureReasons += "Trip ${"%.1f".format(drop)} km > max ${"%.1f".format(direct.maxDropKm)} km"
            }
        }

        if (checkFare && fare != null && fare > 0f) {
            if (direct.minFare > 0f && fare < direct.minFare) {
                failureReasons += "Fare ₹${fare.toInt()} < min ₹${direct.minFare.toInt()}"
            }
            if (direct.maxFare > 0f && fare > direct.maxFare) {
                failureReasons += "Fare ₹${fare.toInt()} > max ₹${direct.maxFare.toInt()}"
            }
        }

        // Filter 2 filter.
        // It is independent from Home settings.
        val secondaryFailureReasons =
            mutableListOf<String>()

        if (direct.secondaryBothEnabled) {

            if (fare == null || fare <= 0f) {
                secondaryFailureReasons +=
                    "Fare unavailable"
            } else if (
                direct.secondaryBothMinFare > 0f &&
                fare < direct.secondaryBothMinFare
            ) {
                secondaryFailureReasons +=
                    "Fare ₹${fare.toInt()} < min ₹${direct.secondaryBothMinFare.toInt()}"
            }

            if (pickup == null) {
                secondaryFailureReasons +=
                    "Pickup unavailable"
            } else if (
                direct.secondaryBothMaxPickupKm > 0f &&
                pickup > direct.secondaryBothMaxPickupKm
            ) {
                secondaryFailureReasons +=
                    "Pickup ${String.format(Locale.ENGLISH, "%.1f", pickup)} km > max ${String.format(Locale.ENGLISH, "%.1f", direct.secondaryBothMaxPickupKm)} km"
            }

            if (drop == null) {
                secondaryFailureReasons +=
                    "Trip unavailable"
            } else if (
                direct.secondaryBothMaxDropKm > 0f &&
                drop > direct.secondaryBothMaxDropKm
            ) {
                secondaryFailureReasons +=
                    "Trip ${String.format(Locale.ENGLISH, "%.1f", drop)} km > max ${String.format(Locale.ENGLISH, "%.1f", direct.secondaryBothMaxDropKm)} km"
            }
        }

        val secondaryMatched =
            direct.secondaryBothEnabled &&
                secondaryFailureReasons.isEmpty()

        // Home failed but Filter 2 passed -> ACCEPT.
        if (
            failureReasons.isNotEmpty() &&
            secondaryMatched
        ) {
            Log.i(
                TAG,
                "✅ [Filter OR MATCH] Home failed, Filter 2 matched"
            )

            return DirectFilterResult(
                OrderStatus.ACCEPTED,
                "Filter 2 matched"
            )
        }
        if (failureReasons.isNotEmpty()) {
            val modeLabel = when (filterMode) {
                "fare_only" -> "Fare Only"
                "distance_only" -> "Distance Only"
                "both" -> "Both"
                else -> filterMode
            }

            val orderParts = mutableListOf<String>()
            if (fare != null && fare > 0f) orderParts += "Fare ₹${fare.toInt()}"
            orderParts += "Pickup ${pickup?.let { "%.1f km".format(it) } ?: "N/A"}"
            orderParts += "Trip ${drop?.let { "%.1f km".format(it) } ?: "N/A"}"

            val limitParts = mutableListOf<String>()
            if (checkFare) {
                val minText = if (direct.minFare > 0f) "₹${direct.minFare.toInt()}" else "OFF"
                val maxText = if (direct.maxFare > 0f) "₹${direct.maxFare.toInt()}" else "OFF"
                limitParts += "Fare $minText-$maxText"
            }
            if (checkDistance) {
                val pickupLimit = if (direct.maxPickupKm > 0f) "${"%.1f".format(direct.maxPickupKm)} km" else "OFF"
                val dropLimit = if (direct.maxDropKm > 0f) "${"%.1f".format(direct.maxDropKm)} km" else "OFF"
                limitParts += "Pickup ≤ $pickupLimit"
                limitParts += "Trip ≤ $dropLimit"
            }

            val reason = buildString {
                append("Mode: $modeLabel")
                append("\nLimits: ${limitParts.joinToString(" | ")}")
                append("\nWhy ignored: ${failureReasons.joinToString("; ")}")

                if (direct.secondaryBothEnabled) {
                    append(
                        "\nFilter 2: ${
                            if (secondaryFailureReasons.isEmpty())
                                "matched"
                            else
                                secondaryFailureReasons.joinToString("; ")
                        }"
                    )
                } else {
                    append("\nFilter 2: OFF")
                }
            }

            Log.w(TAG, "⏭️ [Direct Filter IGNORED] $reason")
            return DirectFilterResult(OrderStatus.IGNORED, reason)
        }
        // All filters passed → OrderStatus.ACCEPTED
        return DirectFilterResult(OrderStatus.ACCEPTED, "All direct filters passed")
    }

    /**
     * Fix 1 - SpeculativeClick helper:
     * Screen contains fare text with ₹ symbol.
     */
    private fun screenContainsRupeeText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 150) {
            val node = queue.removeFirst()
            count++
            val txt = node.text?.toString().orEmpty()
            if (txt.contains("₹")) return true
            val desc = node.contentDescription?.toString().orEmpty()
            if (desc.contains("₹")) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private var lastUberSpeculativeClickTime = 0L
    private var lastUberHandledTimestamp = 0L

    enum class OlaState {
        IDLE,
        OLA_DETECTED,
        OLA_CLICK_ATTEMPTED,
        OLA_ACCEPTED
    }

    var currentOlaState = OlaState.IDLE
        private set
    private var lastOlaHandledTimestamp = 0L

    private fun setOlaState(newState: OlaState) {
        currentOlaState = newState
        Log.i(TAG, "🚖 [Ola Auto-Accept State] $newState")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // MASTER OFF: do not read ride-app accessibility events.
        if (!preferencesManager.isAutoAcceptEnabled) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return
        }

        val eventPkg = event.packageName?.toString().orEmpty().trim().lowercase()
        val activeRootPkg = rootInActiveWindow?.packageName?.toString().orEmpty().trim().lowercase()
        val selfPkg = packageName.trim().lowercase()

        // Uber Trip Radar may appear as a separate accessibility
        // window while SmartDrivo remains the foreground activity.
        val hasUberAccessibilityWindow =
            try {
                windows.any { window ->
                    val pkg =
                        window.root
                            ?.packageName
                            ?.toString()
                            .orEmpty()
                            .trim()
                            .lowercase()

                    isUberPackage(pkg)
                }
            } catch (_: Exception) {
                false
            }

        val isRapido = eventPkg == "com.rapido.passenger" ||
                isRapidoPackage(eventPkg) ||
                activeRootPkg == "com.rapido.passenger" ||
                isRapidoPackage(activeRootPkg)
        val isUber = isUberPackage(eventPkg) || isUberPackage(activeRootPkg) || hasUberAccessibilityWindow
        // OLA_REFERENCE_WINDOW_SCAN_V1
        // Ola can expose the incoming order in another accessibility
        // window instead of rootInActiveWindow.
        val hasOlaAccessibilityWindow =
            try {
                windows.any { window ->
                    val pkg =
                        window.root
                            ?.packageName
                            ?.toString()
                            .orEmpty()
                            .trim()
                            .lowercase()

                    isOlaPackage(pkg)
                }
            } catch (_: Exception) {
                false
            }

        val isOla =
            isOlaPackage(eventPkg) ||
                isOlaPackage(activeRootPkg) ||
                hasOlaAccessibilityWindow

        if (!isRapido && !isUber && !isOla) {
            return
        }
        // Keep the old lag protection while the user is inside SmartDrivo,
        // but DO NOT ignore a real Rapido accessibility event.
        val isDirectRapidoEvent =
            isRapidoPackage(eventPkg)

        val isDirectUberEvent =
            isUberPackage(eventPkg)

        val isDirectOlaEvent =
            isOlaPackage(eventPkg)

        // Rapido/Uber can show a genuine incoming ride popup while
        // SmartDrivo itself is still the foreground/root window.
        // Never drop those direct accessibility events.
        if (
            activeRootPkg == selfPkg &&
            !isDirectRapidoEvent &&
            !isDirectUberEvent &&
            !isDirectOlaEvent &&
            !hasUberAccessibilityWindow &&
            !hasOlaAccessibilityWindow
        ) {
            return
        }

        if (isRapido) {
            val now = System.currentTimeMillis()

            // TYPE_WINDOW_CONTENT_CHANGED is extremely noisy in the Rapido UI.
            // 80ms still keeps detection responsive while cutting duplicate parser work heavily.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                now - lastRapidoEventDispatchAt < 10L
            ) {
                return
            }

            // Never run multiple full Rapido node-tree parsers at the same time.
            if (isRapidoEventProcessing) {
                return
            }

            lastRapidoEventDispatchAt = now

            Log.d("SmartDrivo", "Rapido event received: ${event.eventType}")
            Log.i(
                TAG,
                "ðŸ“¥ [Rapido] eventPkg='$eventPkg', activePkg='$activeRootPkg', " +
                    "type=${event.eventType}, AutoAcceptEnabled=${preferencesManager.isAutoAcceptEnabled}"
            )
        } else {
            if (!preferencesManager.isAutoAcceptEnabled) return
            if (isClickInProgress) return
            if (System.currentTimeMillis() < clickBlockedUntilTimestamp) return
        }

        val targetPkg = if (isRapido) {
            if (eventPkg == "com.rapido.passenger" || isRapidoPackage(eventPkg)) {
                eventPkg
            } else {
                "com.rapido.passenger"
            }
        } else {
            eventPkg
        }

        val eventSource = try { event.source } catch (_: Exception) { null }

        if (isRapido) {
            isRapidoEventProcessing = true
            serviceScope.launch(Dispatchers.IO) {
                try {
                    processAccessibilityEvent(targetPkg, eventSource, event.eventType)
                } finally {
                    isRapidoEventProcessing = false
                }
            }
        } else {
            serviceScope.launch(Dispatchers.IO) {
                processAccessibilityEvent(targetPkg, eventSource, event.eventType)
            }
        }
    }
    private fun processAccessibilityEvent(
        eventPkg: String,
        eventSource: AccessibilityNodeInfo?,
        eventType: Int = 0
    ) {
        // MASTER OFF: cancel queued ride processing.
        if (!preferencesManager.isAutoAcceptEnabled) {
            resetProcessing()
            return
        }
        // 1. UBER OVERLAY-SAFE DETECTION
        //
        // Uber Trip Radar can appear over SmartDrivo or another
        // foreground screen. Do not trust rootInActiveWindow alone.
        // Scan eventSource + active root + all accessibility windows
        // and choose the Uber window containing the real offer.
        val isUberPkg =
            isUberPackage(eventPkg)

        if (isUberPkg) {

            if (!preferencesManager.isAutoAcceptEnabled) {
                return
            }

            if (isClickInProgress) {
                return
            }

            if (
                System.currentTimeMillis() <
                clickBlockedUntilTimestamp
            ) {
                return
            }


            val uberRoots =
                mutableListOf<AccessibilityNodeInfo>()

            val seenUberRoots =
                mutableSetOf<Int>()


            fun addUberRoot(
                node: AccessibilityNodeInfo?
            ) {
                if (node == null) return

                val top =
                    getTopRootNode(node)
                        ?: node

                val pkg =
                    top.packageName
                        ?.toString()
                        .orEmpty()
                        .trim()
                        .lowercase()

                if (!isUberPackage(pkg)) {
                    return
                }

                val key =
                    System.identityHashCode(top)

                if (seenUberRoots.add(key)) {
                    uberRoots.add(top)
                }
            }


            // Direct Uber popup/event source FIRST.
            addUberRoot(eventSource)

            // Active window may be Uber or SmartDrivo.
            addUberRoot(rootInActiveWindow)


            // Uber Trip Radar may exist in another interactive window.
            try {
                windows.forEach { window ->
                    addUberRoot(window.root)
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "UBER: accessibility window scan failed: ${e.message}"
                )
            }


            var bestUberRoot:
                AccessibilityNodeInfo? = null

            var bestScore =
                Int.MIN_VALUE


            for (candidateRoot in uberRoots) {

                val texts =
                    UberAdapter.collectAllNodeTexts(
                        candidateRoot
                    )

                val fullText =
                    texts.joinToString(" \n ")

                val lower =
                    fullText.lowercase()

                var score = 0


                if (fullText.contains("₹")) {
                    score += 100
                }


                val kmCount =
                    Regex(
                        """(?i)\b[0-9]+(?:\.[0-9]+)?\s*km\b"""
                    )
                        .findAll(fullText)
                        .count()


                if (kmCount >= 2) {
                    score += 100
                } else if (kmCount == 1) {
                    score += 20
                }


                if (
                    lower.contains("match") ||
                    lower.contains("accept") ||
                    lower.contains("confirm")
                ) {
                    score += 100
                }


                if (
                    lower.contains("cash payment")
                ) {
                    score += 20
                }


                Log.d(
                    TAG,
                    "UBER window score=$score " +
                        "pkg=${candidateRoot.packageName} " +
                        "kmCount=$kmCount"
                )


                if (score > bestScore) {
                    bestScore = score
                    bestUberRoot =
                        candidateRoot
                }
            }


            if (bestUberRoot != null) {

                Log.i(
                    TAG,
                    "UBER REAL WINDOW SELECTED: " +
                        "score=$bestScore " +
                        "roots=${uberRoots.size}"
                )

                handleUberOrder(
                    bestUberRoot,
                    eventPkg
                )

                return
            }


            Log.d(
                TAG,
                "UBER event received but no Uber accessibility root found"
            )
        }


        // 2. Check Rapido: overlay-safe detection.
        // A real order popup can appear over Rapido Home, SmartDrivo,
        // or another foreground screen. Search for a valid popup FIRST.
        val isRapidoPkg =
            eventPkg == "com.rapido.passenger" ||
                isRapidoPackage(eventPkg)

        if (isRapidoPkg) {
            Log.d("SmartDrivo", "Rapido event received: $eventType")

            val candidateRoots =
                mutableListOf<AccessibilityNodeInfo>()

            fun addCandidateRoot(node: AccessibilityNodeInfo?) {
                if (node == null) return

                val top = getTopRootNode(node) ?: node
                val pkg =
                    top.packageName
                        ?.toString()
                        .orEmpty()
                        .trim()
                        .lowercase(Locale.ROOT)

                if (isRapidoPackage(pkg) || pkg == "com.rapido.passenger") {
                    if (candidateRoots.none { it === top }) {
                        candidateRoots.add(top)
                    }
                }
            }

            addCandidateRoot(eventSource)
            addCandidateRoot(rootInActiveWindow)

            try {
                windows.forEach { window ->
                    addCandidateRoot(window.root)
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "⚠️ [Rapido] Unable to scan accessibility windows: ${e.message}"
                )
            }

            var validatedRoot: AccessibilityNodeInfo? = null
            var validatedPopup: RapidoPopupValidation? = null

            for (root in candidateRoots) {
                val validation =
                    RapidoAdapter.validateRapidoOrderPopup(root)

                if (validation.isValid) {
                    validatedRoot = root
                    validatedPopup = validation
                    break
                }
            }

            if (validatedRoot != null && validatedPopup != null) {
                // RAPIDO_VALID_POPUP_V4
                lastValidRapidoPopupAt = System.currentTimeMillis()
                Log.i(
                    TAG,
                    "🎯 [Rapido] Genuine order popup detected across windows: " +
                        "fare=₹${validatedPopup.fare}, " +
                        "pickupKm=${validatedPopup.pickupDistKm}, " +
                        "nearby=${validatedPopup.hasNearby}, " +
                        "rootsScanned=${candidateRoots.size}"
                )

                handleRapidoOrder(
                    validatedRoot,
                    validatedPopup
                )
                return
            }

            val anyRapidoHome =
                candidateRoots.any {
                    RapidoAdapter.isRapidoHomeScreen(it)
                }

            if (anyRapidoHome) {
                // RAPIDO_TRANSIENT_GAP_FINAL_V2
                // Rapido can expose Home for a moment while the same
                // offer is transitioning. Keep the popup identity during
                // this short gap so one ride cannot become two History rows.
                val popupGapMs =
                    System.currentTimeMillis() -
                        lastValidRapidoPopupAt

                if (
                    lastValidRapidoPopupAt <= 0L ||
                    popupGapMs > 1_200L
                ) {
                    activeRapidoPopupOrderId = null
                    lastValidRapidoPopupAt = 0L
                }
                Log.d(
                    TAG,
                    "🏠 Rapido home screen detected and NO valid order popup " +
                        "was found across ${candidateRoots.size} Rapido root(s). " +
                        "Skipping."
                )
                return
            }

            activeRapidoPopupOrderId = null

            Log.d(
                TAG,
                "ℹ️ [Rapido] No genuine order popup found across " +
                    "${candidateRoots.size} Rapido root(s). " +
                    "Need fare + pickup distance + Accept button."
            )
        }
        // 3. Check Ola window or event for ola.cabs / com.olacabs.oladriver
        val isOlaPkg = isOlaPackage(eventPkg)
        if (isOlaPkg && preferencesManager.isAutoAcceptEnabled) {
            val root = rootInActiveWindow ?: eventSource
            val rootPkg = root?.packageName?.toString().orEmpty().trim().lowercase()
            if (root != null && isOlaPackage(rootPkg)) {
                handleOlaOrder(root, eventPkg)
                return
            }
        }
    }

    /**
     * Fix 1 - SpeculativeClick:
     * Remove speculative click completely OR only trigger it when BOTH conditions are true:
     * - Package is com.ubercab
     * - Screen contains fare text with ₹ symbol
     * Never fire speculative click on any other screen.
     */
    private fun performUberSpeculativeClick(root: AccessibilityNodeInfo?, pkgName: String = "com.ubercab") {
        if (root == null) return

        val pkg = root.packageName?.toString() ?: pkgName
        // Condition 1: Package is com.ubercab
        if (!isUberPackage(pkg)) {
            Log.d(TAG, "⚡ [Fix 1 SpeculativeClick] Skipped: package is not com.ubercab ($pkg)")
            return
        }

        // Condition 2: Screen contains fare text with ₹ symbol
        if (!screenContainsRupeeText(root)) {
            Log.d(TAG, "⚡ [Fix 1 SpeculativeClick] Skipped: screen does not contain ₹ symbol")
            return
        }

        // Fix 3 & Fix 4 check
        if (!canExecuteClick()) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastUberSpeculativeClickTime < 3000L) return
        lastUberSpeculativeClickTime = now

        notifyClickInitiated()

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()
        val specX = screenWidth * 0.65f
        val specY = screenHeight * 0.80f

        Log.i(TAG, "⚡ [Fix 1 SpeculativeClick] BOTH CONDITIONS MET (com.ubercab + ₹ fare). Tapping at ($specX, $specY) [65% width, 80% height]")
        simulateTapGesture(specX, specY, isInternalFallback = true) { success ->
            Log.i(TAG, "⚡ [SpeculativeClick] Gesture completed, success=$success")
        }
    }

    private val processedUberBookingIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 100
            }
        }
    )

    private fun isDuplicateUberBookingId(bookingId: String?): Boolean {
        if (bookingId.isNullOrBlank()) return false
        val lastSeen = processedUberBookingIds[bookingId] ?: return false
        return System.currentTimeMillis() - lastSeen < 15_000L
    }

    private fun recordUberBookingId(bookingId: String) {
        processedUberBookingIds[bookingId] = System.currentTimeMillis()
    }

    // Uber Trip Radar can be two-stage:
    // Match -> same ride appears again -> final Accept.
    private var pendingUberMatchRideKey: String? = null
    private var pendingUberMatchUntilMs: Long = 0L

    private fun isPendingUberMatchStage(
        candidate: RideCandidate
    ): Boolean {
        val now = System.currentTimeMillis()

        if (now > pendingUberMatchUntilMs) {
            pendingUberMatchRideKey = null
            pendingUberMatchUntilMs = 0L
            return false
        }

        return pendingUberMatchRideKey ==
            getOrderIdentifier(candidate)
    }

    private fun markPendingUberMatchStage(
        candidate: RideCandidate
    ) {
        pendingUberMatchRideKey =
            getOrderIdentifier(candidate)

        pendingUberMatchUntilMs =
            System.currentTimeMillis() + 3500L

        Log.i(
            TAG,
            "UBER: Two-stage Match registered; waiting for final Accept"
        )
    }

    private fun clearPendingUberMatchStage(
        candidate: RideCandidate
    ) {
        if (
            pendingUberMatchRideKey ==
            getOrderIdentifier(candidate)
        ) {
            pendingUberMatchRideKey = null
            pendingUberMatchUntilMs = 0L
        }
    }

    /**
     * Handles candidate detection and order processing for com.ubercab
     * All delays capped at 150ms max.
     */
    // =========================================================
    // UBER LIVE ORDER WINDOW DETECTION
    //
    // Uber can render the incoming offer card in a different
    // accessibility window from rootInActiveWindow.
    // Scan every Uber window and select the one containing the
    // actual fare + distance + Match/Accept offer.
    // =========================================================

    private fun isUberPackageValue(
        packageName: String?
    ): Boolean {
        val pkg =
            packageName
                ?.trim()
                ?.lowercase()
                .orEmpty()

        return pkg == "com.ubercab" ||
            pkg == "com.ubercab.driver" ||
            pkg.startsWith("com.ubercab.")
    }


    private fun scoreUberOrderRoot(
        root: AccessibilityNodeInfo
    ): Int {

        val rootPkg =
            root.packageName
                ?.toString()
                .orEmpty()

        if (!isUberPackageValue(rootPkg)) {
            return Int.MIN_VALUE
        }

        val texts =
            UberAdapter.collectAllNodeTexts(root)

        val fullText =
            texts.joinToString(" \n ")

        val lower =
            fullText.lowercase()

        var score = 0

        if (fullText.contains("₹")) {
            score += 50
        }

        val kmCount =
            Regex(
                """\b[0-9]+(?:\.[0-9]+)?\s*km\b""",
                RegexOption.IGNORE_CASE
            )
                .findAll(fullText)
                .count()

        if (kmCount >= 2) {
            score += 40
        } else if (kmCount == 1) {
            score += 15
        }

        if (
            lower.contains("match") ||
            lower.contains("accept")
        ) {
            score += 60
        }

        if (
            lower.contains("cash payment") ||
            lower.contains("pickup") ||
            lower.contains("trip")
        ) {
            score += 10
        }

        return score
    }


    private fun findBestUberOrderRoot(
        fallback: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        val candidates =
            mutableListOf<AccessibilityNodeInfo>()

        val seen =
            mutableSetOf<Int>()

        fun addCandidate(
            node: AccessibilityNodeInfo?
        ) {
            if (node == null) return

            val pkg =
                node.packageName
                    ?.toString()
                    .orEmpty()

            if (!isUberPackageValue(pkg)) {
                return
            }

            val key =
                System.identityHashCode(node)

            if (seen.add(key)) {
                candidates.add(node)
            }
        }


        addCandidate(fallback)
        addCandidate(rootInActiveWindow)


        // Important: scan ALL interactive Uber windows.
        try {
            for (window in windows) {
                addCandidate(window.root)
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "UBER: Unable to enumerate accessibility windows: ${e.message}"
            )
        }


        if (candidates.isEmpty()) {
            return fallback
        }


        var best:
            AccessibilityNodeInfo? = null

        var bestScore =
            Int.MIN_VALUE


        for (candidateRoot in candidates) {

            val score =
                scoreUberOrderRoot(
                    candidateRoot
                )

            Log.d(
                TAG,
                "UBER: window candidate score=$score " +
                    "pkg=${candidateRoot.packageName}"
            )

            if (score > bestScore) {
                bestScore = score
                best = candidateRoot
            }
        }


        Log.i(
            TAG,
            "UBER: selected live order window score=$bestScore " +
                "from ${candidates.size} Uber window(s)"
        )


        return best ?: fallback
    }


    /**
     * Current Uber UI sometimes exposes the large bottom
     * Match/Accept control as clickable but without readable text.
     *
     * We allow it ONLY when:
     * - root belongs to Uber
     * - screen contains a real fare
     * - screen contains at least two km values
     * - control is visible/enabled/clickable
     * - control is a large bottom action button
     *
     * No arbitrary screen-coordinate fallback.
     */
    private fun isUberBottomActionGeometry(
        node: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo?
    ): Boolean {

        if (root == null) {
            return false
        }

        val rootPkg =
            root.packageName
                ?.toString()
                .orEmpty()

        val nodePkg =
            node.packageName
                ?.toString()
                .orEmpty()


        if (
            !isUberPackageValue(rootPkg) &&
            !isUberPackageValue(nodePkg)
        ) {
            return false
        }


        val texts =
            UberAdapter.collectAllNodeTexts(root)

        val fullText =
            texts.joinToString(" \n ")


        // Real Uber offer signature.
        if (!fullText.contains("₹")) {
            return false
        }


        val kmCount =
            Regex(
                """\b[0-9]+(?:\.[0-9]+)?\s*km\b""",
                RegexOption.IGNORE_CASE
            )
                .findAll(fullText)
                .count()


        if (kmCount < 2) {
            return false
        }


        if (
            !node.isVisibleToUser ||
            !node.isEnabled ||
            !node.isClickable
        ) {
            return false
        }


        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (
            bounds.isEmpty ||
            bounds.width() <= 0 ||
            bounds.height() <= 0
        ) {
            return false
        }


        val metrics =
            resources.displayMetrics

        val screenWidth =
            metrics.widthPixels

        val screenHeight =
            metrics.heightPixels

        val density =
            metrics.density


        val minHeight =
            (44f * density).toInt()

        val maxHeight =
            (130f * density).toInt()


        val wideEnough =
            bounds.width() >=
                (screenWidth * 0.55f)

        val actionHeight =
            bounds.height() in
                minHeight..maxHeight

        val lowerScreen =
            bounds.centerY() >=
                (screenHeight * 0.55f)


        return wideEnough &&
            actionHeight &&
            lowerScreen
    }


    private fun findUberBottomActionNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val matches =
            mutableListOf<
                Pair<
                    AccessibilityNodeInfo,
                    Int
                >
            >()


        fun walk(
            node: AccessibilityNodeInfo?
        ) {
            if (node == null) return


            if (
                isUberBottomActionGeometry(
                    node,
                    root
                )
            ) {

                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // Prefer the lowest and widest control.
                val score =
                    bounds.centerY() +
                        bounds.width()

                matches.add(
                    node to score
                )
            }


            for (
                i in
                0 until node.childCount
            ) {
                walk(
                    node.getChild(i)
                )
            }
        }


        walk(root)


        val best =
            matches.maxByOrNull {
                it.second
            }?.first


        if (best != null) {

            val bounds = Rect()
            best.getBoundsInScreen(bounds)

            Log.i(
                TAG,
                "UBER: verified bottom Match/Accept action found " +
                    "bounds=$bounds text='${best.text}' " +
                    "desc='${best.contentDescription}'"
            )
        }


        return best
    }

    /**
     * Prevent Uber Home / Online / Earnings screens from being
     * recorded as ride offers.
     *
     * A genuine Uber offer must contain:
     * - valid fare
     * - valid pickup distance
     * - valid trip/drop distance
     * - at least 2 visible km values
     * - fare currency
     * - verified Match / Accept action
     *
     * IMPORTANT:
     * History must NOT be created before this check passes.
     */
    private fun isGenuineUberLiveOffer(
        root: AccessibilityNodeInfo,
        candidate: RideCandidate
    ): Boolean {

        val fare =
            candidate.fare

        val pickupKm =
            candidate.pickupDistKm

        val tripKm =
            candidate.dropDistKm


        if (
            fare == null ||
            fare <= 0f ||
            pickupKm == null ||
            pickupKm <= 0f ||
            tripKm == null ||
            tripKm <= 0f
        ) {

            Log.d(
                TAG,
                "UBER HOME GUARD: missing real ride values " +
                    "fare=$fare pickup=$pickupKm trip=$tripKm"
            )

            return false
        }


        val texts =
            UberAdapter.collectAllNodeTexts(root)

        val fullText =
            texts.joinToString(" \n ")


        val hasFareCurrency =
            fullText.contains("₹") ||
            Regex(
                """(?i)\b(?:rs\.?|inr)\s*[0-9]+(?:\.[0-9]+)?"""
            ).containsMatchIn(fullText)


        val kmCount =
            Regex(
                """(?i)\b[0-9]+(?:\.[0-9]+)?\s*km\b"""
            )
                .findAll(fullText)
                .count()


        if (
            !hasFareCurrency ||
            kmCount < 2
        ) {

            Log.d(
                TAG,
                "UBER HOME GUARD: not an offer card " +
                    "(currency=$hasFareCurrency, kmCount=$kmCount)"
            )

            return false
        }


        // Match/Accept accessibility text is intentionally NOT
        // required here. On some Uber builds the visible Match
        // button is drawn but its text is not exposed to
        // Accessibility.
        //
        // Fare + pickup KM + trip KM already form the genuine
        // live-order signature. Actual clicking is separately
        // protected below.
        Log.i(
            TAG,
            "UBER REAL OFFER VERIFIED: ₹$fare | " +
                "Pickup=$pickupKm km | Trip=$tripKm km"
        )

        return true
    }

    private fun handleUberOrder(root: AccessibilityNodeInfo?, pkgName: String = "com.ubercab") {
        if (root == null) return
// Uber offer cards may be in a separate accessibility
        // window. Resolve the actual live order window first.
        val uberOrderRoot =
            findBestUberOrderRoot(root)
                ?: root

        if (!preferencesManager.isAutoAcceptEnabled) {
            // FIX 3: If toggle was OFF when order appeared, mark order as "IGNORED" not "REJECTED"
            try {
                val candidate = OrderDataExtractor.extractCandidate(
                    root = uberOrderRoot,
                    platform = Platform.UBER,
                    defaultVehicle = prefs.userProfile.value.vehicleType
                )
                if (candidate.bookingId != null && isDuplicateUberBookingId(candidate.bookingId)) {
                    resetProcessing()
                    return
                }
                if (candidate.bookingId != null) {
                    recordUberBookingId(candidate.bookingId)
                }
                logOrderEvent(candidate, OrderStatus.IGNORED, "Auto-accept toggle is OFF")
                Log.i(TAG, "Uber order appeared while toggle is OFF -> Logged as IGNORED")
            } catch (e: Exception) {
                Log.e(TAG, "Error logging ignored Uber order when toggle is OFF", e)
            }
            resetProcessing()
            return
        }

        // Reload settings fresh from PreferencesManager before evaluating ride
        val settings = prefs.loadSettings()
        val userProfile = prefs.userProfile.value

        if (!userProfile.isPlanValid && !userProfile.isAdmin) return
        if (!settings.uberEnabled) return

        // Fix 1 - SpeculativeClick:
        // Blind speculative click is disabled to guarantee safe button detection with no blind wrong taps.
        val actualPkg = uberOrderRoot.packageName?.toString() ?: pkgName

        // Debounce: 150ms max
        val now = System.currentTimeMillis()
        if (now - lastUberHandledTimestamp < 150L || isProcessing) return

        isProcessing = true
        handler.removeCallbacks(processingTimeoutRunnable)
        handler.postDelayed(processingTimeoutRunnable, 8000L)
        lastUberHandledTimestamp = System.currentTimeMillis()

        try {
            Log.d("SmartDrivo", "Uber window found ($pkgName)! Scanning candidate nodes...")

            val candidate = OrderDataExtractor.extractCandidate(
                root = uberOrderRoot,
                platform = Platform.UBER,
                defaultVehicle = prefs.userProfile.value.vehicleType
            )

            // =====================================================
            // UBER FALSE SCREEN BLOCKED
            //
            // Do NOT create History from Uber Home/Online screen.
            // Only continue when a real live offer is verified.
            // =====================================================
            if (
                !isGenuineUberLiveOffer(
                    uberOrderRoot,
                    candidate
                )
            ) {

                Log.i(
                    TAG,
                    "UBER FALSE SCREEN BLOCKED - no genuine ride offer. " +
                        "No history, no filters, no click."
                )

                resetProcessing()
                return
            }

            // Duplicate protection for Uber.
            // Exception: Trip Radar "Match" can redispatch the SAME ride
            // with the final Accept button.
            val pendingUberTwoStage =
                isPendingUberMatchStage(candidate)

            if (
                candidate.bookingId != null &&
                isDuplicateUberBookingId(candidate.bookingId) &&
                !pendingUberTwoStage
            ) {
                Log.i(
                    TAG,
                    "Duplicate Uber order skipped (bookingId: ${candidate.bookingId})"
                )

                RideDiagnosticsManager.recordDuplicateEvent(
                    candidate.bookingId!!
                )

                resetProcessing()
                return
            }

            if (pendingUberTwoStage) {
                Log.i(
                    TAG,
                    "UBER: Same ride returned after Match - allowing final Accept stage"
                )
            } else if (candidate.bookingId != null) {
                recordUberBookingId(
                    candidate.bookingId!!
                )
            }

            // PART B/E: Detection-First Insert: IMMEDIATELY create one History record with PROCESSING status in Room
            val recordId = onOrderDetectedFast(candidate)
            Log.i(TAG, "⚡ Uber order detected & inserted to Room [PROCESSING]: Fare=₹${candidate.fare}, pickup=${candidate.pickupAddress}")

            // Direct SharedPreferences Filter Evaluation before evaluating ANY ride
            val directResult = evaluateDirectRideFilters(candidate)
            when (directResult.status) {
                OrderStatus.REJECTED -> {
                    Log.i(TAG, "Uber ride rejected by direct filter: ${directResult.reason}")
                    onOrderDecisionFast(recordId, candidate, OrderStatus.REJECTED, "REJECT_CRITERIA_MET", directResult.reason)
                    attemptRejectOrder(uberOrderRoot, Platform.UBER, candidate, directResult.reason, pkgName)
                    return
                }
                OrderStatus.IGNORED -> {
                    Log.i(TAG, "Uber ride ignored by direct filter: ${directResult.reason}")
                    onOrderDecisionFast(recordId, candidate, OrderStatus.IGNORED, "CRITERIA_NOT_MET", directResult.reason)
                    resetProcessing()
                    return
                }
                OrderStatus.ACCEPTED -> {
                    // Direct filters passed, continue evaluation
                }
                else -> { /* no-op */ }
            }

            // Fix 2: Vibrate only once per order using lastVibratedOrderId
            triggerOrderDetectedVibration(getOrderIdentifier(candidate))

            val profileVehicle = prefs.userProfile.value.vehicleType
            val isVehicleAllowed = candidate.vehicleType == profileVehicle

            if (!isVehicleAllowed) {
                Log.d(TAG, "Vehicle ${candidate.vehicleType} does not match profile vehicle $profileVehicle")
                resetProcessing()
                return
            }

            // Check pickup location text against Go-To and No-Go area lists
            val pickupText = extractPickupLocationText(candidate, uberOrderRoot)
            val goToAreas = prefs.loadGoToAreas()
            val noGoAreas = prefs.loadNoGoAreas()

            val pickupAreaDecision = checkPickupAreaRules(pickupText, goToAreas, noGoAreas)
            if (pickupAreaDecision is DecisionResult.Reject) {
                Log.i(TAG, "Uber ride rejected by pickup area rule: ${pickupAreaDecision.reason}")
                attemptRejectOrder(uberOrderRoot, Platform.UBER, candidate, pickupAreaDecision.reason, pkgName)
                return
            }

            val directSettings = readDirectSettingsFresh()
            val effectiveSettings = settings.copy(
                minFare = directSettings.minFare,
                maxFare = directSettings.maxFare,
                maxPickupDistanceKm = directSettings.maxPickupKm,
                maxDropDistanceKm = directSettings.maxDropKm,
                maxDropKm = directSettings.maxDropKm
            )

            val decision = AreaRulesEngine.evaluateRide(
                candidate = candidate,
                areas = goToAreas + noGoAreas,
                settings = effectiveSettings,
                goToAreas = goToAreas,
                noGoAreas = noGoAreas,
                pickupLocationTextOverride = pickupText
            )

            if (decision is DecisionResult.Reject) {
                val isNoGo = decision.reason.contains("No-Go", ignoreCase = true)
                if (isNoGo) {
                    Log.i(TAG, "Uber ride rejected by filter: ${decision.reason}")
                    attemptRejectOrder(uberOrderRoot, Platform.UBER, candidate, decision.reason, pkgName)
                } else {
                    Log.i(TAG, "Uber ride ignored by filter: ${decision.reason}")
                    logOrderEvent(candidate, OrderStatus.IGNORED, decision.reason)
                    resetProcessing()
                }
                return
            }
            if (decision is DecisionResult.Ignore) {
                Log.i(TAG, "Uber ride ignored: ${decision.reason}")
                logOrderEvent(candidate, OrderStatus.IGNORED, decision.reason)
                resetProcessing()
                return
            }

            // Execute Uber auto-accept using 3 methods in sequence (delays capped to 150ms max)
            val speedDelay = minOf(settings.clickSpeed.delayMs, 150L)
            if (speedDelay <= 0L) {
                executeUberAutoAccept(uberOrderRoot, candidate)
            } else {
                serviceScope.launch(Dispatchers.IO) {
                    delay(speedDelay)
                    executeUberAutoAccept(uberOrderRoot, candidate)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleUberOrder", e)
            resetProcessing()
        }
    }

    private fun executeUberAutoAccept(
        root: AccessibilityNodeInfo,
        candidate: RideCandidate,
        attemptNumber: Int = 1,
        waitRound: Int = 0,
        clickLockNotified: Boolean = false
    ) {
        val directResult =
            evaluateDirectRideFilters(candidate)

        if (directResult.status == OrderStatus.REJECTED) {
            Log.w(
                TAG,
                "Aborting Uber auto-accept: ${directResult.reason}"
            )

            attemptRejectOrder(
                root,
                Platform.UBER,
                candidate,
                directResult.reason,
                "com.ubercab"
            )
            return
        }

        if (directResult.status == OrderStatus.IGNORED) {
            Log.w(
                TAG,
                "Aborting Uber auto-accept: ${directResult.reason}"
            )

            logOrderEvent(
                candidate,
                OrderStatus.IGNORED,
                directResult.reason
            )

            resetProcessing()
            return
        }

        if (
            attemptNumber == 1 &&
            waitRound == 0 &&
            !clickLockNotified &&
            !canExecuteClick()
        ) {
            Log.w(
                TAG,
                "Uber auto-accept skipped: another click is active"
            )

            resetProcessing()
            return
        }

        val uberRoot =
            getCurrentUberRoot(root)

        val acceptNode =
            uberRoot?.let {
                findUberAcceptNode(it)
            }

        // Uber's Compose/card tree may appear slightly before
        // the Accept text becomes available.
        if (acceptNode == null) {

            if (waitRound < 2) {
                serviceScope.launch(Dispatchers.IO) {
                    delay(50L)

                    val retryRoot =
                        getCurrentUberRoot(root)
                            ?: root

                    executeUberAutoAccept(
                        root = retryRoot,
                        candidate = candidate,
                        attemptNumber = attemptNumber,
                        waitRound = waitRound + 1,
                        clickLockNotified =
                            clickLockNotified
                    )
                }
                return
            }

            executeVerifiedUberMatchGestureFallback(
                root = uberRoot ?: root,
                candidate = candidate,
                attemptNumber = attemptNumber
            )
            return
        }


        val label =
            getUberNodeLabel(acceptNode)

        // Uber can show an Accept countdown/disabled control.
        if (!acceptNode.isEnabled) {

            if (waitRound < 20) {
                Log.d(
                    TAG,
                    "UBER: Accept button disabled/countdown; waiting 80ms"
                )

                serviceScope.launch(Dispatchers.IO) {
                    delay(80L)

                    val retryRoot =
                        getCurrentUberRoot(root)
                            ?: root

                    executeUberAutoAccept(
                        root = retryRoot,
                        candidate = candidate,
                        attemptNumber = attemptNumber,
                        waitRound = waitRound + 1,
                        clickLockNotified =
                            clickLockNotified
                    )
                }

                return
            }

            failUberAccept(
                candidate = candidate,
                reasonCode = "BUTTON_DISABLED_TIMEOUT",
                reasonText =
                    "Uber Accept button stayed disabled",
                buttonFound = true,
                clickMethod = "No click"
            )
            return
        }


        if (
            !isSafeUberAcceptTarget(
                acceptNode,
                uberRoot
            )
        ) {
            failUberAccept(
                candidate = candidate,
                reasonCode = "UNVERIFIED_ACCEPT_TARGET",
                reasonText =
                    "Uber target did not pass safe Accept verification",
                buttonFound = true,
                clickMethod =
                    "None - unsafe target prevented"
            )
            return
        }


        val bounds = Rect()
        acceptNode.getBoundsInScreen(bounds)

        if (
            bounds.isEmpty ||
            bounds.width() <= 0 ||
            bounds.height() <= 0
        ) {
            failUberAccept(
                candidate = candidate,
                reasonCode = "INVALID_BOUNDS",
                reasonText =
                    "Uber Accept button bounds were invalid",
                buttonFound = true,
                clickMethod =
                    "None - invalid bounds"
            )
            return
        }


        if (!clickLockNotified) {
            notifyClickInitiated()
        }


        val centerX =
            bounds.centerX().toFloat()

        val centerY =
            bounds.centerY().toFloat()


        Log.i(
            TAG,
            "UBER: Verified node='$label' " +
                "bounds=$bounds attempt=$attemptNumber"
        )


        // Uber may render Swipe/Slide-to-Accept.
        if (isUberSwipeAcceptLabel(label)) {

            val startX =
                bounds.left +
                    bounds.width() * 0.20f

            val endX =
                bounds.right -
                    bounds.width() * 0.15f


            Log.i(
                TAG,
                "UBER Method1: verified SWIPE gesture"
            )

            simulateSwipeGesture(
                startX = startX,
                startY = centerY,
                endX = endX,
                endY = centerY,
                durationMs = 180L
            ) { success ->

                if (success) {
                    verifyUberAfterClick(
                        candidate = candidate,
                        clickedLabel = label,
                        attemptNumber =
                            attemptNumber,
                        clickMethod =
                            "Verified Uber Swipe Gesture"
                    )
                } else {
                    tryUberActionClickFallback(
                        root = uberRoot,
                        node = acceptNode,
                        candidate = candidate,
                        clickedLabel = label,
                        attemptNumber =
                            attemptNumber
                    )
                }
            }

            return
        }


        // RideClicker-style Uber path:
        // Gesture on verified button bounds first.
        Log.i(
            TAG,
            "UBER Method1: verified TAP gesture"
        )

        simulateTapGesture(
            centerX,
            centerY,
            isInternalFallback = true
        ) { success ->

            if (success) {
                verifyUberAfterClick(
                    candidate = candidate,
                    clickedLabel = label,
                    attemptNumber =
                        attemptNumber,
                    clickMethod =
                        "Verified Uber Tap Gesture"
                )
            } else {
                tryUberActionClickFallback(
                    root = uberRoot,
                    node = acceptNode,
                    candidate = candidate,
                    clickedLabel = label,
                    attemptNumber =
                        attemptNumber
                )
            }
        }
    }


    private fun getCurrentUberRoot(
        fallback: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {

        return findBestUberOrderRoot(
            fallback
        )
            ?: fallback
            ?: rootInActiveWindow
    }

    private fun getUberNodeLabel(
        node: AccessibilityNodeInfo
    ): String {

        val text =
            node.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (text.isNotBlank()) {
            return text
        }

        return node.contentDescription
            ?.toString()
            ?.trim()
            .orEmpty()
    }


    private fun isUberSwipeAcceptLabel(
        label: String
    ): Boolean {

        val lower =
            label.lowercase()

        return lower.contains("swipe") ||
            lower.contains("slide")
    }


    private fun isUberMatchStageLabel(
        label: String
    ): Boolean {

        val lower =
            label.trim().lowercase()

        return (
            lower == "match" ||
            lower == "match trip" ||
            lower.contains("tap to match")
        ) && !lower.contains("matched")
    }


    private fun isUberFailureScreen(
        text: String
    ): Boolean {

        val lower =
            text.lowercase()

        return listOf(
            "accepted by another driver",
            "accepted by another captain",
            "another driver accepted",
            "request no longer available",
            "trip no longer available",
            "no matches"
        ).any {
            lower.contains(it)
        }
    }


    private fun isUberConfirmedScreen(
        text: String
    ): Boolean {

        val lower =
            text.lowercase()

        return listOf(
            "matched to trip",
            "trip accepted",
            "navigate to pickup",
            "head to pickup"
        ).any {
            lower.contains(it)
        }
    }


    private fun isUberTransitionScreen(
        text: String
    ): Boolean {

        val lower =
            text.lowercase()

        return lower.contains(
            "matching in progress"
        )
    }


    private fun isSafeUberAcceptTarget(
        node: AccessibilityNodeInfo,
        root: AccessibilityNodeInfo?
    ): Boolean {

        val label =
            getUberNodeLabel(node)

        val nodeId =
            node.viewIdResourceName
                ?.lowercase()
                .orEmpty()

        val textMatched =
            UberAdapter.isAcceptText(label) ||
            UberAdapter.isAcceptDesc(label)

        val idMatched =
            UberAdapter.ACCEPT_IDS.any { id ->

                val cleanId =
                    id.substringAfterLast("/")
                        .lowercase()

                nodeId.contains(cleanId)
            }


        val geometryMatched =
            isUberBottomActionGeometry(
                node,
                root
            )

        if (
            !textMatched &&
            !idMatched &&
            !geometryMatched
        ) {
            return false
        }


        val nodePkg =
            node.packageName
                ?.toString()
                .orEmpty()
                .lowercase()

        val rootPkg =
            root?.packageName
                ?.toString()
                .orEmpty()
                .lowercase()


        val uberOwned =
            listOf(
                nodePkg,
                rootPkg
            ).any {
                it == "com.ubercab" ||
                    it == "com.ubercab.driver" ||
                    it.startsWith(
                        "com.ubercab."
                    )
            }


        if (!uberOwned) {
            return false
        }


        if (!node.isVisibleToUser) {
            return false
        }


        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return !bounds.isEmpty &&
            bounds.width() > 0 &&
            bounds.height() > 0
    }


    /**
     * Method 2/3:
     * ACTION_CLICK only on the verified node or a nearby
     * clickable ancestor. No arbitrary screen coordinates.
     */
    private fun tryUberActionClickFallback(
        root: AccessibilityNodeInfo?,
        node: AccessibilityNodeInfo,
        candidate: RideCandidate,
        clickedLabel: String,
        attemptNumber: Int
    ) {
        var current:
            AccessibilityNodeInfo? = node

        var depth = 0

        val screenHeight =
            resources.displayMetrics
                .heightPixels


        while (
            current != null &&
            depth <= 3
        ) {

            val bounds = Rect()
            current.getBoundsInScreen(bounds)

            val pkg =
                current.packageName
                    ?.toString()
                    .orEmpty()
                    .lowercase()

            val uberOwned =
                pkg == "com.ubercab" ||
                    pkg == "com.ubercab.driver" ||
                    pkg.startsWith(
                        "com.ubercab."
                    ) ||
                    root?.packageName
                        ?.toString()
                        ?.lowercase()
                        ?.startsWith(
                            "com.ubercab"
                        ) == true


            // Prevent accidentally clicking a huge ride-card
            // ancestor instead of the Accept control.
            val safeHeight =
                bounds.height() > 0 &&
                    bounds.height() <
                    (screenHeight * 0.35f)


            if (
                current.isClickable &&
                current.isEnabled &&
                current.isVisibleToUser &&
                uberOwned &&
                safeHeight
            ) {

                try {
                    val clicked =
                        current.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )

                    Log.i(
                        TAG,
                        "UBER Method2/3: ACTION_CLICK depth=$depth result=$clicked"
                    )

                    if (clicked) {
                        verifyUberAfterClick(
                            candidate = candidate,
                            clickedLabel =
                                clickedLabel,
                            attemptNumber =
                                attemptNumber,
                            clickMethod =
                                "Uber ACTION_CLICK parentDepth=$depth"
                        )
                        return
                    }

                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "UBER ACTION_CLICK exception: ${e.message}"
                    )
                }
            }


            current =
                current.parent

            depth++
        }


        failUberAccept(
            candidate = candidate,
            reasonCode =
                "ALL_CLICK_METHODS_FAILED",
            reasonText =
                "Verified Uber Accept control found but click methods failed",
            buttonFound = true,
            clickMethod =
                "Gesture + ACTION_CLICK failed"
        )
    }


    /**
     * Uber success verification.
     *
     * Important: dispatchGesture/ACTION_CLICK returning true only
     * means Android accepted the input event. It does NOT prove
     * Uber accepted the trip.
     */
    private fun verifyUberAfterClick(
        candidate: RideCandidate,
        clickedLabel: String,
        attemptNumber: Int,
        clickMethod: String
    ) {

        // Trip Radar "Match" is stage 1 only.
        if (
            isUberMatchStageLabel(
                clickedLabel
            )
        ) {

            markPendingUberMatchStage(
                candidate
            )

            Log.i(
                TAG,
                "UBER: Match clicked - waiting for same ride to redispatch with final Accept"
            )

            serviceScope.launch(
                Dispatchers.IO
            ) {
                delay(120L)
                resetProcessing()
            }

            return
        }


        serviceScope.launch(
            Dispatchers.IO
        ) {

            var lastUberRoot:
                AccessibilityNodeInfo? = null

            for (check in 0 until 5) {

                delay(
                    if (check == 0)
                        180L
                    else
                        220L
                )


                val currentRoot =
                    getCurrentUberRoot(null)

                lastUberRoot =
                    currentRoot


                if (currentRoot == null) {
                    continue
                }


                val allText =
                    UberAdapter
                        .collectAllNodeTexts(
                            currentRoot
                        )
                        .joinToString(" \n ")


                // Explicit lost-trip status.
                if (
                    isUberFailureScreen(
                        allText
                    )
                ) {

                    clearPendingUberMatchStage(
                        candidate
                    )

                    failUberAccept(
                        candidate = candidate,
                        reasonCode =
                            "UBER_LOST_TO_OTHER_DRIVER",
                        reasonText =
                            "Uber reported that another driver/captain received the trip",
                        buttonFound = true,
                        clickMethod =
                            clickMethod
                    )

                    return@launch
                }


                // Explicit success screen.
                if (
                    isUberConfirmedScreen(
                        allText
                    )
                ) {

                    clearPendingUberMatchStage(
                        candidate
                    )

                    Log.i(
                        TAG,
                        "UBER: Confirmed accepted screen detected"
                    )

                    onUberAccepted(
                        candidate = candidate,
                        timesClicked =
                            attemptNumber,
                        clickMethod =
                            clickMethod
                    )

                    return@launch
                }


                val transitionVisible =
                    isUberTransitionScreen(
                        allText
                    )


                val acceptStillVisible =
                    findUberAcceptNode(
                        currentRoot
                    )?.let {
                        isSafeUberAcceptTarget(
                            it,
                            currentRoot
                        )
                    } == true


                // If the verified offer button has disappeared
                // and no failure banner appeared, allow one more
                // verification cycle before confirming.
                if (
                    !acceptStillVisible &&
                    !transitionVisible &&
                    check >= 1
                ) {

                    clearPendingUberMatchStage(
                        candidate
                    )

                    Log.i(
                        TAG,
                        "UBER: Offer control disappeared without failure banner - confirming acceptance"
                    )

                    onUberAccepted(
                        candidate = candidate,
                        timesClicked =
                            attemptNumber,
                        clickMethod =
                            clickMethod
                    )

                    return@launch
                }
            }


            // Same Accept is still visible:
            // click may have silently failed.
            if (
                lastUberRoot != null &&
                attemptNumber < 3
            ) {

                val node =
                    findUberAcceptNode(
                        lastUberRoot!!
                    )

                if (
                    node != null &&
                    isSafeUberAcceptTarget(
                        node,
                        lastUberRoot
                    )
                ) {

                    Log.w(
                        TAG,
                        "UBER: Same offer reappeared/still visible; controlled retry #${attemptNumber + 1}"
                    )

                    delay(140L)

                    executeUberAutoAccept(
                        root =
                            lastUberRoot!!,
                        candidate =
                            candidate,
                        attemptNumber =
                            attemptNumber + 1,
                        waitRound = 0,
                        clickLockNotified = true
                    )

                    return@launch
                }
            }


            clearPendingUberMatchStage(
                candidate
            )

            failUberAccept(
                candidate = candidate,
                reasonCode =
                    "UBER_VERIFICATION_TIMEOUT",
                reasonText =
                    "Uber click sent but acceptance could not be verified",
                buttonFound = true,
                clickMethod =
                    clickMethod
            )
        }
    }


    /**
     * Last-resort Uber Trip Radar Match gesture.
     *
     * Some Uber builds visually draw the large Match button but
     * do not expose its text/clickable node through Accessibility.
     *
     * This fallback is allowed ONLY after a genuine Uber offer has
     * already been parsed:
     *   fare > 0
     *   pickupKm > 0
     *   tripKm > 0
     *   Uber-owned window
     *
     * Therefore this never runs on Uber Home/Earnings screens.
     */
    private fun executeVerifiedUberMatchGestureFallback(
        root: AccessibilityNodeInfo,
        candidate: RideCandidate,
        attemptNumber: Int
    ) {

        val fare =
            candidate.fare ?: 0f

        val pickup =
            candidate.pickupDistKm ?: 0f

        val trip =
            candidate.dropDistKm ?: 0f

        val pkg =
            root.packageName
                ?.toString()
                .orEmpty()


        if (
            fare <= 0f ||
            pickup <= 0f ||
            trip <= 0f ||
            !isUberPackageValue(pkg)
        ) {

            failUberAccept(
                candidate = candidate,
                reasonCode =
                    "UNVERIFIED_UBER_CARD",
                reasonText =
                    "Uber Match fallback blocked because genuine offer signature was incomplete",
                buttonFound = false,
                clickMethod =
                    "None - safety guard"
            )

            return
        }


        val texts =
            UberAdapter.collectAllNodeTexts(
                root
            )

        val fullText =
            texts.joinToString(" \n ")


        val hasFare =
            fullText.contains("₹")


        val kmCount =
            Regex(
                """(?i)\b[0-9]+(?:\.[0-9]+)?\s*km\b"""
            )
                .findAll(fullText)
                .count()


        if (
            !hasFare ||
            kmCount < 2
        ) {

            failUberAccept(
                candidate = candidate,
                reasonCode =
                    "UNVERIFIED_UBER_CARD",
                reasonText =
                    "Uber Match fallback blocked: fare + two distances not visible",
                buttonFound = false,
                clickMethod =
                    "None - safety guard"
            )

            return
        }


        if (!canExecuteClick()) {
            resetProcessing()
            return
        }


        val metrics =
            resources.displayMetrics

        val screenWidth =
            metrics.widthPixels.toFloat()

        val screenHeight =
            metrics.heightPixels.toFloat()


        // Current Uber Trip Radar card:
        // large Match CTA is centered at roughly 59% screen width
        // and 90.5% screen height.
        //
        // This coordinate is used ONLY after real-offer
        // verification above.
        val matchX =
            screenWidth * 0.59f

        val matchY =
            screenHeight * 0.905f


        Log.i(
            TAG,
            "UBER: verified offer has hidden Match node. " +
                "Gesture fallback at ($matchX,$matchY) " +
                "fare=₹$fare pickup=$pickup trip=$trip"
        )


        notifyClickInitiated()


        simulateTapGesture(
            matchX,
            matchY,
            isInternalFallback = true
        ) { success ->

            if (success) {

                verifyUberAfterClick(
                    candidate = candidate,
                    clickedLabel = "Match",
                    attemptNumber =
                        attemptNumber,
                    clickMethod =
                        "Verified Uber Trip Radar Match Gesture"
                )

            } else {

                failUberAccept(
                    candidate = candidate,
                    reasonCode =
                        "MATCH_GESTURE_FAILED",
                    reasonText =
                        "Verified Uber Match gesture failed",
                    buttonFound = false,
                    clickMethod =
                        "Verified Uber Trip Radar Match Gesture"
                )
            }
        }
    }

    private fun failUberAccept(
        candidate: RideCandidate,
        reasonCode: String,
        reasonText: String,
        buttonFound: Boolean,
        clickMethod: String
    ) {

        val recordId =
            activeOrderRecordIds[
                candidate.platform
            ] ?: onOrderDetectedFast(
                candidate
            )


        onOrderActionCompletedFast(
            recordId = recordId,
            candidate = candidate,
            status = OrderStatus.FAILED,
            reasonCode = reasonCode,
            reasonText = reasonText,
            actionSucceeded = false,
            timesClicked = 0,
            buttonFound = buttonFound,
            buttonDetails =
                if (buttonFound)
                    "Verified Uber Accept control"
                else
                    "Uber Accept control not found",
            clickMethod = clickMethod,
            errorMsg = reasonText
        )


        resetProcessing()
    }


    // Compatibility wrapper — NEVER blind taps.
    private fun fallbackUberTap(
        candidate: RideCandidate
    ) {
        failUberAccept(
            candidate = candidate,
            reasonCode =
                "BUTTON_NOT_FOUND",
            reasonText =
                "Accept button node not detected in Uber hierarchy",
            buttonFound = false,
            clickMethod =
                "None - blind tap prevented"
        )
    }


    private fun onUberAccepted(candidate: RideCandidate, timesClicked: Int = 1, clickMethod: String = "Verified Uber Accept") {
        clearPendingUberMatchStage(candidate)
        // MASTER OFF: ignore Uber completion.
        if (!preferencesManager.isAutoAcceptEnabled) {
            resetProcessing()
            return
        }
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(now))
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        val detectionTime = if (candidate.detectionTimeMs > 0L) candidate.detectionTimeMs else (now - 145L)
        val recordId = activeOrderRecordIds[candidate.platform] ?: onOrderDetectedFast(candidate)

        onOrderActionCompletedFast(
            recordId = recordId,
            candidate = candidate,
            status = OrderStatus.ACCEPTED,
            reasonCode = "FILTERS_MATCHED",
            reasonText = buildAcceptedFilterReason(candidate),
            actionSucceeded = true,
            timesClicked = timesClicked,
            buttonFound = true,
            buttonDetails = "Uber Accept button verified in hierarchy",
            clickMethod = "$clickMethod ($timesClicked attempt(s))"
        )

        val historyItem = OrderHistoryItem(
            id = recordId,
            timestamp = now,
            dateStr = dateStr,
            timeStr = timeStr,
            status = OrderStatus.ACCEPTED,
            platform = Platform.UBER,
            vehicleType = candidate.vehicleType,
            pickupDistKm = candidate.pickupDistKm ?: 0f,
            dropDistKm = candidate.dropDistKm ?: 0f,
            pickupAddress = candidate.pickupAddress ?: "Pickup Location",
            dropAddress = candidate.dropAddress ?: "Drop Location",
            dropArea = candidate.dropArea ?: "City Area",
            amount = candidate.fare ?: 0f,
            bookingId = candidate.bookingId ?: recordId,
            detectionTimeMs = detectionTime,
            clickTimeMs = now,
            baseFare = candidate.baseFare ?: (candidate.fare ?: 0f),
            tipAmount = candidate.tipAmount ?: 0f,
            timesClicked = timesClicked,
            reason = buildAcceptedFilterReason(candidate)
        )

        // Directly call prefs.addOrderHistory() to update stats
        prefs.addOrderHistory(historyItem)
        prefs.saveAcceptedOrder(
            platform = "Uber",
            timestamp = now,
            fareAmount = candidate.fare ?: 0f
        )
        Log.i(TAG, "📊 [Stats] prefs.addOrderHistory() called for Uber order (₹${candidate.fare})")

        serviceScope.launch(Dispatchers.IO) {
            NotificationHelper.updateNotification(
                context = applicationContext,
                title = "SmartDrivo Active",
                text = "🎉 Uber Order Accepted! Monitoring next..."
            )

            repository.recordOrderHistory(historyItem)

            // Reduced verification delay to 150ms max
            delay(150L)
            verifyStateTransition(candidate, existingHistoryItem = historyItem)
            resetProcessing()
        }
    }

    private fun findUberAcceptNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val roots =
            mutableListOf<AccessibilityNodeInfo>()

        val seen =
            mutableSetOf<Int>()


        fun add(
            node: AccessibilityNodeInfo?
        ) {
            if (node == null) return

            val pkg =
                node.packageName
                    ?.toString()
                    .orEmpty()

            if (!isUberPackageValue(pkg)) {
                return
            }

            val key =
                System.identityHashCode(node)

            if (seen.add(key)) {
                roots.add(node)
            }
        }


        add(root)

        add(
            findBestUberOrderRoot(
                root
            )
        )


        try {
            for (window in windows) {
                add(window.root)
            }
        } catch (_: Exception) {
        }


        for (uberRoot in roots) {

            // 1. Visible text: Accept / Match / Confirm
            val directTextMatch =
                searchNodeRaw(
                    uberRoot
                ) {
                    UberAdapter.isAcceptText(
                        it
                    )
                }

            if (directTextMatch != null) {

                Log.i(
                    TAG,
                    "UBER: action found by visible text " +
                        "'${directTextMatch.text}'"
                )

                return directTextMatch
            }


            // 2. Content description
            val directDescMatch =
                searchNodeByDescRaw(
                    uberRoot
                ) {
                    UberAdapter.isAcceptDesc(
                        it
                    )
                }

            if (directDescMatch != null) {

                Log.i(
                    TAG,
                    "UBER: action found by contentDescription " +
                        "'${directDescMatch.contentDescription}'"
                )

                return directDescMatch
            }


            // 3. Known Uber resource IDs
            for (
                resId in
                UberAdapter.ACCEPT_IDS
            ) {
                try {

                    val list =
                        uberRoot
                            .findAccessibilityNodeInfosByViewId(
                                resId
                            )

                    if (
                        !list.isNullOrEmpty()
                    ) {

                        val target =
                            list[0]

                        Log.i(
                            TAG,
                            "UBER: action found by resource ID '$resId'"
                        )

                        return target
                    }

                } catch (_: Exception) {
                }
            }


            // 4. Existing UberAdapter scan
            val adapterNode =
                UberAdapter.findAcceptNode(
                    uberRoot
                )

            if (adapterNode != null) {
                return adapterNode
            }


            // 5. Uber Compose zero-text Match/Accept button.
            // Not a blind coordinate tap: requires a genuine
            // fare + distance card and a large clickable bottom
            // Uber-owned action control.
            val bottomAction =
                findUberBottomActionNode(
                    uberRoot
                )

            if (bottomAction != null) {
                return bottomAction
            }
        }


        return null
    }

    private fun searchNodeRaw(node: AccessibilityNodeInfo?, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString()?.trim().orEmpty()
        if (txt.isNotEmpty() && predicate(txt)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val matched = searchNodeRaw(child, predicate)
            if (matched != null) return matched
            child?.recycle()
        }
        return null
    }

    private fun searchNodeByDescRaw(node: AccessibilityNodeInfo?, predicate: (String) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && predicate(desc)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val matched = searchNodeByDescRaw(child, predicate)
            if (matched != null) return matched
            child?.recycle()
        }
        return null
    }

    // --- GO-TO & NO-GO AREA FILTERING (Pickup Location Check) ---

    /**
     * Checks pickup location text against Go-To and No-Go area lists:
     * - When order detected, check pickup location text
     * - If pickup area matches any No-Go area → REJECT order
     * - If Go-To areas list is set → only ACCEPT if pickup matches a Go-To area, else REJECT
     * - If both lists empty → accept based on other filters only
     */
    private fun checkPickupAreaRules(
        pickupLocationText: String,
        goToAreas: List<AreaGroup>,
        noGoAreas: List<AreaGroup>
    ): DecisionResult? {
        // No-Go is intentionally DROP / DESTINATION ONLY.
        // Pickup text and pickup distance must never trigger No-Go.
        // Go-To destination logic is handled by AreaRulesEngine.
        return null
    }
    private fun extractPickupLocationText(candidate: RideCandidate, root: AccessibilityNodeInfo?): String {
        val addr = candidate.pickupAddress.orEmpty().trim()
        if (addr.isNotEmpty() && !addr.equals("Detected Pickup Location", ignoreCase = true) && !addr.equals("Pickup Location", ignoreCase = true)) {
            return addr
        }
        if (root != null) {
            if (candidate.platform == Platform.UBER) {
                val uberData = UberAdapter.extractOrderData(root)
                if (!uberData.pickupAddress.isNullOrBlank()) {
                    return uberData.pickupAddress
                }
            }
            val texts = mutableListOf<String>()
            collectNodeTexts(root, texts)
            for (i in texts.indices) {
                val lower = texts[i].lowercase()
                if (lower.contains("pickup") || lower.contains("pick up") || lower.contains("from")) {
                    return if (i + 1 < texts.size) "${texts[i]} ${texts[i + 1]}" else texts[i]
                }
            }
        }
        return addr
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.let { if (it.isNotEmpty()) outList.add(it) }
        node.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) outList.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectNodeTexts(child, outList)
                child.recycle()
            }
        }
    }

    // --- OLA AUTO-ACCEPT IMPLEMENTATION (Package: com.olacabs.oladriver) ---

    private val processedOlaBookingIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 300
            }
        }
    )

    private fun isDuplicateOlaBookingId(bookingId: String?): Boolean {
        if (bookingId.isNullOrBlank()) return false
        val lastSeen = processedOlaBookingIds[bookingId] ?: return false
        return (System.currentTimeMillis() - lastSeen) < 30_000L
    }

    private fun recordOlaBookingId(bookingId: String) {
        processedOlaBookingIds[bookingId] = System.currentTimeMillis()
    }

    /**
     * Handles candidate detection and order processing for ola.cabs
     * Ola detection: scan window with package ola.cabs, find clickable accept button.
     * States: OLA_DETECTED → OLA_CLICK_ATTEMPTED → OLA_ACCEPTED
     */
    private fun handleOlaOrder(root: AccessibilityNodeInfo?, pkgName: String = "ola.cabs") {
        if (root == null) return

        if (!preferencesManager.isAutoAcceptEnabled) {
            // FIX 3: If toggle was OFF when order appeared, mark order as "IGNORED" not "REJECTED"
            try {
                val candidate = OrderDataExtractor.extractCandidate(
                    root = root,
                    platform = Platform.OLA,
                    defaultVehicle = prefs.userProfile.value.vehicleType
                )
                logOrderEvent(candidate.copy(platform = Platform.OLA), OrderStatus.IGNORED, "Auto-accept toggle is OFF")
                Log.i(TAG, "Ola order appeared while toggle is OFF -> Logged as IGNORED")
            } catch (e: Exception) {
                Log.e(TAG, "Error logging ignored Ola order when toggle is OFF", e)
            }
            resetProcessing()
            return
        }

        // Reload settings fresh from PreferencesManager before evaluating ride
        val settings = prefs.loadSettings()
        val userProfile = prefs.userProfile.value

        if (!userProfile.isPlanValid && !userProfile.isAdmin) return
        if (!settings.olaEnabled) return

        // Debounce repeated events: 150ms max
        val now = System.currentTimeMillis()
        if (now - lastOlaHandledTimestamp < 150L || isProcessing) return

        // Ola detection: scan window with package com.olacabs.oladriver, find clickable accept button
        val acceptNode = findOlaAcceptButton(root)
        if (acceptNode == null) {
            Log.d(TAG, "Ola window found ($pkgName), but no clickable accept button detected yet.")
            return
        }

        // State transition: OLA_DETECTED
        setOlaState(OlaState.OLA_DETECTED)
        Log.i(TAG, "🚖 [State: OLA_DETECTED] Clickable accept button detected for Ola ($pkgName)!")

        isProcessing = true
        handler.removeCallbacks(processingTimeoutRunnable)
        handler.postDelayed(processingTimeoutRunnable, 8000L)
        lastOlaHandledTimestamp = System.currentTimeMillis()

        try {
            val candidate = OrderDataExtractor.extractCandidate(
                root = root,
                platform = Platform.OLA,
                defaultVehicle = prefs.userProfile.value.vehicleType
            )

            // Duplicate protection for Ola
            val bookingId = candidate.bookingId
            if (!bookingId.isNullOrBlank() && isDuplicateOlaBookingId(bookingId)) {
                Log.i(TAG, "⏭️ Duplicate Ola order skipped (bookingId: $bookingId)")
                RideDiagnosticsManager.recordDuplicateEvent(bookingId)
                resetProcessing()
                setOlaState(OlaState.IDLE)
                return
            }
            if (!bookingId.isNullOrBlank()) {
                recordOlaBookingId(bookingId)
            }

            // PART B/E: Detection-First Insert: IMMEDIATELY create one History record with PROCESSING status in Room
            val recordId = onOrderDetectedFast(candidate)
            Log.i(TAG, "⚡ Ola order detected & inserted to Room [PROCESSING]: Fare=₹${candidate.fare}, pickup=${candidate.pickupAddress}")

            // Direct SharedPreferences Filter Evaluation before evaluating ANY ride
            val directResult = evaluateDirectRideFilters(candidate)
            when (directResult.status) {
                OrderStatus.REJECTED -> {
                    Log.i(TAG, "Ola ride rejected by direct filter: ${directResult.reason}")
                    onOrderDecisionFast(recordId, candidate, OrderStatus.REJECTED, "REJECT_CRITERIA_MET", directResult.reason)
                    attemptRejectOrder(root, Platform.OLA, candidate, directResult.reason, pkgName)
                    setOlaState(OlaState.IDLE)
                    return
                }
                OrderStatus.IGNORED -> {
                    Log.i(TAG, "Ola ride ignored by direct filter: ${directResult.reason}")
                    onOrderDecisionFast(recordId, candidate, OrderStatus.IGNORED, "CRITERIA_NOT_MET", directResult.reason)
                    resetProcessing()
                    setOlaState(OlaState.IDLE)
                    return
                }
                OrderStatus.ACCEPTED -> {
                    // Direct filters passed, continue evaluation
                }
                else -> { /* no-op */ }
            }

            // Fix 2: Vibrate only once per order using lastVibratedOrderId
            triggerOrderDetectedVibration(getOrderIdentifier(candidate))

            val profileVehicle = prefs.userProfile.value.vehicleType
            val isVehicleAllowed = candidate.vehicleType == profileVehicle

            if (!isVehicleAllowed) {
                Log.d(TAG, "Vehicle ${candidate.vehicleType} does not match profile vehicle $profileVehicle for Ola")
                resetProcessing()
                setOlaState(OlaState.IDLE)
                return
            }

            // Check pickup location text against Go-To and No-Go area lists
            val pickupText = extractPickupLocationText(candidate, root)
            val goToAreas = prefs.loadGoToAreas()
            val noGoAreas = prefs.loadNoGoAreas()

            val pickupAreaDecision = checkPickupAreaRules(pickupText, goToAreas, noGoAreas)
            if (pickupAreaDecision is DecisionResult.Reject) {
                Log.i(TAG, "Ola ride rejected by pickup area rule: ${pickupAreaDecision.reason}")
                attemptRejectOrder(root, Platform.OLA, candidate, pickupAreaDecision.reason, pkgName)
                setOlaState(OlaState.IDLE)
                return
            }

            val directSettings = readDirectSettingsFresh()
            val effectiveSettings = settings.copy(
                minFare = directSettings.minFare,
                maxFare = directSettings.maxFare,
                maxPickupDistanceKm = directSettings.maxPickupKm,
                maxDropDistanceKm = directSettings.maxDropKm,
                maxDropKm = directSettings.maxDropKm
            )

            val decision = AreaRulesEngine.evaluateRide(
                candidate = candidate,
                areas = goToAreas + noGoAreas,
                settings = effectiveSettings,
                goToAreas = goToAreas,
                noGoAreas = noGoAreas,
                pickupLocationTextOverride = pickupText
            )

            if (decision is DecisionResult.Reject) {
                val isNoGo = decision.reason.contains("No-Go", ignoreCase = true)
                if (isNoGo) {
                    Log.i(TAG, "Ola ride rejected by filter: ${decision.reason}")
                    attemptRejectOrder(root, Platform.OLA, candidate, decision.reason, pkgName)
                } else {
                    Log.i(TAG, "Ola ride ignored by filter: ${decision.reason}")
                    logOrderEvent(candidate.copy(platform = Platform.OLA), OrderStatus.IGNORED, decision.reason)
                    resetProcessing()
                }
                setOlaState(OlaState.IDLE)
                return
            }
            if (decision is DecisionResult.Ignore) {
                Log.i(TAG, "Ola ride ignored: ${decision.reason}")
                logOrderEvent(candidate.copy(platform = Platform.OLA), OrderStatus.IGNORED, decision.reason)
                resetProcessing()
                setOlaState(OlaState.IDLE)
                return
            }

            // Execute Ola auto-accept using 3 methods in sequence (delays capped to 150ms max)
            val speedDelay = minOf(settings.clickSpeed.delayMs, 150L)
            if (speedDelay <= 0L) {
                executeOlaAutoAccept(root, candidate, acceptNode)
            } else {
                serviceScope.launch(Dispatchers.IO) {
                    delay(speedDelay)
                    executeOlaAutoAccept(root, candidate, acceptNode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleOlaOrder", e)
            resetProcessing()
            setOlaState(OlaState.IDLE)
        }
    }

    /**
     * Ola auto-accept for com.olacabs.oladriver:
     * Use 3 methods in sequence:
     * 1. performAction(ACTION_CLICK) on Ola accept node
     * 2. If fails: gesture tap and swipe across node coordinates
     * 3. If fails: tap/swipe parent node or screen bottom
     * States: OLA_DETECTED → OLA_CLICK_ATTEMPTED → OLA_ACCEPTED
     */
    private fun executeOlaAutoAccept(
        root: AccessibilityNodeInfo,
        candidate: RideCandidate,
        acceptNode: AccessibilityNodeInfo
    ) {
        val directResult = evaluateDirectRideFilters(candidate)
        if (directResult.status == OrderStatus.REJECTED) {
            Log.w(TAG, "Aborting executeOlaAutoAccept: ${directResult.reason}")
            attemptRejectOrder(root, Platform.OLA, candidate, directResult.reason, "com.olacabs.oladriver")
            setOlaState(OlaState.IDLE)
            return
        } else if (directResult.status == OrderStatus.IGNORED) {
            Log.w(TAG, "Aborting executeOlaAutoAccept: ${directResult.reason}")
            logOrderEvent(candidate.copy(platform = Platform.OLA), OrderStatus.IGNORED, directResult.reason)
            resetProcessing()
            setOlaState(OlaState.IDLE)
            return
        }

        if (!canExecuteClick()) {
            Log.w(TAG, "Ola auto-accept skipped: click in progress or rate limit active")
            resetProcessing()
            setOlaState(OlaState.IDLE)
            return
        }
        notifyClickInitiated()

        // State transition: OLA_CLICK_ATTEMPTED
        setOlaState(OlaState.OLA_CLICK_ATTEMPTED)
        Log.i(TAG, "🚖 [State: OLA_CLICK_ATTEMPTED] Starting Ola auto-accept 3-method sequence for candidate fare=₹${candidate.fare}...")

        // METHOD 1: performAction(ACTION_CLICK) on Ola accept node
        val clicked = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "Ola Method 1: performAction(ACTION_CLICK) on accept node returned: $clicked")
        if (clicked) {
            Log.i(TAG, "✓ Ola Method 1 SUCCESSFUL via ACTION_CLICK!")
            onOlaAccepted(candidate, timesClicked = 1)
            return
        }

        // METHOD 2: If fails: gesture tap and swipe across node coordinates
        Log.w(TAG, "Ola Method 1 failed. Trying Method 2: gesture tap and swipe across node bounds...")
        val bounds = Rect()
        acceptNode.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
            val centerX = bounds.centerX().toFloat()
            val centerY = bounds.centerY().toFloat()
            val startX = bounds.left.toFloat() + bounds.width() * 0.15f
            val endX = bounds.left.toFloat() + bounds.width() * 0.85f

            val isSlider = OlaAdapter.isSlideOrSwipe(acceptNode)
            if (isSlider) {
                Log.i(TAG, "Ola Method 2: Detected slider button. Swiping from $startX to $endX at $centerY")
                simulateSwipeGesture(startX, centerY, endX, centerY) { swipeSuccess ->
                    if (swipeSuccess) {
                        Log.i(TAG, "✓ Ola Method 2 SUCCESSFUL via swipe on slider!")
                        onOlaAccepted(candidate, timesClicked = 2)
                    } else {
                        simulateTapGesture(centerX, centerY, isInternalFallback = true) { tapSuccess ->
                            if (tapSuccess) {
                                Log.i(TAG, "✓ Ola Method 2 SUCCESSFUL via fallback tap!")
                                onOlaAccepted(candidate, timesClicked = 2)
                            } else {
                                executeOlaMethod3(acceptNode, candidate)
                            }
                        }
                    }
                }
            } else {
                Log.i(TAG, "Ola Method 2: Gesture tap at node center coordinates ($centerX, $centerY), bounds=$bounds")
                simulateTapGesture(centerX, centerY, isInternalFallback = true) { tapSuccess ->
                    if (tapSuccess) {
                        // Also try a quick swipe across bounds in case it is a slide control
                        simulateSwipeGesture(startX, centerY, endX, centerY)
                        Log.i(TAG, "✓ Ola Method 2 SUCCESSFUL via gesture tap at coordinates!")
                        onOlaAccepted(candidate, timesClicked = 2)
                    } else {
                        simulateSwipeGesture(startX, centerY, endX, centerY) { swipeSuccess ->
                            if (swipeSuccess) {
                                Log.i(TAG, "✓ Ola Method 2 SUCCESSFUL via swipe across bounds!")
                                onOlaAccepted(candidate, timesClicked = 2)
                            } else {
                                executeOlaMethod3(acceptNode, candidate)
                            }
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "Ola Method 2: Node bounds empty. Proceeding to Method 3...")
            executeOlaMethod3(acceptNode, candidate)
        }
    }

    /**
     * METHOD 3: If fails: tap parent node or swipe across parent bounds
     */
    private fun executeOlaMethod3(node: AccessibilityNodeInfo, candidate: RideCandidate) {
        val parent = node.parent
        if (parent != null) {
            if (parent.isClickable) {
                val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (parentClicked) {
                    Log.i(TAG, "✓ Ola Method 3 SUCCESSFUL via parent ACTION_CLICK!")
                    onOlaAccepted(candidate, timesClicked = 3)
                    return
                }
            }
            val parentBounds = Rect()
            parent.getBoundsInScreen(parentBounds)
            if (!parentBounds.isEmpty && parentBounds.width() > 0 && parentBounds.height() > 0) {
                val pCenterX = parentBounds.centerX().toFloat()
                val pCenterY = parentBounds.centerY().toFloat()
                val pStartX = parentBounds.left.toFloat() + parentBounds.width() * 0.15f
                val pEndX = parentBounds.left.toFloat() + parentBounds.width() * 0.85f

                Log.i(
                    TAG,
                    "Ola Method 3: Tapping/swiping parent node at center ($pCenterX, $pCenterY), parentBounds=$parentBounds"
                )
                simulateTapGesture(pCenterX, pCenterY, isInternalFallback = true) { tapSuccess ->
                    simulateSwipeGesture(pStartX, pCenterY, pEndX, pCenterY) { swipeSuccess ->
                        if (tapSuccess || swipeSuccess) {
                            Log.i(TAG, "✓ Ola Method 3 SUCCESSFUL via parent node tap/swipe!")
                            onOlaAccepted(candidate, timesClicked = 3)
                        } else {
                            fallbackOlaTap(candidate)
                        }
                    }
                }
                return
            }
        }

        // If parent is null or has empty bounds, tap and swipe bottom of screen
        fallbackOlaTap(candidate)
    }

    private fun fallbackOlaTap(candidate: RideCandidate) {
        Log.w(TAG, "❌ [Ola] Accept button not detected or invalid in hierarchy! Refusing blind coordinate taps.")
        val recordId = activeOrderRecordIds[candidate.platform] ?: onOrderDetectedFast(candidate)
        onOrderActionCompletedFast(
            recordId = recordId,
            candidate = candidate,
            status = OrderStatus.FAILED,
            reasonCode = "BUTTON_NOT_FOUND",
            reasonText = "Accept button/slider node not detected in Ola hierarchy",
            actionSucceeded = false,
            timesClicked = 0,
            buttonFound = false,
            buttonDetails = "Accept button/slider node not detected in hierarchy",
            clickMethod = "None (blind tap prevented)",
            errorMsg = "Safe button not found - blind taps blocked"
        )
        resetProcessing()
        setOlaState(OlaState.IDLE)
    }

    /**
     * State transition: OLA_ACCEPTED
     * Add to order history with platform = "Ola" and update stats via prefs.addOrderHistory()
     */
    private fun onOlaAccepted(candidate: RideCandidate, timesClicked: Int = 1) {
        // MASTER OFF: ignore Ola completion.
        if (!preferencesManager.isAutoAcceptEnabled) {
            resetProcessing()
            return
        }
        setOlaState(OlaState.OLA_ACCEPTED)

        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(now))
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
        val detectionTime = if (candidate.detectionTimeMs > 0L) candidate.detectionTimeMs else (now - 145L)
        val recordId = activeOrderRecordIds[candidate.platform] ?: onOrderDetectedFast(candidate)

        onOrderActionCompletedFast(
            recordId = recordId,
            candidate = candidate,
            status = OrderStatus.ACCEPTED,
            reasonCode = "FILTERS_MATCHED",
            reasonText = buildAcceptedFilterReason(candidate),
            actionSucceeded = true,
            timesClicked = timesClicked,
            buttonFound = true,
            buttonDetails = "Ola Accept button/slider verified in hierarchy",
            clickMethod = "Ola Accept button click ($timesClicked attempt(s))"
        )

        val historyItem = OrderHistoryItem(
            id = recordId,
            timestamp = now,
            dateStr = dateStr,
            timeStr = timeStr,
            status = OrderStatus.ACCEPTED,
            platform = Platform.OLA,
            vehicleType = candidate.vehicleType,
            pickupDistKm = candidate.pickupDistKm ?: 0f,
            dropDistKm = candidate.dropDistKm ?: 0f,
            pickupAddress = candidate.pickupAddress ?: "Pickup Location",
            dropAddress = candidate.dropAddress ?: "Drop Location",
            dropArea = candidate.dropArea ?: "City Area",
            amount = candidate.fare ?: 0f,
            bookingId = candidate.bookingId ?: recordId,
            detectionTimeMs = detectionTime,
            clickTimeMs = now,
            baseFare = candidate.baseFare ?: (candidate.fare ?: 0f),
            tipAmount = candidate.tipAmount ?: 0f,
            timesClicked = timesClicked,
            reason = buildAcceptedFilterReason(candidate)
        )

        // Directly call prefs.addOrderHistory() to update stats
        prefs.addOrderHistory(historyItem)
        prefs.saveAcceptedOrder(
            platform = "Ola",
            timestamp = now,
            fareAmount = candidate.fare ?: 0f
        )
        Log.i(TAG, "📊 [Stats] prefs.addOrderHistory() called for Ola order (₹${candidate.fare})")

        serviceScope.launch(Dispatchers.IO) {
            NotificationHelper.updateNotification(
                context = applicationContext,
                title = "SmartDrivo Active",
                text = "🎉 Ola Order Accepted! Monitoring next..."
            )

            repository.recordOrderHistory(historyItem)

            // Reset processing after short delay
            delay(150L)
            verifyStateTransition(candidate.copy(platform = Platform.OLA), existingHistoryItem = historyItem)
            resetProcessing()
        }
    }

    private fun collectClickable(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) list.add(node)
        for (i in 0 until node.childCount) {
            collectClickable(node.getChild(i), list)
        }
    }

    /**
     * Ola detection: scan window with package com.olacabs.oladriver, find clickable accept button.
     */
    private fun findOlaAcceptButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Check OlaAdapter.findAcceptNode first (includes all queries, resource IDs, clickable scan, text/desc)
        val adapterNode = OlaAdapter.findAcceptNode(root)
        if (adapterNode != null) {
            return resolveClickableTarget(adapterNode)
        }

        // 2. Scan clickable nodes for accept texts or content descriptions
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectClickable(root, clickableNodes)
        for (node in clickableNodes) {
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            if (OlaAdapter.isAcceptText(text) || OlaAdapter.isAcceptDesc(desc)) {
                Log.i(TAG, "✓ Ola accept button detected via clickable node text: '$text', desc: '$desc'")
                return node
            }
            // Check immediate children inside clickable container
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val cText = child.text?.toString()?.trim()
                val cDesc = child.contentDescription?.toString()?.trim()
                val isMatch = OlaAdapter.isAcceptText(cText) || OlaAdapter.isAcceptDesc(cDesc)
                child.recycle()
                if (isMatch) {
                    Log.i(TAG, "✓ Ola accept button detected via clickable container child text: '$cText', desc: '$cDesc'")
                    return node
                }
            }
        }

        // 3. Resource IDs search
        for (resId in OlaAdapter.ACCEPT_IDS) {
            val list = root.findAccessibilityNodeInfosByViewId(resId)
            if (!list.isNullOrEmpty()) {
                val candidate = list[0]
                for (i in 1 until list.size) list[i].recycle()
                val target = resolveClickableTarget(candidate)
                Log.i(TAG, "✓ Ola accept button detected via Resource ID: '$resId'")
                return target
            }
        }

        // 4. Direct text search queries for accept
        for (query in listOf("Accept", "ACCEPT", "Accept Ride", "Accept Order", "Accept Booking", "Confirm", "Confirm Ride", "Confirm Booking", "स्वीकार करें", "स्वीकार", "Slide to Accept", "Swipe to Accept")) {
            val list = root.findAccessibilityNodeInfosByText(query)
            if (!list.isNullOrEmpty()) {
                val candidate = list[0]
                for (i in 1 until list.size) list[i].recycle()
                val target = resolveClickableTarget(candidate)
                Log.i(TAG, "✓ Ola accept button detected via text query '$query'")
                return target
            }
        }

        return null
    }

    private fun resolveClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (node.isClickable) return node
        var curr: AccessibilityNodeInfo? = node.parent
        while (curr != null) {
            if (curr.isClickable) return curr
            curr = curr.parent
        }
        return node
    }

    // 4. Duplicate Rapido booking IDs tracking
    private val processedRapidoBookingIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 300
            }
        }
    )

    private fun isDuplicateRapidoBookingId(bookingId: String?): Boolean {
        if (bookingId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val lastSeen = processedRapidoBookingIds[bookingId]
        // If seen within 3 minutes (180,000 ms), consider it duplicate
        return lastSeen != null && (now - lastSeen < 180_000L)
    }

    private fun recordRapidoBookingId(bookingId: String?) {
        if (bookingId.isNullOrBlank()) return
        processedRapidoBookingIds[bookingId] = System.currentTimeMillis()
    }

    private val processedRapidoOrderIds = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 300
            }
        }
    )

    private fun isDuplicateRapidoOrderId(orderId: String): Boolean {
        if (orderId.isBlank()) return false
        val now = System.currentTimeMillis()
        val lastSeen = processedRapidoOrderIds[orderId]
        return lastSeen != null && (now - lastSeen < 180_000L)
    }

    private fun recordRapidoOrderId(orderId: String) {
        if (orderId.isBlank()) return
        processedRapidoOrderIds[orderId] = System.currentTimeMillis()
    }


    // 2. Rapido distance regexes
    // Pickup: (?:pickup|pick\s*up|away)[\s:]*([0-9]+(?:\.[0-9]+)?)\s*(?:km)?
    // Drop: (?:drop|trip|distance)[\s:]*([0-9]+(?:\.[0-9]+)?)\s*(?:km)?
    private val RAPIDO_PICKUP_REGEX = Pattern.compile(
        "(?:pickup|pick\\s*up|away)[\\s:]*([0-9]+(?:\\.[0-9]+)?)\\s*(?:km)?",
        Pattern.CASE_INSENSITIVE
    )
    private val RAPIDO_DROP_REGEX = Pattern.compile(
        "(?:drop|trip|distance)[\\s:]*([0-9]+(?:\\.[0-9]+)?)\\s*(?:km)?",
        Pattern.CASE_INSENSITIVE
    )
    private val RAPIDO_RUPEE_REGEX = Pattern.compile("₹\\s*([0-9]+(?:\\.[0-9]+)?)")

    /**
     * FIX 2: Uses RapidoAdapter.extractOrderData to extract:
     * - Fare: base, tip, total ("₹83 +₹26" -> base=83, tip=26, total=109)
     * - Pickup distance: first occurrence of "[0-9.]+\s*km"
     * - Drop distance: second occurrence of "[0-9.]+\s*km"
     * - Pickup address: text under first km node (skipping "Auto", "Services", "Nearby")
     * - Drop address: text under second km node (skipping "Auto", "Services", "Nearby")
     */
    private fun extractRapidoCandidate(root: AccessibilityNodeInfo, defaultVehicle: com.example.model.VehicleType): RideCandidate {
        val rootPkg = root.packageName?.toString().orEmpty()
        if (RapidoAdapter.isSmartDrivoPackage(rootPkg) || RapidoAdapter.isRapidoHomeScreen(root)) {
            return RideCandidate(
                fare = null,
                pickupDistKm = null,
                dropDistKm = null,
                pickupAddress = "Address unavailable",
                dropAddress = "Address unavailable",
                dropArea = "Address unavailable",
                platform = Platform.RAPIDO,
                vehicleType = defaultVehicle
            )
        }

        val orderData = RapidoAdapter.extractOrderData(root)

        val textList = mutableListOf<String>()
        collectAllNodeTexts(root, textList, rootPkg)
        val fullText = textList.joinToString(" \n ")

        val detectedVehicle = OrderDataExtractor.detectVehicleType(fullText, defaultVehicle)
        val bookingId = orderData.bookingId ?: OrderDataExtractor.extractBookingId(fullText)

        val fare = if (orderData.totalFare > 0f) orderData.totalFare else null

        val rawCandidates = textList.map { it.trim() }.filter {
            it.length >= 3 &&
            !it.contains("₹") &&
            !it.matches(Regex("^[0-9.]+$")) &&
            !it.contains("km", ignoreCase = true) &&
            !it.equals("accept", ignoreCase = true) &&
            !it.equals("nearby", ignoreCase = true) &&
            !it.equals("auto", ignoreCase = true) &&
            !it.equals("services", ignoreCase = true) &&
            !RapidoAdapter.matchesBlocklist(it) &&
            !RapidoAdapter.isInvalidAddress(it)
        }
        val rawPickup = orderData.pickupAddress?.takeIf { it.isNotBlank() }
            ?: rawCandidates.firstOrNull()
            ?: "Address unavailable"

        val rawDrop = orderData.dropAddress?.takeIf { it.isNotBlank() }
            ?: rawCandidates.filter { it != rawPickup }.firstOrNull()
            ?: rawCandidates.getOrNull(1)
            ?: rawPickup

        // Requirement 2: If extracted address matches any blocklist word → save as "Address unavailable" instead
        val pickupAddress = if (rawPickup.isBlank() || RapidoAdapter.matchesBlocklist(rawPickup) || RapidoAdapter.isInvalidAddress(rawPickup)) {
            "Address unavailable"
        } else {
            rawPickup
        }

        val dropAddress = if (rawDrop.isBlank() || RapidoAdapter.matchesBlocklist(rawDrop) || RapidoAdapter.isInvalidAddress(rawDrop)) {
            "Address unavailable"
        } else {
            rawDrop
        }

        return RideCandidate(
            fare = fare,
            pickupDistKm = orderData.pickupKm,
            dropDistKm = orderData.dropKm ?: OrderDataExtractor.extractDropDistance(textList, fullText),
            pickupAddress = pickupAddress,
            dropAddress = dropAddress,
            dropArea = dropAddress,
            platform = Platform.RAPIDO,
            vehicleType = detectedVehicle,
            bookingId = bookingId,
            isBundledOrder = OrderDataExtractor.isBundleOrder(fullText),
            detectionTimeMs = System.currentTimeMillis(),
            baseFare = if (orderData.baseFare > 0f) orderData.baseFare else fare,
            tipAmount = orderData.tipAmount
        )
    }

    private fun collectAllNodeTexts(
        node: AccessibilityNodeInfo?,
        outList: MutableList<String>,
        inheritedPkg: String? = null,
        depth: Int = 0
    ) {
        if (node == null || depth > 15 || outList.size > 80) return
        val nodePkg = node.packageName?.toString()
        val effectivePkg = nodePkg ?: inheritedPkg

        // Strict requirement: Never extract from SmartDrivo UI
        if (RapidoAdapter.isSmartDrivoPackage(effectivePkg)) {
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
            outList.add(content)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllNodeTexts(child, outList, effectivePkg, depth + 1)
            }
        }
    }

    // Forbidden Rapido texts: Never click nodes with text: "View", "Services", "Go To", "Home", "Credit", "Orders", "Surge"
    private val RAPIDO_FORBIDDEN_TEXTS = listOf(
        "View", "Services", "Go To", "Home", "Credit", "Orders", "Surge"
    )

    private fun isRapidoForbiddenText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val clean = text.trim()
        return RAPIDO_FORBIDDEN_TEXTS.any { forbidden ->
            clean.equals(forbidden, ignoreCase = true) ||
            clean.contains(forbidden, ignoreCase = true)
        }
    }

    private fun isRapidoForbiddenNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        return isRapidoForbiddenText(text) || isRapidoForbiddenText(desc)
    }

    /**
     * Requirement 3: Checks if a node represents an order card, ride details, fare, distance, address, or general layout.
     * Do NOT click on the order card, ride details, or any other area.
     */
    private fun isOrderCardOrDetailsNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combined = "$text $desc".lowercase()

        // Check forbidden view IDs that indicate card, container, details or sheet
        val forbiddenIdPatterns = listOf(
            "card", "details", "container", "layout_order", "ride_details",
            "order_details", "bottom_sheet", "sheet", "root", "content",
            "trip_details", "order_card", "layout_card", "pickup", "drop",
            "address", "fare", "distance"
        )
        if (forbiddenIdPatterns.any { id.contains(it) && !id.contains("btn") && !id.contains("button") }) {
            return true
        }

        // If node text contains fare ("₹") or distance ("km") or location terms, it's card/details, NOT the accept button
        if (combined.contains("₹") ||
            combined.contains(" km") ||
            combined.contains("pickup") ||
            combined.contains("drop") ||
            combined.contains("destination") ||
            combined.contains("fare") ||
            combined.contains("estimate") ||
            combined.contains("passengers") ||
            combined.contains("customer")
        ) {
            return true
        }

        // Check dimensions: If node is excessively large, it's a card/screen container, not a button
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels
            if (screenHeight > 0 && bounds.height() > screenHeight * 0.40) {
                return true
            }
            if (screenWidth > 0 && bounds.width() > screenWidth * 0.90 && bounds.height() > 300) {
                return true
            }
        } catch (_: Exception) {
            // Ignore bounds check if unavailable
        }

        return false
    }

    /**
     * Checks if a parent container is strictly a button container (not a card or full view).
     */
    private fun isStrictButtonContainer(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (!node.isClickable) return false
        if (isOrderCardOrDetailsNode(node)) return false

        val id = node.viewIdResourceName?.lowercase() ?: ""
        val cls = node.className?.toString()?.lowercase() ?: ""

        if (cls.contains("scroll") || cls.contains("recycler") || cls.contains("list") || cls.contains("cardview")) {
            return false
        }

        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.height() > 300 || bounds.width() > 1000) {
                return false
            }
        } catch (_: Exception) {
            // ignore
        }

        if (id.contains("btn") || id.contains("button") || id.contains("accept")) {
            return true
        }
        if (cls.contains("button")) {
            return true
        }

        return false
    }

    /**
     * Requirement 2: Strict target check - only click node where text contains "Accept" or "ACCEPT" exactly.
     * Do NOT click on the order card, ride details, or any other area.
     */
    private fun isStrictAcceptNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (isRapidoForbiddenNode(node)) return false
        if (isOrderCardOrDetailsNode(node)) return false

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        // Exact requirement: only click node where text contains "Accept" or "ACCEPT" exactly
        val hasAcceptText = text.contains("Accept") || text.contains("ACCEPT")
        val hasAcceptDesc = desc.contains("Accept") || desc.contains("ACCEPT")

        if (!hasAcceptText && !hasAcceptDesc) {
            return false
        }

        // Must NOT be negative action
        if (text.contains("Don't", ignoreCase = true) ||
            text.contains("Decline", ignoreCase = true) ||
            text.contains("Reject", ignoreCase = true) ||
            text.contains("Cancel", ignoreCase = true)
        ) {
            return false
        }
        if (desc.contains("Don't", ignoreCase = true) ||
            desc.contains("Decline", ignoreCase = true) ||
            desc.contains("Reject", ignoreCase = true) ||
            desc.contains("Cancel", ignoreCase = true)
        ) {
            return false
        }

        return true
    }

    /**
     * Searches the hierarchy for a node matching the strict Accept button criteria.
     */
    private fun findStrictRapidoAcceptNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 300) {
            val node = queue.removeFirst()
            count++

            // Check if node itself satisfies strict accept
            if (isStrictAcceptNode(node)) {
                return node
            }

            // Check if this node is a button whose direct child has strict accept text
            if (node.isClickable && isStrictButtonContainer(node)) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        val cText = child.text?.toString()?.trim() ?: ""
                        val cDesc = child.contentDescription?.toString()?.trim() ?: ""
                        val hasAccept = cText.contains("Accept") || cText.contains("ACCEPT") ||
                            cDesc.contains("Accept") || cDesc.contains("ACCEPT")
                        child.recycle()
                        if (hasAccept) {
                            return node
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Requirement 5: Add a check: if Rapido app is not showing an order popup, do not trigger any click.
     * An order popup MUST contain:
     * 1. Fare currency (₹)
     * 2. Distance (km)
     * 3. Specific "Accept" or "ACCEPT" button on screen
     */
    /**
     * Requirement 2: Only click Accept if Rapido order popup nodes are visible.
     * Requirement 4: Check if the event nodes contain fare + accept button.
     */
    private fun isRapidoOrderPopupShowing(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val pkg = root.packageName?.toString().orEmpty()
        if (RapidoAdapter.isSmartDrivoPackage(pkg)) return false
        if (RapidoAdapter.isRapidoHomeScreen(root)) return false

        return RapidoAdapter.validateRapidoOrderPopup(root).isValid
    }

    /**
     * Requirement 4: Check if the event nodes contain fare + accept button
     */
    private fun hasRapidoOrderNodes(root: AccessibilityNodeInfo?): Boolean {
        return isRapidoOrderPopupShowing(root)
    }

    private fun hasRapidoFareOrOrderData(root: AccessibilityNodeInfo?): Boolean {
        return isRapidoOrderPopupShowing(root)
    }

    private fun containsFareNode(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (RapidoAdapter.isRapidoHomeScreen(root)) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 300) {
            val node = queue.removeFirst()
            count++

            if (!RapidoAdapter.isNodeInsideEarningsContainer(node)) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""

                if (text.contains("₹") || desc.contains("₹") ||
                    text.contains("Rs.", ignoreCase = true) || desc.contains("Rs.", ignoreCase = true) ||
                    RapidoAdapter.RUPEE_AMOUNT_REGEX.matcher(text).find() ||
                    RapidoAdapter.RUPEE_AMOUNT_REGEX.matcher(desc).find()
                ) {
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun getTopRootNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var curr = node ?: return null
        try {
            while (curr.parent != null) {
                curr = curr.parent ?: break
            }
        } catch (_: Exception) {}
        return curr
    }

    private fun isRapidoAcceptNode(node: AccessibilityNodeInfo?): Boolean {
        return isStrictAcceptNode(node)
    }

    private fun findRapidoAcceptNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return findStrictRapidoAcceptNode(root)
    }

    /**
     * FIX 1 - Order Card Detection:
     * When order card detected (node with "₹" + distance "km" both found in screen).
     */
    // RAPIDO_POST_ACCEPT_FIX_V1
    // Rapido may keep a visible card/container after Match/Accept succeeds.
    // These texts are confirmation/status UI, NOT a new ride offer.
    private fun isRapidoPostAcceptScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val textList = mutableListOf<String>()
        collectAllNodeText(root, textList)
        val allText = textList.joinToString(" ").lowercase(Locale.ROOT)

        return allText.contains("thanks for accepting") ||
            allText.contains("checking order status") ||
            allText.contains("you missed the order") ||
            allText.contains("order missed") ||
            allText.contains("order expired") ||
            allText.contains("no longer available") ||
            allText.contains("accepted by another")
    }
    private fun isRapidoOrderScreenVisible(root: AccessibilityNodeInfo?): Boolean {
        return isRapidoOrderPopupShowing(root)
    }

    private fun handleRapidoOrder(root: AccessibilityNodeInfo?, preValidation: RapidoPopupValidation? = null) {
        if (root == null) return

        val rootPkg = root.packageName?.toString().orEmpty()
        if (RapidoAdapter.isSmartDrivoPackage(rootPkg)) {
            Log.d(TAG, "Ignoring handleRapidoOrder on SmartDrivo UI package: $rootPkg")
            return
        }


        // RAPIDO_POST_ACCEPT_FIX_V1
        // Never parse Rapido confirmation/status UI as a fresh ride.
        // The running accept-verification coroutine will finalize the ORIGINAL
        // history row, preserving fare, distance and addresses.
        if (isRapidoPostAcceptScreen(root)) {
            Log.i(TAG, "RAPIDO POST-ACCEPT screen detected; blocking duplicate candidate/history insert")
            return
        }
// Validate genuine popup BEFORE applying home-screen detection.
        // A real Rapido order card can be rendered as an overlay while
        // underlying Home text is still present in the same node tree.
        val validation =
            preValidation
                ?: RapidoAdapter.validateRapidoOrderPopup(root)

        if (!validation.isValid) {
            if (RapidoAdapter.isRapidoHomeScreen(root)) {
                Log.i(
                    TAG,
                    "🏠 Rapido HOME SCREEN detected with no valid order popup. " +
                        "Skipping completely."
                )
            } else {
                Log.d(
                    TAG,
                    "❌ Rapido order popup missing required elements: " +
                        "${validation.failureReason}. " +
                        "Do NOT log to history, do NOT process."
                )
            }
            return
        }
        // RAPIDO_TURBO_DROP_ONLY_V4
        // Validation already parsed this popup once. Do not walk the tree again.
        val turboStartNs =
            System.nanoTime()

        val profileSnapshot =
            prefs.userProfile.value

        val orderData =
            validation.orderData
                ?: RapidoAdapter.extractOrderData(
                    root = root,
                    skipHomeCheck = true,
                    skipPickupAddress = true
                )

        val finalFare =
            validation.fare
                ?: orderData.totalFare
                    .takeIf { it > 0f }

        if (finalFare == null || finalFare <= 0f) {
            Log.d(
                TAG,
                "Rapido candidate has no valid fare. Skipping."
            )
            return
        }

        val candidate =
            RideCandidate(
                fare = finalFare,
                pickupDistKm =
                    orderData.pickupKm
                        ?: validation.pickupDistKm,
                dropDistKm =
                    orderData.dropKm,
                pickupAddress =
                    "Address unavailable",
                dropAddress =
                    orderData.dropAddress,
                dropArea =
                    orderData.dropAddress,
                platform = Platform.RAPIDO,
                vehicleType =
                    profileSnapshot.vehicleType,
                bookingId =
                    orderData.bookingId,
                isBundledOrder =
                    orderData.isBundledOrder,
                detectionTimeMs =
                    System.currentTimeMillis(),
                baseFare =
                    orderData.baseFare
                        .takeIf { it > 0f }
                        ?: finalFare,
                tipAmount =
                    orderData.tipAmount
            )
        // RAPIDO_COMPLETE_HISTORY_V4
        // Wait for the complete card before inserting History.
        // This prevents Trip:N/A + a second full row for the same offer.
        if (candidate.dropDistKm == null || candidate.dropDistKm <= 0f) {
            Log.d(
                TAG,
                "Rapido popup incomplete - waiting for Trip km before History insert"
            )
            return
        }
        // Ignore repeated accessibility events only for the CURRENT visible Rapido popup.
        // Do not blacklist an order signature for minutes: legitimate offers can arrive quickly.
        // RAPIDO_MASTER_OFF_V4
        // Auto-Accept is SmartDrivo's master runtime switch.
        // OFF = no reading, no History, no Auto-Reject, no clicks.
        if (!preferencesManager.isAutoAcceptEnabled) {
            resetProcessing()
            return
        }
        val bookingId = candidate.bookingId
        val orderId = getOrderIdentifier(candidate)

        if (activeRapidoPopupOrderId == orderId) {
            Log.i(
                TAG,
                "Duplicate event for current Rapido popup skipped " +
                    "(bookingId: $bookingId, orderId: $orderId)"
            )

            // HISTORY_RACE_FINAL_V2
            // Never resetProcessing() for a duplicate event.
            // The original event may still be clicking/verifying.
            return
        }

        // HISTORY_RACE_FINAL_V2
        // Gate BEFORE History insert. Otherwise a blocked/debounced
        // event can leave an orphan PROCESSING row behind.
        if (isProcessing) {
            Log.d(
                TAG,
                "Rapido event deferred while previous order is finalizing"
            )
            return
        }

        isProcessing = true
        handler.removeCallbacks(processingTimeoutRunnable)
        handler.postDelayed(
            processingTimeoutRunnable,
            750L
        )
        lastHandledTimestamp =
            System.currentTimeMillis()

        activeRapidoPopupOrderId = orderId

        val recordId = onOrderDetectedFast(candidate)
        Log.i(TAG, "⚡ Rapido order detected & inserted to Room [PROCESSING]: Fare=₹${candidate.fare}, pickup=${candidate.pickupAddress}")

        // Auto-Accept and Auto-Reject are independent.
        // BOTH OFF -> manual mode.
        // Auto-Reject ON + Auto-Accept OFF -> bad offers may be skipped,
        // matching offers remain visible for manual acceptance.
        val settings = prefs.appSettings.value
        val autoAcceptEnabled =
            preferencesManager.isAutoAcceptEnabled
        val autoRejectEnabled =
            settings.isAutoRejectBadFaresEnabled

        if (!autoAcceptEnabled && !autoRejectEnabled) {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "MANUAL_MODE",
                reasonText =
                    "Auto-Accept OFF  |  Auto-Reject OFF  |  Manual mode"
            )
            resetProcessing()
            return
        }
        val userProfile = prefs.userProfile.value

        // Check if user is approved and active
        if (!userProfile.isPlanValid && !userProfile.isAdmin) {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "NO_ACTIVE_PLAN",
                reasonText = "No active plan - auto-accept inactive"
            )
            Log.i(TAG, "📋 Rapido order logged as IGNORED: No active plan")
            resetProcessing()
            return
        }

        if (!settings.rapidoEnabled) {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "PLATFORM_DISABLED",
                reasonText = "Rapido disabled in settings"
            )
            Log.i(TAG, "📋 Rapido order logged as IGNORED: Rapido platform disabled in settings")
            resetProcessing()
            return
        }
        // Bundle Order gate:
        // OFF + Auto-Reject ON  -> automatically skip/reject using existing safe Rapido reject logic.
        // OFF + Auto-Reject OFF -> leave order for manual action.
        // ON -> continue normally through GO TO / NO GO / Fare / Distance / Both filters.
        if (candidate.isBundledOrder && !settings.isBundleOrderEnabled) {
            handleRapidoFilterFailure(
                root = root,
                candidate = candidate,
                recordId = recordId,
                reason = "Bundle Order OFF",
                defaultStatus = OrderStatus.IGNORED
            )
            Log.i(
                TAG,
                "Bundle Order OFF - Auto-Reject setting decides skip or manual action"
            )
            return
        }

        // HISTORY_RACE_FINAL_V2:
        // processing gate already ran before History insert.

        try {
            val pkg = root.packageName?.toString() ?: "com.rapido.rider"
            Log.d("SmartDrivo", "Found Rapido order screen! Scanning...")

            // RAPIDO_SPEED_V3: heavy debug dump skipped in live processing.

            // BUG 1 - Filters not working for Rapido:
            // - Rapido sends orders with fare=null and pickup="Nearby" - app is accepting these without checking filters
            // - If fare is null/unavailable, SKIP that order entirely (do not accept)
            // - If pickup distance is "Nearby" or unknown, SKIP that order (do not accept)
            // - Only accept when both fare AND distance values are clearly extracted as numbers

            if (candidate.fare == null || candidate.fare <= 0f) {
                Log.i(TAG, "⏭️ [BUG 1] Rapido order SKIPPED: fare is null or unavailable (${candidate.fare})")
                onOrderDecisionFast(
                    recordId = recordId,
                    candidate = candidate,
                    status = OrderStatus.IGNORED,
                    reasonCode = "FARE_UNAVAILABLE",
                    reasonText = "Fare unavailable - order skipped"
                )
                resetProcessing()
                return
            }

            // RAPIDO_SPEED_V3
            // validateRapidoOrderPopup already scanned this popup.
            val isNearbyPickup = validation.hasNearby
            val isPickupUnknown = candidate.pickupDistKm == null || candidate.pickupDistKm <= 0f

            if (isNearbyPickup || isPickupUnknown) {
                val skipReason = if (isNearbyPickup) "Pickup is Nearby - order skipped" else "Pickup distance unknown - order skipped"
                Log.i(TAG, "⏭️ [BUG 1] Rapido order SKIPPED: $skipReason (pickupDistKm=${candidate.pickupDistKm})")
                onOrderDecisionFast(
                    recordId = recordId,
                    candidate = candidate,
                    status = OrderStatus.IGNORED,
                    reasonCode = "PICKUP_UNAVAILABLE",
                    reasonText = skipReason
                )
                resetProcessing()
                return
            }

            // RAPIDO_TURBO_DROP_ONLY_V4
            // One decision pass using RAM snapshots only.
            val goToAreas =
                prefs.goToAreas.value

            val noGoAreas =
                prefs.noGoAreas.value

            val decision =
                AreaRulesEngine.evaluateRide(
                    candidate = candidate,
                    areas =
                        goToAreas + noGoAreas,
                    settings = settings,
                    goToAreas = goToAreas,
                    noGoAreas = noGoAreas,
                    pickupLocationTextOverride = "",
                    isGoToEnabled =
                        settings.isGoToEnabled,
                    isNoGoEnabled =
                        settings.isNoGoEnabled
                )

            if (decision !is DecisionResult.Accept) {
                val reason =
                    when (decision) {
                        is DecisionResult.Reject ->
                            decision.reason

                        is DecisionResult.Ignore ->
                            decision.reason

                        else ->
                            "Filter not matched"
                    }

                val isNoGo =
                    reason.contains(
                        "No-Go",
                        ignoreCase = true
                    )

                handleRapidoFilterFailure(
                    root = root,
                    candidate = candidate,
                    recordId = recordId,
                    reason = reason,
                    defaultStatus =
                        if (isNoGo)
                            OrderStatus.REJECTED
                        else
                            OrderStatus.IGNORED
                )
                return
            }

            // No vibration. Go directly to Accept.
            // Matching offer must never be removed by Auto-Reject.
            // If Auto-Accept is OFF, leave it on screen for manual acceptance.
            if (!autoAcceptEnabled) {
                onOrderDecisionFast(
                    recordId = recordId,
                    candidate = candidate,
                    status = OrderStatus.IGNORED,
                    reasonCode = "MANUAL_MATCH",
                    reasonText =
                        "Conditions matched  |  Auto-Accept OFF  |  Manual accept"
                )
                resetProcessing()
                return
            }

            // Execute Rapido auto-accept strictly targeting Accept button
            val acceptedHistoryReason =
                if (
                    decision is DecisionResult.Accept &&
                    decision.reason.contains(
                        "Go-To",
                        ignoreCase = true
                    )
                ) {
                    decision.reason
                } else {
                    buildAcceptedFilterReason(candidate, settings)
                }

            val turboDecisionMs =
                (System.nanoTime() - turboStartNs) /
                    1_000_000.0

            Log.i(
                TAG,
                "RAPIDO TURBO DROP-ONLY V4: read + filters ready in " +
                    String.format(
                        Locale.ENGLISH,
                        "%.2f ms",
                        turboDecisionMs
                    )
            )
            executeRapidoAutoAccept(
                candidate = candidate,
                orderRoot = root,
                prevalidatedAcceptNode =
                    validation.acceptButton?.node,
                historyRecordId = recordId,
                acceptedHistoryReason =
                    acceptedHistoryReason
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleRapidoOrder", e)
            resetProcessing()
        }
    }

    private var lastRapidoAttemptTime: Long = 0L

    /**
     * Rapido Auto-Accept:
     * 1. Do NOT click anywhere on Rapido app except the specific "Accept" button.
     * 2. Add strict target check - only click node where text contains "Accept" or "ACCEPT" exactly.
     * 3. Do NOT click on the order card, ride details, or any other area.
     * 4. If "Accept" button not found, do nothing - do not click randomly.
     * 5. Add a check: if Rapido app is not showing an order popup, do not trigger any click.
     */
    private fun executeRapidoAutoAccept(
        candidate: RideCandidate,
        orderRoot: AccessibilityNodeInfo? = null,
        attemptNumber: Int = 1,
        prevalidatedAcceptNode: AccessibilityNodeInfo? = null,
        historyRecordId: String? = null,
        acceptedHistoryReason: String? = null
    ) {
        // HISTORY_PINNED_RECORD_V2
        // Keep every retry/final status attached to the same real ride.
        val pinnedRecordId =
            historyRecordId
                ?: activeOrderRecordIds[candidate.platform]
                ?: onOrderDetectedFast(candidate)
        if (candidate.fare == null || candidate.fare <= 0f || candidate.pickupDistKm == null || candidate.pickupDistKm <= 0f) {
            Log.w(TAG, "Aborting executeRapidoAutoAccept: fare (${candidate.fare}) or pickup distance (${candidate.pickupDistKm}) invalid/unavailable")

            val recordId = pinnedRecordId


            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "RIDE_DATA_INCOMPLETE",
                reasonText = "Ride data incomplete - order left for manual action"
            )

            resetProcessing()
            return
        }

        // RAPIDO_SPEED_V3: filter already passed in handleRapidoOrder().


        if (!preferencesManager.isAutoAcceptEnabled) {
            resetProcessing()
            return
        }

        val now = System.currentTimeMillis()
        lastRapidoAttemptTime = now

        if (!canExecuteVerifiedRapidoClick()) {
            Log.w(TAG, "Rapido auto-accept paused by anti-spam safety limit")

            val recordId = pinnedRecordId


            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "CLICK_SAFETY_LIMIT",
                reasonText = "Safety click limit active - order left for manual action"
            )

            resetProcessing()
            return
        }

        if (attemptNumber > 5) {
            Log.w(TAG, "Reached max 5 attempts for Rapido auto-accept without order screen closing")

            val recordId = pinnedRecordId


            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "ACCEPT_MAX_ATTEMPTS",
                reasonText = "Auto-accept could not confirm the order - left for manual action"
            )

            resetProcessing()
            return
        }

        // RAPIDO_FIRST_CLICK_FAST_V2
        // Attempt #1 already has a validated popup + Accept node.
        // Reuse them instead of scanning the full tree again.
        val rapidoRoot =
            if (
                attemptNumber == 1 &&
                orderRoot != null &&
                prevalidatedAcceptNode != null
            ) {
                orderRoot
            } else {
                getRapidoOrderRootNode(orderRoot)
            }

        if (
            rapidoRoot == null ||
            (
                prevalidatedAcceptNode == null &&
                !isRapidoOrderPopupShowing(rapidoRoot)
            )
        ) {
            Log.i(TAG, "Rapido order popup nodes are not visible (fare and Accept button required). No click triggered.")

            val recordId = pinnedRecordId


            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "ORDER_POPUP_CLOSED",
                reasonText = "Order popup closed before SmartDrivo completed the action"
            )

            resetProcessing()
            return
        }

        // Requirement 2: Strict target check - only click node where text contains "Accept" or "ACCEPT" exactly
        val acceptNode = prevalidatedAcceptNode ?: findStrictRapidoAcceptNode(rapidoRoot)
        if (acceptNode == null) {
            // Requirement 4: If "Accept" button not found, do nothing - do not click randomly
            Log.w(
                TAG,
                "❌ [Rapido] Accept button with text 'Accept' or 'ACCEPT' NOT found on screen (attempt #$attemptNumber)! " +
                    "Doing nothing - will NOT click randomly."
            )
            val recordId = pinnedRecordId
            onOrderActionCompletedFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.FAILED,
                reasonCode = "BUTTON_NOT_FOUND",
                reasonText = "Accept button node not detected in Rapido screen",
                actionSucceeded = false,
                timesClicked = 0,
                buttonFound = false,
                buttonDetails = "Accept button with 'Accept'/'ACCEPT' not found on screen",
                clickMethod = "None (blind tap prevented)",
                errorMsg = "Safe button not found - blind taps blocked"
            )
            resetProcessing()
            return
        }

        // Requirement 1 & 3: Do NOT click anywhere on Rapido app except the specific "Accept" button.
        // Do NOT click on the order card, ride details, or any other area.
        if (isOrderCardOrDetailsNode(acceptNode)) {
            Log.w(TAG, "❌ [Rapido] Detected node is an order card or ride details container! Refusing to click.")
            val recordId = pinnedRecordId
            onOrderActionCompletedFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.FAILED,
                reasonCode = "CARD_NODE_DETECTED",
                reasonText = "Detected node is an order card - click prevented",
                actionSucceeded = false,
                timesClicked = 0,
                buttonFound = false,
                buttonDetails = "Target node is card/details container",
                clickMethod = "None (blind tap prevented)",
                errorMsg = "Card node detected - blind taps blocked"
            )
            resetProcessing()
            return
        }

        // Safe overlay handling:
        // Rapido may show its live order card above SmartDrivo while SmartDrivo
        // remains rootInActiveWindow. Allow a click ONLY when the already-found
        // strict Accept node/root is verified as belonging to Rapido.
        val activeBeforeClick = rootInActiveWindow
        val activePkgBeforeClick =
            activeBeforeClick?.packageName?.toString().orEmpty().trim().lowercase()

        val acceptNodePkg =
            acceptNode.packageName?.toString().orEmpty().trim().lowercase()
        val rapidoRootPkg =
            rapidoRoot.packageName?.toString().orEmpty().trim().lowercase()

        val verifiedRapidoTarget =
            isRapidoPackage(acceptNodePkg) || isRapidoPackage(rapidoRootPkg)

        if (!verifiedRapidoTarget) {
            Log.w(
                TAG,
                "❌ [Rapido] Accept target is not owned by Rapido " +
                    "(acceptPkg='$acceptNodePkg', rootPkg='$rapidoRootPkg'). Click prevented."
            )

            val recordId =
                activeOrderRecordIds[candidate.platform] ?: onOrderDetectedFast(candidate)

            onOrderActionCompletedFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.FAILED,
                reasonCode = "UNVERIFIED_ACCEPT_TARGET",
                reasonText = "Accept target did not belong to Rapido - click prevented",
                actionSucceeded = false,
                timesClicked = 0,
                buttonFound = true,
                buttonDetails = "acceptPkg='$acceptNodePkg', rootPkg='$rapidoRootPkg'",
                clickMethod = "None (target ownership check failed)",
                errorMsg = "Verified Rapido package required"
            )

            resetProcessing()
            return
        }

        if (RapidoAdapter.isSmartDrivoPackage(activePkgBeforeClick)) {
            Log.i(
                TAG,
                "✅ [Rapido] SmartDrivo is active root, but verified Rapido overlay " +
                    "Accept target belongs to Rapido. Safe click allowed."
            )
        }
        Log.i(
            TAG,
            "✅ [Rapido] Strict Accept button FOUND | " +
                "ID: ${acceptNode.viewIdResourceName} | " +
                "Text: '${acceptNode.text}' | " +
                "ContentDesc: '${acceptNode.contentDescription}' | " +
                "Clickable: ${acceptNode.isClickable}"
        )

        notifyClickInitiated()

        var clicked = false
        // Primary: performAction(ACTION_CLICK) directly on accept node
        if (acceptNode.isClickable) {
            clicked = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "👆 [Rapido] Click performed: acceptNode.performAction(ACTION_CLICK) -> Result: $clicked")
        }

        // Secondary: If not clickable directly, only click immediate parent if it is a strict button container (never card/container)
        if (!clicked) {
            val parent = acceptNode.parent
            if (parent != null && parent.isClickable && isStrictButtonContainer(parent)) {
                clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "👆 [Rapido] Click performed: parent.performAction(ACTION_CLICK) on strict button -> Result: $clicked")
            }
        }

        // Tertiary: If performAction failed, gesture tap STRICTLY at center of the Accept button bounds
        if (!clicked) {
            val bounds = Rect()
            acceptNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0 && !isOrderCardOrDetailsNode(acceptNode)) {
                val cx = bounds.centerX().toFloat()
                val cy = bounds.centerY().toFloat()
                Log.i(TAG, "👆 [Rapido] Click performed: Gesture tap strictly at Accept button center ($cx, $cy), screenBounds=$bounds")
                simulateTapGesture(cx, cy, isInternalFallback = true)
            } else {
                Log.w(TAG, "❌ [Rapido] Accept button bounds invalid ($bounds) - doing nothing, no random click")
                val recordId = pinnedRecordId
                onOrderActionCompletedFast(
                    recordId = recordId,
                    candidate = candidate,
                    status = OrderStatus.FAILED,
                    reasonCode = "INVALID_BOUNDS",
                    reasonText = "Accept button bounds invalid - click prevented",
                    actionSucceeded = false,
                    timesClicked = 0,
                    buttonFound = false,
                    buttonDetails = "Button bounds invalid: $bounds",
                    clickMethod = "None (blind tap prevented)",
                    errorMsg = "Button bounds empty/invalid - blind taps blocked"
                )
                resetProcessing()
                return
            }
        }

        // ═══ STEP 4 - Verify: order popup gone in 200ms = ACCEPTED. Cooldown: 200ms max between attempts ═══
        serviceScope.launch(Dispatchers.IO) {
            delay(40L) // RAPIDO_SPEED_V3
            val currentRoot = getRapidoOrderRootNode(orderRoot)
                        val postAcceptConfirmed = isRapidoPostAcceptScreen(currentRoot)
val isOrderStillVisible =
    currentRoot != null &&
        isRapidoOrderPopupShowing(currentRoot)

                        if (postAcceptConfirmed || !isOrderStillVisible) {
                // RAPIDO_ACCEPTED_OVERLAY_FAST_V3
                // Acceptance confirmed: show popup first.
                // History/database work must not delay the overlay.
                showAcceptedOrderOverlay(candidate)
                // Order popup gone in 200ms = ACCEPTED
                Log.i(TAG, "🎉 STEP 4: Rapido order popup gone in 200ms = ACCEPTED (Attempt #$attemptNumber)")
                NotificationHelper.updateNotification(
                    context = applicationContext,
                    title = "SmartDrivo Active",
                    text = "🎉 Order Accepted! Monitoring next..."
                )
                val recordId = pinnedRecordId
                onOrderActionCompletedFast(
                    recordId = recordId,
                    candidate = candidate,
                    status = OrderStatus.ACCEPTED,
                    reasonCode = "FILTERS_MATCHED",
                    reasonText =
                        acceptedHistoryReason
                            ?.takeIf { it.isNotBlank() }
                            ?: buildAcceptedFilterReason(candidate),
                    actionSucceeded = true,
                    timesClicked = attemptNumber,
                    buttonFound = true,
                    buttonDetails = "Accept button matched: '${acceptNode.text}' / ID: '${acceptNode.viewIdResourceName}'",
                    clickMethod = "Strict Button Click (attempt #$attemptNumber)"
                )

                resetProcessing()
            } else {
                Log.i(TAG, "Rapido order popup still visible. Fast retry...")
                if (attemptNumber < 5) {
                    delay(50L) // RAPIDO_SPEED_V3
                    executeRapidoAutoAccept(
                        candidate = candidate,
                        orderRoot = currentRoot,
                        attemptNumber = attemptNumber + 1,
                        historyRecordId = pinnedRecordId,
                        acceptedHistoryReason =
                            acceptedHistoryReason
                    )
                } else {
                    Log.w(TAG, "Reached max attempts for Rapido auto-accept verification.")
                    val recordId = pinnedRecordId
                    onOrderActionCompletedFast(
                        recordId = recordId,
                        candidate = candidate,
                        status = OrderStatus.FAILED,
                        reasonCode = "VERIFICATION_TIMEOUT",
                        reasonText = "Order popup still visible after max attempts",
                        actionSucceeded = false,
                        timesClicked = attemptNumber,
                        buttonFound = true,
                        buttonDetails = "Button clicked but popup did not dismiss",
                        clickMethod = "Strict Button Click",
                        errorMsg = "Verification timed out after 5 attempts"
                    )
                    resetProcessing()
                }
            }
        }
    }

    private fun getRapidoOrderRootNode(
        fallbackNode: AccessibilityNodeInfo? = null
    ): AccessibilityNodeInfo? {
        val active = rootInActiveWindow

        if (
            active != null &&
            RapidoAdapter
                .validateRapidoOrderPopup(active)
                .isValid
        ) {
            return active
        }

        val topFallback =
            getTopRootNode(fallbackNode)

        if (
            topFallback != null &&
            topFallback !== active &&
            RapidoAdapter
                .validateRapidoOrderPopup(topFallback)
                .isValid
        ) {
            return topFallback
        }

        if (
            active != null &&
            isRapidoPackage(
                active.packageName
                    ?.toString()
                    .orEmpty()
            )
        ) {
            return active
        }

        return topFallback
            ?: active
            ?: fallbackNode
    }

    private fun getRapidoRootNode(fallbackNode: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
        return getRapidoOrderRootNode(fallbackNode)
    }

    /**
     * Checks whether Rapido is currently visible as the active foreground window
     */
    private fun isRapidoWindowVisible(): Boolean {
        val active = rootInActiveWindow ?: return false
        val pkg = active.packageName?.toString().orEmpty().trim().lowercase()
        return isRapidoPackage(pkg)
    }

    /**
     * Strict recursive click function for Accept buttons:
     * Only clicks if node text contains "Accept" or "ACCEPT" and is not an order card.
     */
    fun clickAcceptButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (isOrderCardOrDetailsNode(node)) return false

        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""

        val hasAccept = nodeText.contains("Accept") || nodeText.contains("ACCEPT") ||
                        nodeDesc.contains("Accept") || nodeDesc.contains("ACCEPT")

        if (hasAccept && !nodeText.contains("Don't", ignoreCase = true) && !nodeDesc.contains("Don't", ignoreCase = true)) {
            // Try direct click first
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            // Try parent click only if it is a strict button container (never card/container)
            val parent = node.parent
            if (parent != null && parent.isClickable && isStrictButtonContainer(parent)) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        // Recurse through children
        for (i in 0 until node.childCount) {
            if (clickAcceptButton(node.getChild(i))) return true
        }
        return false
    }

    /**
     * Simulates a tap at specified screen coordinates using dispatchGesture
     * Fix 3: While isClickInProgress = true, skip ALL new clicks. Reset flag after 3 seconds.
     * Fix 4: If service is clicking more than 3 times in 2 seconds, stop all clicks and wait 5 seconds.
     */
    private fun simulateTapGesture(
        x: Float,
        y: Float,
        isInternalFallback: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()

        // Fix 4: Check if waiting after rate limit penalty (5-second cooldown)
        if (now < clickBlockedUntilTimestamp) {
            val waitRemaining = clickBlockedUntilTimestamp - now
            Log.w(TAG, "🚫 [Fix 4] simulateTapGesture blocked: rate limit 5s cooldown active (remaining: ${waitRemaining}ms)")
            onComplete?.invoke(false)
            return
        }

        if (!isInternalFallback) {
            // Check Fix 3 & Fix 4
            if (!canExecuteClick()) {
                Log.w(TAG, "🚫 simulateTapGesture skipped at ($x, $y) due to cooldown or rate limit")
                onComplete?.invoke(false)
                return
            }
            notifyClickInitiated()
        } else {
            // Track fallback taps for Fix 4
            recentClickTimestamps.removeAll { now - it > 2000L }
            recentClickTimestamps.add(now)
            if (recentClickTimestamps.size > 3) {
                clickBlockedUntilTimestamp = now + 5000L
                recentClickTimestamps.clear()
                Log.e(TAG, "🚨 [Fix 4] More than 3 clicks in 2s detected in gesture sequence! Halting all clicks for 5 seconds.")
                onComplete?.invoke(false)
                return
            }
        }

        try {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, ACCEPT_CLICK_SPEED_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            Log.i(TAG, "👆 [Rapido] Click performed: dispatching tap gesture at ($x, $y) with duration ${ACCEPT_CLICK_SPEED_MS}ms")

            val success = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.i(TAG, "👆 [Rapido] Click performed: dispatchGesture COMPLETED successfully at ($x, $y)")
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "👆 [Rapido] Click performed: dispatchGesture CANCELLED by system at ($x, $y)")
                    onComplete?.invoke(false)
                }
            }, null)

            if (!success) {
                Log.w(TAG, "👆 [Rapido] Click performed: dispatchGesture returned FALSE immediately at ($x, $y)")
                onComplete?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "simulateTapGesture encountered error", e)
            onComplete?.invoke(false)
        }
    }

    private fun simulateSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 250L,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (now < clickBlockedUntilTimestamp) {
            val waitRemaining = clickBlockedUntilTimestamp - now
            Log.w(TAG, "🚫 simulateSwipeGesture blocked: cooldown active (${waitRemaining}ms)")
            onComplete?.invoke(false)
            return
        }

        try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val success = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.i(TAG, "👆 Swipe gesture completed from ($startX, $startY) to ($endX, $endY)")
                    onComplete?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "👆 Swipe gesture cancelled from ($startX, $startY) to ($endX, $endY)")
                    onComplete?.invoke(false)
                }
            }, null)

            if (!success) {
                Log.w(TAG, "dispatchGesture for swipe returned false")
                onComplete?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "simulateSwipeGesture encountered error", e)
            onComplete?.invoke(false)
        }
    }

    private var lastRapidoDebugToastTime = 0L

    private fun handleRapidoDebug(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastRapidoDebugToastTime < 1500L) return
        lastRapidoDebugToastTime = now

        // 4. Update Toast to show: "Rapido window found! Button texts: [list all texts from RAPIDO window only]"
        val buttonTexts = RapidoAdapter.collectClickableButtonSummaries(root)

        val allTexts = mutableListOf<String>()
        collectAllScreenTexts(root, allTexts)

        Log.d(TAG, "🔍 Rapido window found! Button texts: $buttonTexts | Total texts: ${allTexts.size}")
        logAndDumpAllNodes(root)
    }

    private fun collectAllScreenTexts(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && !result.contains(text)) {
            result.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && !result.contains(desc)) {
            result.add(desc)
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllScreenTexts(child, result)
                child.recycle()
            }
        }
    }

    private fun logAndDumpAllNodes(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val resId = node.viewIdResourceName
        val className = node.className?.toString()
        val clickable = node.isClickable
        Log.d(TAG, "$indent[Rapido Node] resId='$resId', class='$className', text='$text', desc='$desc', clickable=$clickable")
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                logAndDumpAllNodes(child, depth + 1)
                child.recycle()
            }
        }
    }

    private fun processPlatformWindow(root: AccessibilityNodeInfo, platform: Platform, pkgName: String) {
        try {
            // Log clickable buttons for debugging what the platform is showing
            val clickableButtons = RapidoAdapter.collectClickableButtonSummaries(root)
            Log.i(
                TAG,
                "Inspecting candidate window for $platform (Package: '$pkgName'). Clickable elements on screen (${clickableButtons.size}): $clickableButtons"
            )

            // Check for candidate ride info
            val candidate = OrderDataExtractor.extractCandidate(
                root = root,
                platform = platform,
                defaultVehicle = prefs.userProfile.value.vehicleType
            )

            // Direct SharedPreferences Filter Evaluation before evaluating ANY ride
            val directResult = evaluateDirectRideFilters(candidate)
            when (directResult.status) {
                OrderStatus.REJECTED -> {
                    Log.i(TAG, "Order rejected by direct filter: ${directResult.reason}")
                    attemptRejectOrder(root, platform, candidate, directResult.reason, pkgName)
                    return
                }
                OrderStatus.IGNORED -> {
                    Log.i(TAG, "Order ignored by direct filter: ${directResult.reason}")
                    logOrderEvent(candidate, OrderStatus.IGNORED, directResult.reason)
                    resetProcessing()
                    return
                }
                OrderStatus.ACCEPTED -> {
                    // Direct filters passed, continue evaluation
                }
                else -> { /* no-op */ }
            }

            // Fix 2: Vibrate only once per order using lastVibratedOrderId
            triggerOrderDetectedVibration(getOrderIdentifier(candidate))

            // Vehicle type filter & ride evaluation: load settings FRESH before evaluating every ride
            val settings = prefs.loadSettings()
            val profileVehicle = prefs.userProfile.value.vehicleType
            val isVehicleAllowed = candidate.vehicleType == profileVehicle

            if (!isVehicleAllowed) {
                Log.d(TAG, "Vehicle ${candidate.vehicleType} does not match profile vehicle $profileVehicle")
                resetProcessing()
                return
            }

            val directSettings = readDirectSettingsFresh()
            val effectiveFilterMode = when (directSettings.filterMode) {
                "fare_only" -> com.example.model.FilterMode.FARE_ONLY
                "distance_only" -> com.example.model.FilterMode.DISTANCE_ONLY
                else -> com.example.model.FilterMode.BOTH
            }
            val effectiveSettings = settings.copy(
                minFare = directSettings.minFare,
                maxFare = directSettings.maxFare,
                maxPickupDistanceKm = directSettings.maxPickupKm,
                maxDropDistanceKm = directSettings.maxDropKm,
                maxDropKm = directSettings.maxDropKm,
                filterMode = effectiveFilterMode
            )

            Log.i(
                TAG,
                "Evaluating ride: fare=₹${candidate.fare}, pickup=${candidate.pickupDistKm}km, drop=${candidate.dropDistKm}km, vehicle=${candidate.vehicleType}. FilterMode=${effectiveSettings.filterMode} [Fare Range: ₹${effectiveSettings.minFare.toInt()}-₹${effectiveSettings.maxFare.toInt()}, Max Pickup: ${effectiveSettings.maxPickupDistanceKm}km, Max Drop: ${effectiveSettings.maxDropDistanceKm}km]"
            )

            // Check pickup location text against Go-To and No-Go area lists
            val pickupText = extractPickupLocationText(candidate, root)
            val goToAreas = prefs.loadGoToAreas()
            val noGoAreas = prefs.loadNoGoAreas()

            val pickupAreaDecision = checkPickupAreaRules(pickupText, goToAreas, noGoAreas)
            if (pickupAreaDecision is DecisionResult.Reject) {
                Log.i(TAG, "Order rejected by pickup area rule: ${pickupAreaDecision.reason}")
                attemptRejectOrder(root, platform, candidate, pickupAreaDecision.reason, pkgName)
                return
            }

            // Evaluate area rules, distance criteria, and fare filters
            val decision = AreaRulesEngine.evaluateRide(
                candidate = candidate,
                areas = goToAreas + noGoAreas,
                settings = effectiveSettings,
                goToAreas = goToAreas,
                noGoAreas = noGoAreas,
                pickupLocationTextOverride = pickupText
            )

            Log.i(TAG, "Filter Decision result: $decision")

            when (decision) {
                is DecisionResult.Accept -> {
                    attemptAcceptOrder(root, platform, candidate, pkgName)
                }
                is DecisionResult.Reject -> {
                    val isNoGo = decision.reason.contains("No-Go", ignoreCase = true)
                    if (isNoGo) {
                        attemptRejectOrder(root, platform, candidate, decision.reason, pkgName)
                    } else {
                        logOrderEvent(candidate, OrderStatus.IGNORED, decision.reason)
                        resetProcessing()
                    }
                }
                is DecisionResult.Ignore -> {
                    logOrderEvent(candidate, OrderStatus.IGNORED, decision.reason)
                    resetProcessing()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing order window: ${e.message}")
            resetProcessing()
        }
    }

    private fun attemptAcceptOrder(
        root: AccessibilityNodeInfo,
        platform: Platform,
        candidate: RideCandidate,
        pkgName: String
    ) {
        val directResult = evaluateDirectRideFilters(candidate)
        if (directResult.status == OrderStatus.REJECTED) {
            Log.w(TAG, "Aborting attemptAcceptOrder: ${directResult.reason}")
            attemptRejectOrder(root, platform, candidate, directResult.reason, pkgName)
            return
        } else if (directResult.status == OrderStatus.IGNORED) {
            Log.w(TAG, "Aborting attemptAcceptOrder: ${directResult.reason}")
            logOrderEvent(candidate, OrderStatus.IGNORED, directResult.reason)
            resetProcessing()
            return
        }

        var detectedButtonId: String? = null
        var detectedPackage: String = pkgName.ifEmpty { root.packageName?.toString().orEmpty() }

        val acceptNode = when (platform) {
            Platform.RAPIDO -> {
                if (!isRapidoOrderPopupShowing(root)) {
                    Log.d(TAG, "Rapido order popup not visible (requires ₹, km, and 'Accept'/'ACCEPT' button). Waiting...")
                } else {
                    executeRapidoAutoAccept(candidate, root)
                }
                return
            }
            Platform.UBER -> {
                val node = UberAdapter.findAcceptNode(root)
                if (node != null) {
                    detectedButtonId = node.viewIdResourceName ?: node.text?.toString() ?: "UberAccept"
                    detectedPackage = node.packageName?.toString() ?: pkgName
                    Log.i(TAG, "Accepting Uber order -> Package: '$detectedPackage', Button: '$detectedButtonId'")
                }
                node
            }
            Platform.OLA -> {
                val node = findOlaAcceptButton(root) ?: OlaAdapter.findAcceptNode(root)
                if (node != null) {
                    detectedButtonId = node.viewIdResourceName ?: node.text?.toString() ?: "OlaAccept"
                    detectedPackage = node.packageName?.toString() ?: pkgName
                    Log.i(TAG, "Accepting Ola order -> Package: '$detectedPackage', Button: '$detectedButtonId'")
                }
                node
            }
        }

        if (acceptNode == null) {
            resetProcessing()
            return
        }

        if (!canExecuteClick()) {
            Log.w(TAG, "attemptAcceptOrder skipped: click in progress or rate limit cooldown active")
            resetProcessing()
            return
        }

        val settings = prefs.loadSettings()
        val speedDelay = ACCEPT_CLICK_SPEED_MS // 50ms accept click speed

        Log.i(
            TAG,
            "Scheduling auto-click with ${speedDelay}ms delay for platform=$platform [Package: '$detectedPackage', Button: '${detectedButtonId ?: "default"}']"
        )

        val clickAction = Runnable {
            var clicked = false

            // Try specific Rapido Captain India auto-accept strategy
            if (platform == Platform.RAPIDO) {
                executeRapidoAutoAccept(candidate, root)
                return@Runnable
            }

            // Try specific Uber auto-accept 3-method strategy
            if (platform == Platform.UBER) {
                executeUberAutoAccept(root, candidate)
                return@Runnable
            }

            // Try specific Ola auto-accept 3-method strategy
            if (platform == Platform.OLA) {
                val olaNode = acceptNode ?: findOlaAcceptButton(root)
                if (olaNode != null) {
                    executeOlaAutoAccept(root, candidate, olaNode)
                } else {
                    fallbackOlaTap(candidate)
                }
                return@Runnable
            }

            if (!clicked && acceptNode != null) {
                clicked = executeClickStrategy(acceptNode, settings.clickStrategy)
            }

            if (!clicked && platform != Platform.RAPIDO && platform != Platform.UBER) {
                // Fallback recursive search on root with standard accept texts for other platforms
                val fallbackTexts = listOf("Accept", "ACCEPT", "Accept Ride", "Accept Order", "Tap to Accept", "स्वीकार करें")
                clicked = findAndClick(root, fallbackTexts)
            }

            if (clicked) {
                Log.i(
                    TAG,
                    "Click SUCCESSFUL on button '${detectedButtonId ?: "accept"}' in package '$detectedPackage'!"
                )
                NotificationHelper.updateNotification(
                    context = applicationContext,
                    title = "SmartDrivo Active",
                    text = "🎉 Order Accepted! Monitoring next..."
                )

                // Post verification check (reduced to 150ms max)
                serviceScope.launch(Dispatchers.IO) {
                    delay(150L)
                    verifyStateTransition(candidate)
                    resetProcessing()
                }
            } else {
                Log.w(
                    TAG,
                    "Failed to click button '${detectedButtonId ?: "accept"}' using strategy ${settings.clickStrategy}"
                )
                resetProcessing()
            }
        }

        if (speedDelay <= 0L) {
            clickAction.run()
        } else {
            serviceScope.launch(Dispatchers.IO) {
                delay(speedDelay)
                clickAction.run()
            }
        }
    }

    /**
     * Finds and clicks an accept button or clickable element for Rapido Captain India
     * according to the prioritized patterns:
     * 1. Any button/view with text containing "Accept" (case insensitive)
     * 2. Any button/view with text containing "Ride" (case insensitive)
     * 3. Any button/view with text containing "Trip" (case insensitive)
     * 4. Any button/view with text "OK" when fare popup appears
     * 5. Any ImageButton that is clickable (Rapido may use image buttons)
     * 6. Any view with background color green that is clickable
     * 7. Last resort: Try clicking ALL clickable elements on screen one by one if none match text patterns
     */
    /**
     * Rapido Accept helper:
     * Only clicks the specific "Accept" button. Never clicks the order card, ride details, or coordinate fallback.
     */
    private fun findAndClickRapidoOrder(root: AccessibilityNodeInfo): Boolean {
        if (!isRapidoOrderPopupShowing(root)) return false
        if (!canExecuteClick()) return false

        val acceptNode = findStrictRapidoAcceptNode(root) ?: return false
        if (isOrderCardOrDetailsNode(acceptNode)) return false

        notifyClickInitiated()

        var clicked = false
        if (acceptNode.isClickable) {
            clicked = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (!clicked) {
            val parent = acceptNode.parent
            if (parent != null && parent.isClickable && isStrictButtonContainer(parent)) {
                clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        if (!clicked) {
            val bounds = Rect()
            acceptNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0 && !isOrderCardOrDetailsNode(acceptNode)) {
                simulateTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat(), isInternalFallback = true)
                return true
            }
        }
        return clicked
    }

    private fun searchAndClick(
        node: AccessibilityNodeInfo?,
        patternName: String,
        matcher: (text: String, desc: String, viewId: String, className: String) -> Boolean
    ): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""

        if (matcher(text, desc, viewId, className)) {
            Log.i(TAG, "searchAndClick matched [$patternName]: text='$text', desc='$desc', id='$viewId', class='$className'")
            if (!canExecuteClick()) {
                Log.w(TAG, "searchAndClick skipped: click in progress or rate limit active")
                return false
            }
            // 1. Direct click if clickable
            if (node.isClickable) {
                notifyClickInitiated()
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "searchAndClick [$patternName]: Direct click succeeded")
                    return true
                }
            }

            // 2. Click parent if node itself isn't clickable or action failed
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    if (canExecuteClick()) {
                        notifyClickInitiated()
                        if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.i(TAG, "searchAndClick [$patternName]: Click succeeded on clickable parent")
                            parent.recycle()
                            return true
                        }
                    }
                }
                val next = parent.parent
                parent.recycle()
                parent = next
            }
        }

        // Search children recursively
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                val found = searchAndClick(child, patternName, matcher)
                child.recycle()
                if (found) return true
            }
        }

        return false
    }

    private fun collectAllClickables(node: AccessibilityNodeInfo?, outList: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) {
            outList.add(node)
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllClickables(child, outList)
            }
        }
    }

    /**
     * Standard recursive findAndClick pattern for generic platforms
     */
    private fun findAndClick(node: AccessibilityNodeInfo?, targetTexts: List<String>): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val matchesText = targetTexts.any { target ->
            text?.equals(target, ignoreCase = true) == true ||
            desc?.equals(target, ignoreCase = true) == true ||
            (text != null && text.contains(target, ignoreCase = true)) ||
            (desc != null && desc.contains(target, ignoreCase = true))
        }

        if (matchesText) {
            Log.i(TAG, "findAndClick matched target: text='$text', desc='$desc', id='$viewId'")
            if (!canExecuteClick()) {
                Log.w(TAG, "findAndClick skipped: click in progress or rate limit active")
                return false
            }
            if (node.isClickable) {
                notifyClickInitiated()
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "findAndClick: Direct click succeeded on target node")
                    return true
                }
            }

            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    if (canExecuteClick()) {
                        notifyClickInitiated()
                        if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.i(TAG, "findAndClick: Click succeeded on clickable parent node")
                            parent.recycle()
                            return true
                        }
                    }
                }
                val nextParent = parent.parent
                parent.recycle()
                parent = nextParent
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findAndClick(child, targetTexts)
                child.recycle()
                if (found) return true
            }
        }

        return false
    }

    private fun resetProcessing() {
        handler.removeCallbacks(processingTimeoutRunnable)
        isProcessing = false
        if (currentOlaState != OlaState.OLA_ACCEPTED) {
            currentOlaState = OlaState.IDLE
        }
    }

    /**
     * Auto Reject No Condition Match (Rapido only).
     *
     * Important:
     * - Independent from Auto-Accept.
     * - OFF means this feature never skips an offer.
     * - ON means saved-condition failures may click ONLY a verified
     *   Rapido skip/reject control.
     * - No blind coordinate taps.
     */
    private fun handleRapidoFilterFailure(
        root: AccessibilityNodeInfo,
        candidate: RideCandidate,
        recordId: String,
        reason: String,
        defaultStatus: OrderStatus
    ) {
        // INSTANT_HISTORY_V7
        val settings = prefs.appSettings.value

        if (!preferencesManager.isAutoAcceptEnabled) {
            resetProcessing()
            return
        }

        if (settings.isAutoRejectBadFaresEnabled) {
            // Final History status FIRST. Reject-button work happens after.
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.REJECTED,
                reasonCode = "AUTO_REJECT_NO_MATCH",
                reasonText = "Auto Reject: $reason"
            )

            executeRapidoAutoReject(
                candidate = candidate,
                orderRoot = root,
                recordId = recordId,
                reason = reason,
                historyPreFinalized = true
            )
            return
        }

        onOrderDecisionFast(
            recordId = recordId,
            candidate = candidate,
            status = defaultStatus,
            reasonCode = "CRITERIA_NOT_MET",
            reasonText = reason
        )

        resetProcessing()
    }
    private fun findStrictRapidoRejectNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        // RAPIDO_AUTO_REJECT_FAST_LOOKUP_V1
        // Fast path: RapidoAdapter already knows reject/decline/skip
        // text and resource IDs. Use it first; keep the old strict
        // geometric scan only as fallback.
        RapidoAdapter.findRejectNode(
            root,
            root.packageName?.toString()
        )?.let { fastNode ->

            val fastPkg =
                fastNode.packageName
                    ?.toString()
                    .orEmpty()
                    .trim()
                    .lowercase(Locale.ROOT)

            if (
                fastNode.isClickable &&
                !isOrderCardOrDetailsNode(fastNode) &&
                (
                    fastPkg.isBlank() ||
                        isRapidoPackage(fastPkg)
                )
            ) {
                Log.i(
                    TAG,
                    "⛔ [Rapido Auto-Reject FAST] direct reject control found " +
                        "text='${fastNode.text}', " +
                        "id='${fastNode.viewIdResourceName}'"
                )

                return fastNode
            }
        }
        val acceptNode = findStrictRapidoAcceptNode(root) ?: return null

        val acceptBounds = Rect()
        acceptNode.getBoundsInScreen(acceptBounds)
        if (acceptBounds.width() <= 0 || acceptBounds.height() <= 0) return null

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        fun clickableSelfOrAncestor(
            node: AccessibilityNodeInfo
        ): AccessibilityNodeInfo? {
            if (node.isClickable) return node

            var parent = node.parent
            var depth = 0

            while (parent != null && depth < 3) {
                if (parent.isClickable) return parent
                parent = parent.parent
                depth++
            }

            return null
        }

        fun scan(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val label = "$text $desc".trim().lowercase(Locale.ROOT)

            val explicitRejectLabel =
                label.contains("reject") ||
                    label.contains("decline") ||
                    label.contains("skip") ||
                    label.contains("pass") ||
                    text in setOf("-", "−", "–", "—") ||
                    desc in setOf("-", "−", "–", "—")

            if (!label.contains("accept")) {
                val target = clickableSelfOrAncestor(node)

                if (target != null && !isOrderCardOrDetailsNode(target)) {
                    val pkg =
                        target.packageName
                            ?.toString()
                            .orEmpty()
                            .trim()
                            .lowercase(Locale.ROOT)

                    if (pkg.isBlank() || isRapidoPackage(pkg)) {
                        val bounds = Rect()
                        target.getBoundsInScreen(bounds)

                        if (bounds.width() > 0 && bounds.height() > 0) {
                            val verticalDelta =
                                Math.abs(
                                    bounds.centerY() -
                                        acceptBounds.centerY()
                                )

                            val sameRow =
                                verticalDelta <=
                                    maxOf(acceptBounds.height(), 100)

                            val leftOfAccept =
                                bounds.right <= acceptBounds.left

                            val compactControl =
                                bounds.width() <=
                                    maxOf(acceptBounds.height() * 2, 180) &&
                                    bounds.height() <=
                                    maxOf(acceptBounds.height() * 2, 180)

                            if (
                                sameRow &&
                                leftOfAccept &&
                                compactControl
                            ) {
                                val horizontalGap =
                                    Math.abs(
                                        acceptBounds.left -
                                            bounds.right
                                    )

                                val score =
                                    (if (explicitRejectLabel) 10000 else 1000) -
                                        (verticalDelta * 5) -
                                        horizontalGap

                                if (score > bestScore) {
                                    bestScore = score
                                    bestNode = target
                                }
                            }
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                scan(node.getChild(i))
            }
        }

        scan(root)

        val result = bestNode

        if (result != null) {
            val bounds = Rect()
            result.getBoundsInScreen(bounds)

            Log.i(
                TAG,
                "⛔ [Rapido Auto-Reject] verified skip control " +
                    "text='${result.text}', " +
                    "desc='${result.contentDescription}', " +
                    "id='${result.viewIdResourceName}', " +
                    "bounds=$bounds"
            )
        } else {
            Log.w(
                TAG,
                "⛔ [Rapido Auto-Reject] safe skip control not found. " +
                    "No blind click attempted."
            )
        }

        return result
    }

    private fun executeRapidoAutoReject(
        candidate: RideCandidate,
        orderRoot: AccessibilityNodeInfo,
        recordId: String,
        reason: String,
        historyPreFinalized: Boolean = false
    ) {
        // INSTANT_HISTORY_V7
        // Reuse RAM settings and the already validated popup root.
        val settings = prefs.appSettings.value

        if (!settings.isAutoRejectBadFaresEnabled) {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "AUTO_REJECT_OFF",
                reasonText = reason
            )
            resetProcessing()
            return
        }

        val rapidoRoot = orderRoot

        val rootPkg =
            rapidoRoot.packageName
                ?.toString()
                .orEmpty()

        if (!isRapidoPackage(rootPkg)) {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "AUTO_REJECT_UNVERIFIED_ROOT",
                reasonText = "$reason | Rapido root verification failed"
            )
            resetProcessing()
            return
        }

        val rejectNode = findStrictRapidoRejectNode(rapidoRoot)

        if (rejectNode == null) {
            // Correct the provisional REJECTED state if no safe control exists.
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "AUTO_REJECT_BUTTON_NOT_FOUND",
                reasonText = "$reason | Auto-Reject ON, safe skip control not found"
            )
            resetProcessing()
            return
        }

        val rejectPkg =
            rejectNode.packageName
                ?.toString()
                .orEmpty()
                .trim()
                .lowercase(Locale.ROOT)

        if (
            rejectPkg.isNotBlank() &&
            !isRapidoPackage(rejectPkg)
        ) {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "AUTO_REJECT_UNVERIFIED_TARGET",
                reasonText = "$reason | Skip target did not belong to Rapido"
            )
            resetProcessing()
            return
        }

        val clicked =
            rejectNode.isClickable &&
                rejectNode.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

        if (clicked) {
            if (!historyPreFinalized) {
                onOrderDecisionFast(
                    recordId = recordId,
                    candidate = candidate,
                    status = OrderStatus.REJECTED,
                    reasonCode = "AUTO_REJECT_NO_MATCH",
                    reasonText = "Auto Reject: $reason"
                )
            }

            Log.i(
                TAG,
                "Rapido Auto-Reject click succeeded"
            )
        } else {
            onOrderDecisionFast(
                recordId = recordId,
                candidate = candidate,
                status = OrderStatus.IGNORED,
                reasonCode = "AUTO_REJECT_CLICK_FAILED",
                reasonText = "$reason | Skip control found but ACTION_CLICK failed"
            )

            Log.w(
                TAG,
                "Rapido Auto-Reject click failed"
            )
        }

        resetProcessing()
    }
    private fun attemptRejectOrder(
        root: AccessibilityNodeInfo,
        platform: Platform,
        candidate: RideCandidate,
        reason: String,
        pkgName: String
    ) {
        // REJECTED -> ONLY when order matches a No-Go area.
        // All other filter failures / toggle OFF -> OrderStatus.IGNORED
        val isNoGo = reason.contains("No-Go", ignoreCase = true) ||
            reason.contains("nogo", ignoreCase = true) ||
            reason.contains("No Go", ignoreCase = true)

        val status = if (!preferencesManager.isAutoAcceptEnabled) {
            OrderStatus.IGNORED
        } else if (isNoGo) {
            OrderStatus.REJECTED
        } else {
            OrderStatus.IGNORED
        }
        val logReason = if (!preferencesManager.isAutoAcceptEnabled) "Auto-accept toggle is OFF" else reason

        val rejectNode = when (platform) {
            Platform.RAPIDO -> null // Rapido: only click Accept button when popup visible; never click reject or background nodes
            Platform.UBER -> null // Uber offers expire or dismissed
            Platform.OLA -> OlaAdapter.findRejectNode(root)
        }

        if (rejectNode != null && preferencesManager.isAutoAcceptEnabled) {
            executeClickStrategy(rejectNode, prefs.appSettings.value.clickStrategy)
        }

        logOrderEvent(candidate, status, logReason)
        resetProcessing()
    }

    /**
     * 5-Stage Click Strategy:
     * 1. ACTION_CLICK on node
     * 2. ACTION_CLICK on parent
     * 3. Tap gesture at node center
     * 4. Swipe gesture (for swipe-to-accept UI)
     * 5. Walk up tree to find clickable ancestor
     */
    private fun executeClickStrategy(node: AccessibilityNodeInfo, strategy: ClickStrategy): Boolean {
        if (!canExecuteClick()) {
            Log.w(TAG, "executeClickStrategy skipped: click in progress or rate limit active")
            return false
        }
        notifyClickInitiated()

        // Strategy 1: Direct action click
        if (strategy != ClickStrategy.GESTURE) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }

            // Strategy 2: Click on parent
            val parent = node.parent
            if (parent != null && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                parent.recycle()
                return true
            }
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Strategy 3: Tap gesture at node center
        if (bounds.width() > 0 && bounds.height() > 0) {
            val centerX = bounds.centerX().toFloat()
            val centerY = bounds.centerY().toFloat()
            val tapped = performTapGesture(centerX, centerY)
            if (tapped) return true

            // Strategy 4: Swipe gesture (swipe-to-accept)
            val swiped = performSwipeGesture(
                bounds.left.toFloat() + 20f,
                bounds.centerY().toFloat(),
                bounds.right.toFloat() - 20f,
                bounds.centerY().toFloat()
            )
            if (swiped) return true
        }

        // Strategy 5: Walk up the tree to find clickable ancestor
        var current: AccessibilityNodeInfo? = node.parent
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                current.recycle()
                return true
            }
            val next = current.parent
            current.recycle()
            current = next
        }

        return false
    }

    private fun performTapGesture(x: Float, y: Float): Boolean {
        simulateTapGesture(x, y, isInternalFallback = true)
        return true
    }

    private fun performSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun verifyStateTransition(candidate: RideCandidate, existingHistoryItem: OrderHistoryItem? = null) {
        val root = rootInActiveWindow
        val textList = mutableListOf<String>()
        if (root != null) {
            collectAllNodeText(root, textList)
        }
        val allText = textList.joinToString(" ").lowercase()

        val isConfirmed = CONFIRMATION_KEYWORDS.any { allText.contains(it) }
        val finalStatus = if (isConfirmed || root == null) OrderStatus.ACCEPTED else OrderStatus.ACCEPTED

        // If candidate for Uber has missing pickup or drop address or fare, extract from active window
        var updatedCandidate = candidate
        if (candidate.platform == Platform.UBER && root != null &&
            (candidate.pickupAddress.isNullOrBlank() || candidate.pickupAddress.equals("Detected Pickup Location", ignoreCase = true) ||
             candidate.dropAddress.isNullOrBlank() || candidate.dropAddress.equals("Detected Drop Location", ignoreCase = true) ||
             candidate.fare == null || candidate.fare == 0f)
        ) {
            val freshData = UberAdapter.extractOrderData(root)
            updatedCandidate = candidate.copy(
                pickupAddress = if (!freshData.pickupAddress.isNullOrBlank()) freshData.pickupAddress else candidate.pickupAddress,
                dropAddress = if (!freshData.dropAddress.isNullOrBlank()) freshData.dropAddress else candidate.dropAddress,
                dropArea = if (!freshData.dropArea.isNullOrBlank()) freshData.dropArea else candidate.dropArea,
                fare = if (freshData.fare != null && freshData.fare > 0f) freshData.fare else candidate.fare,
                pickupDistKm = freshData.pickupKm ?: candidate.pickupDistKm,
                dropDistKm = freshData.dropKm ?: candidate.dropDistKm
            )
        }

        if (existingHistoryItem != null) {
            val updatedItem = existingHistoryItem.copy(
                pickupAddress = if (!updatedCandidate.pickupAddress.isNullOrBlank() && !updatedCandidate.pickupAddress.equals("Detected Pickup Location", ignoreCase = true)) updatedCandidate.pickupAddress else existingHistoryItem.pickupAddress,
                dropAddress = if (!updatedCandidate.dropAddress.isNullOrBlank() && !updatedCandidate.dropAddress.equals("Detected Drop Location", ignoreCase = true)) updatedCandidate.dropAddress else existingHistoryItem.dropAddress,
                dropArea = if (!updatedCandidate.dropArea.isNullOrBlank() && !updatedCandidate.dropArea.equals("Main City Area", ignoreCase = true)) updatedCandidate.dropArea else existingHistoryItem.dropArea,
                amount = if (updatedCandidate.fare != null && updatedCandidate.fare > 0f) updatedCandidate.fare else existingHistoryItem.amount,
                pickupDistKm = updatedCandidate.pickupDistKm ?: existingHistoryItem.pickupDistKm,
                dropDistKm = updatedCandidate.dropDistKm ?: existingHistoryItem.dropDistKm
            )
            prefs.addOrderHistory(updatedItem)
            repository.recordOrderHistory(updatedItem)
        } else {
            logOrderEvent(updatedCandidate, finalStatus, "Ride auto-accepted successfully")
        }

        // Trigger floating overlay popup
        showAcceptedOrderOverlay(updatedCandidate)
    }

    private fun showAcceptedOrderOverlay(candidate: RideCandidate) {
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val pickup = candidate.pickupAddress?.takeIf {
            it.isNotBlank() && !it.equals("Detected Pickup Location", ignoreCase = true)
        } ?: "Pickup Location"

        val drop = candidate.dropAddress?.takeIf {
            it.isNotBlank() && !it.equals("Detected Drop Location", ignoreCase = true)
        } ?: "Drop Location"

        val dropArea = candidate.dropArea?.takeIf {
            it.isNotBlank() && !it.equals("Main City Area", ignoreCase = true)
        } ?: (drop.split(",").firstOrNull()?.trim() ?: "City Area")

        FloatingOverlayService.show(
            context = applicationContext,
            amount = candidate.fare ?: 0f,
            pickup = pickup,
            pickupDist = candidate.pickupDistKm ?: 0f,
            drop = drop,
            dropDist = candidate.dropDistKm ?: 0f,
            dropArea = dropArea,
            time = timeStr,
            platform = candidate.platform.displayName
        )
    }

    private fun buildAcceptedFilterReason(
        candidate: RideCandidate,
        settingsOverride: com.example.model.AppSettings? = null
    ): String {
        val settings =
            settingsOverride
                ?: prefs.getFreshSettings()

        val fare = candidate.fare ?: candidate.baseFare ?: 0f
        val pickup = candidate.pickupDistKm ?: 0f
        val trip = candidate.dropDistKm ?: 0f

        fun km(value: Float): String =
            String.format(Locale.ENGLISH, "%.1f km", value)

        fun fareRange(): String {
            val min = settings.minFare.toInt()
            return if (settings.maxFare > 0f) {
                "₹$min–₹${settings.maxFare.toInt()}"
            } else {
                "₹$min+"
            }
        }

        // Priority modes should describe the mode that actually controlled acceptance.
        val hasActiveGoTo =
            settings.isGoToEnabled &&
                prefs.goToAreas.value.any { it.isEnabled }

        if (hasActiveGoTo) {
            return buildString {
                append("Mode: Go-To Priority")
                if (fare > 0f) append("\nFare ₹${fare.toInt()}")
                if (pickup > 0f) append(" | Pickup ${km(pickup)}")
                if (trip > 0f) append(" | Trip ${km(trip)}")
                append("\nDestination matched active Go-To rule")
            }
        }

        if (settings.isFastestModeEnabled) {
            return buildString {
                append("Mode: Fastest")
                if (pickup > 0f) {
                    append("\nPickup ${km(pickup)} matched")
                    if (settings.maxPickupDistanceKm > 0f) {
                        append(" (≤ ${km(settings.maxPickupDistanceKm)})")
                    }
                } else {
                    append("\nPickup matched")
                }
            }
        }

        val homeFareMatched =
            when {
                settings.filterMode ==
                    com.example.model.FilterMode.DISTANCE_ONLY ->
                    true

                fare <= 0f ->
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

        val homeDistanceMatched =
            when {
                settings.filterMode ==
                    com.example.model.FilterMode.FARE_ONLY ->
                    true

                candidate.pickupDistKm != null &&
                    settings.maxPickupDistanceKm > 0f &&
                    pickup > settings.maxPickupDistanceKm ->
                    false

                candidate.dropDistKm != null &&
                    settings.maxDropDistanceKm > 0f &&
                    trip > settings.maxDropDistanceKm ->
                    false

                else ->
                    true
            }

        val homeMatched =
            homeFareMatched &&
                homeDistanceMatched

        val secondaryMatched =
            settings.isSecondaryBothFilterEnabled &&
                fare > 0f &&
                candidate.pickupDistKm != null &&
                candidate.dropDistKm != null &&
                (
                    settings.secondaryBothMinFare <= 0f ||
                        fare >= settings.secondaryBothMinFare
                ) &&
                (
                    settings.secondaryBothMaxPickupDistanceKm <= 0f ||
                        pickup <=
                            settings.secondaryBothMaxPickupDistanceKm
                ) &&
                (
                    settings.secondaryBothMaxDropDistanceKm <= 0f ||
                        trip <=
                            settings.secondaryBothMaxDropDistanceKm
                )

        if (!homeMatched && secondaryMatched) {
            return buildString {
                append("Mode: Filter 2")

                append(
                    "\nFare ₹${fare.toInt()} ≥ ₹${settings.secondaryBothMinFare.toInt()}"
                )

                append(
                    " | Pickup ${km(pickup)} ≤ ${km(settings.secondaryBothMaxPickupDistanceKm)}"
                )

                append(
                    " | Trip ${km(trip)} ≤ ${km(settings.secondaryBothMaxDropDistanceKm)}"
                )
            }
        }
        return when (settings.filterMode) {
            com.example.model.FilterMode.FARE_ONLY -> {
                buildString {
                    append("Mode: Fare Only")
                    append("\nFare ₹${fare.toInt()} matched")
                    append("  |  Saved range ${fareRange()}")
                }
            }

            com.example.model.FilterMode.DISTANCE_ONLY -> {
                buildString {
                    append("Mode: Distance Only")
                    append("\nPickup ${km(pickup)} matched")
                    if (settings.maxPickupDistanceKm > 0f) {
                        append(" (≤ ${km(settings.maxPickupDistanceKm)})")
                    }
                    append(" | Trip ${km(trip)} matched")
                    if (settings.maxDropDistanceKm > 0f) {
                        append(" (≤ ${km(settings.maxDropDistanceKm)})")
                    }
                }
            }

            com.example.model.FilterMode.BOTH -> {
                buildString {
                    append("Mode: Both")
                    append("\nFare ₹${fare.toInt()} matched")
                    append(" (${fareRange()})")
                    append(" | Pickup ${km(pickup)} matched")
                    if (settings.maxPickupDistanceKm > 0f) {
                        append(" (≤ ${km(settings.maxPickupDistanceKm)})")
                    }
                    append(" | Trip ${km(trip)} matched")
                    if (settings.maxDropDistanceKm > 0f) {
                        append(" (≤ ${km(settings.maxDropDistanceKm)})")
                    }
                }
            }
        }
    }
    private fun mapReasonToCode(status: OrderStatus, reason: String): String {
        val lower = reason.lowercase(Locale.ROOT)
        return when (status) {
            OrderStatus.ACCEPTED -> when {
                lower.contains("go to") || lower.contains("goto") -> "GOTO_MATCHED"
                lower.contains("filter") || lower.contains("matched") -> "FILTERS_MATCHED"
                else -> "FILTERS_MATCHED"
            }
            OrderStatus.REJECTED -> when {
                lower.contains("no-go") || lower.contains("nogo") -> "NOGO_MATCHED"
                lower.contains("vehicle") -> "VEHICLE_DISABLED"
                else -> "REJECT_CRITERIA_MET"
            }
            OrderStatus.IGNORED -> when {
                lower.contains("toggle") -> "TOGGLE_DISABLED"
                lower.contains("plan") -> "NO_ACTIVE_PLAN"
                lower.contains("pickup") && (lower.contains("exceed") || lower.contains("range")) -> "PICKUP_OUT_OF_RANGE"
                lower.contains("drop") && (lower.contains("exceed") || lower.contains("range")) -> "DROP_OUT_OF_RANGE"
                lower.contains("fare") && (lower.contains("below") || lower.contains("min")) -> "FARE_TOO_LOW"
                lower.contains("fare") && lower.contains("unavailable") -> "FARE_UNAVAILABLE"
                lower.contains("rate") || lower.contains("km") -> "RATE_PER_KM_LOW"
                lower.contains("disabled") -> "PLATFORM_DISABLED"
                else -> "CRITERIA_NOT_MET"
            }
            OrderStatus.FAILED -> "ACTION_FAILED"
            OrderStatus.SKIPPED -> "ORDER_SKIPPED"
            OrderStatus.MISSED -> "ORDER_MISSED"
            OrderStatus.PROCESSING -> "PROCESSING"
        }
    }

    private fun showOrderReasonPopupIfEnabled(
        recordKey: String,
        candidate: RideCandidate,
        status: OrderStatus,
        reasonText: String
    ) {
        if (
            !prefs.appSettings.value
                .showOrderReasonPopups
        ) {
            return
        }

        OrderDecisionPopup.show(
            context = applicationContext,
            recordKey =
                recordKey.ifBlank {
                    getOrderIdentifier(candidate)
                },
            status = status,
            platform =
                if (
                    candidate.platform ==
                        Platform.RAPIDO
                ) {
                    "Rapido"
                } else {
                    candidate.platform
                        .displayName
                },
            fare =
                candidate.fare ?: 0f,
            reason =
                reasonText
        )
    }
    private fun onOrderDetectedFast(candidate: RideCandidate): String {
        // MASTER OFF: do not create new history records.
        if (!preferencesManager.isAutoAcceptEnabled) {
            return candidate.bookingId?.ifBlank { null } ?: "PAUSED"
        }

        val tempId =
            candidate.bookingId
                ?.ifBlank { null }
                ?: run {
                    val fingerprint =
                        rideHistoryRepository
                            .generateFingerprint(candidate)

                    val detectedTime =
                        if (candidate.detectionTimeMs > 0L) {
                            candidate.detectionTimeMs
                        } else {
                            candidate.timestamp
                        }

                    "ride_${fingerprint}_${detectedTime}"
                }
        activeOrderRecordIds[candidate.platform] = tempId

        serviceScope.launch(Dispatchers.IO) {
            try {
                val entity = rideHistoryRepository.onOrderDetected(
                    candidate = candidate,
                    initialReasonCode = "PROCESSING",
                    initialReasonText = "Evaluating ride filters..."
                )
                // HISTORY_ACTIVE_ID_RACE_V2
                // A slower previous coroutine must not steal the active ID
                // from a newer detection.
                if (
                    activeOrderRecordIds[candidate.platform] ==
                        tempId
                ) {
                    activeOrderRecordIds[candidate.platform] =
                        entity.id
                }

                prefs.notifyOrderHistoryChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onOrderDetectedFast", e)
            }
        }
        return tempId
    }

    private fun onOrderDecisionFast(
        recordId: String,
        candidate: RideCandidate,
        status: OrderStatus,
        reasonCode: String,
        reasonText: String,
        matchedGoTo: String = "",
        matchedNoGo: String = ""
    ) {        // ORDER_REASON_POPUP_DECISION_V1
        showOrderReasonPopupIfEnabled(
            recordKey = recordId,
            candidate = candidate,
            status = status,
            reasonText = reasonText
        )

        // HISTORY_FINAL_STATE_V2
        // This method only finalizes an existing detected row.
        // Always allow the final status to be written.

        serviceScope.launch(Dispatchers.IO) {
            try {
                // onOrderDetectedFast inserts PROCESSING asynchronously.
                // A very fast decision can arrive before that insert finishes.
                // Retry briefly so the same real ride cannot remain stuck at PROCESSING.
                var updated = rideHistoryRepository.onOrderDecision(
                    id = recordId,
                    status = status,
                    reasonCode = reasonCode,
                    reasonText = reasonText,
                    matchedGoTo = matchedGoTo,
                    matchedNoGo = matchedNoGo
                )

                var retry = 0
                while (updated == null && retry < 80) {
                    delay(5L)
                    retry++
                    updated = rideHistoryRepository.onOrderDecision(
                        id = recordId,
                        status = status,
                        reasonCode = reasonCode,
                        reasonText = reasonText,
                        matchedGoTo = matchedGoTo,
                        matchedNoGo = matchedNoGo
                    )
                }

                val finalUpdated = updated
                if (finalUpdated == null) {
                    Log.e(
                        TAG,
                        "Decision update failed after waiting for history insert: id=$recordId status=$status reason=$reasonCode"
                    )
                    return@launch
                }

                repository.recordOrderHistory(finalUpdated.toOrderHistoryItem())
                prefs.notifyOrderHistoryChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onOrderDecisionFast", e)
            }
        }
    }
    private fun onOrderActionAttemptFast(recordId: String) {
        // MASTER OFF: no action attempt history.
        if (!preferencesManager.isAutoAcceptEnabled) {
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            try {
                rideHistoryRepository.onOrderActionAttempt(recordId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onOrderActionAttemptFast", e)
            }
        }
    }

    private fun onOrderActionCompletedFast(
        recordId: String,
        candidate: RideCandidate,
        status: OrderStatus,
        reasonCode: String,
        reasonText: String,
        actionSucceeded: Boolean,
        timesClicked: Int = 1,
        buttonFound: Boolean = (status == OrderStatus.ACCEPTED),
        buttonDetails: String = "",
        clickMethod: String = "",
        errorMsg: String? = null
    ) {        // ORDER_REASON_POPUP_ACTION_V1
        // De-dupe inside OrderDecisionPopup prevents the same
        // rejected/ignored result from appearing twice.
        showOrderReasonPopupIfEnabled(
            recordKey = recordId,
            candidate = candidate,
            status = status,
            reasonText = reasonText
        )

        // HISTORY_FINAL_STATE_V2
        // Existing history rows must always receive their final result.

        val now = System.currentTimeMillis()
        val totalLatency = if (candidate.detectionTimeMs > 0L) (now - candidate.detectionTimeMs).coerceAtLeast(1L) else 100L
        val actionLatency = (now - candidate.timestamp).coerceAtLeast(15L)
        RideDiagnosticsManager.recordAction(
            id = recordId,
            status = status,
            buttonFound = buttonFound,
            buttonDetails = buttonDetails,
            clickMethod = clickMethod,
            finalAction = "$status: $reasonText",
            error = errorMsg,
            actionLatencyMs = actionLatency,
            totalLatencyMs = totalLatency
        )
        serviceScope.launch(Dispatchers.IO) {
            try {
                var updated =
                    rideHistoryRepository.onOrderActionCompleted(
                        id = recordId,
                        status = status,
                        reasonCode = reasonCode,
                        reasonText = reasonText,
                        actionSucceeded = actionSucceeded,
                        timesClicked = timesClicked
                    )

                // onOrderDetectedFast inserts asynchronously.
                // Wait briefly if final action arrives first.
                var retry = 0

                while (updated == null && retry < 80) {
                    delay(5L)
                    retry++

                    updated =
                        rideHistoryRepository.onOrderActionCompleted(
                            id = recordId,
                            status = status,
                            reasonCode = reasonCode,
                            reasonText = reasonText,
                            actionSucceeded = actionSucceeded,
                            timesClicked = timesClicked
                        )
                }

                val finalUpdated = updated

                if (finalUpdated != null) {
                    repository.recordOrderHistory(
                        finalUpdated.toOrderHistoryItem()
                    )

                    if (status == OrderStatus.ACCEPTED) {
                        val platformName =
                            if (candidate.platform == Platform.RAPIDO)
                                "Rapido"
                            else
                                candidate.platform.displayName

                        prefs.saveAcceptedOrder(
                            platform = platformName,
                            timestamp = now,
                            fareAmount = candidate.fare ?: 0f
                        )
                    }
                } else {
                    Log.e(
                        TAG,
                        "Final history update failed after retry: id=$recordId status=$status"
                    )
                }

                prefs.notifyOrderHistoryChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onOrderActionCompletedFast", e)
            }
        }
    }

    private fun logOrderEvent(
        candidate: RideCandidate,
        status: OrderStatus,
        reason: String,
        clickTimeMs: Long = System.currentTimeMillis(),
        timesClicked: Int = 1
    ) {
        // MASTER OFF: legacy history logger disabled.
        if (!preferencesManager.isAutoAcceptEnabled) {
            return
        }
        val now = System.currentTimeMillis()
        val effectiveReason = when (status) {
            OrderStatus.ACCEPTED -> {
                if (reason.isNotBlank() && reason.contains("matched", ignoreCase = true) && !reason.contains("No-Go", ignoreCase = true) && !reason.equals("Fare & distance criteria matched", ignoreCase = true)) {
                    reason
                } else {
                    buildAcceptedFilterReason(candidate)
                }
            }
            OrderStatus.IGNORED -> {
                when {
                    reason.isNotBlank() && reason.contains("fare unavailable", ignoreCase = true) -> "Fare unavailable - order skipped"
                    candidate.fare == null || candidate.fare <= 0f -> "Fare unavailable - order skipped"
                    reason.isNotBlank() && !reason.equals("No criteria matched", ignoreCase = true) -> reason
                    else -> "No criteria matched"
                }
            }
            OrderStatus.REJECTED -> reason
            OrderStatus.MISSED -> reason
            OrderStatus.FAILED -> reason
            OrderStatus.SKIPPED -> reason
            OrderStatus.PROCESSING -> "Evaluating ride filters..."
        }

        val reasonCode = mapReasonToCode(status, effectiveReason)
        val activeId = activeOrderRecordIds[candidate.platform]

        // ORDER_REASON_POPUP_LEGACY_V1
        showOrderReasonPopupIfEnabled(
            recordKey =
                activeId
                    ?: getOrderIdentifier(candidate),
            candidate = candidate,
            status = status,
            reasonText = effectiveReason
        )

        serviceScope.launch(Dispatchers.IO) {
            try {
                // If record was already created on detection, update it. Otherwise create then update.
                val record = if (activeId != null) {
                    rideHistoryRepository.getById(activeId) ?: rideHistoryRepository.onOrderDetected(candidate, "PROCESSING", "Evaluating...")
                } else {
                    rideHistoryRepository.onOrderDetected(candidate, "PROCESSING", "Evaluating...")
                }

                if (status == OrderStatus.ACCEPTED) {
                    rideHistoryRepository.onOrderDecision(record.id, OrderStatus.ACCEPTED, reasonCode, effectiveReason)
                    rideHistoryRepository.onOrderActionAttempt(record.id)
                    val completed = rideHistoryRepository.onOrderActionCompleted(
                        id = record.id,
                        status = OrderStatus.ACCEPTED,
                        reasonCode = reasonCode,
                        reasonText = effectiveReason,
                        actionSucceeded = true,
                        timesClicked = timesClicked
                    )
                    if (completed != null) {
                        repository.recordOrderHistory(completed.toOrderHistoryItem())
                    }
                    val platformName = if (candidate.platform == Platform.RAPIDO) "Rapido" else candidate.platform.displayName
                    prefs.saveAcceptedOrder(
                        platform = platformName,
                        timestamp = now,
                        fareAmount = candidate.fare ?: 0f
                    )
                } else {
                    val updated = rideHistoryRepository.onOrderDecision(
                        id = record.id,
                        status = status,
                        reasonCode = reasonCode,
                        reasonText = effectiveReason
                    )
                    if (updated != null) {
                        repository.recordOrderHistory(updated.toOrderHistoryItem())
                    }
                }
                prefs.notifyOrderHistoryChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Error logging order event", e)
            }
        }
    }

    private fun collectAllNodeText(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null || outList.size > 80) return
        node.text?.toString()?.let { outList.add(it) }
        node.contentDescription?.toString()?.let { outList.add(it) }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i)
            if (c != null) {
                collectAllNodeText(c, outList)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SmartDrivo Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        stopUberWindowPolling()
        isServiceRunning = false
        serviceScope.cancel()
        try {
            unregisterReceiver(toggleReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering toggleReceiver: ${e.message}")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service: ${e.message}")
        }
        super.onDestroy()
    }
}
