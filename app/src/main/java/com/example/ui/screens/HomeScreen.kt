package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.FirebaseRepository
import com.example.data.PreferencesManager
import com.example.model.FilterMode
import com.example.model.OrderHistoryItem
import com.example.model.OrderStatus
import com.example.model.Platform
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.RideHistoryViewModel
import com.example.service.FloatingOverlayService
import com.example.service.SmartDrivoAccessibilityService
import com.example.ui.components.SmartDrivoLogo
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch
import com.example.ui.theme.BlueBorder
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorderDefault
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightSurface
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusActiveGreenBorder
import com.example.ui.theme.StatusInactiveRed
import com.example.ui.theme.StatusInactiveRedBg
import com.example.ui.theme.StatusInactiveRedBorder
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.StatusWarningYellowBg
import com.example.ui.theme.StatusWarningYellowBorder
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.theme.TextDarkTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: PreferencesManager,
    repository: FirebaseRepository,
    historyViewModel: RideHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAreaManager: () -> Unit,
    onNavigateToCommunity: () -> Unit
) {
    val context = LocalContext.current
    val settings by prefs.appSettings.collectAsState()
    val userProfile by prefs.userProfile.collectAsState()
    val historyEntities by historyViewModel.history.collectAsStateWithLifecycle()
    // Room is the single UI source of truth for ride history.
    // Avoid collecting the legacy SharedPreferences JSON history in parallel.
    val historyItems = remember(historyEntities) {
        historyEntities.map { it.toOrderHistoryItem() }
    }
val goToAreas by prefs.goToAreas.collectAsState()
    val noGoAreas by prefs.noGoAreas.collectAsState()
    val activeGoToCount by remember(goToAreas) {
        derivedStateOf {
            goToAreas.count { it.isEnabled && it.keywords.isNotEmpty() }
        }
    }
    val activeNoGoCount by remember(noGoAreas) {
        derivedStateOf {
            noGoAreas.count { it.isEnabled && it.keywords.isNotEmpty() }
        }
    }
    val activeAreaFilterCount by remember(activeGoToCount, activeNoGoCount) {
        derivedStateOf { activeGoToCount + activeNoGoCount }
    }

    var showSimulateDialog by remember { mutableStateOf(false) }
    var simPlatform by remember { mutableStateOf(Platform.RAPIDO) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityGranted by remember {
        mutableStateOf(PermissionHelper.isAccessibilityPermissionGranted(context))
    }
    var isOverlayGranted by remember {
        mutableStateOf(PermissionHelper.isOverlayPermissionGranted(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasAccessibility = PermissionHelper.isAccessibilityPermissionGranted(context)
                val hasOverlay = PermissionHelper.isOverlayPermissionGranted(context)
                isAccessibilityGranted = hasAccessibility
                isOverlayGranted = hasOverlay

                if (prefs.isPendingAutoAcceptActivation) {
                    if (hasAccessibility && hasOverlay) {
                        prefs.isPendingAutoAcceptActivation = false
                        showAccessibilityDialog = false
                        showOverlayDialog = false
                        if (!settings.isAutoAcceptActive) {
                            prefs.saveAppSettings(settings.copy(isAutoAcceptActive = true))
                            context.sendBroadcast(
                                Intent("com.example.TOGGLE_CHANGED")
                                    .setPackage(context.packageName)
                                    .putExtra("enabled", true)
                            )
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Auto-Accept is now Active! ✓",
                                duration = SnackbarDuration.Short
                            )
                        }
                    } else if (hasAccessibility && !hasOverlay) {
                        showAccessibilityDialog = false
                        showOverlayDialog = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val handleToggleAutoAccept: (Boolean) -> Unit = { shouldEnable ->
        if (!shouldEnable) {
            prefs.isPendingAutoAcceptActivation = false
            prefs.saveAppSettings(settings.copy(isAutoAcceptActive = false))
            context.sendBroadcast(
                Intent("com.example.TOGGLE_CHANGED")
                    .setPackage(context.packageName)
                    .putExtra("enabled", false)
            )
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Auto-Accept is now OFF",
                    duration = SnackbarDuration.Short
                )
            }
        } else {
            val hasAccessibility = PermissionHelper.isAccessibilityPermissionGranted(context)
            val hasOverlay = PermissionHelper.isOverlayPermissionGranted(context)
            isAccessibilityGranted = hasAccessibility
            isOverlayGranted = hasOverlay

            if (!hasAccessibility) {
                prefs.isPendingAutoAcceptActivation = true
                showAccessibilityDialog = true
            } else if (!hasOverlay) {
                prefs.isPendingAutoAcceptActivation = true
                showOverlayDialog = true
            } else {
                prefs.isPendingAutoAcceptActivation = false
                prefs.saveAppSettings(settings.copy(isAutoAcceptActive = true))
                context.sendBroadcast(
                    Intent("com.example.TOGGLE_CHANGED")
                        .setPackage(context.packageName)
                        .putExtra("enabled", true)
                )
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Auto-Accept is now Active! ✓",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Fare and Distance inputs with rememberSaveable and hasUnsavedChanges
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }

    // The tab the driver is currently editing. It does NOT become active
    // until Save Settings is pressed.
    var pendingFilterMode by remember { mutableStateOf(settings.filterMode) }

    // Last successfully saved mode. Used only for the Enabled/Disabled badge.
    var savedFilterMode by remember { mutableStateOf(settings.filterMode) }

    LaunchedEffect(settings.filterMode) {
        savedFilterMode = settings.filterMode
        if (!hasUnsavedChanges) {
            pendingFilterMode = settings.filterMode
        }
    }

    var minFareInput by rememberSaveable {
        mutableStateOf(if (settings.minFare > 0) {
            if (settings.minFare % 1.0f == 0f) settings.minFare.toInt().toString() else settings.minFare.toString()
        } else "50")
    }
    var maxFareInput by rememberSaveable {
        mutableStateOf(if (settings.maxFare > 0) {
            if (settings.maxFare % 1.0f == 0f) settings.maxFare.toInt().toString() else settings.maxFare.toString()
        } else "999")
    }
    var maxPickupInput by rememberSaveable {
        mutableStateOf(if (settings.maxPickupDistanceKm > 0) {
            if (settings.maxPickupDistanceKm % 1.0f == 0f) settings.maxPickupDistanceKm.toInt().toString() else settings.maxPickupDistanceKm.toString()
        } else "3.0")
    }
    var maxDropInput by rememberSaveable {
        mutableStateOf(if (settings.maxDropDistanceKm > 0) {
            if (settings.maxDropDistanceKm % 1.0f == 0f) settings.maxDropDistanceKm.toInt().toString() else settings.maxDropDistanceKm.toString()
        } else "7.5")
    }


    // Calculate real-time stats for today
    val todayStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val todayOrders by remember(historyItems, todayStr) {
        derivedStateOf { historyItems.filter { it.dateStr == todayStr } }
    }
    val acceptedCount by remember(todayOrders) {
        derivedStateOf { todayOrders.count { it.status == OrderStatus.ACCEPTED } }
    }
    val earningsAssisted by remember(todayOrders) {
        derivedStateOf {
            todayOrders
                .filter { it.status == OrderStatus.ACCEPTED }
                .sumOf { it.amount.toDouble() }
                .toInt()
        }
    }

    // HOME_MEMBERSHIP_PROFILE_CARD_V1
    // UI-only values for the new Home membership/profile card.
    val membershipIsActive =
        userProfile.isAdmin || userProfile.isPlanValid

    val membershipTitle =
        if (membershipIsActive)
            "Membership Active"
        else
            "Membership Inactive"

    val membershipPlanText =
        when (userProfile.plan.uppercase(Locale.ROOT)) {
            "FREE_TRIAL" -> "1-Day Free Trial"
            "3DAYS" -> "3 Days Plan"
            "7DAYS" -> "7 Days Plan"
            "15DAYS" -> "15 Days Plan"
            "1MONTH", "30DAYS" -> "Monthly Plan"
            "NONE", "" -> "No Active Plan"
            else ->
                userProfile.plan
                    .replace("_", " ")
                    .lowercase(Locale.ROOT)
                    .replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT)
                        else it.toString()
                    }
        }

    val membershipDaysText =
        remember(
            userProfile.planExpireMillis,
            userProfile.isAdmin,
            membershipIsActive
        ) {
            when {
                userProfile.isAdmin ->
                    "Unlimited"

                userProfile.planExpireMillis <= 0L ->
                    "Not activated"

                userProfile.planExpireMillis > System.currentTimeMillis() -> {
                    val millisLeft =
                        userProfile.planExpireMillis -
                            System.currentTimeMillis()

                    val daysLeft =
                        kotlin.math.ceil(
                            millisLeft / 86_400_000.0
                        ).toInt().coerceAtLeast(1)

                    "$daysLeft ${if (daysLeft == 1) "day" else "days"} left"
                }

                else -> {
                    val millisExpired =
                        System.currentTimeMillis() -
                            userProfile.planExpireMillis

                    val daysAgo =
                        kotlin.math.ceil(
                            millisExpired / 86_400_000.0
                        ).toInt().coerceAtLeast(1)

                    "Expired $daysAgo ${if (daysAgo == 1) "day" else "days"} ago"
                }
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {},
        containerColor = LightBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            // HOME_MEMBERSHIP_PROFILE_CARD_V1
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF4FAFF)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFB9DDF7)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 12.dp,
                                vertical = 12.dp
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartDrivoLogo(size = 46.dp)

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "SmartDrivo",
                                    color = TextDarkPrimary,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )

                                Text(
                                    text = membershipTitle,
                                    color =
                                        if (membershipIsActive)
                                            StatusActiveGreen
                                        else
                                            StatusInactiveRed,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Surface(
                                onClick = onNavigateToCommunity,
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFFE3EDF6)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.size(42.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = "Community",
                                        tint = TextDarkSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            border = BorderStroke(
                                1.dp,
                                Color(0xFFE2EDF6)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Mobile Number",
                                            color = TextDarkSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = userProfile.phone.ifBlank { "Not added" },
                                            color = TextDarkPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1.15f)
                                    ) {
                                        Text(
                                            text = "Email ID",
                                            color = TextDarkSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = userProfile.email.ifBlank { "Not added" },
                                            color = TextDarkPrimary,
                                            fontSize = 12.5.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Color(0xFFE1EBF3)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Text(
                                            text = "Plan",
                                            color = TextDarkSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = membershipPlanText,
                                            color = TextDarkPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(0.9f)
                                    ) {
                                        Text(
                                            text = "Plan Status",
                                            color = TextDarkSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text =
                                                if (membershipIsActive)
                                                    "Active"
                                                else
                                                    "Inactive",
                                            color =
                                                if (membershipIsActive)
                                                    StatusActiveGreen
                                                else
                                                    StatusInactiveRed,
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(0.95f)
                                    ) {
                                        Text(
                                            text = "Days Left",
                                            color = TextDarkSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = membershipDaysText,
                                            color =
                                                if (membershipIsActive)
                                                    Color(0xFFF97316)
                                                else
                                                    StatusInactiveRed,
                                            fontSize = 12.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // HOME TOP: Compact Today's Performance
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDefault)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Today's Performance",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = todayOrders.size.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                                Text(
                                    text = "Total Orders",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary,
                                    maxLines = 1
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier.height(38.dp),
                                thickness = 1.dp,
                                color = CardBorderDefault
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = acceptedCount.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusActiveGreen
                                )
                                Text(
                                    text = "Accepted",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary,
                                    maxLines = 1
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier.height(38.dp),
                                thickness = 1.dp,
                                color = CardBorderDefault
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "₹$earningsAssisted",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlueSecondary
                                )
                                Text(
                                    text = "Today Earning",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            // Accessibility Service Warning (Colorful Warning Card)
            if (!isAccessibilityGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusWarningYellowBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, StatusWarningYellowBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.isPendingAutoAcceptActivation = true
                                showAccessibilityDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFFFECB3), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = StatusWarningYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility Service Disabled",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100),
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap to enable SmartDrivo in Accessibility Settings. Auto-Accept will activate automatically.",
                                    color = TextDarkSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = StatusWarningYellow
                            )
                        }
                    }
                }
            } else if (!isOverlayGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BlueContainer),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, BlueBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.isPendingAutoAcceptActivation = true
                                showOverlayDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = BlueSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Display Overlay Required",
                                    fontWeight = FontWeight.Bold,
                                    color = BlueSecondary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Tap to allow SmartDrivo to show popup ride cards over Rapido, Uber, and Ola.",
                                    color = TextDarkSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = BlueSecondary
                            )
                        }
                    }
                }
            }

            // 2. AUTO-ACCEPT ORDERS — COMPACT V2
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            handleToggleAutoAccept(!settings.isAutoAcceptActive)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isAutoAcceptActive) StatusActiveGreenBg else StatusInactiveRedBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (settings.isAutoAcceptActive) StatusActiveGreenBorder else StatusInactiveRedBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    if (settings.isAutoAcceptActive) Color(0xFFDCFCE7) else Color(0xFFFFEBEE),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = if (settings.isAutoAcceptActive) StatusActiveGreen else StatusInactiveRed,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(9.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Auto-Accept Orders",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = if (settings.isAutoAcceptActive)
                                    "ACTIVE • Ready"
                                else
                                    "INACTIVE • Paused",
                                fontSize = 11.sp,
                                color = if (settings.isAutoAcceptActive) StatusActiveGreen else TextDarkSecondary,
                                maxLines = 1
                            )
                        }

                        Switch(
                            checked = settings.isAutoAcceptActive,
                            onCheckedChange = { shouldEnable ->
                                handleToggleAutoAccept(shouldEnable)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BluePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFBDBDBD)
                            )
                        )
                    }
                }
            }
            // AUTO REJECT NO CONDITION MATCH — SEPARATE CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        // AUTO_REJECT_CARD_CLICK_V2
                        .clickable {
                            val enabled = !settings.isAutoRejectBadFaresEnabled
                            prefs.saveAppSettings(
                                settings.copy(
                                    isAutoRejectBadFaresEnabled = enabled
                                )
                            )

                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (enabled)
                                        "Auto Reject No Condition Match ON"
                                    else
                                        "Auto Reject No Condition Match OFF - Manual mode"
                                )
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isAutoRejectBadFaresEnabled)
                            Color(0xFFFFF1F2)
                        else
                            Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (settings.isAutoRejectBadFaresEnabled)
                            Color(0xFFFCA5A5)
                        else
                            CardBorderDefault
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    if (settings.isAutoRejectBadFaresEnabled)
                                        Color(0xFFFFE4E6)
                                    else
                                        Color(0xFFF1F5F9),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⛔",
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(9.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Auto Reject No Condition Match",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary,
                                maxLines = 1
                            )

                            Text(
                                text = if (settings.isAutoRejectBadFaresEnabled)
                                    "ACTIVE • Skip orders that fail saved conditions"
                                else
                                    "INACTIVE • Manual action",
                                fontSize = 10.sp,
                                color = if (settings.isAutoRejectBadFaresEnabled)
                                    StatusInactiveRed
                                else
                                    TextDarkSecondary,
                                maxLines = 1
                            )
                        }

                        Switch(
                            checked = settings.isAutoRejectBadFaresEnabled,
                            onCheckedChange = { enabled ->
                                prefs.saveAppSettings(
                                    settings.copy(
                                        isAutoRejectBadFaresEnabled = enabled
                                    )
                                )

                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (enabled)
                                            "Auto Reject No Condition Match ON ✓"
                                        else
                                            "Auto Reject No Condition Match OFF • Manual mode"
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = StatusInactiveRed,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFBDBDBD)
                            )
                        )
                    }
                }
            }
            // HOME_FILTER_COMPACT_V1
            // ORDER_FILTER_1_UI_V1
            // Same compact visual family as Order Filter 2 / 3.
            // Existing Fare / Distance / Both behavior is unchanged.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            if (settings.isPrimaryFilterEnabled)
                                BlueContainer
                            else
                                CardBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (settings.isPrimaryFilterEnabled)
                            BluePrimary
                        else
                            CardBorderDefault
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {

                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(
                                                    alpha = 0.75f
                                                ),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(
                                                horizontal = 7.dp,
                                                vertical = 3.dp
                                            )
                                    ) {
                                        Text(
                                            text = "FILTER 1",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color =
                                                if (settings.isPrimaryFilterEnabled)
                                                    BluePrimary
                                                else
                                                    TextDarkSecondary
                                        )
                                    }

                                    Spacer(
                                        modifier =
                                            Modifier.width(7.dp)
                                    )

                                    Text(
                                        text = "Order Filter 1",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextDarkPrimary
                                    )
                                }

                                Spacer(
                                    modifier =
                                        Modifier.height(2.dp)
                                )

                                Text(
                                    text = "One ride option",
                                    fontSize = 10.sp,
                                    color = TextDarkSecondary
                                )
                            }

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    text =
                                        if (settings.isPrimaryFilterEnabled)
                                            "ON"
                                        else
                                            "OFF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (settings.isPrimaryFilterEnabled)
                                            BluePrimary
                                        else
                                            TextDarkSecondary
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(5.dp)
                                )

                                Switch(
                                    checked =
                                        settings.isPrimaryFilterEnabled,
                                    onCheckedChange = { enabled ->
                                        prefs.saveAppSettings(
                                            settings.copy(
                                                isPrimaryFilterEnabled =
                                                    enabled
                                            )
                                        )

                                        coroutineScope.launch {
                                            snackbarHostState
                                                .showSnackbar(
                                                    if (enabled)
                                                        "Order Filter 1 ON"
                                                    else
                                                        "Order Filter 1 OFF"
                                                )
                                        }
                                    },
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor =
                                                Color.White,
                                            checkedTrackColor =
                                                BluePrimary,
                                            uncheckedThumbColor =
                                                Color.White,
                                            uncheckedTrackColor =
                                                Color(0xFFBDBDBD)
                                        )
                                )
                            }
                        }

                        // Fare / Distance / Both selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.White.copy(
                                        alpha = 0.72f
                                    ),
                                    RoundedCornerShape(11.dp)
                                )
                                .padding(3.dp),
                            horizontalArrangement =
                                Arrangement.spacedBy(3.dp)
                        ) {
                            FilterMode.values().forEach { mode ->

                                val isSelected =
                                    pendingFilterMode == mode

                                val selectedBg =
                                    when (mode) {
                                        FilterMode.FARE_ONLY ->
                                            BluePrimary

                                        FilterMode.DISTANCE_ONLY ->
                                            BlueSecondary

                                        FilterMode.BOTH ->
                                            StatusActiveGreen
                                    }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(
                                            RoundedCornerShape(
                                                8.dp
                                            )
                                        )
                                        .background(
                                            if (isSelected) {
                                                selectedBg
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            if (
                                                pendingFilterMode !=
                                                mode
                                            ) {
                                                pendingFilterMode =
                                                    mode
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        }
                                        .padding(
                                            vertical = 6.dp,
                                            horizontal = 3.dp
                                        ),
                                    contentAlignment =
                                        Alignment.Center
                                ) {
                                    Text(
                                        text =
                                            when (mode) {
                                                FilterMode.FARE_ONLY ->
                                                    "Fare"

                                                FilterMode.DISTANCE_ONLY ->
                                                    "Distance"

                                                FilterMode.BOTH ->
                                                    "Both"
                                            },
                                        fontWeight =
                                            if (isSelected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Medium
                                            },
                                        fontSize = 11.sp,
                                        color =
                                            if (isSelected) {
                                                Color.White
                                            } else {
                                                TextDarkSecondary
                                            },
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text =
                                when (pendingFilterMode) {
                                    FilterMode.FARE_ONLY ->
                                        "Fare must match saved Minimum / Maximum"

                                    FilterMode.DISTANCE_ONLY ->
                                        "Pickup and Drop must match saved maximums"

                                    FilterMode.BOTH ->
                                        "Fare + Pickup + Drop must all match"
                                },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BluePrimary
                        )

                        // Inputs
                        when (pendingFilterMode) {

                            FilterMode.FARE_ONLY -> {

                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            7.dp
                                        )
                                ) {
                                    OutlinedTextField(
                                        value = minFareInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                minFareInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Min Fare ₹",
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                10.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )

                                    OutlinedTextField(
                                        value = maxFareInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                maxFareInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Max Fare ₹",
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                10.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )
                                }
                            }

                            FilterMode.DISTANCE_ONLY -> {

                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            7.dp
                                        )
                                ) {
                                    OutlinedTextField(
                                        value = maxPickupInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                maxPickupInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Max Pickup",
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                10.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )

                                    OutlinedTextField(
                                        value = maxDropInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                maxDropInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Max Drop",
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                10.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )
                                }
                            }

                            FilterMode.BOTH -> {

                                // Keep BOTH compact: all four inputs
                                // stay in one row.
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            5.dp
                                        )
                                ) {
                                    OutlinedTextField(
                                        value = minFareInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                minFareInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Min ₹",
                                                fontSize = 8.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                9.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )

                                    OutlinedTextField(
                                        value = maxFareInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                maxFareInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Max ₹",
                                                fontSize = 8.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                9.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )

                                    OutlinedTextField(
                                        value = maxPickupInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                maxPickupInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Pickup",
                                                fontSize = 8.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                9.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )

                                    OutlinedTextField(
                                        value = maxDropInput,
                                        onValueChange = { input ->
                                            if (
                                                input.isEmpty() ||
                                                input.matches(
                                                    Regex(
                                                        """^\d*\.?\d*$"""
                                                    )
                                                )
                                            ) {
                                                maxDropInput =
                                                    input
                                                hasUnsavedChanges =
                                                    true
                                            }
                                        },
                                        label = {
                                            Text(
                                                "Drop",
                                                fontSize = 8.sp,
                                                maxLines = 1
                                            )
                                        },
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Decimal
                                            ),
                                        singleLine = true,
                                        modifier =
                                            Modifier.weight(1f),
                                        shape =
                                            RoundedCornerShape(
                                                9.dp
                                            ),
                                        colors =
                                            OutlinedTextFieldDefaults
                                                .colors(
                                                    focusedBorderColor =
                                                        BluePrimary,
                                                    focusedLabelColor =
                                                        BluePrimary,
                                                    unfocusedBorderColor =
                                                        CardBorderDefault,
                                                    focusedTextColor =
                                                        TextDarkPrimary,
                                                    unfocusedTextColor =
                                                        TextDarkPrimary
                                                )
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {

                                val minF =
                                    minFareInput.toFloatOrNull()
                                        ?: settings.minFare

                                val maxF =
                                    maxFareInput.toFloatOrNull()
                                        ?: settings.maxFare

                                val maxP =
                                    maxPickupInput.toFloatOrNull()
                                        ?: settings.maxPickupDistanceKm

                                val maxD =
                                    maxDropInput.toFloatOrNull()
                                        ?: settings.maxDropDistanceKm

                                prefs.saveAppSettings(
                                    settings.copy(
                                        filterMode =
                                            pendingFilterMode,
                                        minFare = minF,
                                        maxFare = maxF,
                                        maxPickupDistanceKm =
                                            maxP,
                                        maxDropDistanceKm =
                                            maxD
                                    )
                                )

                                savedFilterMode =
                                    pendingFilterMode

                                hasUnsavedChanges =
                                    false

                                val savedModeLabel =
                                    when (
                                        pendingFilterMode
                                    ) {
                                        FilterMode.FARE_ONLY ->
                                            "Fare"

                                        FilterMode.DISTANCE_ONLY ->
                                            "Distance"

                                        FilterMode.BOTH ->
                                            "Both"
                                    }

                                coroutineScope.launch {
                                    snackbarHostState
                                        .showSnackbar(
                                            "$savedModeLabel settings saved ✓"
                                        )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag(
                                    "save_settings_button"
                                ),
                            shape =
                                RoundedCornerShape(11.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        BluePrimary,
                                    contentColor =
                                        Color.White
                                )
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier =
                                    Modifier.size(16.dp)
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(7.dp)
                            )

                            Text(
                                text = "Save Order Filter 1",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            // 7. Area Rules Engine card — COMPACT
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAreaManager() },
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorderDefault)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(BlueContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = null,
                                tint = BlueSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(9.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Area Rules Engine",
                                    fontWeight = FontWeight.Bold,
                                    color = TextDarkPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (activeAreaFilterCount > 0)
                                        Color(0xFFDCFCE7)
                                    else
                                        Color(0xFFF1F5F9),
                                    border = BorderStroke(
                                        1.dp,
                                        if (activeAreaFilterCount > 0)
                                            Color(0xFF86EFAC)
                                        else
                                            Color(0xFFE2E8F0)
                                    )
                                ) {
                                    Text(
                                        text = "$activeAreaFilterCount Active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeAreaFilterCount > 0)
                                            Color(0xFF15803D)
                                        else
                                            Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (activeAreaFilterCount > 0)
                                    "$activeGoToCount Go-To • $activeNoGoCount No-Go"
                                else
                                    "No active area rules",
                                color = TextDarkSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextDarkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Simulation Dialog
    if (showSimulateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSimulateDialog = false },
            title = {
                Text(
                    text = "Test Order Acceptance Overlay",
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose platform to test the live overlay (5-minute countdown timer, pickup & drop distances in km, full address, and X dismiss button):",
                        fontSize = 11.5.sp,
                        color = TextDarkSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(Platform.RAPIDO, Platform.UBER, Platform.OLA).forEach { plat ->
                            val isSelected = simPlatform == plat
                            Surface(
                                onClick = { simPlatform = plat },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) when (plat) {
                                    Platform.RAPIDO -> Color(0xFFF59E0B)
                                    Platform.UBER -> Color(0xFF18181B)
                                    Platform.OLA -> Color(0xFF10B981)
                                    else -> BluePrimary
                                } else Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (plat) {
                                            Platform.RAPIDO -> "⚡ Rapido"
                                            Platform.UBER -> "🚕 Uber"
                                            Platform.OLA -> "🚖 Ola"
                                            else -> plat.displayName
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }

                    // Preview of data
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val (pickupDist, dropDist, fare) = when (simPlatform) {
                                Platform.RAPIDO -> Triple(0.8f, 7.2f, 145f)
                                Platform.UBER -> Triple(1.4f, 14.5f, 275f)
                                Platform.OLA -> Triple(1.1f, 9.8f, 195f)
                                else -> Triple(1.0f, 8.0f, 150f)
                            }
                            Text(
                                text = "• Platform: ${simPlatform.displayName}  |  Fare: ₹${fare.toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDarkPrimary
                            )
                            Text(
                                text = "• Pickup: $pickupDist km  |  Drop: $dropDist km",
                                fontSize = 11.sp,
                                color = Color(0xFF047857),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• 5-min countdown timer with live auto-dismiss.",
                                fontSize = 10.5.sp,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "• Full pickup & drop addresses with X button to close.",
                                fontSize = 10.5.sp,
                                color = TextDarkSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isOverlayGranted) {
                            showSimulateDialog = false
                            showOverlayDialog = true
                            return@Button
                        }
                        showSimulateDialog = false
                        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                        val now = System.currentTimeMillis()
                        data class SimRideData(
                            val pickupDistKm: Float,
                            val dropDistKm: Float,
                            val fare: Float,
                            val pickupAddress: String,
                            val dropAddress: String,
                            val dropArea: String
                        )

                        val simData = when (simPlatform) {
                            Platform.RAPIDO -> SimRideData(
                                pickupDistKm = 0.8f,
                                dropDistKm = 7.2f,
                                fare = 145f,
                                pickupAddress = "Sector 14 Metro Station Gate 2, Old DLF Colony, MG Road, Gurgaon",
                                dropAddress = "Building 10, DLF Cyber City Phase 2, DLF City, Gurgaon, Haryana 122002",
                                dropArea = "DLF Cyber City"
                            )
                            Platform.UBER -> SimRideData(
                                pickupDistKm = 1.4f,
                                dropDistKm = 14.5f,
                                fare = 275f,
                                pickupAddress = "Terminal 3 Departure Ramp, Indira Gandhi International Airport, New Delhi",
                                dropAddress = "Block A, Connaught Place, Inner Circle, Near Rajiv Chowk Metro Gate 5, New Delhi",
                                dropArea = "Connaught Place"
                            )
                            Platform.OLA -> SimRideData(
                                pickupDistKm = 1.1f,
                                dropDistKm = 9.8f,
                                fare = 195f,
                                pickupAddress = "H-No 42, 100 Feet Road, Near Sony Center, Indiranagar, Bengaluru",
                                dropAddress = "RMZ Ecospace, Outer Ring Road, Bellandur, Bengaluru, Karnataka 560103",
                                dropArea = "Bellandur Outer Ring Road"
                            )
                            else -> SimRideData(
                                pickupDistKm = 1.0f,
                                dropDistKm = 8.0f,
                                fare = 150f,
                                pickupAddress = "Main Market, Sector 18, Noida, Uttar Pradesh 201301",
                                dropAddress = "Tower B, Advant Navis Business Park, Sector 142, Noida, Uttar Pradesh",
                                dropArea = "Sector 142"
                            )
                        }

                        val simulated = OrderHistoryItem(
                            id = UUID.randomUUID().toString(),
                            timestamp = now,
                            dateStr = dateStr,
                            timeStr = timeStr,
                            status = OrderStatus.ACCEPTED,
                            platform = simPlatform,
                            vehicleType = userProfile.vehicleType,
                            pickupDistKm = simData.pickupDistKm,
                            dropDistKm = simData.dropDistKm,
                            pickupAddress = simData.pickupAddress,
                            dropAddress = simData.dropAddress,
                            dropArea = simData.dropArea,
                            amount = simData.fare,
                            bookingId = "CRN-${(10000..99999).random()}",
                            detectionTimeMs = now - 142L,
                            clickTimeMs = now,
                            baseFare = (simData.fare * 0.8f),
                            tipAmount = (simData.fare * 0.2f),
                            timesClicked = 1,
                            reason = "Fare ₹${simData.fare.toInt()} matched, Pickup ${String.format(Locale.ENGLISH, "%.1fkm", simData.pickupDistKm)} matched"
                        )
                        repository.recordOrderHistory(simulated)

                        // Trigger overlay
                        FloatingOverlayService.show(
                            context = context,
                            amount = simulated.amount,
                            pickup = simulated.pickupAddress,
                            pickupDist = simulated.pickupDistKm,
                            drop = simulated.dropAddress,
                            dropDist = simulated.dropDistKm,
                            dropArea = simulated.dropArea,
                            time = simulated.timeStr,
                            platform = simulated.platform.displayName
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Launch ${simPlatform.displayName} Overlay", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulateDialog = false }) {
                    Text("Cancel", color = TextDarkSecondary)
                }
            }
        )
    }

    // Accessibility Permission Explanation Dialog
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = {
                showAccessibilityDialog = false
                prefs.isPendingAutoAcceptActivation = false
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(BlueContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Accessibility,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Accessibility Service Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = TextDarkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SmartDrivo requires Accessibility permission to detect incoming ride orders from Rapido, Uber, and Ola, and auto-click the Accept button according to your fare and distance filters.",
                        fontSize = 11.sp,
                        color = TextDarkSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorderDefault)
                    ) {
                        Column(
                            modifier = Modifier.padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("1. Tap 'Enable in Settings' below", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                            Text("2. Locate and tap 'SmartDrivo'", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                            Text("3. Switch the toggle ON and allow", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                            Text("4. Return here — Auto-Accept activates automatically!", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDialog = false
                        prefs.isPendingAutoAcceptActivation = true
                        PermissionHelper.openAccessibilitySettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Enable in Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAccessibilityDialog = false
                        prefs.isPendingAutoAcceptActivation = false
                    }
                ) {
                    Text("Cancel", color = TextDarkSecondary)
                }
            }
        )
    }

    // Display Over Other Apps Permission Dialog
    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverlayDialog = false
                prefs.isPendingAutoAcceptActivation = false
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(BlueContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = BlueSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Display Over Other Apps",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = TextDarkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SmartDrivo needs 'Display over other apps' permission to show the floating ride details popup and accept overlay on top of Rapido, Uber, and Ola.",
                        fontSize = 11.sp,
                        color = TextDarkSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightSurface),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorderDefault)
                    ) {
                        Column(
                            modifier = Modifier.padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("1. Tap 'Enable in Settings' below", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                            Text("2. Find SmartDrivo and allow permission", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = TextDarkPrimary)
                            Text("3. Return here — Auto-Accept activates automatically!", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = BlueSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayDialog = false
                        prefs.isPendingAutoAcceptActivation = true
                        PermissionHelper.openOverlaySettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlueSecondary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Enable in Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayDialog = false
                        prefs.isPendingAutoAcceptActivation = false
                    }
                ) {
                    Text("Cancel", color = TextDarkSecondary)
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorderDefault)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 14.sp, color = TextDarkSecondary)
        }
    }
}

@Composable
private fun AutomationHealthCard(
    isAccessibilityActive: Boolean,
    isOverlayActive: Boolean,
    isAutoAcceptActive: Boolean,
    onClickAccessibility: () -> Unit,
    onClickOverlay: () -> Unit
) {
    val allHealthy = isAccessibilityActive && isOverlayActive && isAutoAcceptActive
    val bg = if (allHealthy) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
    val borderCol = if (allHealthy) Color(0xFFBBF7D0) else Color(0xFFFDE68A)
    val titleCol = if (allHealthy) Color(0xFF15803D) else Color(0xFFB45309)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (allHealthy) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = if (allHealthy) StatusActiveGreen else Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Automation Health",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = titleCol
                        )
                        Text(
                            text = if (allHealthy) "All systems ready & monitoring" else "Action required for full automation",
                            fontSize = 12.sp,
                            color = TextDarkSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (allHealthy) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, if (allHealthy) Color(0xFF86EFAC) else Color(0xFFFCD34D))
                ) {
                    Text(
                        text = if (allHealthy) "OPTIMAL" else "ATTENTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (allHealthy) Color(0xFF15803D) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Status items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthStatusChip(
                    label = "Accessibility",
                    isActive = isAccessibilityActive,
                    onClick = if (!isAccessibilityActive) onClickAccessibility else null,
                    modifier = Modifier.weight(1f)
                )
                HealthStatusChip(
                    label = "Overlay",
                    isActive = isOverlayActive,
                    onClick = if (!isOverlayActive) onClickOverlay else null,
                    modifier = Modifier.weight(1f)
                )
                HealthStatusChip(
                    label = "Auto-Accept",
                    isActive = isAutoAcceptActive,
                    onClick = null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HealthStatusChip(
    label: String,
    isActive: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val activeColor = StatusActiveGreen
    val inactiveColor = StatusInactiveRed
    val activeBg = Color(0xFFDCFCE7)
    val inactiveBg = Color(0xFFFEE2E2)

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) activeBg else inactiveBg,
        border = BorderStroke(1.dp, if (isActive) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isActive) "● $label" else "○ $label",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) activeColor else inactiveColor,
                maxLines = 1
            )
            Text(
                text = if (isActive) "Active" else if (onClick != null) "Enable" else "Off",
                fontSize = 10.sp,
                color = if (isActive) activeColor else inactiveColor
            )
        }
    }
}
