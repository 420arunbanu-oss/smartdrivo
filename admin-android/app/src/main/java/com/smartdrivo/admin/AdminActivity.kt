package com.smartdrivo.admin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private val Orange = Color(0xFFFF791A)
private val Page = Color(0xFF0E1219)
private val CardBackground = Color(0xFF1A212D)
private val Muted = Color(0xFFB3BFCD)

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Admin users, UTRs and payments must not appear in Android screenshots/recents.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val store = remember { AdminStore() }
            AdminApp(store, this)
        }
    }
}

@Composable
private fun AdminApp(store: AdminStore, activity: ComponentActivity) {
    val colors = darkColorScheme(
        primary = Orange, background = Page, surface = CardBackground,
        onBackground = Color.White, onSurface = Color.White,
        surfaceVariant = Color(0xFF222C39), onSurfaceVariant = Muted
    )
    MaterialTheme(colorScheme = colors) {
        when (store.access) {
            "ADMIN" -> DashboardShell(store)
            "CHECKING" -> Box(Modifier.fillMaxSize().background(Page), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Orange)
                    Spacer(Modifier.height(16.dp))
                    Text("Checking secure admin permissions…")
                }
            }
            else -> LoginScreen(store, activity)
        }
    }
}

@Composable
private fun LoginScreen(store: AdminStore, activity: ComponentActivity) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var googleError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().background(Page).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Orange, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text("SmartDrivo Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Secure driver and payments management", color = Muted)
        Spacer(Modifier.height(32.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = CardBackground) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("Admin Sign In", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Admin email") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Button(
                    onClick = { store.loginWithEmail(email, password); password = "" },
                    modifier = Modifier.fillMaxWidth(), enabled = !store.busy
                ) { Text("Sign In Securely") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val res = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
                                check(res != 0) { "Configure Firebase Google Sign-in and app SHA-1/SHA-256 first." }
                                val clientId = activity.getString(res)
                                val option = GetGoogleIdOption.Builder()
                                    .setServerClientId(clientId)
                                    .setFilterByAuthorizedAccounts(false)
                                    .build()
                                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                                val result = CredentialManager.create(activity).getCredential(
                                    context = activity, request = request
                                )
                                val credential = result.credential
                                check(credential is CustomCredential &&
                                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) { "Invalid Google authentication response." }
                                store.loginWithGoogleIdToken(
                                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                                )
                            } catch (e: Exception) {
                                googleError = e.localizedMessage ?: "Google sign-in could not start."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !store.busy
                ) { Text("Continue with Google") }
                TextButton(onClick = { store.resetPassword(email) }) { Text("Forgot Password?") }
                if (store.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (googleError.isNotBlank()) Text(googleError, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(18.dp))
        Icon(Icons.Default.Shield, contentDescription = null, tint = Orange)
        Text("Only active Firestore admins can access data", color = Muted)
        MessageBanner(store)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardShell(store: AdminStore) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartDrivo Admin", fontWeight = FontWeight.Bold)
                        Text(store.email, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                },
                actions = {
                    IconButton(onClick = { store.verifySession() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recheck admin session")
                    }
                    IconButton(onClick = { store.signOut() }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.Default.Dashboard, Icons.Default.Groups, Icons.Default.Payments, Icons.Default.Settings)
                val titles = listOf("Home", "Drivers", "Payments", "Settings")
                titles.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MessageBanner(store)
            if (store.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(store)
                    1 -> DriversScreen(store)
                    2 -> PaymentsScreen(store)
                    else -> SettingsScreen(store)
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(store: AdminStore) {
    if (store.message.isNotBlank()) {
        Surface(color = Color(0xFF462C20), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD19C))
                Text(store.message, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = store::dismissMessage) { Text("OK") }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Orange)
            Text(detail, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HomeScreen(store: AdminStore) {
    val active = store.drivers.count { it.isActive && !it.isBlocked }
    val pending = store.payments.count { it.status == "PENDING" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Live Firestore data · up to 200 most recently retrieved records", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Drivers", "${store.drivers.size}", "Visible accounts", Modifier.weight(1f))
            StatCard("Active", "$active", "Unlocked drivers", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Pending", "$pending", "Payments to review", Modifier.weight(1f))
            StatCard("Approved", "${store.payments.count { it.status == "APPROVED" }}", "Payments shown", Modifier.weight(1f))
        }
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Orange)
                    Spacer(Modifier.width(8.dp))
                    Text("Security Status", fontWeight = FontWeight.Bold)
                }
                Text("Admin role checked at sign-in and again before each write.")
                Text("Payments are protected by immutable approved-UTR reservations.", color = Muted)
                Text("Screen capture blocked; Firestore offline cache disabled.", color = Muted)
            }
        }
    }
}

@Composable
private fun DriversScreen(store: AdminStore) {
    var search by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Driver?>(null) }
    val shown = store.drivers.filter {
        val term = search.trim()
        term.isEmpty() || listOf(it.name, it.email, it.phone, it.uid, it.vehicleType)
            .any { field -> field.contains(term, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search driver, email, phone, UID") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        Text("${shown.size} drivers shown · Maximum 200 synced", color = Muted, modifier = Modifier.padding(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.uid }) { driver ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(driver.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (driver.isActive) "ACTIVE" else "LOCKED",
                                color = if (driver.isActive) Color(0xFF60D8AA) else Color(0xFFFF9797))
                        }
                        Text(driver.phone.ifBlank { driver.email }, color = Muted)
                        Text("${driver.vehicleType} · ${driver.plan} · Expires ${formatTime(driver.expiryMillis)}", color = Muted)
                        if (driver.device.isNotBlank()) Text("Device: ${driver.device}", color = Muted)
                        if (driver.isBlocked) Text("PERMANENT ACCOUNT BLOCK", color = Color(0xFFFF9797))
                        if (driver.isAdmin) Text("ADMIN ACCOUNT — PROTECTED", color = Orange)
                        if (!driver.isAdmin) {
                            OutlinedButton(onClick = { selected = driver }, enabled = !store.busy) {
                                Text(if (driver.isActive) "Deactivate Driver" else "Activate Driver")
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { driver ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Confirm driver status") },
            text = { Text("${if (driver.isActive) "Deactivate" else "Activate"} ${driver.name}? This changes the driver app's remote access.") },
            confirmButton = {
                Button(onClick = { store.setDriverActive(driver, !driver.isActive); selected = null }, enabled = !store.busy) {
                    Text("Confirm")
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PaymentsScreen(store: AdminStore) {
    var pendingOnly by rememberSaveable { mutableStateOf(true) }
    var target by remember { mutableStateOf<Payment?>(null) }
    var rejecting by remember { mutableStateOf(false) }
    var lastFour by remember { mutableStateOf("") }
    val shown = store.payments.filter { !pendingOnly || it.status == "PENDING" }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = pendingOnly, onClick = { pendingOnly = true },
                label = { Text("Pending") }, modifier = Modifier.padding(6.dp)
            )
            FilterChip(
                selected = !pendingOnly, onClick = { pendingOnly = false },
                label = { Text("All Payments") }
            )
        }
        Text("${shown.size} payments shown", color = Muted, modifier = Modifier.padding(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.paymentId }) { payment ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMoney(payment.amount), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = Orange, modifier = Modifier.weight(1f))
                            Text(payment.status, color = when (payment.status) {
                                "APPROVED" -> Color(0xFF60D8AA)
                                "REJECTED" -> Color(0xFFFF9797)
                                else -> Orange
                            })
                        }
                        Text("Plan: ${payment.plan} · UTR: ${payment.utr}")
                        Text("Driver UID: ${payment.uid}", color = Muted)
                        if (payment.status == "PENDING") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = {
                                    target = payment; rejecting = false; lastFour = ""
                                }, enabled = !store.busy) { Text("Approve") }
                                OutlinedButton(onClick = {
                                    target = payment; rejecting = true; lastFour = ""
                                }, enabled = !store.busy) { Text("Reject") }
                            }
                        }
                    }
                }
            }
        }
    }
    target?.let { p ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(if (rejecting) "Reject payment?" else "Approve membership?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${formatMoney(p.amount)} · ${p.plan}\nUTR: ${p.utr}\nDriver: ${p.uid}")
                    if (!rejecting) {
                        Text("Verify the payment externally first. Type the last 4 UTR digits to confirm:")
                        OutlinedTextField(
                            value = lastFour,
                            onValueChange = { lastFour = it.filter(Char::isDigit).take(4) },
                            label = { Text("Last 4 UTR digits") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejecting) store.rejectPayment(p) else store.approvePayment(p)
                        target = null
                    },
                    enabled = !store.busy && (rejecting ||
                        (AdminValidation.isValidUtr(p.utr) && lastFour == p.utr.takeLast(4)))
                ) { Text(if (rejecting) "Reject" else "Approve & Activate") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(store: AdminStore) {
    val current = store.settings
    var upi by remember(current) { mutableStateOf(current.upiId) }
    val keys = listOf("plan3Days", "plan7Days", "plan15Days", "plan1Month")
    val labels = listOf("3 Days", "7 Days", "15 Days", "1 Month")
    var priceFields by remember(current) {
        mutableStateOf(keys.associateWith { current.prices[it]?.toString().orEmpty() })
    }
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Membership & Payments Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Connected to Firestore /settings/global", color = Muted)
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Receiving UPI", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = upi, onValueChange = { upi = it.trim() }, label = { Text("UPI ID") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = "SmartDrivo", onValueChange = {}, readOnly = true,
                    label = { Text("Beneficiary") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Plan Prices (₹)", fontWeight = FontWeight.Bold)
                keys.forEachIndexed { index, key ->
                    OutlinedTextField(
                        value = priceFields[key].orEmpty(),
                        onValueChange = { next ->
                            priceFields = priceFields + (key to next.filter(Char::isDigit).take(6))
                        },
                        label = { Text(labels[index]) }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
            }
        }
        Button(onClick = { confirm = true }, enabled = !store.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save Admin Settings")
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Confirm settings changes") },
            text = { Text("This updates UPI and plan prices for SmartDrivo drivers. Continue?") },
            confirmButton = {
                Button(onClick = {
                    val values = priceFields.mapValues { it.value.toLongOrNull() ?: -1L }
                    store.saveSettings(upi, values)
                    confirm = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
