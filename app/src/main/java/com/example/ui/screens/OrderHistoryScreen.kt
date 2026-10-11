package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PreferencesManager
import com.example.model.AppSettings
import com.example.model.OrderHistoryItem
import com.example.model.OrderStatus
import com.example.model.Platform
import com.example.ui.viewmodel.RideHistoryViewModel
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorderDefault
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightSurface
import com.example.ui.theme.PlatformRapido
import com.example.ui.theme.PlatformRapidoBg
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusInactiveRed
import com.example.ui.theme.StatusInactiveRedBg
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.StatusWarningYellowBg
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.theme.TextDarkTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class HistoryTab(val label: String) {
    ALL("All"),
    ACCEPTED("Accepted"),
    IGNORED("Ignored"),
    REJECTED("Rejected")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    prefs: PreferencesManager,
    viewModel: RideHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val historyEntities by viewModel.history.collectAsStateWithLifecycle()
    val userProfile by prefs.userProfile.collectAsStateWithLifecycle()
    val appSettings by prefs.appSettings.collectAsStateWithLifecycle()
    // INSTANT_HISTORY_V7
    // PROCESSING is an internal transient state only.
    // Do not show a yellow card while the final decision is being written.
    val history = remember(historyEntities) {
        historyEntities
            .asSequence()
            .filter {
                it.status != OrderStatus.PROCESSING.name
            }
            .sortedByDescending {
                it.detectedAt
            }
            .map {
                it.toOrderHistoryItem()
            }
            .toList()
    }
    val totalAccepted by viewModel.acceptedCount.collectAsStateWithLifecycle()


    var selectedTab by remember { mutableStateOf(HistoryTab.ALL) }
    var selectedPlatformFilter by remember { mutableStateOf<Platform?>(null) }
    var selectedDateRange by remember { mutableStateOf("Today") } // Today by default; user can switch to Yesterday/All
    val historyListState = rememberLazyListState()

    val todayStr = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date())
    }
    val yesterdayStr = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(System.currentTimeMillis() - 86400000L))
    }
    val sevenDaysAgo = remember { System.currentTimeMillis() - 7 * 86400000L }

    // HISTORY_DATE_FILTER_FINAL_V1
    // First filter by date + platform. Tab counts/stats are based on this
    // same base list, so the numbers always match what the user selected.
    val datePlatformHistory by remember(
        history,
        selectedPlatformFilter,
        selectedDateRange,
        todayStr,
        yesterdayStr,
        sevenDaysAgo
    ) {
        derivedStateOf {
            history.filter { item ->
                val matchPlatform =
                    selectedPlatformFilter == null ||
                        item.platform == selectedPlatformFilter

                val matchDate =
                    when (selectedDateRange) {
                        "Today" ->
                            item.dateStr == todayStr

                        "Yesterday" ->
                            item.dateStr == yesterdayStr

                        "Last 7 Days" ->
                            item.timestamp >= sevenDaysAgo

                        else ->
                            true
                    }

                matchPlatform && matchDate
            }
        }
    }

    val acceptedCount =
        remember(datePlatformHistory) {
            datePlatformHistory.count {
                it.status == OrderStatus.ACCEPTED
            }
        }

    val rejectedCount =
        remember(datePlatformHistory) {
            datePlatformHistory.count {
                it.status != OrderStatus.PROCESSING &&
                    it.status != OrderStatus.ACCEPTED &&
                    (
                        it.status == OrderStatus.REJECTED ||
                            isNoGoOrder(it)
                    )
            }
        }

    val ignoredCount =
        remember(datePlatformHistory) {
            datePlatformHistory.count {
                !isNoGoOrder(it) &&
                    it.status != OrderStatus.ACCEPTED &&
                    it.status != OrderStatus.REJECTED &&
                    it.status != OrderStatus.PROCESSING
            }
        }

    val allCount by remember(datePlatformHistory) {
        derivedStateOf {
            datePlatformHistory.size
        }
    }

    val filteredList by remember(
        datePlatformHistory,
        selectedTab
    ) {
        derivedStateOf {
            datePlatformHistory.filter { item ->
                when (selectedTab) {
                    HistoryTab.ALL ->
                        true

                    HistoryTab.ACCEPTED ->
                        item.status == OrderStatus.ACCEPTED

                    HistoryTab.REJECTED ->
                        item.status != OrderStatus.PROCESSING &&
                            item.status != OrderStatus.ACCEPTED &&
                            (
                                item.status == OrderStatus.REJECTED ||
                                    isNoGoOrder(item)
                            )

                    HistoryTab.IGNORED ->
                        !isNoGoOrder(item) &&
                            item.status != OrderStatus.ACCEPTED &&
                            item.status != OrderStatus.REJECTED &&
                            item.status != OrderStatus.PROCESSING
                }
            }
        }
    }
    // AUTO-SCROLL TO NEWEST ORDER
    val newestVisibleOrderId = filteredList.firstOrNull()?.id

    LaunchedEffect(newestVisibleOrderId) {
        if (newestVisibleOrderId != null && filteredList.isNotEmpty()) {
            historyListState.scrollToItem(0)
        }
    }
    val totalAcceptedCount by remember(acceptedCount) {
        derivedStateOf { acceptedCount }
    }
    val totalEarnings by remember(datePlatformHistory) {
        derivedStateOf {
            datePlatformHistory
                .filter {
                    it.status == OrderStatus.ACCEPTED
                }
                .sumOf {
                    it.amount.toDouble()
                }
                .toInt()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Order History ($allCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextDarkPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDarkPrimary
                        )
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clearHistory()
                            prefs.clearOrderHistory()
                        }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = StatusInactiveRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextDarkPrimary
                )
            )
        },
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Four Tabs: Accepted, Ignored, Rejected, All
            val tabs = HistoryTab.entries

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tabs.forEach { tab ->

                    val selected = selectedTab == tab

                    val count = when (tab) {
                        HistoryTab.ALL -> allCount
                        HistoryTab.ACCEPTED -> acceptedCount
                        HistoryTab.IGNORED -> ignoredCount
                        HistoryTab.REJECTED -> rejectedCount
                    }

                    val icon = when (tab) {
                        HistoryTab.ALL -> Icons.Outlined.ListAlt
                        HistoryTab.ACCEPTED -> Icons.Outlined.CheckCircle
                        HistoryTab.IGNORED -> Icons.Outlined.Schedule
                        HistoryTab.REJECTED -> Icons.Outlined.Cancel
                    }

                    val accent = when (tab) {
                        HistoryTab.ALL -> BluePrimary
                        HistoryTab.ACCEPTED -> StatusActiveGreen
                        HistoryTab.IGNORED -> Color(0xFFF59E0B)
                        HistoryTab.REJECTED -> StatusInactiveRed
                    }

                    val softBg = when (tab) {
                        HistoryTab.ALL -> Color(0xFFEAF5FF)
                        HistoryTab.ACCEPTED -> Color(0xFFECFBF4)
                        HistoryTab.IGNORED -> Color(0xFFFFF8E7)
                        HistoryTab.REJECTED -> Color(0xFFFFF0F1)
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedTab = tab
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (selected) softBg
                                else Color.White
                        ),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color =
                                if (selected) accent
                                else accent.copy(alpha = 0.18f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 3.dp,
                                    vertical = 8.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        accent.copy(alpha = 0.12f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.label,
                                    tint = accent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(2.dp)
                            )

                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight =
                                    if (selected)
                                        FontWeight.Bold
                                    else
                                        FontWeight.SemiBold,
                                color = TextDarkPrimary,
                                maxLines = 1
                            )

                            Spacer(
                                modifier = Modifier.height(2.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .background(
                                        accent.copy(alpha = 0.12f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(
                                        horizontal = 9.dp,
                                        vertical = 2.dp
                                    )
                            ) {
                                Text(
                                    text = count.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
            // Stats Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorderDefault)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stat 1: Total Accepted
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Accepted",
                            fontSize = 11.sp,
                            color = TextDarkSecondary,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(StatusActiveGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = totalAcceptedCount.toString(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(CardBorderDefault)
                    )

                    // Stat 2: Total Fare / Assisted
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Assisted Fare",
                            fontSize = 11.sp,
                            color = TextDarkSecondary,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹$totalEarnings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(CardBorderDefault)
                    )

                    // Stat 3: Total Logged
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Logged",
                            fontSize = 11.sp,
                            color = TextDarkSecondary,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = allCount.toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueSecondary
                        )
                    }
                }
            }

            // Horizontal Filter Chips: Date Range + Platform
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val dates = listOf("All", "Today", "Yesterday")
                items(dates) { d ->
                    FilterChip(
                        selected = selectedDateRange == d,
                        onClick = { selectedDateRange = d },
                        label = { Text(d, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = TextDarkPrimary
                        )
                    )
                }

                items(Platform.entries) { platform ->
                    FilterChip(
                        selected = selectedPlatformFilter == platform,
                        onClick = {
                            selectedPlatformFilter = if (selectedPlatformFilter == platform) null else platform
                        },
                        label = { Text(platform.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (platform) {
                                Platform.RAPIDO -> PlatformRapido
                                Platform.UBER -> Color(0xFF212121)
                                Platform.OLA -> StatusActiveGreen
                            },
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = TextDarkPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (history.isEmpty())
                            "No orders recorded yet.\nIncoming orders will appear here automatically."
                        else
                            "No orders match the selected tab or filter.",
                        color = TextDarkSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = historyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        HistoryCard(item, appSettings)
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// HISTORY COMPACT UI SAFE V3
// HISTORY_CARD_STYLE_V4
@Composable
private fun HistoryCard(item: OrderHistoryItem, appSettings: AppSettings) {
    val orderTimestamp =
        if (item.timestamp > 0L) {
            item.timestamp
        } else {
            System.currentTimeMillis()
        }

    val formattedDate = remember(item.dateStr, orderTimestamp) {
        if (item.dateStr.isNotBlank()) {
            item.dateStr
        } else {
            val sdf = SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.ENGLISH
            )
            sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            sdf.format(Date(orderTimestamp))
        }
    }

    val formattedTime = remember(orderTimestamp) {
        val sdf = SimpleDateFormat(
            "h:mm a",
            Locale.ENGLISH
        )
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        sdf.format(Date(orderTimestamp))
    }

    // Visual status only. History decision logic below is unchanged.
    val historyVisualStatus =
        when {
            item.status == OrderStatus.PROCESSING ->
                OrderStatus.PROCESSING

            item.status == OrderStatus.ACCEPTED ->
                OrderStatus.ACCEPTED

            item.status == OrderStatus.FAILED ->
                OrderStatus.FAILED

            item.status == OrderStatus.SKIPPED ->
                OrderStatus.SKIPPED

            isNoGoOrder(item) ||
                item.status == OrderStatus.REJECTED ->
                OrderStatus.REJECTED

            item.status == OrderStatus.MISSED ->
                OrderStatus.MISSED

            else ->
                OrderStatus.IGNORED
        }

    val historyCardBackground =
        when (historyVisualStatus) {
            OrderStatus.ACCEPTED ->
                StatusActiveGreenBg

            OrderStatus.IGNORED ->
                StatusWarningYellowBg

            OrderStatus.REJECTED ->
                StatusInactiveRedBg

            OrderStatus.PROCESSING ->
                Color(0xFFFFF8E1)

            OrderStatus.FAILED ->
                Color(0xFFFFEBEE)

            OrderStatus.SKIPPED ->
                Color(0xFFF3EEFA)

            OrderStatus.MISSED ->
                Color(0xFFF8FAFC)
        }

    val historyCardAccent =
        when (historyVisualStatus) {
            OrderStatus.ACCEPTED ->
                StatusActiveGreen

            OrderStatus.IGNORED ->
                StatusWarningYellow

            OrderStatus.REJECTED ->
                StatusInactiveRed

            OrderStatus.PROCESSING ->
                Color(0xFFF59E0B)

            OrderStatus.FAILED ->
                Color(0xFFC62828)

            OrderStatus.SKIPPED ->
                Color(0xFF7E57C2)

            OrderStatus.MISSED ->
                Color(0xFF94A3B8)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = historyCardBackground
            ),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                1.dp,
                historyCardAccent.copy(alpha = 0.65f)
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isNoGo = isNoGoOrder(item)
            val effectiveStatus = when {
                item.status == OrderStatus.PROCESSING -> OrderStatus.PROCESSING
                item.status == OrderStatus.ACCEPTED -> OrderStatus.ACCEPTED
                item.status == OrderStatus.FAILED -> OrderStatus.FAILED
                item.status == OrderStatus.SKIPPED -> OrderStatus.SKIPPED
                isNoGo || item.status == OrderStatus.REJECTED -> OrderStatus.REJECTED
                item.status == OrderStatus.MISSED -> OrderStatus.MISSED
                else -> OrderStatus.IGNORED
            }

            // Row 1: Platform badge + Vehicle badge + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Platform badge
                    val (platformBg, platformText) = when (item.platform) {
                        Platform.RAPIDO -> Pair(PlatformRapidoBg, PlatformRapido)
                        Platform.UBER -> Pair(Color(0xFFEEEEEE), Color(0xFF212121))
                        Platform.OLA -> Pair(StatusActiveGreenBg, StatusActiveGreen)
                    }
                    Box(
                        modifier = Modifier
                            .background(platformBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            item.platform.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = platformText
                        )
                    }

                    // Vehicle badge
                    Box(
                        modifier = Modifier
                            .background(BlueContainer, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            item.vehicleType.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueSecondary
                        )
                    }
                }

                // Status badge
                val (statusBg, statusTextColor) = when (effectiveStatus) {
                    OrderStatus.PROCESSING -> Pair(Color(0xFFFFF8E1), Color(0xFFE65100))
                    OrderStatus.ACCEPTED -> Pair(StatusActiveGreenBg, StatusActiveGreen)
                    OrderStatus.REJECTED -> Pair(StatusInactiveRedBg, StatusInactiveRed)
                    OrderStatus.IGNORED -> Pair(StatusWarningYellowBg, StatusWarningYellow)
                    OrderStatus.FAILED -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
                    OrderStatus.SKIPPED -> Pair(Color(0xFFEDE7F6), Color(0xFF512DA8))
                    OrderStatus.MISSED -> Pair(Color(0xFFEEEEEE), Color.Gray)
                }

                val historyReason =
                    "${item.decisionReasonCode} ${item.decisionReasonText} ${item.reason}"

                // HISTORY_TOGGLE_LABEL_V3
                // Generic "OFF" must never be treated as Auto-Accept OFF.
                val isAutoAcceptOff =
                    effectiveStatus == OrderStatus.IGNORED &&
                        (
                            item.decisionReasonCode.equals(
                                "MASTER_TOGGLE_OFF",
                                ignoreCase = true
                            ) ||
                            (
                                historyReason.contains(
                                    "Auto-Accept",
                                    ignoreCase = true
                                ) &&
                                    historyReason.contains(
                                        "OFF",
                                        ignoreCase = true
                                    )
                            )
                        )

                val isAutoRejectOff =
                    effectiveStatus == OrderStatus.IGNORED &&
                        (
                            item.decisionReasonCode.equals(
                                "AUTO_REJECT_OFF",
                                ignoreCase = true
                            ) ||
                            historyReason.contains(
                                "Auto-Reject OFF",
                                ignoreCase = true
                            )
                        )

                val statusLabel = when {
                    effectiveStatus == OrderStatus.PROCESSING -> "PROCESSING"
                    isAutoAcceptOff -> "IGNORED - AUTO ACCEPT OFF"
                    isAutoRejectOff -> "IGNORED"
                    effectiveStatus == OrderStatus.FAILED -> "ACTION FAILED"
                    else -> effectiveStatus.name
                }
                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor
                    )
                }
            }
            // Row 2: Order Date + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedDate  |  $formattedTime",
                    fontSize = 11.sp,
                    color = TextDarkSecondary,
                    fontWeight = FontWeight.Normal
                )
            }


            // Row 3: Bold Fare (14sp) + Base/Tip breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White.copy(alpha = 0.84f)
                    ),
                border =
                    BorderStroke(
                        1.dp,
                        historyCardAccent.copy(alpha = 0.20f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val displayFare = if (item.amount > 0f) "₹${item.amount.toInt()}" else "₹--"
                        Text(
                            text = displayFare,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary
                        )
                        Text(
                            text = "Total Fare",
                            fontSize = 10.sp,
                            color = TextDarkSecondary,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    // Base / Tip breakdown
                    val base = when {
                        item.baseFare > 0f -> item.baseFare
                        item.amount > 0f && item.tipAmount > 0f -> item.amount - item.tipAmount
                        item.amount > 0f -> item.amount
                        else -> 0f
                    }
                    val tip = if (item.tipAmount > 0f) item.tipAmount else 0f

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Base: ₹${base.toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDarkPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("•", fontSize = 10.sp, color = CardBorderDefault)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tip: +₹${tip.toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tip > 0f) StatusActiveGreen else TextDarkSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = if (tip > 0f) "Includes rider tip/bonus" else "Standard base fare",
                            fontSize = 10.sp,
                            color = TextDarkSecondary
                        )
                    }
                }
            }

            // Row 4: Pick distance + Trip distance chips (padding 4dp, font 10sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pickup distance chip
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = BlueContainer,
                    border = BorderStroke(1.dp, BlueSecondary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Pickup: ${if (item.pickupDistKm > 0f) "%.1f km".format(item.pickupDistKm) else "Nearby"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueSecondary
                        )
                    }
                }

                // Trip distance chip
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, CardBorderDefault)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Trip: ${if (item.dropDistKm > 0f) "%.1f km".format(item.dropDistKm) else "N/A"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkPrimary
                        )
                    }
                }
            }

            HorizontalDivider(color = CardBorderDefault)

            // RAPIDO_TURBO_DROP_ONLY_V4
            // Pickup ADDRESS intentionally removed.
            // Pickup KM chip remains above.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(8.dp)
                        .background(StatusInactiveRed, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DROP ADDRESS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkSecondary
                    )
                    val cleanDrop = item.dropAddress.takeIf {
                        it.isNotBlank() &&
                            !it.equals(
                                "Detected Drop Location",
                                ignoreCase = true
                            )
                    } ?: "Drop Location"
                    Text(
                        text = cleanDrop,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextDarkPrimary,
                        lineHeight = 14.sp
                    )
                }
            }

            // Status Reason Section for each order:
            when (effectiveStatus) {
                OrderStatus.ACCEPTED -> {
                    val reasonText = formatAcceptedFilterReason(item)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusActiveGreenBg, RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, StatusActiveGreen.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✅", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Order Accepted",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusActiveGreen
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = reasonText,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkPrimary
                                )
                            }
                        }
                    }
                }
                OrderStatus.IGNORED -> {
                    val ignoredReason = when {
                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "fare unavailable",
                                ignoreCase = true
                            ) ->
                            "Fare unavailable - order skipped"

                        (item.amount <= 0f && item.baseFare <= 0f) &&
                            (
                                item.reason.isBlank() ||
                                    item.reason.contains(
                                        "fare",
                                        ignoreCase = true
                                    ) ||
                                    item.reason.equals(
                                        "No criteria matched",
                                        ignoreCase = true
                                    )
                            ) ->
                            "Fare unavailable - order skipped"

                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "Nearby",
                                ignoreCase = true
                            ) ->
                            "Pickup is Nearby - order skipped"

                        item.decisionReasonCode.equals(
                            "MASTER_TOGGLE_OFF",
                            ignoreCase = true
                        ) ||
                            (
                                item.reason.contains(
                                    "Auto-Accept",
                                    ignoreCase = true
                                ) &&
                                    item.reason.contains(
                                        "OFF",
                                        ignoreCase = true
                                    )
                            ) ->
                            "Auto-Accept was OFF"

                        item.decisionReasonCode.equals(
                            "AUTO_REJECT_OFF",
                            ignoreCase = true
                        ) ||
                            item.reason.contains(
                                "Auto-Reject OFF",
                                ignoreCase = true
                            ) ->
                            "Filter criteria not matched"

                        item.reason.isNotBlank() &&
                            (
                                item.reason.contains(
                                    "drop distance",
                                    ignoreCase = true
                                ) ||
                                    item.reason.contains(
                                        "Drop ",
                                        ignoreCase = true
                                    )
                            ) ->
                            item.reason

                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "pickup distance",
                                ignoreCase = true
                            ) ->
                            item.reason

                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "fare",
                                ignoreCase = true
                            ) ->
                            item.reason

                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "exceeds max limit",
                                ignoreCase = true
                            ) ->
                            item.reason

                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "below min fare",
                                ignoreCase = true
                            ) ->
                            item.reason

                        item.reason.isNotBlank() &&
                            item.reason.contains(
                                "skipped",
                                ignoreCase = true
                            ) ->
                            item.reason

                        item.reason.isNotBlank() &&
                            !item.reason.equals(
                                "No criteria matched",
                                ignoreCase = true
                            ) ->
                            item.reason

                        else ->
                            "Filter criteria not matched"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusWarningYellowBg, RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, StatusWarningYellow.copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ℹ️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Order Ignored",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusWarningYellow
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = formatIgnoredFilters(item, ignoredReason, appSettings),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkPrimary
                                )
                            }
                        }
                    }
                }
                OrderStatus.REJECTED -> {
                    val actualReason = item.decisionReasonText
                        .ifBlank { item.reason }
                        .trim()

                    val rejectedReason = when {
                        actualReason.isNotBlank() -> actualReason

                        isNoGoOrder(item) -> {
                            val areaName = extractAreaName(item)
                            if (areaName.isNotBlank()) {
                                "No-Go area matched: $areaName"
                            } else {
                                "No-Go area matched"
                            }
                        }

                        else -> "Order rejected by saved conditions"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusInactiveRedBg, RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, StatusInactiveRed.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("❌", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Order Rejected",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusInactiveRed
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = formatRejectedReason(item, rejectedReason, appSettings),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkPrimary
                                )
                            }
                        }
                    }
                }
                OrderStatus.PROCESSING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF9C4), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.6f)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏳", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Processing Order",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = item.decisionReasonText.ifBlank { item.reason.ifBlank { "Evaluating ride filters..." } },
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkPrimary
                                )
                            }
                        }
                    }
                }
                OrderStatus.FAILED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, Color(0xFFEF9A9A)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Action Failed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = item.reason.ifBlank { "Accept action could not complete" },
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkPrimary
                                )
                            }
                        }
                    }
                }
                OrderStatus.SKIPPED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEDE7F6), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, Color(0xFFD1C4E9)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏭️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Order Skipped",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF512DA8)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = item.reason.ifBlank { "Skipped duplicate or invalid order" },
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkPrimary
                                )
                            }
                        }
                    }
                }
                OrderStatus.MISSED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE0E0E0)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Order Missed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = item.reason.ifBlank { "Order timed out before response" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDarkSecondary
                                )
                            }
                        }
                    }
                }
            }

            }
    }
}

