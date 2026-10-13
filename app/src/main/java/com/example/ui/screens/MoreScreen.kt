package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.example.model.VehicleType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.PreferencesManager
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorderDefault
import com.example.ui.theme.LightBackground
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusActiveGreenBg
import com.example.ui.theme.StatusInactiveRed
import com.example.ui.theme.StatusInactiveRedBg
import com.example.ui.theme.StatusInactiveRedBorder
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary
import com.example.ui.theme.TextDarkTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    prefs: PreferencesManager,
    onNavigateToPlanSelection: () -> Unit,
    onNavigateToPaymentHistory: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCommunity: () -> Unit = {},
    onNavigateToAdminPanel: () -> Unit = {},
    onLogout: () -> Unit,
    onBack: () -> Unit = {}
) {
    val userProfile by prefs.userProfile.collectAsState()
    val settings by prefs.appSettings.collectAsState()
    var showTutorialDialog by remember { mutableStateOf(false) }

    Scaffold(
        // SMARTDRIVO_ALL_PAGES_UP_V3
        // Parent layout already handles system bars.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BlueContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "More Options",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = TextDarkPrimary
                            )
                            Text(
                                text = "Account, membership & settings",
                                fontSize = 14.sp,
                                color = TextDarkSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Driver Profile Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorderDefault)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(BlueContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = BluePrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = userProfile.name.ifEmpty { "Driver Account" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextDarkPrimary
                                    )
                                    if (userProfile.isAdmin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(StatusActiveGreenBg, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "ADMIN",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusActiveGreen
                                            )
                                        }
                                    }
                                }
                                if (userProfile.phone.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "📱 ${userProfile.phone}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextDarkPrimary
                                    )
                                }
                                if (userProfile.city.isNotEmpty() || userProfile.state.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val locationText = listOf(userProfile.city, userProfile.state)
                                        .filter { it.isNotEmpty() }
                                        .joinToString(", ")
                                    Text(
                                        text = "📍 $locationText",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BluePrimary
                                    )
                                }
                                if (userProfile.email.isNotEmpty() && userProfile.email != userProfile.phone) {
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "✉️ ${userProfile.email}",
                                        fontSize = 11.sp,
                                        color = TextDarkSecondary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = CardBorderDefault)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Vehicle badge
                            Box(
                                modifier = Modifier
                                    .background(BlueContainer, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                val vIcon = when (userProfile.vehicleType) {
                                    VehicleType.BIKE -> "🏍️"
                                    VehicleType.AUTO -> "🛺"
                                    VehicleType.CAR -> "🚗"
                                }
                                val vLabel = when (userProfile.vehicleType) {
                                    VehicleType.AUTO -> "Auto Rickshaw"
                                    VehicleType.BIKE -> "Bike"
                                    VehicleType.CAR -> "Car"
                                }
                                Text(
                                    text = "$vIcon $vLabel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            }

                            // Membership Status badge
                            val isPlanActive = userProfile.isPlanValid || userProfile.isAdmin
                            val statusBg = if (isPlanActive) StatusActiveGreenBg else StatusInactiveRedBg
                            val statusText = if (isPlanActive) StatusActiveGreen else StatusInactiveRed
                            val planLabel = if (isPlanActive) "Active Plan" else "No Active Plan"

                            Box(
                                modifier = Modifier
                                    .background(statusBg, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "● $planLabel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusText
                                )
                            }
                        }
                    }
                }
            }


            // 2. MEMBERSHIP & BILLING (Unique options: Plans, Payment Receipts)
            item {
                SectionHeader("MEMBERSHIP & BILLING")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorderDefault)
                ) {
                    Column {
                        MoreCardRow(
                            icon = Icons.Default.WorkspacePremium,
                            title = "My Membership",
                            subtitle = "View current plan, remaining days & renewals",
                            onClick = onNavigateToPlanSelection
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = CardBorderDefault,
                            thickness = 1.dp
                        )
                        MoreCardRow(
                            icon = Icons.Default.ReceiptLong,
                            title = "Payment History",
                            subtitle = "Past UPI transactions & order receipts",
                            onClick = onNavigateToPaymentHistory
                        )
                    }
                }
            }

            // 3. NOTIFICATIONS
            item {
                SectionHeader("NOTIFICATIONS")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    ),
                    border = BorderStroke(
                        1.dp,
                        CardBorderDefault
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.saveAppSettings(
                                    settings.copy(
                                        showOrderReasonPopups =
                                            !settings.showOrderReasonPopups
                                    )
                                )
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 10.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Color(0xFFEDE9FE),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier =
                                    Modifier.size(18.dp)
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                text =
                                    "Order Reason Popups",
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color =
                                    TextDarkPrimary
                            )

                            Text(
                                text =
                                    "Show short accepted, ignored and rejected reasons over ride apps",
                                fontSize = 12.sp,
                                color =
                                    TextDarkSecondary
                            )
                        }

                        Switch(
                            checked =
                                settings
                                    .showOrderReasonPopups,
                            onCheckedChange = {
                                enabled ->
                                prefs.saveAppSettings(
                                    settings.copy(
                                        showOrderReasonPopups =
                                            enabled
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // 4. SUPPORT & HELP (Unique options: Tutorial, Telegram Community)
            item {
                SectionHeader("SUPPORT & HELP")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorderDefault)
                ) {
                    Column {
                        MoreCardRow(
                            icon = Icons.Default.School,
                            title = "SmartDrivo Driver Guide",
                            subtitle = "Complete guide to all SmartDrivo features",
                            onClick = { showTutorialDialog = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = CardBorderDefault,
                            thickness = 1.dp
                        )
                        MoreCardRow(
                            icon = Icons.Default.HeadsetMic,
                            title = "Contact SmartDrivo",
                            subtitle = "WhatsApp Channel & driver support",
                            onClick = onNavigateToCommunity
                        )
                    }
                }
            }


            // 5. ADMINISTRATIVE (Only if user has Admin privileges)
            if (userProfile.isAdmin) {
                item {
                    SectionHeader("ADMINISTRATIVE")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorderDefault)
                    ) {
                        MoreCardRow(
                            icon = Icons.Default.AdminPanelSettings,
                            title = "Admin Control Panel",
                            subtitle = "Manage users, subscriptions & system settings",
                            onClick = onNavigateToAdminPanel
                        )
                    }
                }
            }

            // 6. Log Out Button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusInactiveRedBg,
                        contentColor = StatusInactiveRed
                    ),
                    border = BorderStroke(1.dp, StatusInactiveRedBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log Out",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // 7. Footer
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SmartDrivo • v${BuildConfig.VERSION_NAME}",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextDarkTertiary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    // Complete SmartDrivo Driver Guide
    if (showTutorialDialog) {

        AlertDialog(
            onDismissRequest = {
                showTutorialDialog = false
            },

            title = {
                Column {
                    Text(
                        text = "SmartDrivo Driver Guide",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextDarkPrimary
                    )

                    Spacer(
                        modifier = Modifier.height(3.dp)
                    )

                    Text(
                        text = "Complete guide to features & controls",
                        fontSize = 12.sp,
                        color = TextDarkSecondary
                    )
                }
            },

            text = {

                val guideItems = listOf(

                    "1. Auto Accept — Master Switch" to
                        "Auto Accept is the main SmartDrivo switch. When ON, SmartDrivo can monitor eligible ride offers and use your saved settings. When OFF, live ride processing, Auto Reject, Bundle processing, area rules, automatic actions and new ride-history processing stay paused.",

                    "2. Auto Reject — No Condition Match" to
                        "When Auto Accept is ON and Auto Reject is also ON, an order that fails your active conditions can be skipped or rejected using the verified platform reject/skip control. If Auto Reject is OFF, unmatched orders are left for manual action.",

                    "3. Fare / Distance / Both Filters" to
                        "Choose how SmartDrivo evaluates normal ride offers. Fare mode checks your saved fare range. Distance mode checks pickup and drop distance limits. Both mode requires the active fare and distance conditions to match together.",

                    "4. Fastest Mode" to
                        "Fastest Mode is designed for quicker eligible-order processing. It prioritizes the Maximum Pickup Distance rule so nearby eligible rides can be handled with minimum delay. Your main fare and distance values are managed from the Home screen.",

                    "5. Bundle Order" to
                        "Bundle Order has its own ON/OFF control. When ON, detected bundle rides continue through your normal Go-To, No-Go, Fare, Distance or Both rules. When OFF, SmartDrivo will never auto-accept the bundle. If Auto Reject is ON it may be skipped; if Auto Reject is OFF it stays for manual action.",

                    "6. Supported Platforms" to
                        "Use the Filters screen to enable or disable Rapido, Uber and Ola individually. SmartDrivo processes only the platforms you have enabled.",

                    "7. Go-To Areas" to
                        "Create Go-To areas for locations you prefer to travel towards. When Go-To filtering is enabled, SmartDrivo compares the detected ride destination with your saved area rules and conditions.",

                    "8. No-Go Areas" to
                        "Create No-Go areas for destinations you want to avoid. Enabled No-Go rules can block an otherwise matching ride when its detected destination matches one of your saved restricted areas.",

                    "9. Ride History" to
                        "History keeps the processed ride result along with useful details such as platform, fare, pickup/drop distance, status and decision reason. It helps you understand why a ride was accepted, rejected or ignored.",

                    "10. Accepted Ride Overlay" to
                        "After a successful supported ride acceptance, SmartDrivo can show the compact floating overlay with platform, fare, pickup distance, drop distance and destination information. The overlay can also be closed manually.",

                    "11. SmartDrivo Notification" to
                        "When Auto Accept is ON, the notification panel shows SmartDrivo Active — Monitoring orders. When Auto Accept is OFF, SmartDrivo live automation is paused and the persistent active notification is removed.",

                    "12. Membership & Renewal" to
                        "My Membership shows your current plan, remaining days and expiry date. Available renewal plans and prices are loaded from SmartDrivo admin settings, so updated plan pricing can appear in the app automatically.",

                    "13. UPI & QR Payment" to
                        "After selecting a membership plan, SmartDrivo shows the admin UPI payment QR and a Pay with UPI Apps button. The selected plan amount is included in the payment request. After payment, enter the 12-digit UTR and submit it for admin approval.",

                    "14. Payment History" to
                        "Payment History shows your submitted membership payments and their current status, such as Pending, Approved or Rejected. Membership days are activated or extended after payment approval.",
                )


                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(
                            rememberScrollState()
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = "SmartDrivo helps drivers apply their own ride preferences consistently across supported driver apps.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BluePrimary
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )


                    guideItems.forEach { item ->

                        Text(
                            text = item.first,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextDarkPrimary
                        )

                        Text(
                            text = item.second,
                            fontSize = 12.5.sp,
                            color = TextDarkSecondary
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = "Note: SmartDrivo Accessibility Service must be enabled for supported live ride monitoring. Auto Accept remains the master ON/OFF control for SmartDrivo's live order processing.",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDarkSecondary
                    )
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showTutorialDialog = false
                    }
                ) {
                    Text(
                        text = "Got it",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BluePrimary
                    )
                }
            }
        )
    }
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

/**
 * Standard card row matching HomeScreen and SettingsScreen styling:
 * - BlueContainer box with BluePrimary icon
 * - Bold 15sp title
 * - Regular 13sp subtitle
 * - Trailing chevron
 */
@Composable
fun MoreCardRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(BlueContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BluePrimary,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextDarkPrimary
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                color = TextDarkSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextDarkTertiary,
            modifier = Modifier.size(17.dp)
        )
    }
}
