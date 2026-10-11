package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val city: String = "",
    val state: String = "",
    val vehicleType: VehicleType = VehicleType.AUTO,
    val plan: String = "NONE",
    val planPrice: Int = 0,
    val planExpireMillis: Long = 0L,
    val isApproved: Boolean = false,
    val isAdmin: Boolean = false,
    val isActive: Boolean = true,

    // BLOCKED_USER_DEVICE_BLACKLIST_V1
    // Permanent account/device blocks are separate from temporary Admin Lock.
    val isBlocked: Boolean = false,
    val blockReason: String = "",
    val isDeviceBlacklisted: Boolean = false,
    val deviceBlacklistReason: String = "",

    // DEVICE_LOCK_V1
    // Privacy-safe per-install binding. No IMEI / serial / MAC / Android ID.
    val boundInstallId: String = "",
    val boundDeviceName: String = "",
    val isDeviceAuthorized: Boolean = true,
    val deviceChangeRequested: Boolean = false,

    val referralCode: String = "SMART50",
    val createdAt: Long = System.currentTimeMillis(),
    val mobile: String = ""
) {
    val isFreeTrial: Boolean
        get() =
            plan.equals(
                "FREE_TRIAL",
                ignoreCase = true
            )

    val isFreeTrialActive: Boolean
        get() =
            isActive &&
                !isBlocked &&
                isFreeTrial &&
                planExpireMillis >
                    System.currentTimeMillis()

    val isPlanValid: Boolean
        get() =
            isActive &&
                !isBlocked &&
                planExpireMillis >
                    System.currentTimeMillis() &&
                (
                    isApproved ||
                        isFreeTrial
                )
    val effectiveMobile: String
        get() = mobile.ifEmpty { phone }
}