/**
 * Format which filter matched for ACCEPTED orders:
 * e.g. "Fare ₹113 matched, Pickup 0.6km matched"
 */
private fun normalizeHistoryReasonText(raw: String): String {
    // PLUS_AMOUNT_MOJIBAKE_DISPLAY_FIX_V1
    // Repairs old +Amount rows that were already stored with broken UTF-8 symbols.
    return raw
        .replace("\u00E2\u201A\u00B9", "\u20B9")
        .replace("\u00E2\u2030\u00A5", ">=")
        .replace("\u00E2\u2030\u00A4", "<=")
        .replace("\u00E2\u20AC\u00A2", " | ")
        // Old rows saved the UTF-8 bullet as mojibake.
        .replace("Ã¢â‚¬Â¢", " | ")
        .replace("Ã¢â€°Â¥", ">=")
        .replace("Ã¢â€°Â¤", "<=")
        .replace(
            Regex(
                """(?i)Separate\s+Both"""
            ),
            "Filter 2"
        )
        .replace(
            Regex(
                """\s*\|\s*"""
            ),
            " | "
        )
        .trim()
}
private fun formatAcceptedFilterReason(item: OrderHistoryItem): String {
    val storedReason = normalizeHistoryReasonText(
        item.decisionReasonText
            .ifBlank { item.reason }
    )

    // New records store the exact mode-aware reason in the service.
    // Always display that exact reason instead of rebuilding every field.
    if (storedReason.isNotBlank() &&
        !storedReason.equals("Ride auto-accepted successfully", ignoreCase = true) &&
        !storedReason.equals("Criteria matched", ignoreCase = true)
    ) {
        return storedReason
    }

    // Legacy fallback for older history rows that did not store mode.
    val matches = mutableListOf<String>()

    val fare = if (item.amount > 0f) item.amount else item.baseFare
    if (fare > 0f) {
        matches += "Fare ₹${fare.toInt()} matched"
    }

    if (item.pickupDistKm > 0f) {
        matches += "Pickup ${String.format(Locale.ENGLISH, "%.1f km", item.pickupDistKm)} matched"
    }

    if (item.dropDistKm > 0f) {
        matches += "Trip ${String.format(Locale.ENGLISH, "%.1f km", item.dropDistKm)} matched"
    }

    return if (matches.isNotEmpty()) {
        matches.joinToString(" | ")
    } else {
        "Criteria matched"
    }
}
/**
 * Extract matched No-Go area name for REJECTED orders:
 * e.g. "No-Go area matched: Koramangala"
 */
