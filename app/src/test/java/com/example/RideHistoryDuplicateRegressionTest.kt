package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.RideHistoryRepository
import com.example.model.OrderStatus
import com.example.model.Platform
import com.example.model.RideCandidate
import com.example.model.VehicleType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RideHistoryDuplicateRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var history: RideHistoryRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        history = RideHistoryRepository(db.rideHistoryDao())
    }

    @After fun tearDown() { db.close() }

    private fun order(fare: Float?, drop: String = "Kachiguda", booking: String? = null) =
        RideCandidate(
            platform = Platform.RAPIDO,
            vehicleType = VehicleType.AUTO,
            fare = fare,
            baseFare = fare,
            pickupDistKm = if (fare == null) null else 0.5f,
            dropDistKm = if (fare == null) null else 7.4f,
            pickupAddress = "Address unavailable",
            dropAddress = drop,
            dropArea = drop,
            bookingId = booking,
            detectionTimeMs = System.currentTimeMillis()
        )

    private suspend fun accept(candidate: RideCandidate, reason: String = "AUTO_PRIORITY_INSTANT") =
        history.onOrderDetected(candidate).also {
            history.onOrderActionCompleted(
                id = it.id,
                status = OrderStatus.ACCEPTED,
                reasonCode = reason,
                reasonText = "Accepted",
                actionSucceeded = true
            )
        }

    @Test fun finalizedIdenticalRapidoEventDoesNotCreateSecondHistoryRow() = runBlocking {
        val first = accept(order(70f), "FILTERS_MATCHED")
        val again = history.onOrderDetected(order(70f))
        assertEquals(first.id, again.id)
        assertEquals(1, history.getAllHistoryDirect().size)
    }

    @Test fun fullThenEmptyPrioritySameDropDoesNotDuplicate() = runBlocking {
        val first = accept(order(164f))
        val again = history.onOrderDetected(order(null))
        assertEquals(first.id, again.id)
        assertEquals(164f, again.fare, 0.01f)
        assertEquals(1, history.getAllHistoryDirect().size)
    }

    @Test fun emptyThenFullPriorityEnrichesSameAcceptedRow() = runBlocking {
        val first = accept(order(null))
        val enriched = history.onOrderDetected(order(164f))
        assertEquals(first.id, enriched.id)
        assertEquals(OrderStatus.ACCEPTED.name, enriched.status)
        assertEquals(164f, enriched.fare, 0.01f)
        assertEquals(7.4f, enriched.dropDistanceKm, 0.01f)
        assertEquals(1, history.getAllHistoryDirect().size)
    }

    @Test fun rejectedOrIgnoredSmallFareDriftSameDropHasOneRow() = runBlocking {
        val address = "Milan Function Hall - Limra Mission School, Shah Gunj, Hyderabad"
        val one = order(128f, address).copy(
            baseFare = 109f, tipAmount = 19f,
            pickupDistKm = 0.9f, dropDistKm = 3.7f
        )
        val first = history.onOrderDetected(one)
        history.onOrderDecision(
            first.id, OrderStatus.IGNORED, "CRITERIA_NOT_MET", "No enabled filter matched"
        )
        val two = order(129f, address).copy(
            baseFare = 75f, tipAmount = 54f,
            pickupDistKm = 0.9f, dropDistKm = 3.7f
        )
        val again = history.onOrderDetected(two)
        assertEquals(first.id, again.id)
        assertEquals(1, history.getAllHistoryDirect().size)
        // Keep original strong fare/base/tip instead of mixing multiple snapshots.
        assertEquals(128f, again.fare, 0.01f)
        assertEquals(109f, again.baseFare, 0.01f)
        assertEquals(19f, again.tipAmount, 0.01f)
    }

    @Test fun newDifferentDropIsNeverCollapsed() = runBlocking {
        accept(order(70f, "Kachiguda"))
        history.onOrderDetected(order(70f, "Charminar"))
        assertEquals(2, history.getAllHistoryDirect().size)
    }

    @Test fun differentValidFareSameDropIsNotPartialPriorityDuplicate() = runBlocking {
        accept(order(70f))
        history.onOrderDetected(order(71f))
        assertEquals(2, history.getAllHistoryDirect().size)
    }
}