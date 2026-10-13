package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One independent, strict AND rule. Lists of enabled rules are combined by OR. */
data class UnlimitedFilterRule(
    val id: String,
    val enabled: Boolean,
    val minimum: Float,
    val maxPickupKm: Float,
    val maxDropKm: Float
) {
    fun isValid(): Boolean =
        minimum.isFinite() && minimum > 0f &&
        maxPickupKm.isFinite() && maxPickupKm > 0f &&
        maxDropKm.isFinite() && maxDropKm > 0f

    fun matches(amount: Float?, pickup: Float?, drop: Float?): Boolean =
        enabled && isValid() &&
        amount != null && amount.isFinite() && amount >= minimum &&
        pickup != null && pickup.isFinite() && pickup >= 0f && pickup <= maxPickupKm &&
        drop != null && drop.isFinite() && drop >= 0f && drop <= maxDropKm

    companion object {
        @Volatile private var cachedRaw: String? = null
        @Volatile private var cachedRules: List<UnlimitedFilterRule> = emptyList()

        fun newRule(plus: Boolean): UnlimitedFilterRule =
            UnlimitedFilterRule(
                id = UUID.randomUUID().toString(),
                enabled = false,
                minimum = if (plus) 10f else 50f,
                maxPickupKm = 3f,
                maxDropKm = 7.5f
            )

        fun encode(rules: List<UnlimitedFilterRule>): String {
            val array = JSONArray()
            rules.forEach { r ->
                array.put(JSONObject().apply {
                    put("id", r.id)
                    put("enabled", r.enabled)
                    put("minimum", r.minimum.toDouble())
                    put("pickup", r.maxPickupKm.toDouble())
                    put("drop", r.maxDropKm.toDouble())
                })
            }
            return array.toString()
        }

        fun decode(raw: String?): List<UnlimitedFilterRule> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isBlank()) continue
                        val rule = UnlimitedFilterRule(
                            id = id,
                            enabled = obj.optBoolean("enabled", false),
                            minimum = obj.optDouble("minimum", Double.NaN).toFloat(),
                            maxPickupKm = obj.optDouble("pickup", Double.NaN).toFloat(),
                            maxDropKm = obj.optDouble("drop", Double.NaN).toFloat()
                        )
                        if (rule.isValid()) add(rule)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        // Constant-time reuse on repeated order events with unchanged prefs.
        @Synchronized
        fun decodeCached(raw: String?): List<UnlimitedFilterRule> {
            if (raw == cachedRaw) return cachedRules
            val value = decode(raw)
            cachedRaw = raw
            cachedRules = value
            return value
        }
    }
}