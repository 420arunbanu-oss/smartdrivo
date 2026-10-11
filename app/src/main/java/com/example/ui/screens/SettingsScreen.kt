package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.LightSurface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PreferencesManager
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorderDefault
import com.example.ui.theme.LightBackground
import com.example.ui.theme.PlatformOla
import com.example.ui.theme.PlatformRapido
import com.example.ui.theme.PlatformUber
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.theme.TextDarkTertiary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    onNavigateToAdminWeb: () -> Unit = {},
    onBack: () -> Unit
) {
    val settings by prefs.appSettings.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }

    val showSavedSnackbar: () -> Unit = {
        snackbarJob?.cancel()
        snackbarJob = coroutineScope.launch {
            val displayJob = launch {
                snackbarHostState.showSnackbar(
                    message = "Smart Filter Saved ✓",
                    duration = SnackbarDuration.Indefinite
                )
            }
            delay(2000L)
            snackbarHostState.currentSnackbarData?.dismiss()
            displayJob.cancel()
        }
    }

    var minFareText by rememberSaveable {
        mutableStateOf(
            if (settings.minFare > 0) {
                if (settings.minFare % 1.0f == 0f) settings.minFare.toInt().toString() else settings.minFare.toString()
            } else "50"
        )
    }
    var maxFareText by rememberSaveable {
        mutableStateOf(
            if (settings.maxFare > 0) {
                if (settings.maxFare % 1.0f == 0f) settings.maxFare.toInt().toString() else settings.maxFare.toString()
            } else "999"
        )
    }
    var maxPickupText by rememberSaveable {
        mutableStateOf(
            if (settings.maxPickupDistanceKm > 0) {
                if (settings.maxPickupDistanceKm % 1.0f == 0f) settings.maxPickupDistanceKm.toInt().toString() else settings.maxPickupDistanceKm.toString()
            } else "3.0"
        )
    }
    var maxDropText by rememberSaveable {
        mutableStateOf(
            if (settings.maxDropDistanceKm > 0) {
                if (settings.maxDropDistanceKm % 1.0f == 0f) settings.maxDropDistanceKm.toInt().toString() else settings.maxDropDistanceKm.toString()
            } else "7.5"
        )
    }


    // Separate Both filter inputs - independent from Home page.
    var secondaryMinFareText by rememberSaveable {
        mutableStateOf(
            if (settings.secondaryBothMinFare % 1.0f == 0f)
                settings.secondaryBothMinFare.toInt().toString()
            else
                settings.secondaryBothMinFare.toString()
        )
    }

    var secondaryMaxPickupText by rememberSaveable {
        mutableStateOf(
            if (settings.secondaryBothMaxPickupDistanceKm % 1.0f == 0f)
                settings.secondaryBothMaxPickupDistanceKm.toInt().toString()
            else
                settings.secondaryBothMaxPickupDistanceKm.toString()
        )
    }

    var secondaryMaxDropText by rememberSaveable {
        mutableStateOf(
            if (settings.secondaryBothMaxDropDistanceKm % 1.0f == 0f)
                settings.secondaryBothMaxDropDistanceKm.toInt().toString()
            else
                settings.secondaryBothMaxDropDistanceKm.toString()
        )
    }

    var tertiaryMinFareText by rememberSaveable {
        mutableStateOf(
            if (settings.tertiaryBothMinFare % 1.0f == 0f)
                settings.tertiaryBothMinFare.toInt().toString()
            else
                settings.tertiaryBothMinFare.toString()
        )
    }

    var tertiaryMaxPickupText by rememberSaveable {
        mutableStateOf(
            if (settings.tertiaryBothMaxPickupDistanceKm % 1.0f == 0f)
                settings.tertiaryBothMaxPickupDistanceKm.toInt().toString()
            else
                settings.tertiaryBothMaxPickupDistanceKm.toString()
        )
    }

    var tertiaryMaxDropText by rememberSaveable {
        mutableStateOf(
            if (settings.tertiaryBothMaxDropDistanceKm % 1.0f == 0f)
                settings.tertiaryBothMaxDropDistanceKm.toInt().toString()
            else
                settings.tertiaryBothMaxDropDistanceKm.toString()
        )
    }

    // PLUS_AMOUNT_FILTER_UI_V1
    var plusAmountMinText by rememberSaveable { mutableStateOf(if (settings.plusAmountMin % 1.0f == 0f) settings.plusAmountMin.toInt().toString() else settings.plusAmountMin.toString()) }
    var plusAmountMaxPickupText by rememberSaveable { mutableStateOf(if (settings.plusAmountMaxPickupDistanceKm % 1.0f == 0f) settings.plusAmountMaxPickupDistanceKm.toInt().toString() else settings.plusAmountMaxPickupDistanceKm.toString()) }
    var plusAmountMaxDropText by rememberSaveable { mutableStateOf(if (settings.plusAmountMaxDropDistanceKm % 1.0f == 0f) settings.plusAmountMaxDropDistanceKm.toInt().toString() else settings.plusAmountMaxDropDistanceKm.toString()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Smart Filter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextDarkPrimary
                        )
                        Text(
                            text = "Configure fastest auto-accept mode",
                            fontSize = 14.sp,
                            color = TextDarkSecondary
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextDarkPrimary
                )
            )
        },
        containerColor = LightBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. FASTEST MODE
            // FASTEST_COMPACT_CARD_V1
            // ACTIVE_ONLY_CARD_COLOR_V3
            item {
                SectionHeader("1. FASTEST MODE")

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isFastestModeEnabled) BlueContainer else CardBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (settings.isFastestModeEnabled) BluePrimary else CardBorderDefault),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(9.dp)
                    ) {

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

                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(
                                                alpha = 0.75f
                                            ),
                                            RoundedCornerShape(9.dp)
                                        )
                                        .padding(
                                            horizontal = 9.dp,
                                            vertical = 5.dp
                                        )
                                ) {
                                    Text(
                                        text = "FASTEST",
                                        fontSize = 10.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                        color = BluePrimary
                                    )
                                }

                                Spacer(
                                    modifier =
                                        Modifier.height(7.dp)
                                )

                                Text(
                                    text = "Fastest Mode",
                                    fontWeight =
                                        FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextDarkPrimary
                                )

                                Text(
                                    text =
                                        "Pickup-only speed option",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(
                                    text =
                                        if (
                                            settings
                                                .isFastestModeEnabled
                                        )
                                            "ON"
                                        else
                                            "OFF",
                                    fontSize = 11.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color =
                                        if (
                                            settings
                                                .isFastestModeEnabled
                                        )
                                            BluePrimary
                                        else
                                            TextDarkSecondary
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(7.dp)
                                )

                                Switch(
                                    checked =
                                        settings
                                            .isFastestModeEnabled,
                                    onCheckedChange = {
                                        enabled ->

                                        val currentPickup =
                                            maxPickupText
                                                .toFloatOrNull()
                                                ?.takeIf {
                                                    it > 0f
                                                }
                                                ?: settings
                                                    .maxPickupDistanceKm
                                                    .takeIf {
                                                        it > 0f
                                                    }
                                                ?: 3.0f

                                        maxPickupText =
                                            if (
                                                currentPickup %
                                                    1.0f ==
                                                    0f
                                            ) {
                                                currentPickup
                                                    .toInt()
                                                    .toString()
                                            } else {
                                                currentPickup
                                                    .toString()
                                            }

                                        prefs.saveAppSettings(
                                            settings.copy(
                                                isFastestModeEnabled =
                                                    enabled,
                                                maxPickupDistanceKm =
                                                    currentPickup
                                            )
                                        )

                                        hasUnsavedChanges =
                                            false

                                        showSavedSnackbar()
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
                                                Color(
                                                    0xFFBDBDBD
                                                )
                                        )
                                )
                            }
                        }

                        Text(
                            text =
                                "Only Maximum Pickup Distance must match",
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            color = BluePrimary
                        )

                        if (
                            settings.isFastestModeEnabled
                        ) {

                            val pickupValue =
                                maxPickupText
                                    .toFloatOrNull()

                            val pickupValid =
                                pickupValue != null &&
                                    pickupValue > 0f

                            OutlinedTextField(
                                value = maxPickupText,
                                onValueChange = {
                                    input ->

                                    if (
                                        input.isEmpty() ||
                                        input.matches(
                                            Regex(
                                                """^\d*\.?\d*$"""
                                            )
                                        )
                                    ) {
                                        maxPickupText =
                                            input

                                        hasUnsavedChanges =
                                            true
                                    }
                                },
                                label = {
                                    Text(
                                        "Maximum Pickup (km)",
                                        fontSize = 10.sp
                                    )
                                },
                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType =
                                            KeyboardType.Decimal
                                    ),
                                singleLine = true,
                                modifier =
                                    Modifier.fillMaxWidth(),
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

                            Button(
                                onClick = {

                                    val maxPickup =
                                        maxPickupText
                                            .toFloatOrNull()

                                    if (
                                        maxPickup != null &&
                                        maxPickup > 0f
                                    ) {
                                        prefs.saveAppSettings(
                                            settings.copy(
                                                isFastestModeEnabled =
                                                    true,
                                                maxPickupDistanceKm =
                                                    maxPickup
                                            )
                                        )

                                        hasUnsavedChanges =
                                            false

                                        showSavedSnackbar()
                                    }
                                },
                                enabled = pickupValid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape =
                                    RoundedCornerShape(
                                        11.dp
                                    ),
                                colors =
                                    ButtonDefaults
                                        .buttonColors(
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
                                    text =
                                        "Save Fastest Mode",
                                    fontWeight =
                                        FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            // 2. ORDER FILTER 2 + ORDER FILTER 3
            item {
                SectionHeader("2. ORDER FILTERS")

                OrderFilterCompactCard(
                    badge = "FILTER 2",
                    title = "Order Filter 2",
                    subtitle = "Second ride option",
                    isChecked = settings.isSecondaryBothFilterEnabled,
                    minFareText = secondaryMinFareText,
                    maxPickupText = secondaryMaxPickupText,
                    maxDropText = secondaryMaxDropText,
                    onMinFareChange = { secondaryMinFareText = it },
                    onMaxPickupChange = { secondaryMaxPickupText = it },
                    onMaxDropChange = { secondaryMaxDropText = it },
                    onCheckedChange = { enabled ->
                        val minFare = secondaryMinFareText.toFloatOrNull()
                        val pickup = secondaryMaxPickupText.toFloatOrNull()
                        val drop = secondaryMaxDropText.toFloatOrNull()

                        val valid =
                            minFare != null && minFare > 0f &&
                                pickup != null && pickup > 0f &&
                                drop != null && drop > 0f

                        if (enabled && !valid) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Enter valid Filter 2 values first"
                                )
                            }
                        } else {
                            prefs.saveAppSettings(
                                settings.copy(
                                    isSecondaryBothFilterEnabled = enabled,
                                    secondaryBothMinFare =
                                        minFare ?: settings.secondaryBothMinFare,
                                    secondaryBothMaxPickupDistanceKm =
                                        pickup ?: settings.secondaryBothMaxPickupDistanceKm,
                                    secondaryBothMaxDropDistanceKm =
                                        drop ?: settings.secondaryBothMaxDropDistanceKm
                                )
                            )
                            showSavedSnackbar()
                        }
                    },
                    onSave = {
                        val minFare = secondaryMinFareText.toFloatOrNull()
                        val pickup = secondaryMaxPickupText.toFloatOrNull()
                        val drop = secondaryMaxDropText.toFloatOrNull()

                        if (
                            minFare != null && minFare > 0f &&
                            pickup != null && pickup > 0f &&
                            drop != null && drop > 0f
                        ) {
                            prefs.saveAppSettings(
                                settings.copy(
                                    secondaryBothMinFare = minFare,
                                    secondaryBothMaxPickupDistanceKm = pickup,
                                    secondaryBothMaxDropDistanceKm = drop
                                )
                            )
                            showSavedSnackbar()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OrderFilterCompactCard(
                    badge = "FILTER 3",
                    title = "Order Filter 3",
                    subtitle = "Third ride option",
                    isChecked = settings.isTertiaryBothFilterEnabled,
                    minFareText = tertiaryMinFareText,
                    maxPickupText = tertiaryMaxPickupText,
                    maxDropText = tertiaryMaxDropText,
                    onMinFareChange = { tertiaryMinFareText = it },
                    onMaxPickupChange = { tertiaryMaxPickupText = it },
                    onMaxDropChange = { tertiaryMaxDropText = it },
                    onCheckedChange = { enabled ->
                        val minFare = tertiaryMinFareText.toFloatOrNull()
                        val pickup = tertiaryMaxPickupText.toFloatOrNull()
                        val drop = tertiaryMaxDropText.toFloatOrNull()

                        val valid =
                            minFare != null && minFare > 0f &&
                                pickup != null && pickup > 0f &&
                                drop != null && drop > 0f

                        if (enabled && !valid) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Enter valid Filter 3 values first"
                                )
                            }
                        } else {
                            prefs.saveAppSettings(
                                settings.copy(
                                    isTertiaryBothFilterEnabled = enabled,
                                    tertiaryBothMinFare =
                                        minFare ?: settings.tertiaryBothMinFare,
                                    tertiaryBothMaxPickupDistanceKm =
                                        pickup ?: settings.tertiaryBothMaxPickupDistanceKm,
                                    tertiaryBothMaxDropDistanceKm =
                                        drop ?: settings.tertiaryBothMaxDropDistanceKm
                                )
                            )
                            showSavedSnackbar()
                        }
                    },
                    onSave = {
                        val minFare = tertiaryMinFareText.toFloatOrNull()
                        val pickup = tertiaryMaxPickupText.toFloatOrNull()
                        val drop = tertiaryMaxDropText.toFloatOrNull()

                        if (
                            minFare != null && minFare > 0f &&
                            pickup != null && pickup > 0f &&
                            drop != null && drop > 0f
                        ) {
                            prefs.saveAppSettings(
                                settings.copy(
                                    tertiaryBothMinFare = minFare,
                                    tertiaryBothMaxPickupDistanceKm = pickup,
                                    tertiaryBothMaxDropDistanceKm = drop
                                )
                            )
                            showSavedSnackbar()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OrderFilterCompactCard(
                    badge = "+ AMOUNT",
                    title = "+ Amount Filter",
                    subtitle = "Green +₹ amount option",
                    isChecked = settings.isPlusAmountFilterEnabled,
                    minFareText = plusAmountMinText,
                    maxPickupText = plusAmountMaxPickupText,
                    maxDropText = plusAmountMaxDropText,
                    primaryLabel = "Min + Amount ₹",
                    ruleText = "+ Amount + Pickup + Drop must all match",
                    onMinFareChange = { plusAmountMinText = it },
                    onMaxPickupChange = { plusAmountMaxPickupText = it },
                    onMaxDropChange = { plusAmountMaxDropText = it },
                    onCheckedChange = { enabled ->
                        val amount = plusAmountMinText.toFloatOrNull()
                        val pickup = plusAmountMaxPickupText.toFloatOrNull()
                        val drop = plusAmountMaxDropText.toFloatOrNull()
                        val valid = amount != null && amount > 0f && pickup != null && pickup > 0f && drop != null && drop > 0f
                        if (enabled && !valid) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Enter valid + Amount filter values first") }
                        } else {
                            prefs.saveAppSettings(settings.copy(
                                isPlusAmountFilterEnabled = enabled,
                                plusAmountMin = amount ?: settings.plusAmountMin,
                                plusAmountMaxPickupDistanceKm = pickup ?: settings.plusAmountMaxPickupDistanceKm,
                                plusAmountMaxDropDistanceKm = drop ?: settings.plusAmountMaxDropDistanceKm
                            ))
                            showSavedSnackbar()
                        }
                    },
                    onSave = {
                        val amount = plusAmountMinText.toFloatOrNull()
                        val pickup = plusAmountMaxPickupText.toFloatOrNull()
                        val drop = plusAmountMaxDropText.toFloatOrNull()
                        if (amount != null && amount > 0f && pickup != null && pickup > 0f && drop != null && drop > 0f) {
                            prefs.saveAppSettings(settings.copy(
                                plusAmountMin = amount,
                                plusAmountMaxPickupDistanceKm = pickup,
                                plusAmountMaxDropDistanceKm = drop
                            ))
                            showSavedSnackbar()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Accept if Home Filter, Filter 2, Filter 3 or +Amount matches.",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BluePrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            // 3. BUNDLE ORDER
            // BUNDLE_COMPACT_CARD_V1
            item {
                SectionHeader("3. BUNDLE ORDER")

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isBundleOrderEnabled) BlueContainer else CardBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (settings.isBundleOrderEnabled) BluePrimary else CardBorderDefault),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier =
                            Modifier.padding(11.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(9.dp)
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(
                                                alpha = 0.75f
                                            ),
                                            RoundedCornerShape(
                                                9.dp
                                            )
                                        )
                                        .padding(
                                            horizontal = 9.dp,
                                            vertical = 5.dp
                                        )
                                ) {
                                    Text(
                                        text = "BUNDLE",
                                        fontSize = 10.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                        color = BluePrimary
                                    )
                                }

                                Spacer(
                                    modifier =
                                        Modifier.height(7.dp)
                                )

                                Text(
                                    text = "Bundle Order",
                                    fontWeight =
                                        FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextDarkPrimary
                                )

                                Text(
                                    text =
                                        "Multiple-order ride option",
                                    fontSize = 11.sp,
                                    color = TextDarkSecondary
                                )
                            }

                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(
                                    text =
                                        if (
                                            settings
                                                .isBundleOrderEnabled
                                        )
                                            "ON"
                                        else
                                            "OFF",
                                    fontSize = 11.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    color =
                                        if (
                                            settings
                                                .isBundleOrderEnabled
                                        )
                                            BluePrimary
                                        else
                                            TextDarkSecondary
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(7.dp)
                                )

                                Switch(
                                    checked =
                                        settings
                                            .isBundleOrderEnabled,
                                    onCheckedChange = {
                                        enabled ->

                                        prefs.saveAppSettings(
                                            settings.copy(
                                                isBundleOrderEnabled =
                                                    enabled
                                            )
                                        )

                                        showSavedSnackbar()
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
                                                Color(
                                                    0xFFBDBDBD
                                                )
                                        )
                                )
                            }
                        }

                        Text(
                            text =
                                if (
                                    settings
                                        .isBundleOrderEnabled
                                ) {
                                    "Bundle orders use saved Fare + Distance + Area filters"
                                } else {
                                    "Bundle orders stay manual and will not be auto-accepted"
                                },
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                if (
                                    settings
                                        .isBundleOrderEnabled
                                )
                                    BluePrimary
                                else
                                    TextDarkSecondary
                        )
                    }
                }
            }
            // 4. SUPPORTED PLATFORMS (Rapido, Uber, Ola toggles)
            item {
                SectionHeader("4. SUPPORTED PLATFORMS")
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorderDefault),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Rapido Toggle
                        SettingToggleRow(
                            icon = Icons.Default.ElectricBolt,
                            iconTint = PlatformRapido,
                            title = "Rapido",
                            subtitle = "Auto-accept orders for Rapido Captain",
                            isChecked = settings.rapidoEnabled,
                            onCheckedChange = { isEnabled ->
                                prefs.saveAppSettings(settings.copy(rapidoEnabled = isEnabled))
                                showSavedSnackbar()
                            }
                        )

                        HorizontalDivider(color = CardBorderDefault)

                        // UBER_DISABLED_OLA_FOCUS_V1
                        // Uber hidden while SmartDrivo focuses on Ola.

                        // Ola Toggle
                        SettingToggleRow(
                            icon = Icons.Default.Layers,
                            iconTint = PlatformOla,
                            title = "Ola",
                            subtitle = "Auto-accept orders for Ola Driver",
                            isChecked = settings.olaEnabled,
                            onCheckedChange = { isEnabled ->
                                prefs.saveAppSettings(settings.copy(olaEnabled = isEnabled))
                                showSavedSnackbar()
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun OrderFilterCompactCard(
    badge: String,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    minFareText: String,
    maxPickupText: String,
    maxDropText: String,
    primaryLabel: String = "Min Fare ₹",
    ruleText: String = "Fare + Pickup + Drop must all match",
    onMinFareChange: (String) -> Unit,
    onMaxPickupChange: (String) -> Unit,
    onMaxDropChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    val minFare = minFareText.toFloatOrNull()
    val maxPickup = maxPickupText.toFloatOrNull()
    val maxDrop = maxDropText.toFloatOrNull()

    val valid =
        minFare != null && minFare > 0f &&
            maxPickup != null && maxPickup > 0f &&
            maxDrop != null && maxDrop > 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                if (isChecked) BlueContainer else CardBackground
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isChecked) BluePrimary else CardBorderDefault
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.75f),
                                RoundedCornerShape(9.dp)
                            )
                            .padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            )
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextDarkPrimary
                    )

                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextDarkSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isChecked) "ON" else "OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (isChecked) BluePrimary
                            else TextDarkSecondary
                    )

                    Spacer(modifier = Modifier.width(7.dp))

                    Switch(
                        checked = isChecked,
                        onCheckedChange = onCheckedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BluePrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFBDBDBD)
                        )
                    )
                }
            }

            Text(
                text = ruleText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = BluePrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                CompactFilterField(
                    value = minFareText,
                    label = primaryLabel,
                    modifier = Modifier.weight(1f),
                    onValueChange = onMinFareChange
                )

                CompactFilterField(
                    value = maxPickupText,
                    label = "Max Pickup",
                    modifier = Modifier.weight(1f),
                    onValueChange = onMaxPickupChange
                )

                CompactFilterField(
                    value = maxDropText,
                    label = "Max Drop",
                    modifier = Modifier.weight(1f),
                    onValueChange = onMaxDropChange
                )
            }

            Button(
                onClick = onSave,
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BluePrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(7.dp))

                Text(
                    text = "Save $title",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CompactFilterField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (
                input.isEmpty() ||
                input.matches(
                    Regex("""^\d*\.?\d*$""")
                )
            ) {
                onValueChange(input)
            }
        },
        label = {
            Text(
                text = label,
                fontSize = 9.sp,
                maxLines = 1
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BluePrimary,
            focusedLabelColor = BluePrimary,
            unfocusedBorderColor = CardBorderDefault,
            focusedTextColor = TextDarkPrimary,
            unfocusedTextColor = TextDarkPrimary
        )
    )
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = BluePrimary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = BluePrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(BlueContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(9.dp))
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextDarkPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextDarkSecondary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BluePrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFBDBDBD)
            )
        )
    }
}
