package com.example.model

import androidx.compose.runtime.Immutable

enum class ClickSpeed(val delayMs: Long, val label: String) {
    SPEED_1MS(1L, "1ms (Instant)"),
    SPEED_10MS(10L, "10ms (Ultra fast)"),
    SPEED_50MS(50L, "50ms (Balanced)"),
    SPEED_100MS(100L, "100ms (Safe)");

    val displayName: String get() = label
}

enum class ClickStrategy(val label: String) {
    AUTO("Action Click (Fastest)"),
    GESTURE("Gesture Tap & Swipe"),
    FALLBACK("Tree Walk Fallback");

    val displayName: String get() = label
}

enum class OutOfRangeAction(val label: String) {
    AUTO_REJECT("Auto Reject"),
    AUTO_IGNORE("Auto Ignore"),
    DO_NOTHING("Do Nothing")
}

enum class BundleOrderAction(val label: String) {
    AUTO_REJECT("Auto Reject"),
    AUTO_IGNORE("Auto Ignore")
}

enum class AppThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System Default");

    val displayName: String get() = label
}

enum class FilterMode(val displayName: String) {
    FARE_ONLY("Fare Only"),
    DISTANCE_ONLY("Distance Only"),
    BOTH("Both")
}

@Immutable
data class CommunityLinks(
    val whatsappUrl: String = "https://chat.whatsapp.com/smartdrivo",
    val telegramUrl: String = "https://t.me/smartdrivo",
    val instagramUrl: String = "https://www.instagram.com/smartdrivo/",
    val supportTelegramUrl: String = "https://t.me/SmartDrivoSupport"
)

@Immutable
data class AppSettings(
    val isAutoAcceptActive: Boolean = false,
    // Independent user-controlled switch:
    // when ON, Rapido offers that fail SAVED conditions can be skipped automatically.
    val isAutoRejectBadFaresEnabled: Boolean = false,
    val isFastestModeEnabled: Boolean = false,

    // Separate BOTH filter from Smart Filter screen.
    // Completely independent from Home page Fare/Distance/Both.
    val isSecondaryBothFilterEnabled: Boolean = false,
    val secondaryBothMinFare: Float = 50f,
    val secondaryBothMaxPickupDistanceKm: Float = 3.0f,
    val secondaryBothMaxDropDistanceKm: Float = 7.5f,

    // ORDER_FILTER_3_V1
    // Independent third filter: Fare + Pickup + Drop must all match.
    val isTertiaryBothFilterEnabled: Boolean = false,
    val tertiaryBothMinFare: Float = 50f,
    val tertiaryBothMaxPickupDistanceKm: Float = 3.0f,
    val tertiaryBothMaxDropDistanceKm: Float = 7.5f,

    // PLUS_AMOUNT_FILTER_V1
    val isPlusAmountFilterEnabled: Boolean = false,
    val plusAmountMin: Float = 10f,
    val plusAmountMaxPickupDistanceKm: Float = 3.0f,
    val plusAmountMaxDropDistanceKm: Float = 7.5f,

    // Platforms enabled
    val rapidoEnabled: Boolean = true,
    val uberEnabled: Boolean = true,
    val olaEnabled: Boolean = true,
    // Vehicles enabled
    val autoEnabled: Boolean = true,
    val bikeEnabled: Boolean = true,
    val carEnabled: Boolean = true,
    // Speed & Strategy
    val clickSpeed: ClickSpeed = ClickSpeed.SPEED_50MS,
    val clickStrategy: ClickStrategy = ClickStrategy.AUTO,
    // Filter mode ("Fare Only" | "Distance Only" | "Both")
    val filterMode: FilterMode = FilterMode.BOTH,
    // Distance criteria
    val maxPickupDistanceKm: Float = 3.0f,
    val maxDropDistanceKm: Float = 7.5f,
    // Fare criteria
    val minFare: Float = 50f,
    val maxFare: Float = 999f,
    // Backwards compatibility
    val maxDropKm: Float = 7.5f,
    // Short on-screen reason popup for every finalized ride decision.
    val showOrderReasonPopups: Boolean = true,
    // Feedback & Auto start
    val soundAlertOnAccept: Boolean = true,
    val vibrateOnAccept: Boolean = true,
    val autostartOnBoot: Boolean = true,
    // Rules
    val outOfRangeAction: OutOfRangeAction = OutOfRangeAction.AUTO_REJECT,
    // Bundle Order: OFF = manual only, ON = use normal saved filters.
    val isBundleOrderEnabled: Boolean = false,
    // Kept for backward compatibility with older saved settings.
    val bundleOrderAction: BundleOrderAction = BundleOrderAction.AUTO_REJECT,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    // Area filters
    val isGoToEnabled: Boolean = false,
    val isNoGoEnabled: Boolean = true
)
