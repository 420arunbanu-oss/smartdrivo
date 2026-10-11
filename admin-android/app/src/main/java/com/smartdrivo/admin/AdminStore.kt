package com.smartdrivo.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.util.Locale

/**
 * This client NEVER grants permissions. Server-side Firestore Rules must enforce
 * isAdmin == true AND isActive == true (see proposed rules hardening notes).
 * No credentials, driver snapshots or UTRs are written to app-owned storage.
 */
class AdminStore {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()
    }
    private var roleListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    var access by mutableStateOf("CHECKING")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    val drivers = mutableStateListOf<Driver>()
    val payments = mutableStateListOf<Payment>()
    var settings by mutableStateOf(AdminSettings())
        private set

    init {
        if (auth.currentUser == null) access = "SIGNED_OUT" else verifySession()
    }

    fun dismissMessage() { message = "" }

    private fun error(error: Exception) {
        busy = false
        message = error.localizedMessage ?: "Operation failed. Check your connection."
    }

    fun loginWithEmail(emailAddress: String, password: String) {
        if (emailAddress.isBlank() || password.isBlank()) {
            message = "Enter your admin email and password."; return
        }
        busy = true
        auth.signInWithEmailAndPassword(emailAddress.trim(), password)
            .addOnSuccessListener { busy = false; verifySession() }
            .addOnFailureListener(::error)
    }

    fun loginWithGoogleIdToken(idToken: String) {
        busy = true
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { busy = false; verifySession() }
            .addOnFailureListener(::error)
    }

    fun resetPassword(emailAddress: String) {
        if (emailAddress.isBlank()) { message = "Enter your email first."; return }
        busy = true
        auth.sendPasswordResetEmail(emailAddress.trim())
            .addOnSuccessListener { busy = false; message = "Password reset email sent if account is eligible." }
            .addOnFailureListener(::error)
    }

    fun verifySession() {
        val caller = auth.currentUser ?: run { access = "SIGNED_OUT"; return }
        access = "CHECKING"
        busy = true
        db.collection("users").document(caller.uid).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                busy = false
                if (!AdminValidation.isAuthorizedAdmin(
                        snapshot.getBoolean("isAdmin"), snapshot.getBoolean("isActive")
                    )) {
                    signOut("Admin access denied. isAdmin and isActive must both be true.")
                } else {
                    access = "ADMIN"
                    email = caller.email.orEmpty()
                    attachListeners(caller.uid)
                }
            }
            .addOnFailureListener {
                // Do not authorize from potentially stale local cache.
                signOut("Admin verification failed. Please reconnect and log in.")
            }
    }

    private fun attachListeners(uid: String) {
        removeListeners()
        roleListener = db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null ||
                !AdminValidation.isAuthorizedAdmin(
                    snapshot.getBoolean("isAdmin"), snapshot.getBoolean("isActive")
                )
            ) {
                signOut("Admin permissions changed or connection unavailable.")
            }
        }
        usersListener = db.collection("users").limit(200).addSnapshotListener { snapshots, e ->
            if (e != null) { message = "Drivers sync failed: ${e.localizedMessage}"; return@addSnapshotListener }
            drivers.clear()
            drivers.addAll(snapshots?.documents.orEmpty().map(Driver::from).sortedBy { it.name.lowercase() })
        }
        paymentsListener = db.collection("payments").limit(200).addSnapshotListener { snapshots, e ->
            if (e != null) { message = "Payments sync failed: ${e.localizedMessage}"; return@addSnapshotListener }
            payments.clear()
            payments.addAll(snapshots?.documents.orEmpty().map(Payment::from)
                .sortedByDescending { it.submittedAt })
        }
        settingsListener = db.collection("settings").document("global")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { message = "Settings sync failed: ${e.localizedMessage}"; return@addSnapshotListener }
                val prices = (snapshot?.get("planPrices") as? Map<*, *>)
                    ?.entries?.mapNotNull { (k, v) ->
                        val number = (v as? Number)?.toLong() ?: v?.toString()?.toLongOrNull()
                        if (k is String && number != null) k to number else null
                    }?.toMap().orEmpty()
                settings = AdminSettings(
                    upiId = snapshot?.getString("upiId").orEmpty(),
                    beneficiaryName = snapshot?.getString("beneficiaryName") ?: "SmartDrivo",
                    prices = prices
                )
            }
    }

    private fun removeListeners() {
        roleListener?.remove(); roleListener = null
        usersListener?.remove(); usersListener = null
        paymentsListener?.remove(); paymentsListener = null
        settingsListener?.remove(); settingsListener = null
    }

    fun signOut(reason: String = "") {
        removeListeners()
        auth.signOut()
        drivers.clear(); payments.clear()
        settings = AdminSettings()
        access = "SIGNED_OUT"
        busy = false
        email = ""
        if (reason.isNotBlank()) message = reason
    }

    /** Admin is verified fresh from SERVER before every sensitive operation. */
    private fun withFreshAdmin(work: (String, DocumentReference) -> Unit) {
        val caller = auth.currentUser ?: run { signOut(); return }
        if (access != "ADMIN") { message = "Log in again as an admin."; return }
        busy = true
        val adminRef = db.collection("users").document(caller.uid)
        adminRef.get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (!AdminValidation.isAuthorizedAdmin(
                        doc.getBoolean("isAdmin"), doc.getBoolean("isActive")
                    )) signOut("Admin permissions revoked.")
                else work(caller.uid, adminRef)
            }
            .addOnFailureListener { error(it) }
    }

    private fun audit(actor: String, action: String, target: String) = mapOf(
        "actorUid" to actor,
        "action" to action,
        "target" to target,
        "at" to FieldValue.serverTimestamp()
    )

    fun setDriverActive(driver: Driver, active: Boolean) = withFreshAdmin { actor, adminRef ->
        if (driver.isAdmin || driver.uid == actor) {
            busy = false; message = "Admin accounts cannot be changed here."; return@withFreshAdmin
        }
        val targetRef = db.collection("users").document(driver.uid)
        val auditRef = db.collection("logs").document()
        db.runTransaction { tx ->
            val adminDoc = tx.get(adminRef)
            val doc = tx.get(targetRef)
            check(AdminValidation.isAuthorizedAdmin(adminDoc.getBoolean("isAdmin"), adminDoc.getBoolean("isActive")))
            check(doc.exists() && doc.getBoolean("isAdmin") != true && driver.uid != actor)
            tx.update(targetRef, mapOf("isActive" to active, "updatedAt" to System.currentTimeMillis()))
            tx.set(auditRef, audit(actor, if (active) "DRIVER_ACTIVATE" else "DRIVER_DEACTIVATE", driver.uid))
            true
        }.addOnSuccessListener { busy = false; message = "Driver status updated." }
            .addOnFailureListener(::error)
    }

    fun rejectPayment(payment: Payment) = withFreshAdmin { actor, adminRef ->
        val ref = db.collection("payments").document(payment.paymentId)
        val log = db.collection("logs").document()
        db.runTransaction { tx ->
            val a = tx.get(adminRef)
            val p = tx.get(ref)
            check(AdminValidation.isAuthorizedAdmin(a.getBoolean("isAdmin"), a.getBoolean("isActive")))
            check(p.exists() && p.getString("status")?.uppercase(Locale.ROOT) == "PENDING") {
                "Only pending payments can be rejected"
            }
            tx.update(ref, mapOf(
                "status" to "REJECTED", "rejectedAt" to System.currentTimeMillis(), "rejectedBy" to actor
            ))
            tx.set(log, audit(actor, "PAYMENT_REJECT", payment.paymentId))
            true
        }.addOnSuccessListener { busy = false; message = "Payment rejected." }
            .addOnFailureListener(::error)
    }

    /** Exactly matches web-admin UTR lock and membership extension semantics. */
    fun approvePayment(payment: Payment) = withFreshAdmin { actor, adminRef ->
        if (!AdminValidation.isValidUtr(payment.utr)) {
            busy = false; message = "Invalid 12-digit UTR."; return@withFreshAdmin
        }
        // Legacy APPROVED payments may predate permanent paymentUtrs reservations.
        // This query and the transaction below both need network access.
        db.collection("payments").whereEqualTo("utrNumber", payment.utr).get(Source.SERVER)
            .addOnSuccessListener { rows ->
                val alreadyUsed = rows.documents.any { row ->
                    row.id != payment.paymentId &&
                        row.getString("status")?.uppercase(Locale.ROOT) == "APPROVED"
                }
                if (alreadyUsed) {
                    busy = false; message = "This UTR has already been approved."; return@addOnSuccessListener
                }
                val paymentRef = db.collection("payments").document(payment.paymentId)
                val utrRef = db.collection("paymentUtrs").document(payment.utr)
                val auditRef = db.collection("logs").document()
                db.runTransaction { tx ->
                    // All document reads MUST happen before transaction writes.
                    val a = tx.get(adminRef)
                    val p = tx.get(paymentRef)
                    val lock = tx.get(utrRef)
                    check(AdminValidation.isAuthorizedAdmin(a.getBoolean("isAdmin"), a.getBoolean("isActive")))
                    check(p.exists()) { "Payment not found" }
                    check(p.getString("status")?.uppercase(Locale.ROOT) == "PENDING") { "Not pending" }
                    check(p.getString("utrNumber") == payment.utr) { "UTR changed" }
                    check(!lock.exists()) { "UTR already approved" }
                    val uid = p.getString("uid")?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Missing driver UID")
                    val userRef = db.collection("users").document(uid)
                    val user = tx.get(userRef)
                    check(user.exists()) { "Driver account missing" }
                    val storedPlan = p.getString("planSelected") ?: "7DAYS"
                    val amount = (p.get("amount") as? Number)?.toDouble() ?: 0.0
                    check(amount > 0.0) { "Payment amount missing" }
                    val oldExpiry = (user.get("planExpireMillis") as? Number)?.toLong() ?: 0L
                    val now = System.currentTimeMillis()
                    val expires = AdminValidation.membershipExpiry(now, oldExpiry, storedPlan)
                    tx.update(paymentRef, mapOf("status" to "APPROVED", "approvedAt" to now, "approvedBy" to actor))
                    tx.update(userRef, mapOf(
                        "plan" to storedPlan.uppercase(Locale.ROOT),
                        "planPrice" to amount,
                        "planExpireMillis" to expires,
                        "isApproved" to true,
                        "isActive" to true,
                        "updatedAt" to now
                    ))
                    tx.set(utrRef, mapOf(
                        "utrNumber" to payment.utr,
                        "paymentId" to payment.paymentId,
                        "uid" to uid,
                        "approvedBy" to actor,
                        "approvedAt" to now
                    ))
                    tx.set(auditRef, audit(actor, "PAYMENT_APPROVE", payment.paymentId))
                    true
                }.addOnSuccessListener { busy = false; message = "Payment approved; membership updated." }
                    .addOnFailureListener(::error)
            }.addOnFailureListener(::error)
    }

    fun saveSettings(upi: String, prices: Map<String, Long>) = withFreshAdmin { actor, adminRef ->
        val clean = upi.trim()
        if (clean.isBlank() || !clean.matches(Regex("^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$"))) {
            busy = false; message = "Enter a valid UPI ID."; return@withFreshAdmin
        }
        if (prices.size != 4 || prices.values.any { it < 1 || it > 100_000 }) {
            busy = false; message = "Plan prices must be between ₹1 and ₹100000."; return@withFreshAdmin
        }
        // A transaction also re-checks active admin state before any write.
        val ref = db.collection("settings").document("global")
        val log = db.collection("logs").document()
        db.runTransaction { tx ->
            val adminDoc = tx.get(adminRef)
            check(AdminValidation.isAuthorizedAdmin(adminDoc.getBoolean("isAdmin"), adminDoc.getBoolean("isActive")))
            tx.set(ref, mapOf(
                "upiId" to clean, "beneficiaryName" to "SmartDrivo",
                "planPrices" to prices,
                "updatedAt" to System.currentTimeMillis(), "updatedBy" to actor
            ), SetOptions.merge())
            tx.set(log, audit(actor, "SETTINGS_UPDATE", "global"))
            true
        }.addOnSuccessListener { busy = false; message = "UPI and plan prices saved." }
            .addOnFailureListener(::error)
    }
}