private fun cleanHistoryReasonV7(raw: String): String {
    return raw
        .replace("\u00E2\u20AC\u00A2", " | ")
        .replace("\u2022", " | ")
        .replace("\u00E2\u2030\u00A5", ">=")
        .replace("\u00E2\u2030\u00A4", "<=")
        .replace(
            Regex("""(?i)Separate\s+Both"""),
            "Filter 2"
        )
        .replace(
            Regex("""\s*\|\s*"""),
            " | "
        )
        .trim()
}

private fun formatGenericFilterFailureV7(
    raw: String
): String? {
    val clean =
        cleanHistoryReasonV7(raw)

    if (
        !clean.contains(
            "No condition matched",
            ignoreCase = true
        )
    ) {
        return null
    }

    val filter1 =
        when {
            clean.contains(
                "Fare Only failed",
                ignoreCase = true
            ) ->
                "Fare Only"

            clean.contains(
                "Distance Only failed",
                ignoreCase = true
            ) ->
                "Distance Only"

            clean.contains(
                "Both failed",
                ignoreCase = true
            ) ->
                "Both"

            else ->
                null
        }

    val filter2Failed =
        clean.contains(
            "Filter 2 failed",
            ignoreCase = true
        )

    val filter3Failed =
        clean.contains(
            "Filter 3 failed",
            ignoreCase = true
        )

    return buildString {
        if (filter1 != null) {
            append(
                "Filter 1: $filter1 Not Match ❌"
            )
        } else {
            append(
                "Filter 1: Not Match ❌"
            )
        }

        if (filter2Failed) {
            append(
                "\nFilter 2: Not Match ❌"
            )
        }

        if (filter3Failed) {
            append(
                "\nFilter 3: Not Match ❌"
            )
        }
    }
}
private fun formatRejectedReason(
    item: OrderHistoryItem,
    rawReason: String,
    settings: AppSettings
): String {
    val raw =
        cleanHistoryReasonV7(
            rawReason
        )

    formatGenericFilterFailureV7(raw)
        ?.let {
            return it
        }
    // COMPACT_HISTORY_REASON_V1
    if (
        isNoGoOrder(item) ||
        raw.contains(
            "No-Go",
            ignoreCase = true
        ) ||
        raw.contains(
            "Drop matched:",
            ignoreCase = true
        )
    ) {
        val areaName =
            extractAreaName(item)
                .ifBlank {
                    Regex(
                        """(?i)No-Go\s+Area:\s*(?:destination\s+)?matches\s+'([^']+)'"""
                    )
                        .find(raw)
                        ?.groupValues
                        ?.getOrNull(1)
                        .orEmpty()
                }
                .ifBlank {
                    Regex(
                        """(?i)Drop\s+matched:\s*([^|\n]+)"""
                    )
                        .find(raw)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                        .orEmpty()
                }

        return if (areaName.isNotBlank()) {
            "No-Go Area: $areaName ❌"
        } else {
            "No-Go Area Match ❌"
        }
    }

    // Auto-Rejected filter orders use the same short Filter 1 / Filter 2 reason.
    if (
        raw.contains("Limits:", ignoreCase = true) ||
        raw.contains("Filter 2:", ignoreCase = true) ||
        raw.contains("Filter 3:", ignoreCase = true) ||
        raw.contains("Distance Only", ignoreCase = true) ||
        raw.contains("Why ignored:", ignoreCase = true)
    ) {
        return formatIgnoredFilters(item, raw, settings)
    }

    return raw
        .replace(
            Regex("""(?i)^Auto-Rejected\s*\(skipped\)\s*:\s*"""),
            "Auto Reject: "
        )
        .replace(
            Regex("""(?i)^Auto-Rejected\s*:\s*"""),
            "Auto Reject: "
        )
}
private fun formatIgnoredFilters(
    item: OrderHistoryItem,
    rawReason: String,
    settings: AppSettings
): String {
    val normalizedReason =
        cleanHistoryReasonV7(
            rawReason
        )

    formatGenericFilterFailureV7(
        normalizedReason
    )?.let {
        return it
    }

    val lines =
        normalizedReason
            .lines()
            .map {
                it.trim()
            }
    val limitsLine =
        lines.firstOrNull {
            it.startsWith("Limits:", ignoreCase = true)
        }.orEmpty()

    val filter2Line =
        lines.firstOrNull {
            it.startsWith(
                "Filter 2:",
                ignoreCase = true
            )
        }.orEmpty()

    val filter3Line =
        lines.firstOrNull {
            it.startsWith(
                "Filter 3:",
                ignoreCase = true
            )
        }.orEmpty()

    // HISTORY_REASON_ACCURACY_V2
    // Prefer the exact stored failures, including fare failures.
    val whyLine =
        lines.firstOrNull {
            it.startsWith(
                "Why ignored:",
                ignoreCase = true
            )
        }.orEmpty()

    if (whyLine.isNotBlank()) {
        val result =
            mutableListOf<String>()

        val filter1Reason =
            whyLine
                .substringAfter(":")
                .trim()

        val compactFilter1Reason =
            when {
                filter1Reason.contains(
                    "fare",
                    ignoreCase = true
                ) ->
                    "Fare Only Not Match ❌"

                filter1Reason.contains(
                    "pickup",
                    ignoreCase = true
                ) ||
                    filter1Reason.contains(
                        "trip",
                        ignoreCase = true
                    ) ->
                    "Distance Only Not Match ❌"

                else ->
                    "Not Match ❌"
            }

        result +=
            "Filter 1: $compactFilter1Reason"

        if (
            settings.isSecondaryBothFilterEnabled &&
            filter2Line.isNotBlank()
        ) {
            result +=
                "Filter 2: Not Match ❌"
        }

        if (
            settings.isTertiaryBothFilterEnabled &&
            filter3Line.isNotBlank()
        ) {
            result +=
                "Filter 3: Not Match ❌"
        }

        return result.joinToString("\n")
    }

    if (limitsLine.isBlank() && filter2Line.isBlank() && filter3Line.isBlank()) {
        return normalizedReason
            .lines()
            .map { it.trim() }
            .filterNot {
                it.startsWith("Mode:", ignoreCase = true) ||
                it.startsWith("Why ignored:", ignoreCase = true)
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { rawReason }
    }

    fun getNumber(pattern: String, text: String): Float? =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()

    fun km(value: Float): String =
        if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(Locale.ENGLISH, "%.1f", value)
        }

    val filter1PickupMax =
        getNumber("""Pickup\s*[≤<]=?\s*([0-9.]+)""", limitsLine)

    val filter1TripMax =
        getNumber("""Trip\s*[≤<]=?\s*([0-9.]+)""", limitsLine)

    val filter1PickupFailed =
        filter1PickupMax != null &&
        item.pickupDistKm > filter1PickupMax

    val filter1TripFailed =
        filter1TripMax != null &&
        item.dropDistKm > filter1TripMax

    val filter1Text = when {
        filter1PickupFailed && filter1TripFailed ->
            "Pickup + Trip exceed limits"

        filter1PickupFailed ->
            "Pickup exceeds ${km(filter1PickupMax!!)} km"

        filter1TripFailed ->
            "Trip exceeds ${km(filter1TripMax!!)} km"

        else ->
            "Filter not matched"
    }

    val result = mutableListOf(
        "Filter 1: $filter1Text"
    )

    if (settings.isSecondaryBothFilterEnabled && filter2Line.isNotBlank()) {
        val filter2PickupMax =
            getNumber(
                """Pickup.*?max\s*([0-9.]+)""",
                filter2Line
            ) ?: settings.secondaryBothMaxPickupDistanceKm

        val filter2TripMax =
            getNumber(
                """Trip.*?max\s*([0-9.]+)""",
                filter2Line
            ) ?: settings.secondaryBothMaxDropDistanceKm

        val filter2PickupFailed =
            Regex(
                """Pickup.*?>\s*max""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(filter2Line)

        val filter2TripFailed =
            Regex(
                """Trip.*?>\s*max""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(filter2Line)

        val filter2Text = when {
            filter2PickupFailed && filter2TripFailed ->
                "Pickup + Trip exceed limits"

            filter2PickupFailed ->
                "Pickup exceeds ${km(filter2PickupMax)} km"

            filter2TripFailed ->
                "Trip exceeds ${km(filter2TripMax)} km"

            else ->
                "Filter not matched"
        }

        result += "Filter 2: $filter2Text"
    }

    if (
        settings.isTertiaryBothFilterEnabled &&
        filter3Line.isNotBlank()
    ) {
        val filter3PickupMax =
            getNumber(
                """Pickup.*?max\s*([0-9.]+)""",
                filter3Line
            ) ?: settings.tertiaryBothMaxPickupDistanceKm

        val filter3TripMax =
            getNumber(
                """Trip.*?max\s*([0-9.]+)""",
                filter3Line
            ) ?: settings.tertiaryBothMaxDropDistanceKm

        val filter3PickupFailed =
            Regex(
                """Pickup.*?>\s*max""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(filter3Line)

        val filter3TripFailed =
            Regex(
                """Trip.*?>\s*max""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(filter3Line)

        val filter3Text =
            when {
                filter3PickupFailed &&
                    filter3TripFailed ->
                    "Pickup + Trip exceed limits"

                filter3PickupFailed ->
                    "Pickup exceeds ${km(filter3PickupMax)} km"

                filter3TripFailed ->
                    "Trip exceeds ${km(filter3TripMax)} km"

                else ->
                    "Filter not matched"
            }

        result +=
            "Filter 3: $filter3Text"
    }

    return result.joinToString("\n")
}
private fun extractAreaName(item: OrderHistoryItem): String {
    if (item.matchedNoGoGroup.isNotBlank()) {
        return item.matchedNoGoGroup.trim()
    }

    val r = item.decisionReasonText
        .ifBlank { item.reason }
        .trim()

    if (r.contains("No-Go area matched:", ignoreCase = true)) {
        val extracted = r
            .substringAfter("No-Go area matched:", "")
            .trim()
            .removeSurrounding("[", "]")
            .removeSurrounding("'", "'")
            .trim()

        if (extracted.isNotBlank()) return extracted
    }

    if (r.contains("'")) {
        val candidate = r.substringAfter("'").substringBefore("'").trim()
        if (candidate.isNotBlank() && candidate.length < 80) return candidate
    }

    if (r.contains("\"")) {
        val candidate = r.substringAfter("\"").substringBefore("\"").trim()
        if (candidate.isNotBlank() && candidate.length < 80) return candidate
    }

    if (item.dropArea.isNotBlank()) {
        return item.dropArea.trim()
    }

    if (item.dropAddress.isNotBlank()) {
        val part = item.dropAddress.split(",").firstOrNull()?.trim().orEmpty()
        if (part.isNotBlank() && part.length < 80) return part
    }

    return ""
}

/**
 * Returns true if an order history item was rejected due to matching a No-Go area.
 */
private fun isNoGoOrder(item: OrderHistoryItem): Boolean {
    val combinedReason = buildString {
        append(item.decisionReasonCode)
        append(" ")
        append(item.decisionReasonText)
        append(" ")
        append(item.reason)
        append(" ")
        append(item.matchedNoGoGroup)
    }

    return item.matchedNoGoGroup.isNotBlank() ||
        combinedReason.contains("No-Go", ignoreCase = true) ||
        combinedReason.contains("NOGO", ignoreCase = true) ||
        combinedReason.contains("No Go", ignoreCase = true)
}
