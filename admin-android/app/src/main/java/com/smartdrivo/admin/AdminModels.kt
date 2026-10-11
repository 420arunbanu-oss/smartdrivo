package com.smartdrivo.admin

import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mirrors the existing SmartDrivo web-admin Firestore document schema. */
data class Driver(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val vehicleType: String,
    val plan: String,
    val expiryMillis: Long,
    val isActive: Boolean,
    val isAdmin: Boolean,
    val isBlocked: Boolean,
    val device: String
) {
    companion object {
        fun from(document: DocumentSnapshot) = Driver(
            uid = document.id,
            name = document.getString("name") ?: "Unnamed Driver",
            email = document.getString("email") ?: "",
            phone = document.getString("phone") ?: document.getString("mobile") ?: "",
            vehicleType = document.getString("vehicleType") ?: "AUTO",
            plan = document.getString("plan") ?: "NONE",
            expiryMillis = (document.get("planExpireMillis") as? Number)?.toLong() ?: 0L,
            isActive = document.getBoolean("isActive") != false,
            isAdmin = document.getBoolean("isAdmin") == true,
            isBlocked = document.getBoolean("isBlocked") == true,
            device = document.getString("registeredDevice") ?: document.getString("lastDevice") ?: ""
        )
    }
}

data class Payment(
    val paymentId: String,
    val uid: String,
    val plan: String,
    val amount: Double,
    val utr: String,
    val status: String,
    val submittedAt: Long
) {
    companion object {
        fun from(document: DocumentSnapshot) = Payment(
            paymentId = document.id,
            uid = document.getString("uid") ?: "",
            plan = document.getString("planSelected") ?: "7DAYS",
            amount = (document.get("amount") as? Number)?.toDouble() ?: 0.0,
            utr = document.getString("utrNumber") ?: "",
            status = (document.getString("status") ?: "PENDING").uppercase(Locale.ROOT),
            submittedAt = (document.get("submittedAt") as? Number)?.toLong()
                ?: (document.get("createdAt") as? Number)?.toLong() ?: 0L
        )
    }
}

data class AdminSettings(
    val upiId: String = "",
    val beneficiaryName: String = "SmartDrivo",
    val prices: Map<String, Long> = emptyMap()
)

object AdminValidation {
    fun isValidUtr(utr: String): Boolean = Regex("^[0-9]{12}$").matches(utr)

    fun planDays(plan: String): Int = when (plan.uppercase(Locale.ROOT)) {
        "3DAYS" -> 3
        "7DAYS" -> 7
        "15DAYS" -> 15
        "1MONTH", "30DAYS" -> 30
        else -> throw IllegalArgumentException("Unknown membership plan: $plan")
    }

    fun membershipExpiry(nowMillis: Long, oldExpiryMillis: Long, plan: String): Long {
        val base = maxOf(nowMillis, oldExpiryMillis)
        val delta = Math.multiplyExact(planDays(plan).toLong(), 86_400_000L)
        return Math.addExact(base, delta)
    }

    fun isAuthorizedAdmin(isAdmin: Boolean?, isActive: Boolean?): Boolean =
        isAdmin == true && isActive == true
}

fun formatTime(millis: Long): String = if (millis <= 0L) "—" else
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(millis))

fun formatMoney(amount: Double): String = "₹${"%.0f".format(Locale.ENGLISH, amount)}"
