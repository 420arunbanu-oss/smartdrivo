package com.example.data.db

import android.content.Context
import android.util.Log
import com.example.data.db.dao.RideHistoryDao
import com.example.data.db.entity.RideHistoryEntity
import com.example.model.OrderStatus
import com.example.model.OrderHistoryItem
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.model.VehicleType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class RideHistoryRepository(
    private val dao: RideHistoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val allHistory: Flow<List<RideHistoryEntity>> = dao.getAllHistory()
    val latestRide: Flow<RideHistoryEntity?> = dao.getLatestRide()
    val acceptedCount: Flow<Int> = dao.getAcceptedCount()

    private val mutex = Mutex()
    private fun todayDateStr(): String =
        SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.ENGLISH
        ).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date())

    suspend fun purgePreviousDaysSafe() =
        withContext(ioDispatcher) {
            dao.deleteOtherDates(todayDateStr())
        }

    suspend fun restoreHistoryItems(
        items: List<OrderHistoryItem>
    ) = withContext(ioDispatcher) {
        items.forEach { item ->
            // Legacy import may never replace a newer real-time Room row.
            dao.insertIfAbsent(
                RideHistoryEntity.fromOrderHistoryItem(item)
            )
        }
    }
    private fun startOfTodayMillis(): Long {
        val calendar = java.util.Calendar.getInstance(
            TimeZone.getTimeZone("Asia/Kolkata")
        )

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }

    suspend fun purgePreviousDays() {
        withContext(ioDispatcher) {
            dao.deleteOtherDates(todayDateStr())
        }
    }


    /**
     * Step 1: THE MOMENT an order candidate is detected:
     * Immediately inserts a single record with status = PROCESSING into Room.
     * If a duplicate accessibility event arrives for the same booking, updates existing
     * record and increments duplicateEventsIgnored instead of creating duplicate cards.
     */
    suspend fun onOrderDetected(
        candidate: RideCandidate,
        initialReasonCode: String = "DETECTING",
        initialReasonText: String = "Evaluating ride filters..."
    ): RideHistoryEntity = withContext(ioDispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val detectedTime = if (candidate.detectionTimeMs > 0L) candidate.detectionTimeMs else now
            val fingerprint = generateFingerprint(candidate)

            // Check duplicate by bookingId (if available)
            val byBookingId = if (!candidate.bookingId.isNullOrBlank()) {
                dao.getByBookingId(candidate.bookingId)
            } else null

            // Check duplicate by fingerprint
            val byFingerprint = if (byBookingId == null) {
                dao.getByFingerprint(fingerprint)?.takeIf { previous ->
                    val oldDrop = previous.dropAddress.trim().lowercase(Locale.ROOT)
                    val newDrop = candidate.dropAddress?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    oldDrop.isBlank() || newDrop.isBlank() || oldDrop == newDrop
                }
            } else null

            // HISTORY_DEDUP_PHASE2A_V1
            // Auto Priority sometimes extracts full details on one event and
            // only destination on a second event AFTER click. Both represent
            // the same trip if they have the exact same drop, arrive close
            // together, and at least one is missing fare data.
            // This runs only during background Room processing, not click.
            val exactDrop = candidate.dropAddress
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            val validDrop = exactDrop.isNotBlank() &&
                exactDrop != "address unavailable" &&
                exactDrop != "drop location" &&
                exactDrop != "detected drop location"
            val nearbyPartialPriority =
                if (candidate.platform == Platform.RAPIDO && validDrop) {
                    dao.getRecentPlatformHistory(Platform.RAPIDO.name, now - 5_000L)
                        .firstOrNull { previous ->
                            val sameDrop = previous.dropAddress
                                .trim().lowercase(Locale.ROOT) == exactDrop
                            val compatibleBooking =
                                candidate.bookingId.isNullOrBlank() ||
                                    previous.bookingId.isBlank() ||
                                    previous.bookingId == candidate.bookingId
                            val oneIncomplete =
                                (candidate.fare ?: 0f) <= 0f || previous.fare <= 0f
                            val priorityRecord =
                                previous.decisionReasonCode.startsWith("AUTO_PRIORITY") ||
                                    (previous.status == OrderStatus.PROCESSING.name &&
                                        (now - previous.detectedAt) in 0L..1_200L)
                            sameDrop && compatibleBooking && oneIncomplete && priorityRecord
                        }
                } else null

            // RAPIDO_FINAL_HISTORY_NEAR_DUP_V1
            // Ignore/reject can be finalized before Rapido's next accessibility
            // snapshot. One-rupee fare drift must not create a second card.
            // Never merge Accepted records or distinct booking IDs.
            val nearbyFinalized = if (
                candidate.platform == Platform.RAPIDO &&
                candidate.bookingId.isNullOrBlank() && validDrop && exactDrop.length >= 12
            ) {
                dao.getRecentPlatformHistory(Platform.RAPIDO.name, now - 3_000L)
                    .firstOrNull { previous ->
                        previous.bookingId.isBlank() &&
                            previous.status in setOf(
                                OrderStatus.IGNORED.name, OrderStatus.REJECTED.name
                            ) &&
                            (now - previous.detectedAt) in 0L..3_000L &&
                            previous.dropAddress.trim().lowercase(Locale.ROOT) == exactDrop &&
                            previous.dropDistanceKm > 0f &&
                            (candidate.dropDistKm ?: 0f) > 0f &&
                            kotlin.math.abs(previous.dropDistanceKm - (candidate.dropDistKm ?: 0f)) <= 0.11f &&
                            previous.pickupDistanceKm > 0f &&
                            (candidate.pickupDistKm ?: 0f) > 0f &&
                            kotlin.math.abs(previous.pickupDistanceKm - (candidate.pickupDistKm ?: 0f)) <= 0.16f &&
                            previous.fare > 0f &&
                            (candidate.fare ?: 0f) > 0f &&
                            kotlin.math.abs(previous.fare - (candidate.fare ?: 0f)) <= 1.01f
                    }
            } else null

            val existing = byBookingId ?: nearbyFinalized ?: byFingerprint ?: nearbyPartialPriority

            if (existing != null) {
                // HISTORY_DEDUP_FINAL_V2
                val isRecent =
                    (now - existing.detectedAt) <
                        8_000L

                val sameBookingId =
                    !candidate.bookingId.isNullOrBlank() &&
                        existing.bookingId ==
                            candidate.bookingId

                // Merge repeated accessibility events only while
                // the original real ride is still PROCESSING.
                if (
                    isRecent &&
                    existing.status ==
                        OrderStatus.PROCESSING.name
                ) {
                    val merged = existing.copy(
                        fare = if ((candidate.fare ?: 0f) > 0f) (candidate.fare ?: 0f) else existing.fare,
                        baseFare = if ((candidate.fare ?: 0f) > 0f) {
                            candidate.baseFare?.takeIf { it > 0f } ?: (candidate.fare ?: 0f)
                        } else existing.baseFare,
                        tipAmount = if ((candidate.fare ?: 0f) > 0f) {
                            candidate.tipAmount ?: 0f
                        } else existing.tipAmount,
                        pickupDistanceKm = if ((candidate.pickupDistKm ?: 0f) > 0f) (candidate.pickupDistKm ?: 0f) else existing.pickupDistanceKm,
                        dropDistanceKm = if ((candidate.dropDistKm ?: 0f) > 0f) (candidate.dropDistKm ?: 0f) else existing.dropDistanceKm,
                        pickupAddress = if (!(candidate.pickupAddress.isNullOrBlank())) (candidate.pickupAddress ?: "") else existing.pickupAddress,
                        dropAddress = if (!(candidate.dropAddress.isNullOrBlank())) (candidate.dropAddress ?: "") else existing.dropAddress,
                        dropArea = if (!(candidate.dropArea.isNullOrBlank())) (candidate.dropArea ?: "") else existing.dropArea,
                        duplicateEventsIgnored = existing.duplicateEventsIgnored + 1
                    )
                    dao.update(merged)

                    Log.d(
                        "RideHistoryRepo",
                        "Merged duplicate event into active record: " +
                            "${merged.id} " +
                            "(duplicates: ${merged.duplicateEventsIgnored})"
                    )

                    return@withContext merged
                }

                // HISTORY_DEDUP_PHASE2A_V1
                // Finalized duplicate events should not create a second
                // Accepted row. Booking-ID equality remains authoritative.
                // Without a booking ID, require identical full fingerprint
                // within 3 seconds or exact partial Auto Priority match.
                val sameRecentFingerprint =
                    byFingerprint != null && byFingerprint.id == existing.id &&
                        (now - existing.detectedAt) in 0L..3_000L
                val samePartialPriority =
                    nearbyPartialPriority != null && nearbyPartialPriority.id == existing.id &&
                        (now - existing.detectedAt) in 0L..5_000L
                val sameUnmatchedSnapshot =
                    nearbyFinalized != null && nearbyFinalized.id == existing.id &&
                        (now - existing.detectedAt) in 0L..3_000L

                if (isRecent && (sameBookingId || sameRecentFingerprint || samePartialPriority || sameUnmatchedSnapshot)) {
                    // Preserve the completed status and strong data; fill
                    // missing fields if a later event recovers them.
                    val incomingFare = candidate.fare ?: 0f
                    val enriched = existing.copy(
                        bookingId = existing.bookingId.ifBlank { candidate.bookingId.orEmpty() },
                        fare = if (existing.fare > 0f) existing.fare else incomingFare,
                        baseFare = if (existing.baseFare > 0f) existing.baseFare
                            else candidate.baseFare?.takeIf { it > 0f } ?: incomingFare,
                        tipAmount = if (existing.fare > 0f) existing.tipAmount
                            else candidate.tipAmount ?: existing.tipAmount,
                        pickupDistanceKm = if (existing.pickupDistanceKm > 0f)
                            existing.pickupDistanceKm else candidate.pickupDistKm ?: 0f,
                        dropDistanceKm = if (existing.dropDistanceKm > 0f)
                            existing.dropDistanceKm else candidate.dropDistKm ?: 0f,
                        dropAddress = existing.dropAddress.ifBlank { candidate.dropAddress.orEmpty() },
                        dropArea = existing.dropArea.ifBlank { candidate.dropArea.orEmpty() },
                        duplicateEventsIgnored = existing.duplicateEventsIgnored + 1
                    )
                    dao.update(enriched)
                    Log.i("RideHistoryRepo", "Deduped finalized ride: ${enriched.id}")
                    return@withContext enriched
                }
            }

            // Create new record
            val recordId =
                if (!candidate.bookingId.isNullOrBlank()) {
                    candidate.bookingId
                } else {
                    // Fingerprint handles duplicate accessibility events.
                    // detectedTime makes a later identical real offer unique.
                    "ride_${fingerprint}_${detectedTime}"
                }

            val (dateStr, timeStr) = formatDateTime(detectedTime)
            val insertLatency = now - detectedTime

            val newEntity = RideHistoryEntity(
                id = recordId,
                bookingId = candidate.bookingId ?: "",
                bookingFingerprint = fingerprint,
                platform = candidate.platform.name,
                vehicleType = candidate.vehicleType.name,
                detectedAt = detectedTime,
                historyInsertedAt = now,
                decisionAt = 0L,
                actionAttemptAt = 0L,
                actionCompletedAt = 0L,
                fare = candidate.fare ?: 0f,
                pickupDistanceKm = candidate.pickupDistKm ?: 0f,
                dropDistanceKm = candidate.dropDistKm ?: 0f,
                pickupAddress = candidate.pickupAddress ?: "",
                dropAddress = candidate.dropAddress ?: "",
                dropArea = candidate.dropArea ?: "",
                status = OrderStatus.PROCESSING.name,
                decisionReasonCode = initialReasonCode,
                decisionReasonText = initialReasonText,
                matchedGoToGroup = "",
                matchedNoGoGroup = "",
                actionSucceeded = false,
                historyInsertLatencyMs = insertLatency.coerceAtLeast(0L),
                decisionLatencyMs = 0L,
                actionLatencyMs = 0L,
                totalProcessingMs = 0L,
                duplicateEventsIgnored = 0,
                baseFare = candidate.baseFare ?: (candidate.fare ?: 0f),
                tipAmount = candidate.tipAmount ?: 0f,
                timesClicked = 0,
                dateStr = dateStr,
                timeStr = timeStr
            )

            dao.insert(newEntity)
            Log.i("RideHistoryRepo", "Inserted detected ride: ${newEntity.id} [PROCESSING] platform=${newEntity.platform}, fare=₹${newEntity.fare}")
            newEntity
        }
    }

    /**
     * Step 2: Evaluation completed.
     * Updates the SAME record with decision result (ACCEPTED, IGNORED, REJECTED, SKIPPED).
     */
    suspend fun onOrderDecision(
        id: String,
        status: OrderStatus,
        reasonCode: String,
        reasonText: String,
        matchedGoTo: String = "",
        matchedNoGo: String = ""
    ): RideHistoryEntity? = withContext(ioDispatcher) {
        mutex.withLock {
            val existing = dao.getById(id) ?: dao.getByBookingId(id) ?: dao.getByFingerprint(id) ?: return@withContext null
            val now = System.currentTimeMillis()
            val decisionLatency = (now - existing.detectedAt).coerceAtLeast(0L)
            val totalProcessing = if (status != OrderStatus.ACCEPTED) decisionLatency else existing.totalProcessingMs

            val updated = existing.copy(
                status = status.name,
                decisionAt = now,
                decisionLatencyMs = decisionLatency,
                decisionReasonCode = reasonCode,
                decisionReasonText = reasonText,
                matchedGoToGroup = matchedGoTo,
                matchedNoGoGroup = matchedNoGo,
                totalProcessingMs = totalProcessing
            )
            dao.update(updated)
            Log.i("RideHistoryRepo", "Updated decision for ${updated.id}: ${updated.status} - $reasonCode ($reasonText)")
            updated
        }
    }

    /**
     * Step 3: Performing accept/reject action attempt.
     */
    suspend fun onOrderActionAttempt(id: String): RideHistoryEntity? = withContext(ioDispatcher) {
        mutex.withLock {
            val existing = dao.getById(id) ?: dao.getByBookingId(id) ?: dao.getByFingerprint(id) ?: return@withContext null
            val now = System.currentTimeMillis()
            val updated = existing.copy(actionAttemptAt = now)
            dao.update(updated)
            updated
        }
    }

    /**
     * Step 4: Action completed (e.g. click performed or failed).
     * Updates SAME record with final outcome, click times, and real measured latencies.
     */
    suspend fun onOrderActionCompleted(
        id: String,
        status: OrderStatus,
        reasonCode: String,
        reasonText: String,
        actionSucceeded: Boolean,
        timesClicked: Int = 1
    ): RideHistoryEntity? = withContext(ioDispatcher) {
        mutex.withLock {
            val existing = dao.getById(id) ?: dao.getByBookingId(id) ?: dao.getByFingerprint(id) ?: return@withContext null
            val now = System.currentTimeMillis()
            val attemptTime = if (existing.actionAttemptAt > 0L) existing.actionAttemptAt else if (existing.decisionAt > 0L) existing.decisionAt else existing.detectedAt
            val actionLatency = if (attemptTime > 0L) (now - attemptTime).coerceAtLeast(0L) else 0L
            val totalProcessing = (now - existing.detectedAt).coerceAtLeast(0L)

            val updated = existing.copy(
                status = status.name,
                actionCompletedAt = now,
                actionSucceeded = actionSucceeded,
                actionLatencyMs = actionLatency,
                totalProcessingMs = totalProcessing,
                decisionReasonCode = reasonCode,
                decisionReasonText = reasonText,
                timesClicked = timesClicked
            )
            dao.update(updated)
            Log.i("RideHistoryRepo", "Action completed for ${updated.id}: ${updated.status} total=${totalProcessing}ms, action=${actionLatency}ms")
            updated
        }
    }


    // PROCESSING_HISTORY_TIMEOUT_V1
    // Never allow a history row to remain PROCESSING forever.
    suspend fun finalizeAllStaleProcessing(
        timeoutMs: Long = 8_000L
    ): Int = withContext(ioDispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()

            val staleRows = dao.getAllHistoryList().filter { row ->
                row.status == OrderStatus.PROCESSING.name &&
                    (now - row.detectedAt) >= timeoutMs
            }

            var fixedCount = 0

            staleRows.forEach { stale ->

                val latest = dao.getById(stale.id)

                if (
                    latest != null &&
                    latest.status == OrderStatus.PROCESSING.name &&
                    (System.currentTimeMillis() - latest.detectedAt) >= timeoutMs
                ) {
                    val finishedAt = System.currentTimeMillis()
                    val elapsed =
                        (finishedAt - latest.detectedAt)
                            .coerceAtLeast(timeoutMs)

                    dao.update(
                        latest.copy(
                            status = OrderStatus.IGNORED.name,
                            decisionAt = finishedAt,
                            decisionLatencyMs = elapsed,
                            totalProcessingMs = elapsed,
                            decisionReasonCode = "PROCESSING_RECOVERY",
                            decisionReasonText = "Order ended before SmartDrivo received a final result"
                        )
                    )

                    fixedCount++

                    Log.w(
                        "RideHistoryRepo",
                        "PROCESSING timeout fixed: ${latest.id}"
                    )
                }
            }

            fixedCount
        }
    }
    suspend fun getById(id: String): RideHistoryEntity? = withContext(ioDispatcher) {
        dao.getById(id) ?: dao.getByBookingId(id) ?: dao.getByFingerprint(id)
    }

    suspend fun clearHistory() = withContext(ioDispatcher) {
        dao.clearAll()
    }

    suspend fun getAllHistoryDirect(): List<RideHistoryEntity> = withContext(ioDispatcher) {
        dao.getAllHistoryList()
    }

    fun generateFingerprint(candidate: RideCandidate): String {
        if (!candidate.bookingId.isNullOrBlank()) {
            return "${candidate.platform}_${candidate.bookingId}"
        }
        val fareInt = (candidate.fare ?: 0f).toInt()
        val pickup10 = ((candidate.pickupDistKm ?: 0f) * 10).toInt()
        val drop10 = ((candidate.dropDistKm ?: 0f) * 10).toInt()
        val pickupClean = (candidate.pickupAddress ?: "").take(12).replace(Regex("[^A-Za-z0-9]"), "")
        // A 12-character drop prefix collides for many Charminar offers.
        val fullDrop = candidate.dropAddress.orEmpty().trim().lowercase(Locale.ROOT)
        val dropClean = fullDrop.replace(Regex("[^a-z0-9]"), "").take(48)
        val dropSignature = fullDrop.hashCode().toUInt().toString(16)
        return "${candidate.platform}_${fareInt}_${pickup10}_${drop10}_${pickupClean}_${dropClean}_${dropSignature}"
    }

    private fun formatDateTime(timestamp: Long): Pair<String, String> {
        val date = Date(if (timestamp > 0L) timestamp else System.currentTimeMillis())
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return Pair(sdfDate.format(date), sdfTime.format(date))
    }

    companion object {
        @Volatile
        private var INSTANCE: RideHistoryRepository? = null

        fun getInstance(context: Context): RideHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RideHistoryRepository(
                    AppDatabase.getInstance(context).rideHistoryDao()
                ).also { INSTANCE = it }
            }
        }

        fun createForTest(dao: RideHistoryDao, dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): RideHistoryRepository {
            return RideHistoryRepository(dao, dispatcher)
        }
    }
}
