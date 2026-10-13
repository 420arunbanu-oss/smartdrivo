package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.RideHistoryRepository
import com.example.data.db.dao.RideHistoryDao
import com.example.data.db.entity.RideHistoryEntity
import com.example.model.OrderStatus
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.model.VehicleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RideHistoryCoreTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RideHistoryDao
    private lateinit var repository: RideHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.rideHistoryDao()
        repository = RideHistoryRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test1_detectionFirstInsertCreatesProcessingRecordImmediately() = runBlocking {
        val candidate = RideCandidate(
            platform = Platform.RAPIDO,
            vehicleType = VehicleType.AUTO,
            fare = 85f,
            pickupDistKm = 1.2f,
            dropDistKm = 5.4f,
            pickupAddress = "Indiranagar 100ft Rd",
            dropAddress = "Koramangala 5th Block",
            dropArea = "Koramangala",
            bookingId = "RAP-TEST-001",
            detectionTimeMs = System.currentTimeMillis() - 20L
        )

        val entity = repository.onOrderDetected(
            candidate = candidate,
            initialReasonCode = "PROCESSING",
            initialReasonText = "Evaluating ride filters..."
        )

        assertNotNull(entity)
        assertEquals("RAP-TEST-001", entity.bookingId)
        assertEquals(OrderStatus.PROCESSING.name, entity.status)
        assertEquals(OrderStatus.PROCESSING, entity.orderStatus)
        assertEquals("PROCESSING", entity.decisionReasonCode)
        assertEquals("Evaluating ride filters...", entity.decisionReasonText)
        assertTrue(entity.historyInsertLatencyMs >= 0L)
        assertTrue(entity.detectedAt > 0L)

        // Verify it is immediately accessible in the database
        val fromDb = repository.getById(entity.id)
        assertNotNull(fromDb)
        assertEquals(OrderStatus.PROCESSING.name, fromDb?.status)
    }

    @Test
    fun test2_decisionUpdateTransitionsProcessingToFinalStatuses() = runBlocking {
        val candidate = RideCandidate(
            platform = Platform.UBER,
            vehicleType = VehicleType.CAR,
            fare = 250f,
            pickupDistKm = 0.8f,
            dropDistKm = 8.0f,
            pickupAddress = "MG Road",
            dropAddress = "Whitefield",
            dropArea = "Whitefield",
            bookingId = "UBER-TEST-002",
            detectionTimeMs = System.currentTimeMillis() - 40L
        )

        val detected = repository.onOrderDetected(candidate, "PROCESSING", "Evaluating...")
        assertEquals(OrderStatus.PROCESSING.name, detected.status)

        // Decision: ACCEPTED
        val accepted = repository.onOrderDecision(
            id = detected.id,
            status = OrderStatus.ACCEPTED,
            reasonCode = "FILTERS_MATCHED",
            reasonText = "Fare ₹250 matched, Pickup 0.8km matched"
        )

        assertNotNull(accepted)
        assertEquals(OrderStatus.ACCEPTED.name, accepted?.status)
        assertEquals(OrderStatus.ACCEPTED, accepted?.orderStatus)
        assertEquals("FILTERS_MATCHED", accepted?.decisionReasonCode)
        assertEquals("Fare ₹250 matched, Pickup 0.8km matched", accepted?.decisionReasonText)
        assertTrue(accepted?.decisionLatencyMs ?: -1L >= 0L)

        // Test IGNORED transition on a new ride
        val candidate2 = candidate.copy(bookingId = "UBER-TEST-003", fare = 40f)
        val detected2 = repository.onOrderDetected(candidate2, "PROCESSING", "Evaluating...")
        val ignored = repository.onOrderDecision(
            id = detected2.id,
            status = OrderStatus.IGNORED,
            reasonCode = "FARE_TOO_LOW",
            reasonText = "Fare ₹40 below min ₹100"
        )
        assertNotNull(ignored)
        assertEquals(OrderStatus.IGNORED.name, ignored?.status)
        assertEquals(OrderStatus.IGNORED, ignored?.orderStatus)
        assertEquals("FARE_TOO_LOW", ignored?.decisionReasonCode)

        // Test REJECTED transition on a new ride
        val candidate3 = candidate.copy(bookingId = "UBER-TEST-004", dropAddress = "NoGo Zone")
        val detected3 = repository.onOrderDetected(candidate3, "PROCESSING", "Evaluating...")
        val rejected = repository.onOrderDecision(
            id = detected3.id,
            status = OrderStatus.REJECTED,
            reasonCode = "NOGO_MATCHED",
            reasonText = "No-Go area matched: NoGo Zone",
            matchedNoGo = "NoGo Zone"
        )
        assertNotNull(rejected)
        assertEquals(OrderStatus.REJECTED.name, rejected?.status)
        assertEquals(OrderStatus.REJECTED, rejected?.orderStatus)
        assertEquals("NOGO_MATCHED", rejected?.decisionReasonCode)
        assertEquals("NoGo Zone", rejected?.matchedNoGoGroup)
    }

    @Test
    fun test3_sameRideDetectedMultipleTimesUpdatesExistingRecordWithoutDuplicates() = runBlocking {
        val candidate = RideCandidate(
            platform = Platform.OLA,
            vehicleType = VehicleType.BIKE,
            fare = 60f,
            pickupDistKm = 1.0f,
            dropDistKm = 3.5f,
            pickupAddress = "HSR Layout Sector 1",
            dropAddress = "Bellandur Gate",
            dropArea = "Bellandur",
            bookingId = "OLA-DEDUP-001",
            detectionTimeMs = System.currentTimeMillis() - 50L
        )

        // First detection event
        val first = repository.onOrderDetected(candidate, "PROCESSING", "First detection pass")

        // Second detection event with same bookingId (e.g. accessibility event fires repeatedly)
        val updatedCandidate = candidate.copy(fare = 65f)
        val second = repository.onOrderDetected(updatedCandidate, "PROCESSING", "Second detection pass")

        assertEquals(first.id, second.id)
        assertEquals(65f, second.fare, 0.01f)

        // Ensure only ONE row exists in database
        val allHistory = repository.getAllHistoryDirect()
        assertEquals(1, allHistory.size)
        assertEquals(second.id, allHistory[0].id)
    }

    @Test
    fun test4_actionAttemptAndCompletedCorrectlySetsTimestampsAndLatency() = runBlocking {
        val now = System.currentTimeMillis()
        val candidate = RideCandidate(
            platform = Platform.RAPIDO,
            vehicleType = VehicleType.AUTO,
            fare = 120f,
            pickupDistKm = 0.5f,
            dropDistKm = 4.0f,
            pickupAddress = "BTM 2nd Stage",
            dropAddress = "Silk Board",
            dropArea = "Silk Board",
            bookingId = "RAP-ACTION-001",
            detectionTimeMs = now - 100L
        )

        val detected = repository.onOrderDetected(candidate, "PROCESSING", "Evaluating...")
        repository.onOrderDecision(detected.id, OrderStatus.ACCEPTED, "FILTERS_MATCHED", "Matched")

        // Attempt accept action
        repository.onOrderActionAttempt(detected.id)

        // Action completed
        val completed = repository.onOrderActionCompleted(
            id = detected.id,
            status = OrderStatus.ACCEPTED,
            reasonCode = "FILTERS_MATCHED",
            reasonText = "Criteria matched",
            actionSucceeded = true,
            timesClicked = 1
        )

        assertNotNull(completed)
        assertTrue(completed?.actionAttemptAt ?: 0L > 0L)
        assertTrue((completed?.actionCompletedAt ?: 0L) >= (completed?.actionAttemptAt ?: 0L))
        assertTrue((completed?.actionLatencyMs ?: -1L) >= 0L)
        assertTrue((completed?.totalProcessingMs ?: -1L) >= 0L)
        assertTrue(completed?.actionSucceeded == true)
        assertEquals(1, completed?.timesClicked)
    }

    @Test
    fun test5_responseLatencyCalculationProducesTruthfulNonNegativeNumbers() = runBlocking {
        val detectionStart = System.currentTimeMillis() - 250L
        val candidate = RideCandidate(
            platform = Platform.UBER,
            vehicleType = VehicleType.CAR,
            fare = 350f,
            pickupDistKm = 1.5f,
            dropDistKm = 12.0f,
            pickupAddress = "Airport Rd",
            dropAddress = "Electronic City",
            dropArea = "Electronic City",
            bookingId = "TRUTHFUL-LATENCY-001",
            detectionTimeMs = detectionStart
        )

        val detected = repository.onOrderDetected(candidate, "PROCESSING", "Evaluating...")
        assertTrue(detected.historyInsertLatencyMs >= 0L)

        val decision = repository.onOrderDecision(
            detected.id,
            OrderStatus.ACCEPTED,
            "FILTERS_MATCHED",
            "Matched filters"
        )
        assertNotNull(decision)
        assertTrue(decision!!.decisionLatencyMs >= 0L)
        assertTrue(decision.decisionAt >= detected.detectedAt)

        repository.onOrderActionAttempt(detected.id)
        val finalRecord = repository.onOrderActionCompleted(
            detected.id,
            OrderStatus.ACCEPTED,
            "FILTERS_MATCHED",
            "Matched filters",
            actionSucceeded = true,
            timesClicked = 1
        )

        assertNotNull(finalRecord)
        assertTrue(finalRecord!!.actionLatencyMs >= 0L)
        assertTrue(finalRecord.totalProcessingMs >= 0L)
        assertTrue(finalRecord.totalProcessingMs >= finalRecord.decisionLatencyMs)
    }

    @Test
    fun test6_orderHistoryConversionDisplaysAllStatusesCorrectly() = runBlocking {
        val statuses = listOf(
            OrderStatus.PROCESSING,
            OrderStatus.ACCEPTED,
            OrderStatus.REJECTED,
            OrderStatus.IGNORED,
            OrderStatus.FAILED,
            OrderStatus.SKIPPED,
            OrderStatus.MISSED
        )

        statuses.forEachIndexed { index, status ->
            val entity = RideHistoryEntity(
                id = "status-test-$index",
                bookingId = "BK-$index",
                platform = Platform.RAPIDO.name,
                vehicleType = VehicleType.AUTO.name,
                status = status.name,
                decisionReasonCode = status.name,
                decisionReasonText = "Reason for ${status.name}",
                fare = (index + 1) * 50f,
                pickupDistanceKm = 1.0f,
                dropDistanceKm = 4.0f,
                pickupAddress = "Pickup $index",
                dropAddress = "Drop $index",
                dropArea = "Area $index",
                detectedAt = System.currentTimeMillis() - 100L,
                historyInsertedAt = System.currentTimeMillis() - 90L,
                decisionAt = System.currentTimeMillis() - 50L,
                actionAttemptAt = System.currentTimeMillis() - 40L,
                actionCompletedAt = System.currentTimeMillis() - 10L,
                historyInsertLatencyMs = 10L,
                decisionLatencyMs = 40L,
                actionLatencyMs = 30L,
                totalProcessingMs = 90L,
                actionSucceeded = status == OrderStatus.ACCEPTED,
                timesClicked = 1
            )

            val item = entity.toOrderHistoryItem()
            assertEquals(status, item.status)
            assertEquals("Reason for ${status.name}", item.reason)
            assertEquals((index + 1) * 50f, item.amount, 0.01f)
            assertEquals(10L, item.historyInsertLatencyMs)
            assertEquals(40L, item.decisionLatencyMs)
            assertEquals(30L, item.actionLatencyMs)
            assertEquals(90L, item.totalProcessingMs)
        }
    }

    @Test
    fun test7_clearHistoryRemovesAllRecordsCleanly() = runBlocking {
        // Insert 3 records
        for (i in 1..3) {
            val candidate = RideCandidate(
                platform = Platform.OLA,
                vehicleType = VehicleType.AUTO,
                fare = 100f * i,
                pickupDistKm = 1f,
                dropDistKm = 5f,
                pickupAddress = "Pickup $i",
                dropAddress = "Drop $i",
                dropArea = "Area $i",
                bookingId = "CLEAR-TEST-$i"
            )
            repository.onOrderDetected(candidate, "PROCESSING", "Evaluating...")
        }

        assertEquals(3, repository.getAllHistoryDirect().size)

        // Clear history
        repository.clearHistory()

        // Database and flow should be empty
        assertEquals(0, repository.getAllHistoryDirect().size)
        val flowItems = repository.allHistory.first()
        assertTrue(flowItems.isEmpty())
    }

    @Test
    fun test8_sameFingerprintOneSecondAfterFinalDedupes() = runBlocking {
        val baseTime =
            System.currentTimeMillis()

        val firstCandidate = RideCandidate(
            platform = Platform.RAPIDO,
            vehicleType = VehicleType.AUTO,
            fare = 75f,
            pickupDistKm = 0.5f,
            dropDistKm = 3.0f,
            pickupAddress = "Same Pickup",
            dropAddress = "Same Drop",
            dropArea = "Same Area",
            bookingId = null,
            detectionTimeMs = baseTime
        )

        val first =
            repository.onOrderDetected(
                firstCandidate,
                "PROCESSING",
                "Evaluating..."
            )

        repository.onOrderDecision(
            first.id,
            OrderStatus.ACCEPTED,
            "FILTERS_MATCHED",
            "First accepted"
        )

        val second =
            repository.onOrderDetected(
                firstCandidate.copy(
                    detectionTimeMs =
                        baseTime + 1_000L
                ),
                "PROCESSING",
                "Evaluating..."
            )

        assertEquals(first.id, second.id)
        assertEquals(
            1,
            repository.getAllHistoryDirect().size
        )
    }

    @Test
    fun test8b_sameFingerprintAfterTenSecondsCreatesNewHistoryRow() = runBlocking {
        val previousTime = System.currentTimeMillis() - 10_000L
        val previous = RideCandidate(
            platform = Platform.RAPIDO,
            vehicleType = VehicleType.AUTO,
            fare = 75f,
            pickupDistKm = 0.5f,
            dropDistKm = 3.0f,
            pickupAddress = "Same Pickup",
            dropAddress = "Same Drop",
            dropArea = "Same Area",
            bookingId = null,
            detectionTimeMs = previousTime
        )
        val first = repository.onOrderDetected(previous, "PROCESSING", "Evaluating...")
        repository.onOrderDecision(
            first.id, OrderStatus.ACCEPTED, "FILTERS_MATCHED", "First accepted"
        )
        val next = repository.onOrderDetected(
            previous.copy(detectionTimeMs = System.currentTimeMillis()),
            "PROCESSING", "Evaluating..."
        )
        assertTrue(first.id != next.id)
        assertEquals(2, repository.getAllHistoryDirect().size)
    }

    @Test
    fun test9_sameBookingIdAfterFinalKeepsFinalRow() = runBlocking {
        val candidate = RideCandidate(
            platform = Platform.RAPIDO,
            vehicleType = VehicleType.AUTO,
            fare = 95f,
            pickupDistKm = 0.4f,
            dropDistKm = 2.5f,
            pickupAddress = "Pickup",
            dropAddress = "Drop",
            dropArea = "Area",
            bookingId = "RAP-FINAL-KEEP-001",
            detectionTimeMs =
                System.currentTimeMillis()
        )

        val first =
            repository.onOrderDetected(
                candidate,
                "PROCESSING",
                "Evaluating..."
            )

        repository.onOrderDecision(
            first.id,
            OrderStatus.ACCEPTED,
            "FILTERS_MATCHED",
            "Accepted"
        )

        val duplicate =
            repository.onOrderDetected(
                candidate.copy(
                    detectionTimeMs =
                        candidate.detectionTimeMs +
                            500L
                ),
                "PROCESSING",
                "Duplicate event"
            )

        assertEquals(first.id, duplicate.id)
        assertEquals(
            OrderStatus.ACCEPTED.name,
            duplicate.status
        )
        assertEquals(
            1,
            repository.getAllHistoryDirect().size
        )
    }
}
