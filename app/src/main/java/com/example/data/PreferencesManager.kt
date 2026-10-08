package com.example.data

import com.example.model.AreaEntry

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AppSettings
import com.example.model.AppThemeMode
import com.example.model.AreaGroup
import com.example.model.AreaType
import com.example.model.BundleOrderAction
import com.example.model.ClickSpeed
import com.example.model.ClickStrategy
import com.example.model.FilterMode
import com.example.model.MembershipPlan
import com.example.model.OrderHistoryItem
import com.example.model.OrderStatus
import com.example.model.OutOfRangeAction
import com.example.model.PaymentStatus
import com.example.model.PaymentSubmission
import com.example.model.Platform
import com.example.model.UserProfile
import com.example.model.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PreferencesManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartdrivo_prefs", Context.MODE_PRIVATE)

    // Reactive StateFlows for UI
    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _appSettings = MutableStateFlow(loadAppSettingsRaw())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _goToAreas = MutableStateFlow(loadGoToAreaGroups())
    val goToAreas: StateFlow<List<AreaGroup>> = _goToAreas.asStateFlow()

    private val _noGoAreas = MutableStateFlow(loadNoGoAreaGroups())
    val noGoAreas: StateFlow<List<AreaGroup>> = _noGoAreas.asStateFlow()

    private val _isGoToEnabled = MutableStateFlow(prefs.getBoolean(KEY_GOTO_ENABLED, false))
    val isGoToEnabledFlow: StateFlow<Boolean> = _isGoToEnabled.asStateFlow()
    var isGoToEnabled: Boolean
        get() = prefs.getBoolean(KEY_GOTO_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GOTO_ENABLED, value).apply()
            _isGoToEnabled.value = value
        }

    private val _isNoGoEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOGO_ENABLED, true))
    val isNoGoEnabledFlow: StateFlow<Boolean> = _isNoGoEnabled.asStateFlow()
    var isNoGoEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOGO_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOGO_ENABLED, value).apply()
            _isNoGoEnabled.value = value
        }

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_IS_LOGGED_IN, false))
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
            _isLoggedIn.value = value
        }

    private val _hasOpenedBefore = MutableStateFlow(prefs.getBoolean(KEY_HAS_OPENED_BEFORE, false))
    val hasOpenedBeforeFlow: StateFlow<Boolean> = _hasOpenedBefore.asStateFlow()
    var hasOpenedBefore: Boolean
        get() = prefs.getBoolean(KEY_HAS_OPENED_BEFORE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HAS_OPENED_BEFORE, value).apply()
            _hasOpenedBefore.value = value
        }

    init {
        // Initialize shared StateFlows on first access
        if (_sharedOrderHistory.value.isEmpty()) {
            val initial = loadOrderHistory()
            if (initial.isNotEmpty()) {
                _sharedOrderHistory.value = initial
            }
        }
        val storedTotal = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)
        val historyAccepted = _sharedOrderHistory.value.count { it.status == OrderStatus.ACCEPTED }
        val count = maxOf(storedTotal, historyAccepted)
        if (_sharedTotalAccepted.value < count) {
            _sharedTotalAccepted.value = count
        }

        // Keep _sharedOrderHistory reactively synced with Room database
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.example.data.db.RideHistoryRepository.getInstance(context).allHistory.collect { entities ->
                    val items = entities.map { it.toOrderHistoryItem() }
                    _sharedOrderHistory.value = items
                    val accepted = items.count { it.status == OrderStatus.ACCEPTED }
                    val stored = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)
                    _sharedTotalAccepted.value = maxOf(stored, accepted)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Auto-refresh StateFlow if preferences change from any service or thread
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ORDER_HISTORY) {
                val updated = loadOrderHistory()
                _sharedOrderHistory.value = updated
                val accepted = updated.count { it.status == OrderStatus.ACCEPTED }
                val tot = maxOf(prefs.getInt(KEY_TOTAL_ACCEPTED, 0), accepted)
                _sharedTotalAccepted.value = tot
            } else if (key == KEY_TOTAL_ACCEPTED) {
                _sharedTotalAccepted.value = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)
            }
        }
    }

    private val _orderHistory = _sharedOrderHistory
    val orderHistory: StateFlow<List<OrderHistoryItem>> = _sharedOrderHistory.asStateFlow()

    private val _paymentSubmissions = MutableStateFlow(loadPaymentSubmissions())
    val paymentSubmissions: StateFlow<List<PaymentSubmission>> = _paymentSubmissions.asStateFlow()

    private val _allUsers = MutableStateFlow(loadAllUsers())
    val allUsers: StateFlow<List<UserProfile>> = _allUsers.asStateFlow()


    private val _membershipPlans =
        MutableStateFlow(loadMembershipPlans())

    val membershipPlans:
        StateFlow<List<MembershipPlan>> =
        _membershipPlans.asStateFlow()
    private val _upiId = MutableStateFlow(prefs.getString(KEY_UPI_ID, DEFAULT_UPI_ID) ?: DEFAULT_UPI_ID)
    val upiId: StateFlow<String> = _upiId.asStateFlow()

    private val _qrImageUrl = MutableStateFlow(prefs.getString(KEY_QR_IMAGE_URL, "") ?: "")
    val qrImageUrl: StateFlow<String> = _qrImageUrl.asStateFlow()

    private val _whatsappLink = MutableStateFlow(prefs.getString(KEY_COMMUNITY_WHATSAPP, "https://chat.whatsapp.com/smartdrivo") ?: "https://chat.whatsapp.com/smartdrivo")
    val whatsappLink: StateFlow<String> = _whatsappLink.asStateFlow()

    private val _telegramLink = MutableStateFlow(prefs.getString(KEY_COMMUNITY_TELEGRAM, "https://t.me/smartdrivo") ?: "https://t.me/smartdrivo")
    val telegramLink: StateFlow<String> = _telegramLink.asStateFlow()

    private val _instagramLink = MutableStateFlow(prefs.getString(KEY_COMMUNITY_INSTAGRAM, "https://www.instagram.com/smartdrivo/") ?: "https://www.instagram.com/smartdrivo/")
    val instagramLink: StateFlow<String> = _instagramLink.asStateFlow()

    private val _supportTelegramLink = MutableStateFlow(
        prefs.getString(
            KEY_COMMUNITY_SUPPORT_TELEGRAM,
            "https://t.me/SmartDrivoSupport"
        ) ?: "https://t.me/SmartDrivoSupport"
    )

    val supportTelegramLink: StateFlow<String> =
        _supportTelegramLink.asStateFlow()
    private val _communityLinks = MutableStateFlow(
        com.example.model.CommunityLinks(
            whatsappUrl = prefs.getString(
                KEY_COMMUNITY_WHATSAPP,
                "https://chat.whatsapp.com/smartdrivo"
            ) ?: "https://chat.whatsapp.com/smartdrivo",

            telegramUrl = prefs.getString(
                KEY_COMMUNITY_TELEGRAM,
                "https://t.me/smartdrivo"
            ) ?: "https://t.me/smartdrivo",

            instagramUrl = prefs.getString(
                KEY_COMMUNITY_INSTAGRAM,
                "https://www.instagram.com/smartdrivo/"
            ) ?: "https://www.instagram.com/smartdrivo/",

            supportTelegramUrl = prefs.getString(
                KEY_COMMUNITY_SUPPORT_TELEGRAM,
                "https://t.me/SmartDrivoSupport"
            ) ?: "https://t.me/SmartDrivoSupport"
        )
    )
    val communityLinks: StateFlow<com.example.model.CommunityLinks> = _communityLinks.asStateFlow()

    private val _lastAcceptedRide = MutableStateFlow<OrderHistoryItem?>(loadLastAccepted())
    val lastAcceptedRide: StateFlow<OrderHistoryItem?> = _lastAcceptedRide.asStateFlow()

    private val _totalAccepted = _sharedTotalAccepted
    val totalAcceptedFlow: StateFlow<Int> = _sharedTotalAccepted.asStateFlow()

    val totalAccepted: Int
        get() = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)

    val lastAcceptedPlatform: String
        get() = prefs.getString(KEY_LAST_ACCEPTED_PLATFORM, "Rapido") ?: "Rapido"

    val lastAcceptedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_ACCEPTED_TIMESTAMP, 0L)

    val lastAcceptedFare: Float
        get() = prefs.getFloat(KEY_LAST_ACCEPTED_FARE, 0f)

    val isAutoAcceptEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ACCEPT, true)

    val isFastestModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FASTEST_MODE, false)

    /**
     * Freshly reloads AppSettings from SharedPreferences without using any cached values.
     * Updates internal _appSettings StateFlow and returns the latest settings.
     */
    fun loadSettings(): AppSettings {
        return loadAppSettings()
    }

    fun reloadAppSettings(): AppSettings {
        return loadAppSettings()
    }

    fun incrementTotalAccepted(): Int {
        val newCount = prefs.getInt(KEY_TOTAL_ACCEPTED, 0) + 1
        prefs.edit().putInt(KEY_TOTAL_ACCEPTED, newCount).apply()
        _totalAccepted.value = newCount
        return newCount
    }

    fun saveAcceptedOrder(
        platform: String = "Rapido",
        timestamp: Long = System.currentTimeMillis(),
        fareAmount: Float = 0f
    ) {
        val currentTotal = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)
        val newTotal = currentTotal + 1
        prefs.edit()
            .putInt(KEY_TOTAL_ACCEPTED, newTotal)
            .putString(KEY_LAST_ACCEPTED_PLATFORM, platform)
            .putLong(KEY_LAST_ACCEPTED_TIMESTAMP, timestamp)
            .putFloat(KEY_LAST_ACCEPTED_FARE, fareAmount)
            .apply()
        _totalAccepted.value = newTotal
    }

    fun recordAcceptedOrder(
        platform: String = "Rapido",
        timestamp: Long = System.currentTimeMillis(),
        fareAmount: Float = 0f
    ) {
        saveAcceptedOrder(platform, timestamp, fareAmount)
    }

    fun loadStats(): List<OrderHistoryItem> {
        val latest = loadOrderHistory()
        _sharedOrderHistory.value = latest
        val storedTotal = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)
        val historyAccepted = latest.count { it.status == OrderStatus.ACCEPTED }
        val count = maxOf(storedTotal, historyAccepted)
        if (count > storedTotal) {
            prefs.edit().putInt(KEY_TOTAL_ACCEPTED, count).apply()
        }
        _totalAccepted.value = count
        _sharedTotalAccepted.value = count
        _lastAcceptedRide.value = loadLastAccepted()
        return latest
    }

    fun notifyOrderHistoryChanged() {
        val latest = loadOrderHistory()
        _sharedOrderHistory.value = latest
        val storedTotal = prefs.getInt(KEY_TOTAL_ACCEPTED, 0)
        val historyAccepted = latest.count { it.status == OrderStatus.ACCEPTED }
        val count = maxOf(storedTotal, historyAccepted)
        _sharedTotalAccepted.value = count
        _totalAccepted.value = count
    }

    var isPendingAutoAcceptActivation: Boolean
        get() = prefs.getBoolean(KEY_PENDING_AUTO_ACCEPT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PENDING_AUTO_ACCEPT, value).apply()
        }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("setting_") || key == KEY_GOTO_ENABLED || key == KEY_NOGO_ENABLED)) {
            loadAppSettings()
        }
    }

    init {
        try {
            prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (_: Exception) {}
        try {
            // Start with empty lists by default - remove legacy default groups if present
            val currentGoTo = _goToAreas.value.filterNot { it.name.equals("Airport & Tech Park", ignoreCase = true) || it.id == "area_goto_1" }
            val currentNoGo = _noGoAreas.value.filterNot { it.name.equals("Toll Gate North / Restricted Zone", ignoreCase = true) || it.id == "area_nogo_1" }
            if (currentGoTo.size != _goToAreas.value.size || currentNoGo.size != _noGoAreas.value.size) {
                saveAreaGroups(currentGoTo, currentNoGo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- User Profile ---
    fun saveUserProfile(profile: UserProfile) {
        prefs.edit().apply {
            putString(KEY_UID, profile.uid)
            putString(KEY_NAME, profile.name)
            putString(KEY_EMAIL, profile.email)
            putString(KEY_PHONE, profile.phone)
            putString(KEY_CITY, profile.city)
            putString(KEY_STATE, profile.state)
            putString(KEY_VEHICLE, profile.vehicleType.name)
            putString(KEY_PLAN, profile.plan)
            putInt(KEY_PLAN_PRICE, profile.planPrice)
            putLong(KEY_PLAN_EXPIRE, profile.planExpireMillis)
            putBoolean(KEY_IS_APPROVED, profile.isApproved)
            putBoolean(KEY_IS_ADMIN, profile.isAdmin)
            putBoolean(KEY_IS_ACTIVE, profile.isActive)
            putString(KEY_REFERRAL, profile.referralCode)
            apply()
        }
        _userProfile.value = profile
        updateUserInList(profile)
    }

    private fun loadUserProfile(): UserProfile {
        val uid = prefs.getString(KEY_UID, "") ?: ""
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val city = prefs.getString(KEY_CITY, "") ?: ""
        val state = prefs.getString(KEY_STATE, "") ?: ""
        val vehicleStr = prefs.getString(KEY_VEHICLE, "AUTO")
        val plan = prefs.getString(KEY_PLAN, "NONE") ?: "NONE"
        val price = prefs.getInt(KEY_PLAN_PRICE, 0)
        val expire = prefs.getLong(KEY_PLAN_EXPIRE, 0L)
        val isApproved = prefs.getBoolean(KEY_IS_APPROVED, false)
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, true)
        val referral = prefs.getString(KEY_REFERRAL, "SMART50") ?: "SMART50"

        return UserProfile(
            uid = if (uid.isEmpty()) UUID.randomUUID().toString() else uid,
            name = name,
            email = email,
            phone = phone,
            city = city,
            state = state,
            vehicleType = VehicleType.fromString(vehicleStr),
            plan = plan,
            planPrice = price,
            planExpireMillis = expire,
            isApproved = isApproved,
            isAdmin = isAdmin,
            isActive = isActive,
            referralCode = referral
        )
    }

    // --- App Settings ---
    fun setAutoAcceptActive(active: Boolean) {
        val current = _appSettings.value
        saveAppSettings(current.copy(isAutoAcceptActive = active))
    }

    fun saveAppSettings(settings: AppSettings) {
        prefs.edit().apply {
            putBoolean(KEY_AUTO_ACCEPT, settings.isAutoAcceptActive)
            putBoolean(KEY_AUTO_REJECT_BAD_FARES, settings.isAutoRejectBadFaresEnabled)
            putBoolean(KEY_FASTEST_MODE, settings.isFastestModeEnabled)
            putBoolean(
                "setting_order_reason_popups",
                settings.showOrderReasonPopups
            )

            putBoolean(
                KEY_SECONDARY_BOTH_ENABLED,
                settings.isSecondaryBothFilterEnabled
            )
            putFloat(
                KEY_SECONDARY_BOTH_MIN_FARE,
                settings.secondaryBothMinFare
            )
            putFloat(
                KEY_SECONDARY_BOTH_MAX_PICKUP,
                settings.secondaryBothMaxPickupDistanceKm
            )
            putFloat(
                KEY_SECONDARY_BOTH_MAX_DROP,
                settings.secondaryBothMaxDropDistanceKm
            )

            putBoolean(
                KEY_TERTIARY_BOTH_ENABLED,
                settings.isTertiaryBothFilterEnabled
            )
            putFloat(
                KEY_TERTIARY_BOTH_MIN_FARE,
                settings.tertiaryBothMinFare
            )
            putFloat(
                KEY_TERTIARY_BOTH_MAX_PICKUP,
                settings.tertiaryBothMaxPickupDistanceKm
            )
            putFloat(
                KEY_TERTIARY_BOTH_MAX_DROP,
                settings.tertiaryBothMaxDropDistanceKm
            )

            putBoolean(KEY_PLUS_AMOUNT_ENABLED, settings.isPlusAmountFilterEnabled)
            putFloat(KEY_PLUS_AMOUNT_MIN, settings.plusAmountMin)
            putFloat(KEY_PLUS_AMOUNT_MAX_PICKUP, settings.plusAmountMaxPickupDistanceKm)
            putFloat(KEY_PLUS_AMOUNT_MAX_DROP, settings.plusAmountMaxDropDistanceKm)

            putBoolean(KEY_RAPIDO_ENABLED, settings.rapidoEnabled)
            putBoolean(KEY_UBER_ENABLED, settings.uberEnabled)
            putBoolean(KEY_OLA_ENABLED, settings.olaEnabled)
            putBoolean(KEY_AUTO_TYPE_ENABLED, settings.autoEnabled)
            putBoolean(KEY_BIKE_TYPE_ENABLED, settings.bikeEnabled)
            putBoolean(KEY_CAR_TYPE_ENABLED, settings.carEnabled)
            putString(KEY_CLICK_SPEED, settings.clickSpeed.name)
            putString(KEY_CLICK_STRATEGY, settings.clickStrategy.name)
            putString(KEY_FILTER_MODE, settings.filterMode.name)
            putFloat(KEY_MAX_PICKUP_DIST, settings.maxPickupDistanceKm)
            putFloat(KEY_MAX_DROP_DIST, settings.maxDropDistanceKm)
            putFloat(KEY_MAX_DROP_KM, settings.maxDropDistanceKm)
            putFloat(KEY_MIN_FARE, settings.minFare)
            putFloat(KEY_MAX_FARE, settings.maxFare)
            putString(KEY_OUT_OF_RANGE, settings.outOfRangeAction.name)
            putString(KEY_BUNDLE_ACTION, settings.bundleOrderAction.name)
            putBoolean(KEY_BUNDLE_ENABLED, settings.isBundleOrderEnabled)
            putString(KEY_THEME_MODE, settings.themeMode.name)
            putBoolean(KEY_AUTOSTART, settings.autostartOnBoot)
            putBoolean(KEY_GOTO_ENABLED, settings.isGoToEnabled)
            putBoolean(KEY_NOGO_ENABLED, settings.isNoGoEnabled)
            commit()
        }
        _isGoToEnabled.value = settings.isGoToEnabled
        _isNoGoEnabled.value = settings.isNoGoEnabled
        _appSettings.value = settings
    }

    private fun getSafeFloat(key: String, defaultValue: Float): Float {
        return try {
            prefs.getFloat(key, defaultValue)
        } catch (_: ClassCastException) {
            try {
                prefs.getString(key, null)?.toFloatOrNull() ?: defaultValue
            } catch (_: Exception) {
                try {
                    prefs.getInt(key, defaultValue.toInt()).toFloat()
                } catch (_: Exception) {
                    defaultValue
                }
            }
        }
    }

    fun getFreshMaxDropDistanceKm(): Float {
        return getSafeFloat(KEY_MAX_DROP_DIST, getSafeFloat(KEY_MAX_DROP_KM, 7.5f))
    }

    fun getFreshSettings(): AppSettings {
        val fresh = loadAppSettingsRaw()
        _appSettings.value = fresh
        return fresh
    }

    private fun loadAppSettingsRaw(): AppSettings {
        val maxPickup = getSafeFloat(KEY_MAX_PICKUP_DIST, 3.0f)
        val maxDrop = getSafeFloat(KEY_MAX_DROP_DIST, getSafeFloat(KEY_MAX_DROP_KM, 7.5f))
        val minFare = getSafeFloat(KEY_MIN_FARE, 50f)
        val maxFare = getSafeFloat(KEY_MAX_FARE, 999f)

        return AppSettings(
            isAutoAcceptActive = prefs.getBoolean(KEY_AUTO_ACCEPT, true),
            isAutoRejectBadFaresEnabled = prefs.getBoolean(KEY_AUTO_REJECT_BAD_FARES, false),
            isFastestModeEnabled = prefs.getBoolean(KEY_FASTEST_MODE, false),
            showOrderReasonPopups =
                prefs.getBoolean(
                    "setting_order_reason_popups",
                    true
                ),

            isSecondaryBothFilterEnabled =
                prefs.getBoolean(
                    KEY_SECONDARY_BOTH_ENABLED,
                    false
                ),

            secondaryBothMinFare =
                getSafeFloat(
                    KEY_SECONDARY_BOTH_MIN_FARE,
                    50f
                ),

            secondaryBothMaxPickupDistanceKm =
                getSafeFloat(
                    KEY_SECONDARY_BOTH_MAX_PICKUP,
                    3.0f
                ),

            secondaryBothMaxDropDistanceKm =
                getSafeFloat(
                    KEY_SECONDARY_BOTH_MAX_DROP,
                    7.5f
                ),

            isTertiaryBothFilterEnabled =
                prefs.getBoolean(
                    KEY_TERTIARY_BOTH_ENABLED,
                    false
                ),

            tertiaryBothMinFare =
                getSafeFloat(
                    KEY_TERTIARY_BOTH_MIN_FARE,
                    50f
                ),

            tertiaryBothMaxPickupDistanceKm =
                getSafeFloat(
                    KEY_TERTIARY_BOTH_MAX_PICKUP,
                    3.0f
                ),

            tertiaryBothMaxDropDistanceKm =
                getSafeFloat(
                    KEY_TERTIARY_BOTH_MAX_DROP,
                    7.5f
                ),

            isPlusAmountFilterEnabled = prefs.getBoolean(KEY_PLUS_AMOUNT_ENABLED, false),
            plusAmountMin = getSafeFloat(KEY_PLUS_AMOUNT_MIN, 10f),
            plusAmountMaxPickupDistanceKm = getSafeFloat(KEY_PLUS_AMOUNT_MAX_PICKUP, 3.0f),
            plusAmountMaxDropDistanceKm = getSafeFloat(KEY_PLUS_AMOUNT_MAX_DROP, 7.5f),

            rapidoEnabled = prefs.getBoolean(KEY_RAPIDO_ENABLED, true),
            uberEnabled = prefs.getBoolean(KEY_UBER_ENABLED, true),
            olaEnabled = prefs.getBoolean(KEY_OLA_ENABLED, true),
            autoEnabled = prefs.getBoolean(KEY_AUTO_TYPE_ENABLED, true),
            bikeEnabled = prefs.getBoolean(KEY_BIKE_TYPE_ENABLED, true),
            carEnabled = prefs.getBoolean(KEY_CAR_TYPE_ENABLED, true),
            clickSpeed = try {
                ClickSpeed.valueOf(prefs.getString(KEY_CLICK_SPEED, "SPEED_10MS") ?: "SPEED_10MS")
            } catch (e: Exception) { ClickSpeed.SPEED_10MS },
            clickStrategy = try {
                ClickStrategy.valueOf(prefs.getString(KEY_CLICK_STRATEGY, "AUTO") ?: "AUTO")
            } catch (e: Exception) { ClickStrategy.AUTO },
            filterMode = try {
                FilterMode.valueOf(prefs.getString(KEY_FILTER_MODE, "BOTH") ?: "BOTH")
            } catch (e: Exception) { FilterMode.BOTH },
            maxPickupDistanceKm = maxPickup,
            maxDropDistanceKm = maxDrop,
            maxDropKm = maxDrop,
            minFare = minFare,
            maxFare = maxFare,
            outOfRangeAction = try {
                OutOfRangeAction.valueOf(prefs.getString(KEY_OUT_OF_RANGE, "AUTO_REJECT") ?: "AUTO_REJECT")
            } catch (e: Exception) { OutOfRangeAction.AUTO_REJECT },
            bundleOrderAction = try {
                BundleOrderAction.valueOf(prefs.getString(KEY_BUNDLE_ACTION, "AUTO_REJECT") ?: "AUTO_REJECT")
            } catch (e: Exception) { BundleOrderAction.AUTO_REJECT },
            isBundleOrderEnabled = prefs.getBoolean(KEY_BUNDLE_ENABLED, false),
            themeMode = try {
                AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, "LIGHT") ?: "LIGHT")
            } catch (e: Exception) { AppThemeMode.LIGHT },
            autostartOnBoot = prefs.getBoolean(KEY_AUTOSTART, true),
            isGoToEnabled = prefs.getBoolean(KEY_GOTO_ENABLED, false),
            isNoGoEnabled = prefs.getBoolean(KEY_NOGO_ENABLED, true)
        )
    }

    fun loadAppSettings(): AppSettings {
        val loaded = loadAppSettingsRaw()
        try {
            _appSettings.value = loaded
        } catch (_: Exception) {}
        return loaded
    }

    // --- Area Groups (Go-To & No-Go) ---
    fun saveAreaGroups(goToGroups: List<AreaGroup>, noGoGroups: List<AreaGroup>) {
        // Save Go-To groups
        val goToArr = JSONArray()
        for (g in goToGroups) {
            goToArr.put(areaGroupToJson(g))
        }
        prefs.edit().putString(KEY_GOTO_AREA_GROUPS, goToArr.toString()).apply()
        _goToAreas.value = goToGroups

        // Save No-Go groups
        val noGoArr = JSONArray()
        for (g in noGoGroups) {
            noGoArr.put(areaGroupToJson(g))
        }
        prefs.edit().putString(KEY_NOGO_AREA_GROUPS, noGoArr.toString()).apply()
        _noGoAreas.value = noGoGroups
    }

      private fun areaGroupToJson(g: AreaGroup): JSONObject {
        val obj = JSONObject()

        obj.put("id", g.id)
        obj.put("name", g.name)
        obj.put("isEnabled", g.isEnabled)
        obj.put("type", g.type.name)
        obj.put("keywords", JSONArray(g.keywords))
        obj.put("filtersEnabled", g.filtersEnabled)
        obj.put("minFare", g.minFare.toDouble())
        obj.put("maxFare", g.maxFare.toDouble())
        obj.put("minPickupKm", g.minPickupKm.toDouble())
        obj.put("maxPickupKm", g.maxPickupKm.toDouble())
        obj.put("maxDropKm", g.maxDropKm.toDouble())
    val areasArr = JSONArray()
    for (a in g.areas) {
        areasArr.put(
            JSONObject().apply {
                put("name", a.name)
                put("minFare", a.minFare.toDouble())
                put("maxPickupKm", a.maxPickupKm.toDouble())
                put("maxDropKm", a.maxDropKm.toDouble())
            }
        )
    }
    obj.put("areas", areasArr)
        return obj
    }

    private fun jsonToAreaGroup(obj: JSONObject): AreaGroup {
        val areaEntries = mutableListOf<AreaEntry>()
        val areasArr = obj.optJSONArray("areas")
        if (areasArr != null) {
            for (i in 0 until areasArr.length()) {
                val a = areasArr.optJSONObject(i) ?: continue
                areaEntries.add(
                    AreaEntry(
                        name = a.optString("name", ""),
                        minFare = a.optDouble("minFare", 0.0).toFloat(),
                        maxPickupKm = a.optDouble("maxPickupKm", 0.0).toFloat(),
                        maxDropKm = a.optDouble("maxDropKm", 0.0).toFloat()
                    )
                )
            }
        }
        val kwList = mutableListOf<String>()
        val kwArr = obj.optJSONArray("keywords")
        if (kwArr != null) {
            for (k in 0 until kwArr.length()) {
                kwList.add(kwArr.getString(k))
            }
        }
        return AreaGroup(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Area"),
            isEnabled = obj.optBoolean("isEnabled", true),
            type = try { AreaType.valueOf(obj.optString("type", "GO_TO")) } catch (e: Exception) { AreaType.GO_TO },
            keywords = kwList,
            filtersEnabled = obj.optBoolean("filtersEnabled", false),
            minFare = obj.optDouble("minFare", 50.0).toFloat(),
            maxFare = obj.optDouble("maxFare", 0.0).toFloat(),
            minPickupKm = obj.optDouble("minPickupKm", 0.5).toFloat(),
            maxPickupKm = obj.optDouble("maxPickupKm", 3.0).toFloat(),
            maxDropKm = obj.optDouble("maxDropKm", 7.5).toFloat()
        )
    }

    private fun loadGoToAreaGroups(): List<AreaGroup> {
        val json = prefs.getString(KEY_GOTO_AREA_GROUPS, null) ?: return emptyList()
        val list = mutableListOf<AreaGroup>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val group = jsonToAreaGroup(arr.getJSONObject(i))
                if (!group.name.equals("Airport & Tech Park", ignoreCase = true) && group.id != "area_goto_1") {
                    list.add(group)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun loadNoGoAreaGroups(): List<AreaGroup> {
        val json = prefs.getString(KEY_NOGO_AREA_GROUPS, null) ?: return emptyList()
        val list = mutableListOf<AreaGroup>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val group = jsonToAreaGroup(arr.getJSONObject(i))
                if (!group.name.equals("Toll Gate North / Restricted Zone", ignoreCase = true) && group.id != "area_nogo_1") {
                    list.add(group)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- Legacy Area Groups (kept for backward compatibility) ---
    fun saveAreaGroups(list: List<AreaGroup>) {
        val arr = JSONArray()
        for (g in list) {
            arr.put(areaGroupToJson(g))
        }
        prefs.edit().putString(KEY_AREA_GROUPS, arr.toString()).apply()
    }

    private fun loadAreaGroups(): List<AreaGroup> {
        val json = prefs.getString(KEY_AREA_GROUPS, null) ?: return emptyList()
        val list = mutableListOf<AreaGroup>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(jsonToAreaGroup(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- Go-To & No-Go Area Lists (stored as AreaGroup objects) ---

    fun loadGoToAreas(): List<AreaGroup> = loadGoToAreaGroups()
    fun loadNoGoAreas(): List<AreaGroup> = loadNoGoAreaGroups()

    fun saveGoToAreas(list: List<AreaGroup>) {
        saveAreaGroups(list, loadNoGoAreaGroups())
    }

    @JvmName("saveGoToAreasStrings")
    fun saveGoToAreas(list: List<String>) {
        val groups = list.map { AreaGroup(id = UUID.randomUUID().toString(), name = it.trim(), type = AreaType.GO_TO, keywords = listOf(it.trim())) }
        _goToAreas.value = groups
    }

    fun saveNoGoAreas(list: List<AreaGroup>) {
        saveAreaGroups(loadGoToAreaGroups(), list)
    }

    @JvmName("saveNoGoAreasStrings")
    fun saveNoGoAreas(list: List<String>) {
        val groups = list.map { AreaGroup(id = UUID.randomUUID().toString(), name = it.trim(), type = AreaType.NO_GO, keywords = listOf(it.trim())) }
        _noGoAreas.value = groups
    }

    fun saveAreaLists(
        goTo: List<String>,
        noGo: List<String>,
        goToEnabled: Boolean = this.isGoToEnabled,
        noGoEnabled: Boolean = this.isNoGoEnabled
    ) {
        saveGoToAreas(goTo)
        saveNoGoAreas(noGo)
        this.isGoToEnabled = goToEnabled
        this.isNoGoEnabled = noGoEnabled
        val currentSettings = _appSettings.value
        _appSettings.value = currentSettings.copy(
            isGoToEnabled = goToEnabled,
            isNoGoEnabled = noGoEnabled
        )
    }

    private fun saveJsonStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.trim()) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadJsonStringList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) {
                    result.add(s.trim())
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Order History ---
    fun addOrderHistory(item: OrderHistoryItem) {
        val current = loadOrderHistory().toMutableList()
        val existingIndex = current.indexOfFirst {
            it.id == item.id || (item.bookingId.isNotBlank() && it.bookingId == item.bookingId)
        }
        if (existingIndex >= 0) {
            current[existingIndex] = item
        } else {
            current.add(0, item) // reverse-chronological
        }
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        saveOrderHistory(current)

        // Also persist to Room
        val entity = com.example.data.db.entity.RideHistoryEntity.fromOrderHistoryItem(item)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.example.data.db.AppDatabase.getInstance(context).rideHistoryDao().insert(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (item.status == OrderStatus.ACCEPTED) {
            _lastAcceptedRide.value = item
            saveLastAccepted(item)
        }
    }

    fun clearOrderHistory() {
        saveOrderHistory(emptyList())
        prefs.edit()
            .remove(KEY_LAST_ACCEPTED)
            .remove(KEY_TOTAL_ACCEPTED)
            .remove(KEY_LAST_ACCEPTED_PLATFORM)
            .remove(KEY_LAST_ACCEPTED_TIMESTAMP)
            .remove(KEY_LAST_ACCEPTED_FARE)
            .apply()
        _lastAcceptedRide.value = null
        _totalAccepted.value = 0
        _sharedTotalAccepted.value = 0
        _sharedOrderHistory.value = emptyList()

        // Clear Room database
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.example.data.db.RideHistoryRepository.getInstance(context).clearHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveOrderHistory(list: List<OrderHistoryItem>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("timestamp", item.timestamp)
            obj.put("dateStr", item.dateStr)
            obj.put("timeStr", item.timeStr)
            obj.put("status", item.status.name)
            obj.put("platform", item.platform.name)
            obj.put("vehicleType", item.vehicleType.name)
            obj.put("pickupDistKm", item.pickupDistKm.toDouble())
            obj.put("dropDistKm", item.dropDistKm.toDouble())
            obj.put("pickupAddress", item.pickupAddress)
            obj.put("dropAddress", item.dropAddress)
            obj.put("dropArea", item.dropArea)
            obj.put("amount", item.amount.toDouble())
            obj.put("bookingId", item.bookingId)
            obj.put("detectionTimeMs", item.detectionTimeMs)
            obj.put("clickTimeMs", item.clickTimeMs)
            obj.put("baseFare", item.baseFare.toDouble())
            obj.put("tipAmount", item.tipAmount.toDouble())
            obj.put("timesClicked", item.timesClicked)
            obj.put("reason", item.reason)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ORDER_HISTORY, arr.toString()).apply()
        // Immediate StateFlow UI update trigger
        _sharedOrderHistory.value = list
    }

    private fun loadOrderHistory(): List<OrderHistoryItem> {
        val json = prefs.getString(KEY_ORDER_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<OrderHistoryItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    OrderHistoryItem(
                        id = obj.optString("id", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        dateStr = obj.optString("dateStr", ""),
                        timeStr = obj.optString("timeStr", ""),
                        status = try { OrderStatus.valueOf(obj.optString("status", "ACCEPTED")) } catch (e: Exception) { OrderStatus.ACCEPTED },
                        platform = try {
                            val p = obj.optString("platform", "RAPIDO")
                            Platform.entries.firstOrNull { it.name.equals(p, ignoreCase = true) || it.displayName.equals(p, ignoreCase = true) } ?: Platform.RAPIDO
                        } catch (e: Exception) { Platform.RAPIDO },
                        vehicleType = try { VehicleType.valueOf(obj.optString("vehicleType", "AUTO")) } catch (e: Exception) { VehicleType.AUTO },
                        pickupDistKm = obj.optDouble("pickupDistKm", 0.0).toFloat(),
                        dropDistKm = obj.optDouble("dropDistKm", 0.0).toFloat(),
                        pickupAddress = obj.optString("pickupAddress", ""),
                        dropAddress = obj.optString("dropAddress", ""),
                        dropArea = obj.optString("dropArea", ""),
                        amount = obj.optDouble("amount", 0.0).toFloat(),
                        bookingId = obj.optString("bookingId", ""),
                        detectionTimeMs = obj.optLong("detectionTimeMs", 0L),
                        clickTimeMs = obj.optLong("clickTimeMs", 0L),
                        baseFare = obj.optDouble("baseFare", 0.0).toFloat(),
                        tipAmount = obj.optDouble("tipAmount", 0.0).toFloat(),
                        timesClicked = obj.optInt("timesClicked", 1),
                        reason = obj.optString("reason", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveLastAccepted(item: OrderHistoryItem) {
        val obj = JSONObject().apply {
            put("id", item.id)
            put("timestamp", item.timestamp)
            put("dateStr", item.dateStr)
            put("timeStr", item.timeStr)
            put("platform", item.platform.name)
            put("amount", item.amount.toDouble())
            put("dropArea", item.dropArea)
            put("pickupAddress", item.pickupAddress)
            put("dropAddress", item.dropAddress)
            put("pickupDistKm", item.pickupDistKm.toDouble())
            put("dropDistKm", item.dropDistKm.toDouble())
            put("detectionTimeMs", item.detectionTimeMs)
            put("clickTimeMs", item.clickTimeMs)
            put("baseFare", item.baseFare.toDouble())
            put("tipAmount", item.tipAmount.toDouble())
            put("timesClicked", item.timesClicked)
        }
        prefs.edit().putString(KEY_LAST_ACCEPTED, obj.toString()).apply()
    }

    private fun loadLastAccepted(): OrderHistoryItem? {
        val json = prefs.getString(KEY_LAST_ACCEPTED, null) ?: return null
        return try {
            val obj = JSONObject(json)
            OrderHistoryItem(
                id = obj.optString("id", ""),
                timestamp = obj.optLong("timestamp", 0L),
                dateStr = obj.optString("dateStr", ""),
                timeStr = obj.optString("timeStr", ""),
                platform = try {
                    val p = obj.optString("platform", "RAPIDO")
                    Platform.entries.firstOrNull { it.name.equals(p, ignoreCase = true) || it.displayName.equals(p, ignoreCase = true) } ?: Platform.RAPIDO
                } catch (e: Exception) { Platform.RAPIDO },
                amount = obj.optDouble("amount", 0.0).toFloat(),
                dropArea = obj.optString("dropArea", ""),
                pickupAddress = obj.optString("pickupAddress", ""),
                dropAddress = obj.optString("dropAddress", ""),
                pickupDistKm = obj.optDouble("pickupDistKm", 0.0).toFloat(),
                dropDistKm = obj.optDouble("dropDistKm", 0.0).toFloat(),
                detectionTimeMs = obj.optLong("detectionTimeMs", 0L),
                clickTimeMs = obj.optLong("clickTimeMs", 0L),
                baseFare = obj.optDouble("baseFare", 0.0).toFloat(),
                tipAmount = obj.optDouble("tipAmount", 0.0).toFloat(),
                timesClicked = obj.optInt("timesClicked", 1)
            )
        } catch (e: Exception) { null }
    }

    // --- Payment Submissions & Duplicate UTR Validation ---
    fun isDuplicateUtr(utrNumber: String, excludePaymentId: String? = null): Boolean {
        val clean = utrNumber.trim()
        if (clean.isEmpty()) return false
        return _paymentSubmissions.value.any {
            it.utrNumber.trim().equals(clean, ignoreCase = true) &&
            (excludePaymentId == null || it.paymentId != excludePaymentId)
        }
    }

    /**
     * Attempts to add a payment submission.
     * @return true if accepted, false if blocked due to duplicate UTR
     */
    fun addPaymentSubmission(sub: PaymentSubmission): Boolean {
        val cleanUtr = sub.utrNumber.trim()
        if (isDuplicateUtr(cleanUtr)) {
            return false // Block duplicate UTR
        }
        val list = _paymentSubmissions.value.toMutableList()
        list.add(0, sub)
        savePaymentSubmissions(list)
        return true
    }

    fun updatePaymentStatus(paymentId: String, status: PaymentStatus) {
        val list = _paymentSubmissions.value.map {
            if (it.paymentId == paymentId) {
                it.copy(status = status, approvedAt = if (status == PaymentStatus.APPROVED) System.currentTimeMillis() else null)
            } else it
        }
        savePaymentSubmissions(list)
    }

    fun updatePaymentUtr(paymentId: String, newUtr: String) {
        val list = _paymentSubmissions.value.map {
            if (it.paymentId == paymentId) {
                it.copy(utrNumber = newUtr.trim())
            } else it
        }
        savePaymentSubmissions(list)
    }

    fun deletePaymentSubmission(paymentId: String) {
        val list = _paymentSubmissions.value.filterNot { it.paymentId == paymentId }
        savePaymentSubmissions(list)
    }

    private fun persistPaymentSubmissionsToPrefs(list: List<PaymentSubmission>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("paymentId", item.paymentId)
                put("uid", item.uid)
                put("userName", item.userName)
                put("utrNumber", item.utrNumber)
                put("planSelected", item.planSelected)
                put("amount", item.amount)
                put("status", item.status.name)
                put("submittedAt", item.submittedAt)
                put("approvedAt", item.approvedAt ?: -1L)
                put("note", item.note)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PAYMENTS, arr.toString()).apply()
    }

    private fun savePaymentSubmissions(list: List<PaymentSubmission>) {
        persistPaymentSubmissionsToPrefs(list)
        _paymentSubmissions.value = list
    }

    fun setPaymentSubmissions(list: List<PaymentSubmission>) {
        savePaymentSubmissions(list)
    }

    fun setAllUsers(list: List<UserProfile>) {
        saveAllUsers(list)
    }

    private fun loadPaymentSubmissions(): List<PaymentSubmission> {
        val json = prefs.getString(KEY_PAYMENTS, null)
        if (!json.isNullOrEmpty()) {
            val list = mutableListOf<PaymentSubmission>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val approvedAtRaw = obj.optLong("approvedAt", -1L)
                    list.add(
                        PaymentSubmission(
                            paymentId = obj.optString("paymentId", ""),
                            uid = obj.optString("uid", ""),
                            userName = obj.optString("userName", ""),
                            utrNumber = obj.optString("utrNumber", ""),
                            planSelected = obj.optString("planSelected", "7DAYS"),
                            amount = obj.optInt("amount", 129),
                            status = try { PaymentStatus.valueOf(obj.optString("status", "PENDING")) } catch (e: Exception) { PaymentStatus.PENDING },
                            submittedAt = obj.optLong("submittedAt", System.currentTimeMillis()),
                            approvedAt = if (approvedAtRaw > 0) approvedAtRaw else null,
                            note = obj.optString("note", "")
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) { e.printStackTrace() }
        }

        return emptyList()
    }

    // --- User & Driver Partners Management ---
    fun updateUserInList(user: UserProfile) {
        val current = _allUsers.value.toMutableList()
        val index = current.indexOfFirst {
            it.uid == user.uid ||
            (it.phone.isNotEmpty() && user.phone.isNotEmpty() && it.phone == user.phone) ||
            (it.email.isNotEmpty() && user.email.isNotEmpty() && it.email == user.email)
        }
        if (index >= 0) {
            current[index] = user
        } else {
            current.add(0, user)
        }
        saveAllUsers(current)
    }

    fun toggleUserActiveStatus(uid: String) {
        val current = _allUsers.value.map {
            if (it.uid == uid) {
                it.copy(isActive = !it.isActive)
            } else it
        }
        saveAllUsers(current)
        if (_userProfile.value.uid == uid) {
            val u = current.firstOrNull { it.uid == uid }
            if (u != null) saveUserProfile(u)
        }
    }

    fun extendUserPlan(uid: String, days: Int, planLabel: String, price: Int = 0) {
        val current = _allUsers.value.map {
            if (it.uid == uid) {
                val baseTime = if (it.planExpireMillis > System.currentTimeMillis()) it.planExpireMillis else System.currentTimeMillis()
                val newExpiry = baseTime + (days * 86400000L)
                it.copy(
                    plan = planLabel,
                    planPrice = if (price > 0) price else it.planPrice,
                    planExpireMillis = newExpiry,
                    isApproved = true,
                    isActive = true
                )
            } else it
        }
        saveAllUsers(current)
        if (_userProfile.value.uid == uid) {
            val u = current.firstOrNull { it.uid == uid }
            if (u != null) saveUserProfile(u)
        }
    }

    fun activateDriverAccount(uid: String, days: Int, planLabel: String) {
        extendUserPlan(uid, days, planLabel)
    }

    private fun persistAllUsersToPrefs(list: List<UserProfile>) {
        val arr = JSONArray()
        for (u in list) {
            val obj = JSONObject().apply {
                put("uid", u.uid)
                put("name", u.name)
                put("email", u.email)
                put("phone", u.phone)
                put("city", u.city)
                put("state", u.state)
                put("vehicleType", u.vehicleType.name)
                put("plan", u.plan)
                put("planPrice", u.planPrice)
                put("planExpireMillis", u.planExpireMillis)
                put("isApproved", u.isApproved)
                put("isAdmin", u.isAdmin)
                put("isActive", u.isActive)
                put("referralCode", u.referralCode)
                put("createdAt", u.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ALL_USERS, arr.toString()).apply()
    }

    private fun saveAllUsers(list: List<UserProfile>) {
        persistAllUsersToPrefs(list)
        _allUsers.value = list
    }

    private fun loadAllUsers(): List<UserProfile> {
        val json = prefs.getString(KEY_ALL_USERS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<UserProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        UserProfile(
                            uid = obj.optString("uid", ""),
                            name = obj.optString("name", ""),
                            email = obj.optString("email", ""),
                            phone = obj.optString("phone", ""),
                            city = obj.optString("city", ""),
                            state = obj.optString("state", ""),
                            vehicleType = VehicleType.fromString(obj.optString("vehicleType", "AUTO")),
                            plan = obj.optString("plan", "7DAYS"),
                            planPrice = obj.optInt("planPrice", 129),
                            planExpireMillis = obj.optLong("planExpireMillis", System.currentTimeMillis()),
                            isApproved = obj.optBoolean("isApproved", false),
                            isAdmin = obj.optBoolean("isAdmin", false),
                            isActive = obj.optBoolean("isActive", true),
                            referralCode = obj.optString("referralCode", "SMART50"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) { e.printStackTrace() }
        }

        return emptyList()
    }


    private fun buildMembershipPlans(
        price3: Int,
        price7: Int,
        price15: Int,
        price30: Int
    ): List<MembershipPlan> {
        return listOf(
            MembershipPlan(
                "3DAYS",
                3,
                price3,
                "3 Days Pass",
                "Quick trial pass for new drivers"
            ),
            MembershipPlan(
                "7DAYS",
                7,
                price7,
                "7 Days Pass",
                "Most popular weekly pass"
            ),
            MembershipPlan(
                "15DAYS",
                15,
                price15,
                "15 Days Pass",
                "Best bi-weekly discount pack"
            ),
            MembershipPlan(
                "1MONTH",
                30,
                price30,
                "1 Month Pass",
                "Full monthly unlimited pass"
            )
        )
    }

    private fun loadMembershipPlans():
        List<MembershipPlan> {

        return buildMembershipPlans(
            prefs.getInt(
                KEY_GLOBAL_PLAN_3,
                59
            ),
            prefs.getInt(
                KEY_GLOBAL_PLAN_7,
                129
            ),
            prefs.getInt(
                KEY_GLOBAL_PLAN_15,
                199
            ),
            prefs.getInt(
                KEY_GLOBAL_PLAN_30,
                329
            )
        )
    }

    fun updateMembershipPlanPrices(
        price3: Int,
        price7: Int,
        price15: Int,
        price30: Int
    ) {
        prefs.edit()
            .putInt(
                KEY_GLOBAL_PLAN_3,
                price3
            )
            .putInt(
                KEY_GLOBAL_PLAN_7,
                price7
            )
            .putInt(
                KEY_GLOBAL_PLAN_15,
                price15
            )
            .putInt(
                KEY_GLOBAL_PLAN_30,
                price30
            )
            .apply()

        _membershipPlans.value =
            buildMembershipPlans(
                price3,
                price7,
                price15,
                price30
            )
    }
    // --- Global Admin Settings ---
    fun updateUpiId(newId: String) {
        prefs.edit().putString(KEY_UPI_ID, newId).apply()
        _upiId.value = newId
    }

    fun updateQrImageUrl(url: String) {
        prefs.edit().putString(KEY_QR_IMAGE_URL, url).apply()
        _qrImageUrl.value = url
    }

    fun updateCommunityLinks(
        whatsapp: String,
        telegram: String,
        instagram: String,
        supportTelegram: String = "https://t.me/SmartDrivoSupport"
    ) {
        prefs.edit().apply {
            putString(KEY_COMMUNITY_WHATSAPP, whatsapp)
            putString(KEY_COMMUNITY_TELEGRAM, telegram)
            putString(KEY_COMMUNITY_INSTAGRAM, instagram)
            putString(KEY_COMMUNITY_SUPPORT_TELEGRAM, supportTelegram)
            apply()
        }

        _whatsappLink.value = whatsapp
        _telegramLink.value = telegram
        _instagramLink.value = instagram
        _supportTelegramLink.value = supportTelegram

        _communityLinks.value =
            com.example.model.CommunityLinks(
                whatsappUrl = whatsapp,

                telegramUrl = telegram,
                instagramUrl = instagram,

                supportTelegramUrl = supportTelegram
            )
    }

    companion object {
        const val DEFAULT_UPI_ID = "gpay-11189725657@okaxis"

        // Process-wide shared StateFlow for instant cross-component updates (UI auto-refresh)
        private val _sharedOrderHistory = MutableStateFlow<List<OrderHistoryItem>>(emptyList())
        val sharedOrderHistory: StateFlow<List<OrderHistoryItem>> = _sharedOrderHistory.asStateFlow()

        private val _sharedTotalAccepted = MutableStateFlow<Int>(0)
        val sharedTotalAccepted: StateFlow<Int> = _sharedTotalAccepted.asStateFlow()

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }

        fun updateSharedOrderHistory(list: List<OrderHistoryItem>) {
            _sharedOrderHistory.value = list
        }

        private const val KEY_UID = "user_uid"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PHONE = "user_phone"
        private const val KEY_CITY = "user_city"
        private const val KEY_STATE = "user_state"
        private const val KEY_VEHICLE = "user_vehicle"
        private const val KEY_PLAN = "user_plan"
        private const val KEY_PLAN_PRICE = "user_plan_price"
        private const val KEY_PLAN_EXPIRE = "user_plan_expire"
        private const val KEY_IS_APPROVED = "user_is_approved"
        private const val KEY_IS_ADMIN = "user_is_admin"
        private const val KEY_IS_ACTIVE = "user_is_active"
        private const val KEY_REFERRAL = "user_referral"
        private const val KEY_IS_LOGGED_IN = "user_is_logged_in"
        private const val KEY_HAS_OPENED_BEFORE = "app_has_opened_before"

        private const val KEY_AUTO_ACCEPT = "setting_auto_accept"
        private const val KEY_AUTO_REJECT_BAD_FARES = "setting_auto_reject_bad_fares"
        private const val KEY_FASTEST_MODE = "setting_fastest_mode"

        private const val KEY_SECONDARY_BOTH_ENABLED =
            "setting_secondary_both_filter_enabled"
        private const val KEY_SECONDARY_BOTH_MIN_FARE =
            "setting_secondary_both_min_fare"
        private const val KEY_SECONDARY_BOTH_MAX_PICKUP =
            "setting_secondary_both_max_pickup_km"
        private const val KEY_SECONDARY_BOTH_MAX_DROP =
            "setting_secondary_both_max_drop_km"

        private const val KEY_TERTIARY_BOTH_ENABLED =
            "setting_tertiary_both_filter_enabled"
        private const val KEY_TERTIARY_BOTH_MIN_FARE =
            "setting_tertiary_both_min_fare"
        private const val KEY_TERTIARY_BOTH_MAX_PICKUP =
            "setting_tertiary_both_max_pickup_km"
        private const val KEY_TERTIARY_BOTH_MAX_DROP =
            "setting_tertiary_both_max_drop_km"

        private const val KEY_PLUS_AMOUNT_ENABLED = "setting_plus_amount_filter_enabled"
        private const val KEY_PLUS_AMOUNT_MIN = "setting_plus_amount_min"
        private const val KEY_PLUS_AMOUNT_MAX_PICKUP = "setting_plus_amount_max_pickup_km"
        private const val KEY_PLUS_AMOUNT_MAX_DROP = "setting_plus_amount_max_drop_km"

        private const val KEY_RAPIDO_ENABLED = "setting_rapido"
        private const val KEY_UBER_ENABLED = "setting_uber"
        private const val KEY_OLA_ENABLED = "setting_ola"
        private const val KEY_AUTO_TYPE_ENABLED = "setting_vehicle_auto"
        private const val KEY_BIKE_TYPE_ENABLED = "setting_vehicle_bike"
        private const val KEY_CAR_TYPE_ENABLED = "setting_vehicle_car"
        private const val KEY_CLICK_SPEED = "setting_click_speed"
        private const val KEY_CLICK_STRATEGY = "setting_click_strategy"
        private const val KEY_FILTER_MODE = "setting_filter_mode"
        private const val KEY_MAX_PICKUP_DIST = "setting_max_pickup_dist_km"
        private const val KEY_MAX_DROP_DIST = "setting_max_drop_dist_km"
        private const val KEY_MAX_DROP_KM = "setting_max_drop_km"
        private const val KEY_MIN_FARE = "setting_min_fare"
        private const val KEY_MAX_FARE = "setting_max_fare"
        private const val KEY_OUT_OF_RANGE = "setting_out_of_range"
        private const val KEY_BUNDLE_ACTION = "setting_bundle_action"
        private const val KEY_BUNDLE_ENABLED = "setting_bundle_order_enabled"
        private const val KEY_THEME_MODE = "setting_theme_mode"
        private const val KEY_AUTOSTART = "setting_autostart"
        private const val KEY_PENDING_AUTO_ACCEPT = "key_pending_auto_accept"
        private const val KEY_GOTO_ENABLED = "setting_goto_enabled"
        private const val KEY_NOGO_ENABLED = "setting_nogo_enabled"

        private const val KEY_AREA_GROUPS = "area_groups_json"
        const val KEY_GOTO_AREAS = "goto_areas"
        const val KEY_NOGO_AREAS = "nogo_areas"
        private const val KEY_GOTO_AREA_GROUPS = "goto_area_groups_json"
        private const val KEY_NOGO_AREA_GROUPS = "nogo_area_groups_json"
        private const val KEY_ORDER_HISTORY = "order_history_json"
        private const val KEY_LAST_ACCEPTED = "last_accepted_json"
        private const val KEY_TOTAL_ACCEPTED = "total_accepted"
        private const val KEY_LAST_ACCEPTED_PLATFORM = "last_accepted_platform"
        private const val KEY_LAST_ACCEPTED_TIMESTAMP = "last_accepted_timestamp"
        private const val KEY_LAST_ACCEPTED_FARE = "last_accepted_fare"
        private const val KEY_PAYMENTS = "payments_json"
        private const val KEY_ALL_USERS = "all_users_json"
        private const val KEY_GLOBAL_PLAN_3 =
            "global_plan_price_3"

        private const val KEY_GLOBAL_PLAN_7 =
            "global_plan_price_7"

        private const val KEY_GLOBAL_PLAN_15 =
            "global_plan_price_15"

        private const val KEY_GLOBAL_PLAN_30 =
            "global_plan_price_30"
        private const val KEY_UPI_ID = "global_upi_id"
        private const val KEY_QR_IMAGE_URL = "global_qr_url"
        private const val KEY_COMMUNITY_WHATSAPP = "comm_whatsapp"
        private const val KEY_COMMUNITY_TELEGRAM = "comm_telegram"
        private const val KEY_COMMUNITY_INSTAGRAM = "comm_instagram"
        private const val KEY_COMMUNITY_SUPPORT_TELEGRAM = "comm_support_telegram"
    }
}






