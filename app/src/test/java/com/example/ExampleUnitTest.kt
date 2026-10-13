package com.example

import com.example.engine.AreaRulesEngine
import com.example.engine.DecisionResult
import com.example.engine.OlaAdapter
import com.example.engine.RapidoAdapter
import com.example.engine.UberAdapter
import com.example.model.AreaGroup
import com.example.model.AppSettings
import com.example.model.FilterMode
import com.example.model.Platform
import com.example.model.RideCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testRapidoPackageDetection() {
        assertTrue(RapidoAdapter.isRapidoPackage("com.rapido.captain"))
        assertTrue(RapidoAdapter.isRapidoPackage("com.rapido.rider"))
        assertTrue(RapidoAdapter.isRapidoPackage("com.rapido.passenger"))
        assertTrue(Platform.isRapidoPackage("com.rapido.captain"))
        assertEquals(Platform.RAPIDO, Platform.fromPackage("com.rapido.captain"))
        assertEquals(Platform.RAPIDO, Platform.fromPackage("com.rapido.rider"))
        assertEquals(Platform.RAPIDO, Platform.fromPackage("com.rapido.passenger"))
    }

    @Test
    fun testRapidoAcceptTextMatching() {
        assertTrue(RapidoAdapter.isAcceptText("Accept"))
        assertTrue(RapidoAdapter.isAcceptText("ACCEPT"))
        assertTrue(RapidoAdapter.isAcceptText("Accept Ride"))
        assertTrue(RapidoAdapter.isAcceptText("Accept Order"))
        assertTrue(RapidoAdapter.isAcceptText("स्वीकार करें"))
        assertTrue(RapidoAdapter.isAcceptText("स्वीकार"))
        assertTrue(RapidoAdapter.isAcceptText("Tap to Accept"))
        assertTrue(RapidoAdapter.isAcceptText("Swipe to Accept"))
        assertFalse(RapidoAdapter.isAcceptText("Don't Accept"))
    }

    @Test
    fun testRapidoContentDescriptionMatching() {
        assertTrue(RapidoAdapter.isAcceptDescription("Accept ride"))
        assertTrue(RapidoAdapter.isAcceptDescription("Accept order"))
        assertTrue(RapidoAdapter.isAcceptDescription("Accept"))
        assertTrue(RapidoAdapter.isAcceptDescription("स्वीकार करें"))
    }

    @Test
    fun testOlaAndUberAcceptTextMatching() {
        assertTrue(OlaAdapter.isAcceptText("Accept"))
        assertTrue(OlaAdapter.isAcceptText("ACCEPT"))
        assertTrue(OlaAdapter.isAcceptText("Accept Ride"))
        assertTrue(OlaAdapter.isAcceptText("Accept Order"))
        assertTrue(OlaAdapter.isAcceptText("स्वीकार करें"))

        assertTrue(UberAdapter.isAcceptText("Accept"))
        assertTrue(UberAdapter.isAcceptText("ACCEPT"))
        assertTrue(UberAdapter.isAcceptText("Confirm"))
        assertTrue(UberAdapter.isAcceptText("Match"))
        assertTrue(UberAdapter.isAcceptText("Accept Trip"))
        assertTrue(UberAdapter.isAcceptText("स्वीकार करें"))
    }

    @Test
    fun testRapidoFareParsingMultipleCards() {
        // Current fare policy:
        // when multiple valid fare cards are visible, use the highest verified
        // total so SmartDrivo does not under-read the active offer.
        val multipleCardsSingleString = listOf("₹51 +₹16", "₹84 +₹33")
        val data1 = RapidoAdapter.extractOrderDataFromTexts(multipleCardsSingleString)
        assertEquals(84f, data1.baseFare, 0.01f)
        assertEquals(33f, data1.tipAmount, 0.01f)
        assertEquals(117f, data1.totalFare, 0.01f)

        // Same behavior when base/tip values are exposed as separate nodes.
        val multipleCardsSeparateStrings = listOf("₹51", "+₹16", "₹84", "+₹33")
        val data2 = RapidoAdapter.extractOrderDataFromTexts(multipleCardsSeparateStrings)
        assertEquals(84f, data2.baseFare, 0.01f)
        assertEquals(33f, data2.tipAmount, 0.01f)
        assertEquals(117f, data2.totalFare, 0.01f)

        // Test base fare only
        val singleBaseFare = listOf("Auto", "₹73", "1.2 km", "Koramangala")
        val data3 = RapidoAdapter.extractOrderDataFromTexts(singleBaseFare)
        assertEquals(73f, data3.baseFare, 0.01f)
        assertEquals(0f, data3.tipAmount, 0.01f)
        assertEquals(73f, data3.totalFare, 0.01f)

        // Test format "₹83 +₹26"
        val data4 = RapidoAdapter.extractOrderDataFromTexts(listOf("₹83 +₹26"))
        assertEquals(83f, data4.baseFare, 0.01f)
        assertEquals(26f, data4.tipAmount, 0.01f)
        assertEquals(109f, data4.totalFare, 0.01f)
    }

    @Test
    fun testRapidoAcceptResourceIdsList() {
        val expected = listOf(
            "com.rapido.rider:id/accept_order",
            "com.rapido.rider:id/accept_button",
            "com.rapido.rider:id/acceptOrderBtn",
            "com.rapido.rider:id/btn_accept_ride",
            "com.rapido.rider:id/accept_ride_button",
            "com.rapido.rider:id/btn_accept",
            "com.rapido.rider:id/cta_accept",
            "com.rapido.rider:id/layout_accept",
            "com.rapido.rider:id/tv_accept",
            "com.rapido.rider:id/action_accept"
        )
        assertEquals(expected, RapidoAdapter.RAPIDO_ACCEPT_RESOURCE_IDS)
    }

    @Test
    fun testAreaRulesEngineEmptyGoToAndNoGoDoesNotReject() {
        val candidate = com.example.model.RideCandidate(
            fare = 150f,
            pickupDistKm = 1.5f,
            dropDistKm = 5.0f,
            pickupAddress = "Indiranagar 100ft Road",
            dropAddress = "Whitefield Main Road",
            dropArea = "Whitefield"
        )
        val settings = com.example.model.AppSettings(
            minFare = 50f,
            maxFare = 500f,
            maxPickupDistanceKm = 5f,
            maxDropDistanceKm = 15f,
            isGoToEnabled = false,
            isNoGoEnabled = false
        )

        // Both lists empty, both toggles false -> MUST ACCEPT
        val result = com.example.engine.AreaRulesEngine.evaluateRide(
            candidate = candidate,
            settings = settings,
            goToAreas = emptyList<String>(),
            noGoAreas = emptyList<String>()
        )
        assertTrue("Expected Accept when area lists are empty", result is com.example.engine.DecisionResult.Accept)
    }

    @Test
    fun testAreaRulesEngineGoToDisabledSkipsFilterEvenWithAreas() {
        val candidate = com.example.model.RideCandidate(
            fare = 120f,
            pickupDistKm = 2.0f,
            dropDistKm = 4.0f,
            pickupAddress = "Majestic",
            dropAddress = "Koramangala",
            dropArea = "Koramangala"
        )
        // goToAreas has "Airport", but Go-To toggle is OFF -> should NOT reject
        val settings = com.example.model.AppSettings(
            minFare = 50f,
            maxFare = 500f,
            maxPickupDistanceKm = 5f,
            maxDropDistanceKm = 15f,
            isGoToEnabled = false,
            isNoGoEnabled = false
        )

        val result = com.example.engine.AreaRulesEngine.evaluateRide(
            candidate = candidate,
            settings = settings,
            goToAreas = listOf("Airport", "Electronic City"),
            noGoAreas = emptyList()
        )
        assertTrue("Expected Accept when Go-To toggle is OFF", result is com.example.engine.DecisionResult.Accept)
    }

    @Test
    fun testAreaRulesEngineGoToEnabledFiltersCorrectly() {
        val candidate = com.example.model.RideCandidate(
            fare = 120f,
            pickupDistKm = 2.0f,
            dropDistKm = 4.0f,
            pickupAddress = "Majestic",
            dropAddress = "Koramangala",
            dropArea = "Koramangala"
        )
        val settings = com.example.model.AppSettings(
            minFare = 50f,
            maxFare = 500f,
            maxPickupDistanceKm = 5f,
            maxDropDistanceKm = 15f,
            isGoToEnabled = true,
            isNoGoEnabled = false
        )

        // Does not match Airport -> Reject with Go-To Area Filter reason
        val resultReject = com.example.engine.AreaRulesEngine.evaluateRide(
            candidate = candidate,
            settings = settings,
            goToAreas = listOf("Airport"),
            noGoAreas = emptyList()
        )
        assertTrue(resultReject is com.example.engine.DecisionResult.Reject)
        val reason = (resultReject as com.example.engine.DecisionResult.Reject).reason
        assertTrue("Reason should mention Go-To Area Filter: $reason", reason.startsWith("Go-To Area Filter"))

        // Matches Koramangala -> Accept
        val resultAccept = com.example.engine.AreaRulesEngine.evaluateRide(
            candidate = candidate,
            settings = settings,
            goToAreas = listOf("Koramangala"),
            noGoAreas = emptyList()
        )
        assertTrue(resultAccept is com.example.engine.DecisionResult.Accept)
    }

    @Test
    fun testAreaRulesEngineNoGoFilter() {
        val settings = com.example.model.AppSettings(
            minFare = 50f,
            maxFare = 500f,
            maxPickupDistanceKm = 5f,
            maxDropDistanceKm = 15f,
            isGoToEnabled = false,
            isNoGoEnabled = true
        )

        // Pickup matches a No-Go area, but destination does NOT.
        // No-Go must NOT reject based on pickup.
        val pickupOnlyCandidate = com.example.model.RideCandidate(
            fare = 100f,
            pickupDistKm = 1.0f,
            dropDistKm = 3.0f,
            pickupAddress = "Silk Board Junction",
            dropAddress = "BTM Layout",
            dropArea = "BTM Layout"
        )

        val pickupOnlyResult = com.example.engine.AreaRulesEngine.evaluateRide(
            candidate = pickupOnlyCandidate,
            settings = settings,
            goToAreas = emptyList(),
            noGoAreas = listOf("Silk Board")
        )

        assertTrue(
            "Pickup-only No-Go match must not reject: $pickupOnlyResult",
            pickupOnlyResult !is com.example.engine.DecisionResult.Reject
        )

        // Destination matches No-Go area -> MUST reject.
        val dropCandidate = com.example.model.RideCandidate(
            fare = 100f,
            pickupDistKm = 1.0f,
            dropDistKm = 3.0f,
            pickupAddress = "BTM Layout Pickup",
            dropAddress = "Silk Board Junction",
            dropArea = "Silk Board Junction"
        )

        val dropResult = com.example.engine.AreaRulesEngine.evaluateRide(
            candidate = dropCandidate,
            settings = settings,
            goToAreas = emptyList(),
            noGoAreas = listOf("Silk Board")
        )

        assertTrue(dropResult is com.example.engine.DecisionResult.Reject)
        val reason = (dropResult as com.example.engine.DecisionResult.Reject).reason
        assertTrue(
            "Reason should mention No-Go destination match: $reason",
            reason.contains("destination", ignoreCase = true)
        )
    }
    @Test
    fun testUberStandardUpfrontOfferExtraction() {
        val texts = listOf(
            "₹164.50",
            "Uber Auto",
            "★ 4.92",
            "3 min (1.1 km) away",
            "Koramangala 4th Block, 80 Feet Road",
            "22 min (7.4 km) trip",
            "Indiranagar 100 Feet Road, Near Metro Station",
            "Tap to accept"
        )

        val data = UberAdapter.extractOrderDataFromTexts(texts)

        assertEquals(164.50f, data.fare ?: 0f, 0.01f)
        assertEquals(1.1f, data.pickupKm ?: 0f, 0.01f)
        assertEquals(7.4f, data.dropKm ?: 0f, 0.01f)
        assertEquals("Koramangala 4th Block, 80 Feet Road", data.pickupAddress)
        assertEquals("Indiranagar 100 Feet Road, Near Metro Station", data.dropAddress)
        assertEquals("Indiranagar 100 Feet Road", data.dropArea)
        assertEquals(com.example.model.VehicleType.AUTO, data.vehicleType)
    }

    @Test
    fun testUberTripRadarExtraction() {
        val texts = listOf(
            "Trip Radar",
            "₹210",
            "Uber Go",
            "5 mins away",
            "MG Road, Ashok Nagar",
            "18 mins trip",
            "Electronic City Phase 1",
            "Match"
        )

        val data = UberAdapter.extractOrderDataFromTexts(texts)

        assertEquals(210f, data.fare ?: 0f, 0.01f)
        assertEquals("MG Road, Ashok Nagar", data.pickupAddress)
        assertEquals("Electronic City Phase 1", data.dropAddress)
        assertEquals("Electronic City Phase 1", data.dropArea)
        assertEquals(com.example.model.VehicleType.CAR, data.vehicleType)
    }

    @Test
    fun testUberSurgeAdditionFare() {
        val texts = listOf(
            "₹120 +₹40",
            "Uber Moto",
            "2.5 km away",
            "Sector 14 HSR Layout",
            "6.8 km trip",
            "BTM 2nd Stage Water Tank",
            "Accept"
        )

        val data = UberAdapter.extractOrderDataFromTexts(texts)

        assertEquals(160f, data.fare ?: 0f, 0.01f)
        assertEquals(2.5f, data.pickupKm ?: 0f, 0.01f)
        assertEquals(6.8f, data.dropKm ?: 0f, 0.01f)
        assertEquals("Sector 14 HSR Layout", data.pickupAddress)
        assertEquals("BTM 2nd Stage Water Tank", data.dropAddress)
        assertEquals(com.example.model.VehicleType.BIKE, data.vehicleType)
    }

    @Test
    fun testUberMultilineAddressExtraction() {
        val texts = listOf(
            "₹280.00",
            "Uber Premier",
            "4 mins away",
            "Block B, DLF Cyber City",
            "Phase 2, Gurugram",
            "25 mins trip",
            "Ambience Mall",
            "NH-48, Gurugram",
            "Tap to accept"
        )

        val data = UberAdapter.extractOrderDataFromTexts(texts)

        assertEquals(280f, data.fare ?: 0f, 0.01f)
        assertEquals("Block B, DLF Cyber City, Phase 2, Gurugram", data.pickupAddress)
        assertEquals("Ambience Mall, NH-48, Gurugram", data.dropAddress)
        assertEquals(com.example.model.VehicleType.CAR, data.vehicleType)
    }

    @Test
    fun testRapidoNearbyPickupDistanceIsNull() {
        val texts = listOf("Auto", "Nearby", "3.4 km", "MG Road", "₹65")
        val data = RapidoAdapter.extractOrderDataFromTexts(texts)

        assertNull("Pickup km must be null when pickup is Nearby", data.pickupKm)
        assertEquals(3.4f, data.dropKm ?: 0f, 0.01f)
        assertEquals(65f, data.totalFare, 0.01f)
    }

    @Test
    fun testRapidoAddressExtractionActualText() {
        val texts = listOf(
            "₹95",
            "1.1 km",
            "Indiranagar Metro Station",
            "4.2 km",
            "Koramangala 5th Block"
        )
        val data = RapidoAdapter.extractOrderDataFromTexts(texts)

        assertEquals("Indiranagar Metro Station", data.pickupAddress)
        assertEquals("Koramangala 5th Block", data.dropAddress)
        assertEquals(95f, data.totalFare, 0.01f)
    }

    @Test
    fun testRapidoAddressExtractionRawFallback() {
        val texts = listOf(
            "₹120",
            "HSR Layout Sector 1",
            "BTM Layout Stage 2"
        )
        val data = RapidoAdapter.extractOrderDataFromTexts(texts)

        assertEquals("HSR Layout Sector 1", data.pickupAddress)
        assertEquals("BTM Layout Stage 2", data.dropAddress)
    }

    @Test
    fun testDropDistanceRejectionInAreaRulesEngine() {
        val settings = AppSettings(
            minFare = 50f,
            maxFare = 500f,
            maxPickupDistanceKm = 3.0f,
            maxDropDistanceKm = 7.5f,
            filterMode = FilterMode.BOTH
        )

        val candidateExceedingDrop = RideCandidate(
            fare = 150f,
            pickupDistKm = 1.0f,
            dropDistKm = 10.5f, // > 7.5km
            pickupAddress = "Indiranagar",
            dropAddress = "Electronic City",
            dropArea = "Electronic City",
            platform = Platform.RAPIDO
        )

        val decision = AreaRulesEngine.evaluateRide(
            candidate = candidateExceedingDrop,
            settings = settings,
            goToAreas = emptyList<String>(),
            noGoAreas = emptyList<String>()
        )

        assertTrue(decision is DecisionResult.Reject)
        val reject = decision as DecisionResult.Reject
        assertTrue(
            "Current compact reject reason should identify the failed Order Filter 1: ${reject.reason}",
            reject.reason.contains("Order Filter 1 failed", ignoreCase = true)
        )

        // Also test with FilterMode.DISTANCE_ONLY to ensure drop distance is not bypassed
        val distanceOnlySettings = settings.copy(filterMode = FilterMode.DISTANCE_ONLY)
        val decisionDistanceOnly = AreaRulesEngine.evaluateRide(
            candidate = candidateExceedingDrop,
            settings = distanceOnlySettings,
            goToAreas = emptyList<String>(),
            noGoAreas = emptyList<String>()
        )
        assertTrue(decisionDistanceOnly is DecisionResult.Reject)
    }

    @Test
    fun testRapidoDropDistanceExtractionFormats() {
        // Test explicit drop label
        val texts1 = listOf("₹85", "Pickup: 1.2 km", "Indiranagar", "Drop: 9.5 km", "Whitefield")
        val data1 = RapidoAdapter.extractOrderDataFromTexts(texts1)
        assertEquals(1.2f, data1.pickupKm ?: 0f, 0.01f)
        assertEquals(9.5f, data1.dropKm ?: 0f, 0.01f)

        // Test suffix drop label: "9.5 km drop"
        val texts2 = listOf("₹85", "1.2 km away", "Indiranagar", "9.5 km drop", "Whitefield")
        val data2 = RapidoAdapter.extractOrderDataFromTexts(texts2)
        assertEquals(1.2f, data2.pickupKm ?: 0f, 0.01f)
        assertEquals(9.5f, data2.dropKm ?: 0f, 0.01f)

        // Test "Nearby" with drop distance
        val texts3 = listOf("₹85", "Nearby", "Indiranagar", "11.2 km trip", "Whitefield")
        val data3 = RapidoAdapter.extractOrderDataFromTexts(texts3)
        assertNull(data3.pickupKm)
        assertEquals(11.2f, data3.dropKm ?: 0f, 0.01f)
    }

    @Test
    fun testBlocklistAddressExclusionInRapidoAdapter() {
        val blocklistItems = listOf(
            "Accepted", "Auto-Accept Orders", "Max Pickup", "Max Drop",
            "ADDRESS", "Fare", "Distance", "Filter", "Settings", "Home", "History", "Areas"
        )
        for (item in blocklistItems) {
            assertTrue("Item $item should match blocklist", RapidoAdapter.matchesBlocklist(item))
            assertTrue("Item $item should be invalid address", RapidoAdapter.isInvalidAddress(item))
        }

        // Test that when extracted texts only contain blocklist words or SmartDrivo UI texts, address is "Address unavailable"
        val smartDrivoTexts = listOf(
            "Auto-Accept Orders",
            "Accepted",
            "Max Pickup",
            "Max Drop",
            "Fare",
            "Distance",
            "Filter",
            "Settings",
            "ADDRESS"
        )
        val orderData = RapidoAdapter.extractOrderDataFromTexts(smartDrivoTexts)
        assertEquals("Address unavailable", orderData.pickupAddress)
        assertEquals("Address unavailable", orderData.dropAddress)

        // Test with real addresses mixed with blocklist items
        val mixedTexts = listOf(
            "₹90",
            "1.5 km",
            "Max Pickup (km)",
            "Koramangala 4th Block",
            "4.0 km",
            "Auto-Accept Orders",
            "Indiranagar 100ft Road"
        )
        val mixedOrderData = RapidoAdapter.extractOrderDataFromTexts(mixedTexts)
        assertEquals("Koramangala 4th Block", mixedOrderData.pickupAddress)
        assertEquals("Indiranagar 100ft Road", mixedOrderData.dropAddress)
    }

    @Test
    fun testSmartDrivoPackageIdentification() {
        assertTrue(RapidoAdapter.isSmartDrivoPackage("com.smartdrivo.app"))
        assertTrue(RapidoAdapter.isSmartDrivoPackage("com.example"))
        assertTrue(RapidoAdapter.isSmartDrivoPackage("com.example.smartdrivo"))
        assertFalse(RapidoAdapter.isSmartDrivoPackage("com.rapido.passenger"))
        assertFalse(RapidoAdapter.isSmartDrivoPackage("com.rapido.rider"))
        assertFalse(RapidoAdapter.isSmartDrivoPackage("com.rapido.captain"))
    }

    @Test
    fun testRapidoHomeScreenDetection() {
        // Root containing "Today's Earnings" -> HOME SCREEN
        val homeTexts1 = listOf("ON DUTY", "Today's Earnings", "₹450", "Blue Performance", "Map")
        assertTrue("Should detect home screen with Today's Earnings", RapidoAdapter.isRapidoHomeScreenTexts(homeTexts1))

        // Root containing "ON DUTY" -> HOME SCREEN
        val homeTexts2 = listOf("ON DUTY", "Captain", "Rating: 4.8")
        assertTrue("Should detect home screen with ON DUTY", RapidoAdapter.isRapidoHomeScreenTexts(homeTexts2))

        // Root containing "Blue Performance" -> HOME SCREEN
        val homeTexts3 = listOf("Blue Performance", "Level 2", "Incentives")
        assertTrue("Should detect home screen with Blue Performance", RapidoAdapter.isRapidoHomeScreenTexts(homeTexts3))

        // Real order popup texts -> NOT home screen
        val orderTexts = listOf("₹85 +₹20", "0.4 km", "MG Road", "Indiranagar", "Accept")
        assertFalse("Real order should not be detected as home screen", RapidoAdapter.isRapidoHomeScreenTexts(orderTexts))
    }

    @Test
    fun testRapidoEarningsFareExclusion() {
        // Today's Earnings should NOT be extracted as order fare
        val homeTexts = listOf("Today's Earnings", "₹350", "ON DUTY", "Blue Performance")
        val data = RapidoAdapter.extractOrderDataFromTexts(homeTexts)
        assertEquals("Fare from Today's Earnings must be ignored", 0f, data.totalFare, 0.01f)

        // Real order fare should be extracted
        val orderTexts = listOf("Auto", "₹95", "1.2 km", "Koramangala", "Accept")
        val orderData = RapidoAdapter.extractOrderDataFromTexts(orderTexts)
        assertEquals("Real order fare must be extracted", 95f, orderData.totalFare, 0.01f)
    }

    @Test
    fun testRapidoOrderPopupValidationRules() {
        // 1. Home screen data: MUST FAIL
        val homeTexts = listOf("ON DUTY", "Today's Earnings", "₹350", "Blue Performance")
        val validation1 = RapidoAdapter.validateRapidoOrderPopupFromTexts(homeTexts, hasAcceptButton = false)
        assertFalse("Home screen should not be a valid order popup", validation1.isValid)

        // 2. Genuine order popup with all 3: Fare, pickup km, Accept button: MUST PASS
        val validTexts = listOf("₹75", "0.4 km", "Koramangala", "Indiranagar", "Accept")
        val validation2 = RapidoAdapter.validateRapidoOrderPopupFromTexts(validTexts, hasAcceptButton = true)
        assertTrue("Valid order with fare, pickup km, and Accept button should pass", validation2.isValid)
        assertEquals(75f, validation2.fare ?: 0f, 0.01f)
        assertEquals(0.4f, validation2.pickupDistKm ?: 0f, 0.01f)

        // 3. Genuine order popup with "Nearby": MUST PASS
        val nearbyTexts = listOf("₹60", "Nearby", "MG Road", "Indiranagar", "Accept")
        val validation3 = RapidoAdapter.validateRapidoOrderPopupFromTexts(nearbyTexts, hasAcceptButton = true)
        assertTrue("Valid order with Nearby, fare, and Accept button should pass", validation3.isValid)
        assertTrue(validation3.hasNearby)

        // 4. Missing Accept button: MUST FAIL
        val noAcceptTexts = listOf("₹75", "0.4 km", "Koramangala")
        val validation4 = RapidoAdapter.validateRapidoOrderPopupFromTexts(noAcceptTexts, hasAcceptButton = false)
        assertFalse("Order missing Accept button must fail", validation4.isValid)

        // 5. Missing pickup distance and missing Nearby: MUST FAIL
        val noDistTexts = listOf("₹75", "Koramangala")
        val validation5 = RapidoAdapter.validateRapidoOrderPopupFromTexts(noDistTexts, hasAcceptButton = true)
        assertFalse("Order missing distance and Nearby must fail", validation5.isValid)

        // 6. Missing fare (e.g. only earnings): MUST FAIL
        val noFareTexts = listOf("0.4 km", "Today's Earnings", "₹350", "Koramangala")
        val validation6 = RapidoAdapter.validateRapidoOrderPopupFromTexts(noFareTexts, hasAcceptButton = true)
        assertFalse("Order with only earnings fare must fail", validation6.isValid)
    }
}
