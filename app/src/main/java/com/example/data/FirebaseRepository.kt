package com.example.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.model.AreaGroup
import com.example.model.AreaType
import com.example.model.MembershipPlan
import com.example.model.OrderHistoryItem
import com.example.model.PaymentStatus
import com.example.model.PaymentSubmission
import com.example.model.UserProfile
import com.example.model.VehicleType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class FirebaseRepository(
    private val context: Context,
    private val prefs: PreferencesManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var firestore: FirebaseFirestore? = null
    private var realtimeDb: FirebaseDatabase? = null
    private var auth: FirebaseAuth? = null
    private var ownUserListener: ListenerRegistration? = null
    private var ownPaymentsListener: ListenerRegistration? = null

    // BLOCKED_USER_DEVICE_BLACKLIST_V1


    // OFFLINE_GRACE_LOCK_V1
    // The offline lease refreshes only after BOTH the user entitlement and
    // current-device blacklist checks have succeeded.
    private var ownSecurityUserVerified = false



    private fun maybeMarkOwnSecurityVerified(
        uid: String
    ) {
        if (
            ownSecurityUserVerified
        ) {
            prefs.markSecurityVerifiedNow(
                uid
            )
        }
    }

    private var adminUsersListener: ListenerRegistration? = null
    private var adminPaymentsListener: ListenerRegistration? = null

    private var globalSettingsListener: ListenerRegistration? = null
    init {
        try {
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            realtimeDb = FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Firebase not initialized or missing configuration: ${e.message}")
        }
    }

    fun getCurrentUid(): String {
        return auth?.currentUser?.uid ?: prefs.userProfile.value.uid.ifEmpty {
            val generated = UUID.randomUUID().toString()
            prefs.saveUserProfile(prefs.userProfile.value.copy(uid = generated))
            generated
        }
    }

    // ADMIN_DEVICE_INFO_V4
    // Privacy-safe metadata only.
    // No IMEI, serial number, MAC address or Android ID.
    private fun currentDeviceName(): String {
        val maker = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()

        return when {
            maker.isBlank() && model.isBlank() -> "Unknown Android"
            maker.isBlank() -> model
            model.isBlank() -> maker
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    private fun currentAndroidVersion(): String =
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    private fun currentAppVersionName(): String =
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }

    // TRIAL_ABUSE_PROTECTION_V4
    // Raw ANDROID_ID is never uploaded or stored.
    // Only a one-way SHA-256 token is used for one-trial-per-device control.
    // BLOCKED_USER_DEVICE_BLACKLIST_V1
    // Device blacklist reuses the same privacy-safe one-way device token.
    // Raw ANDROID_ID is never stored or uploaded.
    private fun currentSecurityDeviceKey(): String =
        currentTrialDeviceKey()

    private fun currentTrialDeviceKey(): String {
        val androidId =
            try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                    ?.trim()
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }

        val stableSource =
            androidId.ifBlank {
                buildString {
                    append(Build.MANUFACTURER)
                    append("|")
                    append(Build.MODEL)
                    append("|")
                    append(Build.DEVICE)
                    append("|")
                    append(Build.PRODUCT)
                }
            }

        val raw =
            "SMARTDRIVO_TRIAL_DEVICE_V4|" +
                context.packageName +
                "|" +
                stableSource

        return MessageDigest
            .getInstance("SHA-256")
            .digest(
                raw.toByteArray(
                    Charsets.UTF_8
                )
            )
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun userFromDocument(doc: DocumentSnapshot): UserProfile {
        val vehicle = try {
            com.example.model.VehicleType.valueOf(
                (doc.getString("vehicleType") ?: "AUTO").uppercase()
            )
        } catch (_: Exception) {
            com.example.model.VehicleType.AUTO
        }

        val isAdmin = doc.getBoolean("isAdmin") ?: false
        val boundInstallId = doc.getString("boundInstallId").orEmpty()
        val deviceAuthorized = true // Device-lock feature removed

        return UserProfile(
            uid = doc.id,
            name = doc.getString("name") ?: "",
            email = doc.getString("email") ?: "",
            phone = doc.getString("phone")
                ?: doc.getString("mobile")
                ?: doc.getString("mobileNumber")
                ?: "",
            city = doc.getString("city") ?: "",
            state = doc.getString("state") ?: "",
            vehicleType = vehicle,
            plan = doc.getString("plan") ?: "NONE",
            planPrice = doc.getLong("planPrice")?.toInt() ?: 0,
            planExpireMillis = doc.getLong("planExpireMillis") ?: 0L,
            isApproved = doc.getBoolean("isApproved") ?: false,
            isAdmin = isAdmin,
            isActive = doc.getBoolean("isActive") ?: true,
            isBlocked =
                doc.getBoolean("isBlocked") ?: false,
            blockReason =
                doc.getString("blockReason").orEmpty(),
            boundInstallId = boundInstallId,
            boundDeviceName =
                doc.getString("boundDevice")
                    ?: doc.getString("registeredDevice")
                    ?: "",
            isDeviceAuthorized = deviceAuthorized,
            deviceChangeRequested = false,
            referralCode = doc.getString("referralCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun paymentFromDocument(doc: DocumentSnapshot): PaymentSubmission {
        val status = try {
            com.example.model.PaymentStatus.valueOf(
                (doc.getString("status") ?: "PENDING").uppercase()
            )
        } catch (_: Exception) {
            com.example.model.PaymentStatus.PENDING
        }

        return PaymentSubmission(
            paymentId = doc.id,
            uid = doc.getString("uid") ?: "",
            userName = doc.getString("userName") ?: "",
            utrNumber = doc.getString("utrNumber") ?: "",
            planSelected = doc.getString("planSelected") ?: "7DAYS",
            amount = doc.getLong("amount")?.toInt() ?: 0,
            status = status,
            submittedAt = doc.getLong("submittedAt") ?: 0L,
            approvedAt = doc.getLong("approvedAt"),
            note = doc.getString("note") ?: ""
        )
    }

    fun startOwnMembershipSync() {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return

        // Clear old device-session invalidation on startup.
        prefs.clearLoginSessionState()

        ownUserListener?.remove()
        ownPaymentsListener?.remove()

        // OFFLINE_GRACE_LOCK_V1
        ownSecurityUserVerified = false

        ownUserListener = fs.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepo", "Own membership listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val remote = userFromDocument(snapshot)
                    val local = prefs.userProfile.value

                    val merged = remote.copy(
                        name = remote.name.ifBlank { local.name },
                        email = remote.email.ifBlank { local.email },
                        phone = remote.phone.ifBlank { local.phone },
                        city = remote.city.ifBlank { local.city },
                        state = remote.state.ifBlank { local.state },
                        referralCode = remote.referralCode.ifBlank { local.referralCode }
                    )

                    prefs.saveUserProfile(merged)

                    // OFFLINE_GRACE_LOCK_V3
                    // Only a server-backed snapshot can renew the lease.
                    if (
                        !snapshot.metadata.isFromCache
                    ) {
                        ownSecurityUserVerified = true
                        maybeMarkOwnSecurityVerified(
                            uid
                        )
                    }

                    if (!merged.isPlanValid && !merged.isAdmin) {
                        prefs.setAutoAcceptActive(false)
                    }
                }
            }

        ownPaymentsListener = fs.collection("payments")
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepo", "Own payments listener error: ${error.message}")
                    return@addSnapshotListener
                }

                prefs.setPaymentSubmissions(
                    snapshot?.documents
                        ?.map { paymentFromDocument(it) }
                        ?.sortedByDescending { it.submittedAt }
                        ?: emptyList()
                )
            }
    }

    fun stopOwnMembershipSync() {
        ownUserListener?.remove()
        ownPaymentsListener?.remove()
        ownUserListener = null
        ownPaymentsListener = null
    }

    fun startAdminBackendSync() {
        val fs = firestore ?: return
        if (!prefs.userProfile.value.isAdmin) return

        adminUsersListener?.remove()
        adminPaymentsListener?.remove()

        adminUsersListener = fs.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepo", "Admin users listener error: ${error.message}")
                    return@addSnapshotListener
                }

                prefs.setAllUsers(
                    snapshot?.documents
                        ?.map { userFromDocument(it) }
                        ?.sortedByDescending { it.createdAt }
                        ?: emptyList()
                )
            }

        adminPaymentsListener = fs.collection("payments")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepo", "Admin payments listener error: ${error.message}")
                    return@addSnapshotListener
                }

                prefs.setPaymentSubmissions(
                    snapshot?.documents
                        ?.map { paymentFromDocument(it) }
                        ?.sortedByDescending { it.submittedAt }
                        ?: emptyList()
                )
            }
    }

    fun stopAdminBackendSync() {
        adminUsersListener?.remove()
        adminPaymentsListener?.remove()
        adminUsersListener = null
        adminPaymentsListener = null
    }
    // --- User Profile Sync ---
    fun saveUserProfile(
        profile: UserProfile,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val fs = firestore

                if (fs == null) {
                    onComplete?.invoke(false)
                    return@launch
                }

                // AUTH_UID_CANONICAL_USER_DOC_V1
                // Signed-in Firebase UID is the only canonical /users document id.
                // This prevents an old/local UUID from creating another row.
                val authenticatedUid =
                    auth?.currentUser?.uid.orEmpty()

                val uid =
                    authenticatedUid.ifBlank {
                        profile.uid
                    }

                if (uid.isBlank()) {
                    onComplete?.invoke(false)
                    return@launch
                }

                prefs.saveUserProfile(
                    if (profile.uid == uid) profile
                    else profile.copy(uid = uid)
                )

                val now =
                    System.currentTimeMillis()

                val docRef =
                    fs.collection("users")
                        .document(uid)

                val existing =
                    docRef.get().await()

                val currentDevice = currentDeviceName()
                val currentAndroid = currentAndroidVersion()
                val currentAppVersion = currentAppVersionName()

                val safeClientMap =
                    mapOf(
                        "uid" to uid,
                        "name" to profile.name,
                        "email" to profile.email,
                        "phone" to profile.phone,
                        "mobile" to profile.phone,
                        "mobileNumber" to profile.phone,
                        "city" to profile.city,
                        "state" to profile.state,
                        "vehicleType" to
                            profile.vehicleType.name,
                        "referralCode" to
                            profile.referralCode,
                        "updatedAt" to now,
                        "lastDevice" to currentDevice,
                        "lastAndroidVersion" to currentAndroid,
                        "lastAppVersion" to currentAppVersion,
                        "lastSeenAt" to now,

                        // BLOCKED_USER_DEVICE_BLACKLIST_V1
                        // Public hash token only; raw Android ID is never stored.
                        "securityDeviceKey" to
                            currentSecurityDeviceKey()
                    )

                if (!existing.exists()) {

                    // TRIAL_ABUSE_PROTECTION_V4
                    // One free trial per privacy-safe stable device token.
                    // User doc + trial-device reservation are atomic.
                    val trialDeviceKey =
                        currentTrialDeviceKey()

                    val trialDeviceRef =
                        fs.collection(
                            "trialDevices"
                        ).document(
                            trialDeviceKey
                        )

                    val trialEnd =
                        now +
                            (
                                24L *
                                    60L *
                                    60L *
                                    1000L
                            )

                    fs.runTransaction { tx ->
                        val userSnapshot =
                            tx.get(docRef)

                        if (userSnapshot.exists()) {
                            return@runTransaction
                        }

                        val trialDeviceSnapshot =
                            tx.get(
                                trialDeviceRef
                            )

                        val grantTrial =
                            !trialDeviceSnapshot
                                .exists()

                        val plan =
                            if (grantTrial)
                                "FREE_TRIAL"
                            else
                                "NONE"

                        val expiry =
                            if (grantTrial)
                                trialEnd
                            else
                                0L

                        tx.set(
                            docRef,
                            safeClientMap +
                                mapOf(
                                    "plan" to plan,
                                    "planPrice" to 0,
                                    "planExpireMillis" to
                                        expiry,
                                    "isApproved" to false,
                                    "isAdmin" to false,
                                    "isActive" to true,
                                    "createdAt" to now,
                                    "freeTrialStartedAt" to
                                        if (grantTrial)
                                            now
                                        else
                                            0L,
                                    "freeTrialUsed" to true,
                                    "trialDeviceKey" to
                                        trialDeviceKey,
                                    "trialDeniedReason" to
                                        if (grantTrial)
                                            ""
                                        else
                                            "DEVICE_TRIAL_ALREADY_USED",
                                    "registeredDevice" to
                                        currentDevice,
                                    "registeredAndroidVersion" to
                                        currentAndroid,
                                    "registeredAppVersion" to
                                        currentAppVersion,
                                    "registeredDeviceAt" to now,
                                    "registeredDeviceSource" to
                                        "REGISTRATION",

                                )
                        )

                        if (grantTrial) {
                            tx.set(
                                trialDeviceRef,
                                mapOf(
                                    "deviceTrialKey" to
                                        trialDeviceKey,
                                    "firstUid" to
                                        uid,
                                    "createdAt" to
                                        now,
                                    "trialExpireMillis" to
                                        trialEnd,
                                    "registeredDevice" to
                                        currentDevice,
                                    "source" to
                                        "ANDROID_ID_SHA256_V4"
                                )
                            )
                        }
                    }.await()

                    // Read the committed user document to know whether this
                    // device received FREE_TRIAL or was blocked with NONE.
                    // This happens only during registration, never on ride events.
                    val createdProfile =
                        docRef.get().await()

                    val assignedPlan =
                        createdProfile.getString(
                            "plan"
                        ) ?: "NONE"

                    if (assignedPlan == "FREE_TRIAL") {
                        val assignedExpiry =
                            createdProfile.getLong(
                                "planExpireMillis"
                            ) ?: trialEnd

                        prefs.saveUserProfile(
                            profile.copy(
                                uid = uid,
                                plan = "FREE_TRIAL",
                                planPrice = 0,
                                planExpireMillis =
                                    assignedExpiry,
                                isApproved = false,
                                isAdmin = false,
                                isActive = true,
                                createdAt = now
                            )
                        )

                        Log.i(
                            "FirebaseRepo",
                            "TRIAL ABUSE V4: one-day trial granted"
                        )
                    } else {
                        prefs.saveUserProfile(
                            profile.copy(
                                uid = uid,
                                plan = "NONE",
                                planPrice = 0,
                                planExpireMillis = 0L,
                                isApproved = false,
                                isAdmin = false,
                                isActive = true,
                                createdAt = now
                            )
                        )

                        prefs.setAutoAcceptActive(
                            false
                        )

                        Log.i(
                            "FirebaseRepo",
                            "TRIAL ABUSE V4: same-device repeat trial blocked"
                        )
                    }

                } else {

                    // Existing account:
                    // personal fields may sync, but the client
                    // can never restart/extend membership.
                    // Older users have no historical registration-device value.
                    // Backfill once with the first device seen after this update.
                    val registrationDevicePatch =
                        if (existing.getString("registeredDevice").isNullOrBlank()) {
                            mapOf(
                                "registeredDevice" to currentDevice,
                                "registeredAndroidVersion" to currentAndroid,
                                "registeredAppVersion" to currentAppVersion,
                                "registeredDeviceAt" to now,
                                "registeredDeviceSource" to "BACKFILLED"
                            )
                        } else {
                            emptyMap<String, Any>()
                        }

                    // TRIAL_ABUSE_PROTECTION_V4
                    // Backfill older accounts that already consumed a trial.
                    // This makes protection effective for existing users after
                    // their first launch/login on the updated app.
                    val historicalTrialUsed =
                        existing.getBoolean(
                            "freeTrialUsed"
                        ) == true ||
                            existing.getString(
                                "plan"
                            ) == "FREE_TRIAL"

                    if (historicalTrialUsed) {
                        val key =
                            currentTrialDeviceKey()

                        val ref =
                            fs.collection(
                                "trialDevices"
                            ).document(
                                key
                            )

                        try {
                            fs.runTransaction { tx ->
                                val reserved =
                                    tx.get(ref)

                                if (!reserved.exists()) {
                                    val startedAt =
                                        existing.getLong(
                                            "freeTrialStartedAt"
                                        ) ?: now

                                    tx.set(
                                        ref,
                                        mapOf(
                                            "deviceTrialKey" to
                                                key,
                                            "firstUid" to
                                                uid,
                                            "createdAt" to
                                                now,
                                            "trialExpireMillis" to
                                                (
                                                    startedAt +
                                                        24L *
                                                        60L *
                                                        60L *
                                                        1000L
                                                    ),
                                            "registeredDevice" to
                                                currentDevice,
                                            "source" to
                                                "HISTORICAL_BACKFILL_V4"
                                        )
                                    )
                                }
                            }.await()
                        } catch (e: Exception) {
                            Log.w(
                                "FirebaseRepo",
                                "Trial-device backfill skipped: ${e.message}"
                            )
                        }
                    }

                    docRef.set(
                        safeClientMap +
                            registrationDevicePatch,
                        SetOptions.merge()
                    ).await()
                }

                onComplete?.invoke(true)

            } catch (e: Exception) {
                Log.e(
                    "FirebaseRepo",
                    "Failed to sync user: ${e.message}"
                )

                onComplete?.invoke(false)
            }
        }
    }
    // Privileged update: used when verified admin updates users from AdminPanel
    fun adminUpdateUserProfile(profile: UserProfile, onComplete: ((Boolean) -> Unit)? = null) {
        val currentCaller = prefs.userProfile.value
        if (!currentCaller.isAdmin) {
            Log.e("FirebaseRepo", "Unauthorized attempt to write privileged fields")
            onComplete?.invoke(false)
            return
        }
        scope.launch {
            try {
                firestore?.collection("users")?.document(profile.uid)?.set(
                    mapOf(
                        "uid" to profile.uid,
                        "name" to profile.name,
                        "email" to profile.email,
                        "phone" to profile.phone,
                        "city" to profile.city,
                        "state" to profile.state,
                        "vehicleType" to profile.vehicleType.name,
                        "plan" to profile.plan,
                        "planPrice" to profile.planPrice,
                        "planExpireMillis" to profile.planExpireMillis,
                        "isApproved" to profile.isApproved,
                        "isAdmin" to profile.isAdmin,
                        "isActive" to profile.isActive,
                        "isBlocked" to profile.isBlocked,
                        "blockReason" to profile.blockReason,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )?.await()
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e("FirebaseRepo", "Admin user update failed: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    suspend fun fetchFirestoreUserProfile(uid: String, email: String = ""): UserProfile? {
        val fs = firestore ?: return null
        return try {
            var doc = if (uid.isNotBlank()) fs.collection("users").document(uid).get().await() else null
            if ((doc == null || !doc.exists()) && email.isNotBlank()) {
                val query = fs.collection("users").whereEqualTo("email", email).limit(1).get().await()
                if (!query.isEmpty) {
                    doc = query.documents.first()
                }
            }
            if (doc != null && doc.exists()) {
                val phone = doc.getString("phone")
                    ?: doc.getString("mobile")
                    ?: doc.getString("mobileNumber")
                    ?: ""
                val city = doc.getString("city") ?: ""
                val state = doc.getString("state") ?: ""
                val name = doc.getString("name") ?: ""
                val userEmail = doc.getString("email") ?: email
                val plan = doc.getString("plan") ?: "NONE"
                val planPrice = doc.getLong("planPrice")?.toInt() ?: 0
                val planExpireMillis = doc.getLong("planExpireMillis") ?: 0L
                val isApproved = doc.getBoolean("isApproved") ?: false
                val isAdmin = doc.getBoolean("isAdmin") ?: false
                val isActive = doc.getBoolean("isActive") ?: true

                // BLOCKED_USER_DEVICE_BLACKLIST_V1
                val isBlocked =
                    doc.getBoolean("isBlocked") ?: false
                val blockReason =
                    doc.getString("blockReason").orEmpty()

                // OFFLINE_GRACE_LOCK_V3
                // DocumentReference.get() may fall back to Firestore cache
                // while offline. Cache reads must not extend the grace lease.
                if (
                    !doc.metadata.isFromCache
                ) {
                    prefs.markSecurityVerifiedNow(
                        doc.id.ifEmpty { uid }
                    )
                }

                val referralCode = doc.getString("referralCode") ?: ""
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val vehicleTypeStr = doc.getString("vehicleType") ?: "AUTO"
                val vehicleType = try {
                    com.example.model.VehicleType.valueOf(vehicleTypeStr)
                } catch (_: Exception) {
                    com.example.model.VehicleType.AUTO
                }
                UserProfile(
                    uid = doc.id.ifEmpty { uid },
                    name = name,
                    email = userEmail,
                    phone = phone,
                    city = city,
                    state = state,
                    vehicleType = vehicleType,
                    plan = plan,
                    planPrice = planPrice,
                    planExpireMillis = planExpireMillis,
                    isApproved = isApproved,
                    isAdmin = isAdmin,
                    isActive = isActive,
                    isBlocked = isBlocked,
                    blockReason = blockReason,
                    isDeviceBlacklisted = false,
                    deviceBlacklistReason = "",
                    boundInstallId = doc.getString("boundInstallId").orEmpty(),
                    boundDeviceName =
                        doc.getString("boundDevice")
                            ?: doc.getString("registeredDevice")
                            ?: "",
                    isDeviceAuthorized = true,
                    deviceChangeRequested = false,
                    referralCode = referralCode,
                    createdAt = createdAt
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "fetchFirestoreUserProfile failed: ${e.message}")
            null
        }
    }

    fun checkUserProfileFromFirestore(
        uid: String,
        email: String,
        onResult: (UserProfile?) -> Unit
    ) {
        scope.launch {
            val profile = fetchFirestoreUserProfile(uid, email)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(profile)
            }
        }
    }

    // --- Order History Sync ---
    fun recordOrderHistory(item: OrderHistoryItem) {
        prefs.addOrderHistory(item)
        val uid = getCurrentUid()
        scope.launch {
            try {
                val id = item.id.ifEmpty { UUID.randomUUID().toString() }
                firestore?.collection("orders")?.document(uid)?.collection("history")
                    ?.document(id)?.set(
                        mapOf(
                            "timestamp" to item.timestamp,
                            "dateStr" to item.dateStr,
                            "timeStr" to item.timeStr,
                            "status" to item.status.name,
                            "platform" to item.platform.name,
                            "vehicleType" to item.vehicleType.name,
                            "pickupDistKm" to item.pickupDistKm,
                            "dropDistKm" to item.dropDistKm,
                            "pickupAddress" to item.pickupAddress,
                            "dropAddress" to item.dropAddress,
                            "dropArea" to item.dropArea,
                            "amount" to item.amount,
                            "bookingId" to item.bookingId,
                            "detectionTimeMs" to item.detectionTimeMs,
                            "clickTimeMs" to item.clickTimeMs,
                            "baseFare" to item.baseFare,
                            "tipAmount" to item.tipAmount,
                            "timesClicked" to item.timesClicked
                        )
                    )?.await()
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Order history firestore write skipped: ${e.message}")
            }
        }
    }

    // --- Payment Submission Sync ---
    fun submitPayment(
        submission: PaymentSubmission,
        onComplete: (Boolean, PaymentSubmission?) -> Unit
    ) {
        val fs = firestore
        val authUid = auth?.currentUser?.uid

        if (fs == null || authUid.isNullOrBlank()) {
            Log.e("FirebaseRepo", "Payment submit blocked: Firebase/Auth unavailable")
            onComplete(false, null)
            return
        }

        val cleanUtr = submission.utrNumber.trim()
        if (cleanUtr.length != 12 || !cleanUtr.all { it.isDigit() }) {
            Log.e("FirebaseRepo", "Payment submit blocked: invalid 12-digit UTR")
            onComplete(false, null)
            return
        }

        val stablePaymentId = "UTR-$cleanUtr"
        val normalized = submission.copy(
            paymentId = stablePaymentId,
            uid = authUid,
            utrNumber = cleanUtr,
            status = com.example.model.PaymentStatus.PENDING,
            approvedAt = null
        )

        scope.launch {
            try {
                val ownSnapshot = fs.collection("payments")
                    .whereEqualTo("uid", authUid)
                    .get()
                    .await()

                val existingPayment = ownSnapshot.documents
                    .map { paymentFromDocument(it) }
                    .filter {
                        it.utrNumber.trim().equals(cleanUtr, ignoreCase = true)
                    }
                    .sortedWith(
                        compareByDescending<PaymentSubmission> {
                            it.status == com.example.model.PaymentStatus.PENDING
                        }.thenByDescending { it.submittedAt }
                    )
                    .firstOrNull()

                if (existingPayment != null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onComplete(false, existingPayment)
                    }
                    return@launch
                }

                val paymentData = mapOf(
                    "paymentId" to stablePaymentId,
                    "uid" to authUid,
                    "userName" to normalized.userName,
                    "utrNumber" to cleanUtr,
                    "planSelected" to normalized.planSelected,
                    "amount" to normalized.amount,
                    "status" to "PENDING",
                    "submittedAt" to normalized.submittedAt,
                    "approvedAt" to null,
                    "note" to normalized.note
                )

                fs.collection("payments")
                    .document(stablePaymentId)
                    .set(paymentData)
                    .await()

                prefs.addPaymentSubmission(normalized)

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(true, normalized)
                }
            } catch (e: Exception) {
                Log.w(
                    "FirebaseRepo",
                    "Payment submit blocked/failed for UTR $cleanUtr: ${e.message}"
                )

                val retryExisting = try {
                    val retry = fs.collection("payments")
                        .whereEqualTo("uid", authUid)
                        .get()
                        .await()

                    retry.documents
                        .map { paymentFromDocument(it) }
                        .filter {
                            it.utrNumber.trim().equals(cleanUtr, ignoreCase = true)
                        }
                        .sortedByDescending { it.submittedAt }
                        .firstOrNull()
                } catch (_: Exception) {
                    null
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(false, retryExisting)
                }
            }
        }
    }
    fun adminApprovePayment(
        submission: PaymentSubmission,
        onComplete: (Boolean, String) -> Unit
    ) {
        val fs = firestore
        val adminUid = auth?.currentUser?.uid

        if (
            fs == null ||
            adminUid.isNullOrBlank() ||
            !prefs.userProfile.value.isAdmin
        ) {
            onComplete(false, "Admin authorization unavailable")
            return
        }

        val cleanUtr = submission.utrNumber.trim()

        if (
            cleanUtr.length != 12 ||
            !cleanUtr.all { it.isDigit() }
        ) {
            onComplete(false, "UTR must be exactly 12 numeric digits")
            return
        }

        scope.launch {
            try {

                // DUPLICATE_UTR_SECURITY_V3
                // Protect UTRs approved before paymentUtrs reservation existed.
                val historicalMatches =
                    fs.collection("payments")
                        .whereEqualTo("utrNumber", cleanUtr)
                        .get()
                        .await()

                val historicalApprovedDuplicate =
                    historicalMatches.documents.any { doc ->
                        doc.id != submission.paymentId &&
                            (doc.getString("status") ?: "")
                                .equals("APPROVED", ignoreCase = true)
                    }

                if (historicalApprovedDuplicate) {
                    throw IllegalStateException(
                        "This UTR was already approved for another payment"
                    )
                }

                val paymentRef =
                    fs.collection("payments")
                        .document(submission.paymentId)

                val utrRef =
                    fs.collection("paymentUtrs")
                        .document(cleanUtr)

                val now = System.currentTimeMillis()

                var approvedDays = 0

                fs.runTransaction { tx ->

                    val paymentDoc = tx.get(paymentRef)

                    if (!paymentDoc.exists()) {
                        throw IllegalStateException(
                            "Payment record not found"
                        )
                    }

                    val currentStatus =
                        (paymentDoc.getString("status") ?: "PENDING")
                            .uppercase()

                    if (currentStatus == "APPROVED") {
                        throw IllegalStateException(
                            "Payment already approved"
                        )
                    }

                    val storedUtr =
                        paymentDoc.getString("utrNumber")
                            ?.trim()
                            .orEmpty()

                    if (storedUtr != cleanUtr) {
                        throw IllegalStateException(
                            "Payment UTR mismatch"
                        )
                    }

                    val storedUid =
                        paymentDoc.getString("uid")
                            ?.trim()
                            .orEmpty()

                    if (storedUid.isBlank()) {
                        throw IllegalStateException(
                            "Driver UID missing"
                        )
                    }

                    val storedPlan =
                        (paymentDoc.getString("planSelected") ?: "7DAYS")
                            .uppercase()

                    val storedAmount =
                        paymentDoc.getLong("amount")
                            ?.toInt()
                            ?: submission.amount

                    val daysToAdd =
                        when (storedPlan) {
                            "3DAYS" -> 3
                            "7DAYS" -> 7
                            "15DAYS" -> 15
                            "1MONTH", "30DAYS" -> 30
                            else -> 7
                        }

                    approvedDays = daysToAdd

                    // This document is the permanent UTR lock.
                    val utrDoc = tx.get(utrRef)

                    if (utrDoc.exists()) {
                        throw IllegalStateException(
                            "This UTR was already approved"
                        )
                    }

                    val userRef =
                        fs.collection("users")
                            .document(storedUid)

                    val userDoc = tx.get(userRef)

                    if (!userDoc.exists()) {
                        throw IllegalStateException(
                            "Driver account not found"
                        )
                    }

                    val oldExpiry =
                        userDoc.getLong("planExpireMillis")
                            ?: 0L

                    val base =
                        if (oldExpiry > now)
                            oldExpiry
                        else
                            now

                    val newExpiry =
                        base + (daysToAdd * 86400000L)

                    tx.update(
                        paymentRef,
                        mapOf(
                            "status" to "APPROVED",
                            "approvedAt" to now,
                            "approvedBy" to adminUid
                        )
                    )

                    tx.set(
                        userRef,
                        mapOf(
                            "plan" to storedPlan,
                            "planPrice" to storedAmount,
                            "planExpireMillis" to newExpiry,
                            "isApproved" to true,
                            "isActive" to true,
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    )

                    tx.set(
                        utrRef,
                        mapOf(
                            "utrNumber" to cleanUtr,
                            "paymentId" to paymentDoc.id,
                            "uid" to storedUid,
                            "approvedBy" to adminUid,
                            "approvedAt" to now
                        )
                    )
                }.await()

                prefs.updatePaymentStatus(
                    submission.paymentId,
                    com.example.model.PaymentStatus.APPROVED
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(
                        true,
                        "Membership activated for $approvedDays days"
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    "FirebaseRepo",
                    "Admin approval blocked/failed: ${e.message}"
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(
                        false,
                        e.message ?: "Approval failed"
                    )
                }
            }
        }
    }
    fun adminRejectPayment(
        submission: PaymentSubmission,
        onComplete: (Boolean, String) -> Unit
    ) {
        val fs = firestore
        val adminUid = auth?.currentUser?.uid

        if (fs == null || adminUid.isNullOrBlank() || !prefs.userProfile.value.isAdmin) {
            onComplete(false, "Admin authorization unavailable")
            return
        }

        scope.launch {
            try {
                fs.collection("payments").document(submission.paymentId).update(
                    mapOf(
                        "status" to "REJECTED",
                        "approvedAt" to null,
                        "rejectedAt" to System.currentTimeMillis(),
                        "rejectedBy" to adminUid
                    )
                ).await()

                prefs.updatePaymentStatus(
                    submission.paymentId,
                    com.example.model.PaymentStatus.REJECTED
                )

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(true, "Payment rejected")
                }
            } catch (e: Exception) {
                Log.e("FirebaseRepo", "Admin rejection failed: ${e.message}")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Rejection failed")
                }
            }
        }
    }
    // --- Area Groups Sync ---
    fun syncAreas(areas: List<AreaGroup>) {
        prefs.saveAreaGroups(areas)
        val uid = getCurrentUid()
        scope.launch {
            try {
                val db = realtimeDb ?: return@launch
                val goToRef = db.getReference("areas/$uid/goTo")
                val noGoRef = db.getReference("areas/$uid/noGo")

                val goToMap = mutableMapOf<String, Any>()
                val noGoMap = mutableMapOf<String, Any>()

                for (a in areas) {
                    val map = mapOf(
                        "id" to a.id,
                        "name" to a.name,
                        "isEnabled" to a.isEnabled,
                        "minFare" to a.minFare,
                        // Keep legacy field for older data readers.
                        "maxFare" to a.maxFare,
                        "minPickupKm" to a.minPickupKm,
                        "maxPickupKm" to a.maxPickupKm,
                        "maxDropKm" to a.maxDropKm,
                        "keywords" to a.keywords
                    )
                    if (a.type == AreaType.GO_TO) {
                        goToMap[a.id] = map
                    } else {
                        noGoMap[a.id] = map
                    }
                }
                goToRef.setValue(goToMap)
                noGoRef.setValue(noGoMap)
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Realtime DB area sync skipped: ${e.message}")
            }
        }
    }

    /**
     * Publish the administrator UPI ID to Firestore global settings.
     * All logged-in drivers receive this value through fetchGlobalSettings().
     */
    fun adminPublishUpiId(
        upiId: String,
        onComplete: (Boolean) -> Unit
    ) {
        val cleanUpi = upiId.trim()

        if (
            cleanUpi.isBlank() ||
            !prefs.userProfile.value.isAdmin
        ) {
            onComplete(false)
            return
        }

        scope.launch {
            try {

                val fs = firestore
                    ?: throw IllegalStateException(
                        "Firestore unavailable"
                    )

                fs.collection("settings")
                    .document("global")
                    .set(
                        mapOf(
                            "upiId" to cleanUpi,
                            "updatedAt" to
                                System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .await()

                prefs.updateUpiId(cleanUpi)

                kotlinx.coroutines.withContext(
                    Dispatchers.Main
                ) {
                    onComplete(true)
                }

            } catch (e: Exception) {

                Log.e(
                    "FirebaseRepo",
                    "Failed to publish admin UPI ID: ${e.message}"
                )

                kotlinx.coroutines.withContext(
                    Dispatchers.Main
                ) {
                    onComplete(false)
                }
            }
        }
    }
    // --- Realtime / Remote Plan & Settings Sync ---
    fun fetchGlobalSettings() {
        val fs = firestore ?: return

        globalSettingsListener?.remove()

        globalSettingsListener =
            fs.collection("settings")
                .document("global")
                .addSnapshotListener {
                    doc,
                    error ->

                    if (error != null) {
                        Log.w(
                            "FirebaseRepo",
                            "Global settings error: ${error.message}"
                        )
                        return@addSnapshotListener
                    }

                    if (
                        doc == null ||
                        !doc.exists()
                    ) {
                        return@addSnapshotListener
                    }

                    doc.getString("upiId")
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            prefs.updateUpiId(it)
                        }

                    doc.getString("qrImageUrl")
                        ?.let {
                            prefs.updateQrImageUrl(it)
                        }


                    // MINIMUM_VERSION_LOCK_V1
                    // Read once through the existing live global-settings listener
                    // and cache locally. No Firestore/network work is added to the
                    // Rapido/Ola/Uber real-order path.
                    val minimumVersionRaw =
                        doc.get(
                            "minimumAppVersionCode"
                        )

                    val minimumVersionCode =
                        when (
                            minimumVersionRaw
                        ) {
                            is Number ->
                                minimumVersionRaw.toInt()

                            is String ->
                                minimumVersionRaw
                                    .toIntOrNull()
                                    ?: 0

                            else ->
                                0
                        }

                    prefs.updateMinimumVersionPolicy(
                        enabled =
                            doc.getBoolean(
                                "forceUpdateEnabled"
                            ) ?: false,
                        minimumVersionCode =
                            minimumVersionCode,
                        minimumVersionName =
                            doc.getString(
                                "minimumAppVersionName"
                            ) ?: "",
                        updateUrl =
                            doc.getString(
                                "forceUpdateUrl"
                            ) ?: ""
                    )

                    val wa =
                        doc.getString("whatsapp")
                            ?: "https://chat.whatsapp.com/smartdrivo"

                    val tg =
                        doc.getString("telegram")
                            ?: "https://t.me/smartdrivo"

                    val ig =
                        doc.getString("instagram")
                            ?: "https://www.instagram.com/smartdrivo/"

                    val supportTelegram =
                        doc.getString("supportTelegram")
                            ?: "https://t.me/SmartDrivoSupport"

                    prefs.updateCommunityLinks(
                        wa,
                        tg,
                        ig,
                        supportTelegram
                    )

                    val prices =
                        doc.get("planPrices")
                            as? Map<*, *>

                    fun planPrice(
                        key: String,
                        fallback: Int
                    ): Int {
                        val raw =
                            prices?.get(key)

                        return when (raw) {
                            is Number ->
                                raw.toInt()

                            is String ->
                                raw.toIntOrNull()
                                    ?: fallback

                            else ->
                                fallback
                        }
                    }

                    prefs.updateMembershipPlanPrices(
                        price3 =
                            planPrice(
                                "plan3Days",
                                59
                            ),
                        price7 =
                            planPrice(
                                "plan7Days",
                                129
                            ),
                        price15 =
                            planPrice(
                                "plan15Days",
                                199
                            ),
                        price30 =
                            planPrice(
                                "plan1Month",
                                329
                            )
                    )

                    Log.i(
                        "FirebaseRepo",
                        "Global pricing updated live"
                    )
                }
    }


    fun writeDebugLog(message: String) {
        val uid = getCurrentUid()
        scope.launch {
            try {
                val logRef = realtimeDb?.getReference("logs/$uid")?.push()
                logRef?.setValue(
                    mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "message" to message
                    )
                )
            } catch (e: Exception) {
                // Ignore log failures
            }
        }
    }

    // --- Realtime Firestore Sync for Admin (Users & Payments) ---
    fun listenToAllUsers(onUsersChanged: ((List<UserProfile>) -> Unit)? = null): ListenerRegistration? {
        val fs = firestore ?: return null
        return try {
            fs.collection("users").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepo", "Realtime listen to /users error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<UserProfile>()
                    for (doc in snapshot.documents) {
                        try {
                            val uid = doc.id
                            val name = doc.getString("name") ?: ""
                            val email = doc.getString("email") ?: ""
                            val phone = doc.getString("phone") ?: doc.getString("mobile") ?: ""
                            val city = doc.getString("city") ?: ""
                            val state = doc.getString("state") ?: ""
                            val vehicleStr = doc.getString("vehicleType") ?: "AUTO"
                            val plan = doc.getString("plan") ?: "7DAYS"
                            val planPrice = doc.getLong("planPrice")?.toInt() ?: 129
                            val planExpireMillis = doc.getLong("planExpireMillis") ?: System.currentTimeMillis()
                            val isApproved = doc.getBoolean("isApproved") ?: false
                            val isAdmin = doc.getBoolean("isAdmin") ?: false
                            val isActive = doc.getBoolean("isActive") ?: true
                            val referralCode = doc.getString("referralCode") ?: "SMART50"
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                            list.add(
                                UserProfile(
                                    uid = uid,
                                    name = name,
                                    email = email,
                                    phone = phone,
                                    city = city,
                                    state = state,
                                    vehicleType = VehicleType.fromString(vehicleStr),
                                    plan = plan,
                                    planPrice = planPrice,
                                    planExpireMillis = planExpireMillis,
                                    isApproved = isApproved,
                                    isAdmin = isAdmin,
                                    isActive = isActive,
                                    referralCode = referralCode,
                                    createdAt = createdAt
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("FirebaseRepo", "Error parsing user doc ${doc.id}: ${e.message}")
                        }
                    }
                    prefs.setAllUsers(list)
                    onUsersChanged?.invoke(list)
                }
            }
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Failed to attach users listener: ${e.message}")
            null
        }
    }

    fun listenToAllPayments(onPaymentsChanged: ((List<PaymentSubmission>) -> Unit)? = null): ListenerRegistration? {
        val fs = firestore ?: return null
        return try {
            fs.collection("payments").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseRepo", "Realtime listen to /payments error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<PaymentSubmission>()
                    for (doc in snapshot.documents) {
                        try {
                            val paymentId = doc.getString("paymentId") ?: doc.id
                            val uid = doc.getString("uid") ?: ""
                            val userName = doc.getString("userName") ?: ""
                            val utrNumber = doc.getString("utrNumber") ?: ""
                            val planSelected = doc.getString("planSelected") ?: "7DAYS"
                            val amount = doc.getLong("amount")?.toInt() ?: 129
                            val statusStr = doc.getString("status") ?: "PENDING"
                            val status = try { PaymentStatus.valueOf(statusStr) } catch (e: Exception) { PaymentStatus.PENDING }
                            val submittedAt = doc.getLong("submittedAt") ?: System.currentTimeMillis()
                            val approvedAt = doc.getLong("approvedAt")
                            val note = doc.getString("note") ?: ""

                            list.add(
                                PaymentSubmission(
                                    paymentId = paymentId,
                                    uid = uid,
                                    userName = userName,
                                    utrNumber = utrNumber,
                                    planSelected = planSelected,
                                    amount = amount,
                                    status = status,
                                    submittedAt = submittedAt,
                                    approvedAt = approvedAt,
                                    note = note
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("FirebaseRepo", "Error parsing payment doc ${doc.id}: ${e.message}")
                        }
                    }
                    val sorted = list.sortedByDescending { it.submittedAt }
                    prefs.setPaymentSubmissions(sorted)
                    onPaymentsChanged?.invoke(sorted)
                }
            }
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "Failed to attach payments listener: ${e.message}")
            null
        }
    }

    fun adminToggleUserActiveStatus(uid: String, currentActive: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        val newActive = !currentActive
        prefs.toggleUserActiveStatus(uid)
        scope.launch {
            try {
                firestore?.collection("users")?.document(uid)?.update(
                    mapOf(
                        "isActive" to newActive,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )?.await()
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Firestore toggleUserActiveStatus failed: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun adminApprovePayment(
        submission: PaymentSubmission,
        daysToAdd: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        // Legacy callers are forced through the secure transaction.
        adminApprovePayment(submission) { success, _ ->
            onComplete?.invoke(success)
        }
    }
    fun adminRejectPayment(paymentId: String, onComplete: ((Boolean) -> Unit)? = null) {
        prefs.updatePaymentStatus(paymentId, PaymentStatus.REJECTED)
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                firestore?.collection("payments")?.document(paymentId)?.update(
                    mapOf(
                        "status" to PaymentStatus.REJECTED.name,
                        "rejectedAt" to now
                    )
                )?.await()
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Firestore adminRejectPayment failed: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun adminExtendUserPlan(uid: String, daysToAdd: Int, planLabel: String, price: Int, onComplete: ((Boolean) -> Unit)? = null) {
        prefs.extendUserPlan(uid, daysToAdd, planLabel, price)
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val fs = firestore ?: return@launch
                val userDoc = fs.collection("users").document(uid).get().await()
                val currentExpiry = userDoc.getLong("planExpireMillis") ?: 0L
                val baseTime = if (currentExpiry > now) currentExpiry else now
                val newExpiry = baseTime + (daysToAdd * 86400000L)

                fs.collection("users").document(uid).update(
                    mapOf(
                        "plan" to planLabel,
                        "planPrice" to price,
                        "planExpireMillis" to newExpiry,
                        "isApproved" to true,
                        "isActive" to true,
                        "updatedAt" to now
                    )
                ).await()
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Firestore adminExtendUserPlan failed: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }
}
