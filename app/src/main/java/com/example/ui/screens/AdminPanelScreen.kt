package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FirebaseRepository
import com.example.data.PreferencesManager
import com.example.model.PaymentStatus
import com.example.model.PaymentSubmission
import com.example.model.UserProfile
import com.example.model.VehicleType
import com.example.ui.components.QrCodeView
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AccentYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    prefs: PreferencesManager,
    repository: FirebaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val submissions by prefs.paymentSubmissions.collectAsState()
    val allUsers by prefs.allUsers.collectAsState()
    val currentUpiId by prefs.upiId.collectAsState()
    val currentLinks by prefs.communityLinks.collectAsState()
    val currentUserProfile by prefs.userProfile.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Users, 1: Payments, 2: UPI & QR, 3: Community

    // Attach Realtime Firestore Listeners to /users and /payments for live 2-way sync
    androidx.compose.runtime.DisposableEffect(Unit) {
        val usersListener = repository.listenToAllUsers()
        val paymentsListener = repository.listenToAllPayments()
        onDispose {
            usersListener?.remove()
            paymentsListener?.remove()
        }
    }

    // KPI Counters
    val totalUsersCount = allUsers.size
    val activeUsersCount = allUsers.count { it.isActive }
    val inactiveUsersCount = allUsers.count { !it.isActive }
    val pendingApprovalsCount by remember(submissions) {
        derivedStateOf { submissions.count { it.status == PaymentStatus.PENDING } }
    }

    // Duplicate UTRs counter across submissions
    val duplicateUtrMap = remember(submissions) {
        submissions.groupBy { it.utrNumber.trim() }.filter { it.key.isNotEmpty() && it.value.size > 1 }
    }
    val totalDuplicateUtrsCount = duplicateUtrMap.values.sumOf { it.size }

    // State for user management
    var userSearchQuery by remember { mutableStateOf("") }
    var userStatusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, INACTIVE
    var userForPlanEdit by remember { mutableStateOf<UserProfile?>(null) }

    // State for payment management
    var paymentSearchQuery by remember { mutableStateOf("") }
    var paymentStatusFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, APPROVED, REJECTED
    var paymentForUtrEdit by remember { mutableStateOf<PaymentSubmission?>(null) }
    var newUtrInput by remember { mutableStateOf("") }

    // Settings state
    var editedUpiId by remember(currentUpiId) { mutableStateOf(currentUpiId) }
    var editedWa by remember(currentLinks.whatsappUrl) { mutableStateOf(currentLinks.whatsappUrl) }
    var editedTg by remember(currentLinks.telegramUrl) { mutableStateOf(currentLinks.telegramUrl) }
    var editedIg by remember(currentLinks.instagramUrl) { mutableStateOf(currentLinks.instagramUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF00391A), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Admin Control Panel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "SmartDrivo Partner & Payment Management",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Metrics KPI Header
            AdminKpiHeader(
                totalUsers = totalUsersCount,
                activeUsers = activeUsersCount,
                inactiveUsers = inactiveUsersCount,
                pendingApprovals = pendingApprovalsCount,
                onFilterActiveClick = {
                    selectedTab = 0
                    userStatusFilter = "ACTIVE"
                },
                onFilterInactiveClick = {
                    selectedTab = 0
                    userStatusFilter = "INACTIVE"
                },
                onPendingApprovalsClick = {
                    selectedTab = 1
                    paymentStatusFilter = "PENDING"
                }
            )

            // Tab Navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AccentGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AccentGreen
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Users ($totalUsersCount)",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Payments",
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                            if (pendingApprovalsCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(AccentYellow, CircleShape)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$pendingApprovalsCount",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "UPI & QR",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(
                            text = "Community",
                            fontSize = 10.sp,
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // TAB 0: USER MANAGEMENT (SEARCH BY PHONE / EMAIL + ACTIVE/INACTIVE COUNT)
                    UserManagementSection(
                        allUsers = allUsers,
                        searchQuery = userSearchQuery,
                        onSearchChange = { userSearchQuery = it },
                        statusFilter = userStatusFilter,
                        onStatusFilterChange = { userStatusFilter = it },
                        onToggleActive = { _ -> },
                        onOpenPlanEdit = { user ->
                            userForPlanEdit = user
                        }
                    )
                }
                1 -> {
                    // TAB 1: PAYMENT APPROVALS WITH 12-DIGIT UTR VALIDATION & DUPLICATE BLOCKING
                    PaymentApprovalsSection(
                        submissions = submissions,
                        duplicateUtrMap = duplicateUtrMap,
                        totalDuplicateCount = totalDuplicateUtrsCount,
                        searchQuery = paymentSearchQuery,
                        onSearchChange = { paymentSearchQuery = it },
                        statusFilter = paymentStatusFilter,
                        onStatusFilterChange = { paymentStatusFilter = it },
                        onApprove = { sub ->
                            val cleanUtr = sub.utrNumber.trim()
                            // 12-Digit validation
                            if (cleanUtr.length != 12 || !cleanUtr.all { it.isDigit() }) {
                                Toast.makeText(context, "Cannot approve: UTR must be exactly 12 numeric digits!", Toast.LENGTH_LONG).show()
                                return@PaymentApprovalsSection
                            }

                            // Duplicate check: Verify this UTR is not already approved in another submission
                            val isAlreadyApprovedElsewhere = submissions.any {
                                it.paymentId != sub.paymentId &&
                                it.status == PaymentStatus.APPROVED &&
                                it.utrNumber.trim().equals(cleanUtr, ignoreCase = true)
                            }
                            if (isAlreadyApprovedElsewhere) {
                                Toast.makeText(
                                    context,
                                    "Approval Blocked! UTR #$cleanUtr has already been approved for another driver submission.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@PaymentApprovalsSection
                            }

                            // Secure approval:
                            // fresh duplicate lookup + permanent UTR lock +
                            // payment/user update in one Firestore transaction.
                            repository.adminApprovePayment(sub) { success, message ->
                                Toast.makeText(
                                    context,
                                    if (success) {
                                        "✓ $message"
                                    } else {
                                        "Approval Blocked: $message"
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },                        onReject = { sub ->
                            repository.adminRejectPayment(sub.paymentId) {
                                Toast.makeText(context, "Payment marked as REJECTED in Firestore", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEditUtr = { sub ->
                            paymentForUtrEdit = sub
                            newUtrInput = sub.utrNumber
                        },
                        onDeleteSubmission = { sub ->
                            prefs.deletePaymentSubmission(sub.paymentId)
                            Toast.makeText(context, "Submission removed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                2 -> {
                    // TAB 2: UPI ID & QR CODE CONFIGURATION
                    UpiSettingsSection(
                        currentUpiId = editedUpiId,
                        onUpiIdChange = { editedUpiId = it },
                        onSaveUpi = {
                            if (editedUpiId.trim().isNotEmpty()) {
                                prefs.updateUpiId(editedUpiId.trim())
                                Toast.makeText(context, "UPI ID Updated: ${editedUpiId.trim()}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enter a valid UPI ID", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                3 -> {
                    // TAB 3: COMMUNITY LINKS
                    CommunityLinksSection(
                        whatsappUrl = editedWa,
                        telegramUrl = editedTg,
                        instagramUrl = editedIg,
                        onWaChange = { editedWa = it },
                        onTgChange = { editedTg = it },
                        onIgChange = { editedIg = it },
                        onSave = {
                            prefs.updateCommunityLinks(editedWa.trim(), editedTg.trim(), editedIg.trim())
                            Toast.makeText(context, "Community Links Saved Successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Dialog for Admin to edit/correct UTR
    if (paymentForUtrEdit != null) {
        val targetPayment = paymentForUtrEdit!!
        AlertDialog(
            onDismissRequest = { paymentForUtrEdit = null },
            title = {
                Text("Edit 12-Digit UTR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Driver: ${targetPayment.userName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Correct the UTR number if the driver made a typo. It must be exactly 12 numeric digits.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newUtrInput,
                        onValueChange = { input ->
                            if (input.length <= 12 && input.all { it.isDigit() }) {
                                newUtrInput = input
                            }
                        },
                        label = { Text("12-Digit UTR") },
                        placeholder = { Text("e.g. 425619847231") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Text(
                        text = "${newUtrInput.length}/12 Digits",
                        fontSize = 9.sp,
                        color = if (newUtrInput.length == 12) AccentGreen else AccentYellow,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newUtrInput.trim()
                        if (clean.length == 12 && clean.all { it.isDigit() }) {
                            prefs.updatePaymentUtr(targetPayment.paymentId, clean)
                            Toast.makeText(context, "UTR updated to $clean", Toast.LENGTH_SHORT).show()
                            paymentForUtrEdit = null
                        } else {
                            Toast.makeText(context, "UTR must be exactly 12 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                ) {
                    Text("Save UTR")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentForUtrEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for Admin to assign/extend membership plan for a driver
    if (userForPlanEdit != null) {
        val targetUser = userForPlanEdit!!
        AlertDialog(
            onDismissRequest = { userForPlanEdit = null },
            title = {
                Text("Extend Driver Plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Driver: ${targetUser.name} (${targetUser.phone.ifEmpty { targetUser.email }})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Select membership plan duration to activate or extend:", fontSize = 9.5.sp)

                    // Plan Option Buttons
                    PlanChoiceButton(label = "3 Days Pass (₹59)", days = 3, planId = "3DAYS") {
                        prefs.extendUserPlan(targetUser.uid, 3, "3DAYS", 59)
                        prefs.allUsers.value.firstOrNull { it.uid == targetUser.uid }?.let { u ->
                            repository.adminUpdateUserProfile(u)
                        }
                        Toast.makeText(context, "Added 3 Days to ${targetUser.name}", Toast.LENGTH_SHORT).show()
                        userForPlanEdit = null
                    }
                    PlanChoiceButton(label = "7 Days Pass (₹129)", days = 7, planId = "7DAYS") {
                        prefs.extendUserPlan(targetUser.uid, 7, "7DAYS", 129)
                        prefs.allUsers.value.firstOrNull { it.uid == targetUser.uid }?.let { u ->
                            repository.adminUpdateUserProfile(u)
                        }
                        Toast.makeText(context, "Added 7 Days to ${targetUser.name}", Toast.LENGTH_SHORT).show()
                        userForPlanEdit = null
                    }
                    PlanChoiceButton(label = "15 Days Pass (₹199)", days = 15, planId = "15DAYS") {
                        prefs.extendUserPlan(targetUser.uid, 15, "15DAYS", 199)
                        prefs.allUsers.value.firstOrNull { it.uid == targetUser.uid }?.let { u ->
                            repository.adminUpdateUserProfile(u)
                        }
                        Toast.makeText(context, "Added 15 Days to ${targetUser.name}", Toast.LENGTH_SHORT).show()
                        userForPlanEdit = null
                    }
                    PlanChoiceButton(label = "1 Month Pass (₹329)", days = 30, planId = "1MONTH") {
                        prefs.extendUserPlan(targetUser.uid, 30, "1MONTH", 329)
                        prefs.allUsers.value.firstOrNull { it.uid == targetUser.uid }?.let { u ->
                            repository.adminUpdateUserProfile(u)
                        }
                        Toast.makeText(context, "Added 30 Days to ${targetUser.name}", Toast.LENGTH_SHORT).show()
                        userForPlanEdit = null
                    }
                    PlanChoiceButton(label = "1 Year Pass (₹2999)", days = 365, planId = "1YEAR") {
                        prefs.extendUserPlan(targetUser.uid, 365, "1YEAR", 2999)
                        prefs.allUsers.value.firstOrNull { it.uid == targetUser.uid }?.let { u ->
                            repository.adminUpdateUserProfile(u)
                        }
                        Toast.makeText(context, "Added 1 Year to ${targetUser.name}", Toast.LENGTH_SHORT).show()
                        userForPlanEdit = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { userForPlanEdit = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PlanChoiceButton(label: String, days: Int, planId: String, onSelect: () -> Unit) {
    OutlinedButton(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
            Text("+$days d", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
        }
    }
}

/**
 * Top KPI Summary Banner with Total, Active, Inactive, and Pending counts
 */
@Composable
private fun AdminKpiHeader(
    totalUsers: Int,
    activeUsers: Int,
    inactiveUsers: Int,
    pendingApprovals: Int,
    onFilterActiveClick: () -> Unit,
    onFilterInactiveClick: () -> Unit,
    onPendingApprovalsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Drivers Card
                KpiCard(
                    title = "TOTAL",
                    count = "$totalUsers",
                    sub = "Drivers",
                    badgeColor = Color(0xFF64748B),
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )

                // Active Drivers Card
                KpiCard(
                    title = "ACTIVE",
                    count = "$activeUsers",
                    sub = "Subscribed",
                    badgeColor = AccentGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onFilterActiveClick
                )

                // Inactive Drivers Card
                KpiCard(
                    title = "INACTIVE",
                    count = "$inactiveUsers",
                    sub = "Expired/Free",
                    badgeColor = AccentRed,
                    modifier = Modifier.weight(1f),
                    onClick = onFilterInactiveClick
                )

                // Pending Approvals Card
                KpiCard(
                    title = "PENDING",
                    count = "$pendingApprovals",
                    sub = "Payments",
                    badgeColor = AccentYellow,
                    modifier = Modifier.weight(1f),
                    onClick = onPendingApprovalsClick
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    count: String,
    sub: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
            Text(
                text = count,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                fontSize = 7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Section 1: Drivers & Users Management with search by phone/email and active/inactive toggle
 */
@Composable
private fun UserManagementSection(
    allUsers: List<UserProfile>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    onToggleActive: (UserProfile) -> Unit,
    onOpenPlanEdit: (UserProfile) -> Unit
) {
    val context = LocalContext.current

    // Filter users based on search query and status
    val filteredUsers = remember(allUsers, searchQuery, statusFilter) {
        allUsers.filter { user ->
            val matchesSearch = searchQuery.isBlank() ||
                    user.phone.contains(searchQuery, ignoreCase = true) ||
                    user.email.contains(searchQuery, ignoreCase = true) ||
                    user.name.contains(searchQuery, ignoreCase = true) ||
                    user.city.contains(searchQuery, ignoreCase = true) ||
                    user.state.contains(searchQuery, ignoreCase = true)

            val matchesStatus = when (statusFilter) {
                "ACTIVE" -> user.isActive
                "INACTIVE" -> !user.isActive
                else -> true
            }

            matchesSearch && matchesStatus
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search bar for phone & email
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search drivers by phone (+91...) or email...", fontSize = 10.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AccentGreen, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // Filter chips: All, Active, Inactive
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = statusFilter == "ALL",
                onClick = { onStatusFilterChange("ALL") },
                label = { Text("All (${allUsers.size})", fontSize = 9.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = statusFilter == "ACTIVE",
                onClick = { onStatusFilterChange("ACTIVE") },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(AccentGreen, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Active (${allUsers.count { it.isActive }})", fontSize = 9.sp)
                    }
                }
            )
            FilterChip(
                selected = statusFilter == "INACTIVE",
                onClick = { onStatusFilterChange("INACTIVE") },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(AccentRed, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Inactive (${allUsers.count { !it.isActive }})", fontSize = 9.sp)
                    }
                }
            )
        }

        // Driver list count summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing ${filteredUsers.size} of ${allUsers.size} registered drivers",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (filteredUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No drivers found matching \"$searchQuery\"",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { onSearchChange("") }) {
                            Text("Clear Search Query")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredUsers, key = { it.uid.ifEmpty { it.phone + it.email } }) { user ->
                    DriverUserCard(
                        user = user,
                        onToggleActive = { onToggleActive(user) },
                        onEditPlan = { onOpenPlanEdit(user) },
                        onCopyPhone = {
                            if (user.phone.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Phone", user.phone))
                                Toast.makeText(context, "Phone copied: ${user.phone}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCopyEmail = {
                            if (user.email.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Email", user.email))
                                Toast.makeText(context, "Email copied: ${user.email}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual Driver Partner Card with details, active/inactive badge, plan status, and actions
 */
@Composable
private fun DriverUserCard(
    user: UserProfile,
    onToggleActive: () -> Unit,
    onEditPlan: () -> Unit,
    onCopyPhone: () -> Unit,
    onCopyEmail: () -> Unit
) {
    val isExpired = user.planExpireMillis < System.currentTimeMillis() && !user.isAdmin
    val daysRemaining = remember(user.planExpireMillis) {
        val diff = user.planExpireMillis - System.currentTimeMillis()
        if (diff > 0) (diff / 86400000L).toInt() else 0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (user.isActive) AccentGreen.copy(alpha = 0.5f) else AccentRed.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Avatar, Name, Vehicle, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (user.isActive) Color(0xFF00391A) else Color(0xFF3B0B14),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (user.vehicleType) {
                            VehicleType.BIKE -> Icons.Default.TwoWheeler
                            VehicleType.CAR -> Icons.Default.DirectionsCar
                            else -> Icons.Default.DirectionsCar
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (user.isActive) AccentGreen else AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.name.ifEmpty { "Driver Partner" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (user.isAdmin) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2563EB), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("ADMIN", fontSize = 7.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            text = "Vehicle: ${user.vehicleType.name}",
                            fontSize = 8.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Active / Inactive Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (user.isActive) Color(0xFF00391A) else Color(0xFF3B0B14),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (user.isActive) "● ACTIVE" else "● INACTIVE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (user.isActive) AccentGreen else AccentRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Phone & Email Row (with quick copy buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Phone
                    if (user.phone.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onCopyPhone() }
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Phone", modifier = Modifier.size(12.dp), tint = AccentGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(user.phone, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Email
                    if (user.email.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onCopyEmail() }
                        ) {
                            Icon(Icons.Default.Email, contentDescription = "Email", modifier = Modifier.size(12.dp), tint = Color(0xFF60A5FA))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(user.email, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // City & State
                    if (user.city.isNotEmpty() || user.state.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = "Location", modifier = Modifier.size(12.dp), tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(4.dp))
                            val loc = listOf(user.city, user.state).filter { it.isNotEmpty() }.joinToString(", ")
                            Text(loc, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Plan info pill
                Column(horizontalAlignment = Alignment.End) {
                    val planDisplayName = when (user.plan.uppercase()) {
                        "3DAYS" -> "3 Days Pass (₹59)"
                        "7DAYS" -> "7 Days Pass (₹129)"
                        "15DAYS" -> "15 Days Pass (₹199)"
                        "1MONTH", "30DAYS" -> "1 Month Pass (₹329)"
                        "1YEAR" -> "1 Year Pass (₹2999)"
                        else -> user.plan
                    }
                    Text(
                        text = planDisplayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        color = AccentGreen
                    )
                    Text(
                        text = if (user.isAdmin) "No expiration (Admin)"
                        else if (isExpired) "Plan Expired"
                        else "$daysRemaining days remaining",
                        fontSize = 8.sp,
                        color = if (isExpired && !user.isAdmin) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Toggle Active / Inactive + Extend Plan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEditPlan,
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Extend Plan", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Section 2: Payment Approvals with 12-Digit UTR Validation & Duplicate Blocking
 */
@Composable
private fun PaymentApprovalsSection(
    submissions: List<PaymentSubmission>,
    duplicateUtrMap: Map<String, List<PaymentSubmission>>,
    totalDuplicateCount: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    onApprove: (PaymentSubmission) -> Unit,
    onReject: (PaymentSubmission) -> Unit,
    onEditUtr: (PaymentSubmission) -> Unit,
    onDeleteSubmission: (PaymentSubmission) -> Unit
) {
    val pendingCount = submissions.count { it.status == PaymentStatus.PENDING }

    // Filter payments based on query and status
    val filteredSubmissions = remember(submissions, searchQuery, statusFilter) {
        submissions.filter { sub ->
            val matchesSearch = searchQuery.isBlank() ||
                    sub.utrNumber.contains(searchQuery, ignoreCase = true) ||
                    sub.userName.contains(searchQuery, ignoreCase = true) ||
                    sub.paymentId.contains(searchQuery, ignoreCase = true)

            val matchesStatus = when (statusFilter) {
                "PENDING" -> sub.status == PaymentStatus.PENDING
                "APPROVED" -> sub.status == PaymentStatus.APPROVED
                "REJECTED" -> sub.status == PaymentStatus.REJECTED
                else -> true
            }

            matchesSearch && matchesStatus
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // DUPLICATE UTR ALERT BANNER (If any duplicate UTR submissions exist)
        if (totalDuplicateCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B0B14)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AccentRed)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "Warning",
                        tint = AccentRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚠️ Duplicate UTR Detected ($totalDuplicateCount Submissions)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = AccentRed
                        )
                        Text(
                            text = "Multiple drivers submitted the same UTR number. Duplicate approvals are strictly blocked to prevent fraud.",
                            fontSize = 8.5.sp,
                            color = Color(0xFFFCA5A5)
                        )
                    }
                }
            }
        }

        // Search Bar for UTR or Driver Name
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by 12-digit UTR, Driver name or Phone...", fontSize = 10.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AccentGreen, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // Status Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = statusFilter == "ALL",
                    onClick = { onStatusFilterChange("ALL") },
                    label = { Text("All (${submissions.size})", fontSize = 9.sp) }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == "PENDING",
                    onClick = { onStatusFilterChange("PENDING") },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(AccentYellow, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pending ($pendingCount)", fontSize = 9.sp)
                        }
                    }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == "APPROVED",
                    onClick = { onStatusFilterChange("APPROVED") },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(AccentGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approved (${submissions.count { it.status == PaymentStatus.APPROVED }})", fontSize = 9.sp)
                        }
                    }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == "REJECTED",
                    onClick = { onStatusFilterChange("REJECTED") },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(AccentRed, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rejected (${submissions.count { it.status == PaymentStatus.REJECTED }})", fontSize = 9.sp)
                        }
                    }
                )
            }
        }

        if (filteredSubmissions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (submissions.isEmpty()) "No payment submissions yet" else "No payments match filter",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredSubmissions, key = { it.paymentId }) { sub ->
                    val isDuplicate = (duplicateUtrMap[sub.utrNumber.trim()]?.size ?: 0) > 1
                    PaymentCardWithUtrValidation(
                        submission = sub,
                        isDuplicateUtr = isDuplicate,
                        onApprove = { onApprove(sub) },
                        onReject = { onReject(sub) },
                        onEditUtr = { onEditUtr(sub) },
                        onDelete = { onDeleteSubmission(sub) }
                    )
                }
            }
        }
    }
}

/**
 * Payment Card with strict 12-digit UTR validation badge, duplicate notification, and approval logic
 */
@Composable
private fun PaymentCardWithUtrValidation(
    submission: PaymentSubmission,
    isDuplicateUtr: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEditUtr: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val cleanUtr = submission.utrNumber.trim()
    val is12DigitValid = cleanUtr.length == 12 && cleanUtr.all { it.isDigit() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isDuplicateUtr) AccentRed
            else if (submission.status == PaymentStatus.PENDING) AccentYellow.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Driver Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Driver: ${submission.userName.ifEmpty { "Driver Partner" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID: ${submission.paymentId}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Payment Status Badge
                val (badgeBg, badgeColor) = when (submission.status) {
                    PaymentStatus.APPROVED -> Pair(Color(0xFF00391A), AccentGreen)
                    PaymentStatus.REJECTED -> Pair(Color(0xFF3B0B14), AccentRed)
                    PaymentStatus.PENDING -> Pair(Color(0xFF332B00), AccentYellow)
                }

                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = submission.status.name,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount & Plan info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Amount: ₹${submission.amount}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentGreen
                )
                Text(
                    text = "Plan: ${submission.planSelected}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 12-DIGIT UTR VALIDATION BOX
            Surface(
                color = if (isDuplicateUtr) Color(0xFF2D070C) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDuplicateUtr) AccentRed
                    else if (!is12DigitValid) AccentRed
                    else AccentGreen
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UTR / REF NUMBER",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 12-Digit Validation Indicator
                        if (is12DigitValid) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Valid 12-Digits", fontSize = 8.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Invalid (${cleanUtr.length}/12 Digits)",
                                    fontSize = 8.sp,
                                    color = AccentRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Formatted UTR text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cleanUtr.ifEmpty { "NO UTR PROVIDED" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDuplicateUtr || !is12DigitValid) AccentRed else Color.White
                        )

                        Row {
                            // Copy UTR button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("UTR", cleanUtr))
                                    Toast.makeText(context, "UTR copied: $cleanUtr", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UTR", modifier = Modifier.size(14.dp), tint = AccentGreen)
                            }

                            // Edit UTR button
                            IconButton(
                                onClick = onEditUtr,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit UTR", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Duplicate Warning Banner inside card
                    if (isDuplicateUtr) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🛑 DUPLICATE UTR: Already submitted in another payment!",
                                fontSize = 8.sp,
                                color = AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Submission date & timestamp
            Text(
                text = "Submitted: ${SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(submission.submittedAt))}",
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // APPROVAL ACTIONS (For PENDING submissions)
            if (submission.status == PaymentStatus.PENDING) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Approve Button with 12-Digit Validation
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (is12DigitValid && !isDuplicateUtr) AccentGreen else Color(0xFF1B4D2E),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1.2f).height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve Payment", fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                    }

                    // Reject Button
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White),
                        modifier = Modifier.weight(0.9f).height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Section 3: UPI ID and QR settings
 */
@Composable
private fun UpiSettingsSection(
    currentUpiId: String,
    onUpiIdChange: (String) -> Unit,
    onSaveUpi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Configure Admin Payment QR & UPI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            "Drivers scan this QR code or send payment to this UPI ID to purchase membership plans. You can update this merchant UPI ID at any time.",
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = currentUpiId,
            onValueChange = onUpiIdChange,
            label = { Text("Admin UPI ID") },
            placeholder = { Text("e.g. gpay-11189725657@okaxis") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        // Live QR Code Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Live Driver QR Code Preview", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                Spacer(modifier = Modifier.height(10.dp))
                val sampleDeepLink = "upi://pay?pa=${currentUpiId.trim()}&pn=SmartDrivoAdmin&am=129&cu=INR&tn=SmartDrivoMembership"
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    QrCodeView(
                        content = sampleDeepLink,
                        size = 140.dp,
                        darkColor = Color.Black,
                        lightColor = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scan target: $currentUpiId", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(
            onClick = onSaveUpi,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Publish UPI ID", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Section 4: Community Links management
 */
@Composable
private fun CommunityLinksSection(
    whatsappUrl: String,
    telegramUrl: String,
    instagramUrl: String,
    onWaChange: (String) -> Unit,
    onTgChange: (String) -> Unit,
    onIgChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Manage Driver Community Links", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            "These links are shown to drivers on the Community screen and Help channels for support and community discussions.",
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = whatsappUrl,
            onValueChange = onWaChange,
            label = { Text("WhatsApp Driver Group Invite Link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        OutlinedTextField(
            value = telegramUrl,
            onValueChange = onTgChange,
            label = { Text("Telegram Channel Link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        OutlinedTextField(
            value = instagramUrl,
            onValueChange = onIgChange,
            label = { Text("Instagram Profile URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Update Community Links", fontWeight = FontWeight.Bold)
        }
    }
}
