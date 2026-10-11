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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FirebaseRepository
import com.example.data.PreferencesManager
import com.example.model.AreaGroup
import com.example.model.AreaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private val GoToGreen = Color(0xFF1E88E5)
private val GoToGreenBg = Color(0xFFE3F2FD)
private val GoToGreenBorder = Color(0xFF90CAF9)
private val NoGoRed = Color(0xFFC62828)
private val NoGoRedBg = Color(0xFFFFEBEE)
private val NoGoRedBorder = Color(0xFFEF9A9A)

private fun cleanAreaGroups(groups: List<AreaGroup>): List<AreaGroup> =
    groups.map { group ->
        group.copy(
            keywords = group.keywords
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(group.name, ignoreCase = true) }
                .distinctBy { it.lowercase() },
            filtersEnabled = false
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaManagerScreen(
    prefs: PreferencesManager,
    repository: FirebaseRepository,
    onBack: () -> Unit
) {
    val storedGoTo by prefs.goToAreas.collectAsState()
    val storedNoGo by prefs.noGoAreas.collectAsState()

    var goToGroups by remember { mutableStateOf(cleanAreaGroups(prefs.goToAreas.value)) }
    var noGoGroups by remember { mutableStateOf(cleanAreaGroups(prefs.noGoAreas.value)) }
    var selectedType by remember { mutableStateOf(AreaType.GO_TO) }
    var editorGroup by remember { mutableStateOf<AreaGroup?>(null) }
    var showNewEditor by remember { mutableStateOf(false) }
    var deleteGroup by remember { mutableStateOf<AreaGroup?>(null) }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(storedGoTo) {
        val clean = cleanAreaGroups(storedGoTo)
        if (clean != goToGroups) goToGroups = clean
    }

    LaunchedEffect(storedNoGo) {
        val clean = cleanAreaGroups(storedNoGo)
        if (clean != noGoGroups) noGoGroups = clean
    }

    fun persist(go: List<AreaGroup>, no: List<AreaGroup>) {
        val cleanGo = cleanAreaGroups(go)
        val cleanNo = cleanAreaGroups(no)

        goToGroups = cleanGo
        noGoGroups = cleanNo

        prefs.isGoToEnabled = cleanGo.any { it.isEnabled && it.keywords.isNotEmpty() }
        prefs.isNoGoEnabled = cleanNo.any { it.isEnabled && it.keywords.isNotEmpty() }

        scope.launch(Dispatchers.IO) {
            prefs.saveAreaGroups(cleanGo, cleanNo)
            repository.syncAreas(cleanGo + cleanNo)
        }
    }

    fun saveGroup(saved: AreaGroup) {
        if (saved.name.isBlank()) return

        if (saved.type == AreaType.GO_TO) {
            val duplicate = goToGroups.any {
                it.id != saved.id && it.name.equals(saved.name, ignoreCase = true)
            }
            if (duplicate) {
                scope.launch {
                    snackbar.showSnackbar("Go-To group '${saved.name}' already exists")
                }
                return
            }

            val exists = goToGroups.any { it.id == saved.id }
            val updated = if (exists) {
                goToGroups.map { if (it.id == saved.id) saved else it }
            } else {
                goToGroups + saved
            }

            persist(updated, noGoGroups)

            val saveAction = if (exists) "updated" else "created"
            val status = if (saved.isEnabled && saved.keywords.isNotEmpty()) {
                "ACTIVE"
            } else {
                "OFF"
            }

            scope.launch {
                val message =
                    "Go-To '${saved.name}' $saveAction \u2713 \u2022 $status"
                snackbar.showSnackbar(message)
            }
        } else {
            val duplicate = noGoGroups.any {
                it.id != saved.id && it.name.equals(saved.name, ignoreCase = true)
            }
            if (duplicate) {
                scope.launch {
                    snackbar.showSnackbar("No-Go group '${saved.name}' already exists")
                }
                return
            }

            val exists = noGoGroups.any { it.id == saved.id }
            val updated = if (exists) {
                noGoGroups.map { if (it.id == saved.id) saved else it }
            } else {
                noGoGroups + saved
            }

            persist(goToGroups, updated)

            val saveAction = if (exists) "updated" else "created"
            val status = if (saved.isEnabled && saved.keywords.isNotEmpty()) {
                "ACTIVE"
            } else {
                "OFF"
            }

            scope.launch {
                val message =
                    "No-Go '${saved.name}' $saveAction \u2713 \u2022 $status"
                snackbar.showSnackbar(message)
            }
        }
    }
    val visibleGroups = if (selectedType == AreaType.GO_TO) goToGroups else noGoGroups
    val accent = if (selectedType == AreaType.GO_TO) GoToGreen else NoGoRed

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Area Preferences",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Preferred destinations and blocked areas",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorGroup = null
                    showNewEditor = true
                },
                containerColor = accent,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add group")
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(3.dp)
                ) {
                    CompactAreaTab(
                        text = "GO TO (${goToGroups.size})",
                        selected = selectedType == AreaType.GO_TO,
                        color = GoToGreen,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedType = AreaType.GO_TO
                    }

                    CompactAreaTab(
                        text = "NO GO (${noGoGroups.size})",
                        selected = selectedType == AreaType.NO_GO,
                        color = NoGoRed,
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedType = AreaType.NO_GO
                    }
                }
            }

            if (visibleGroups.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedType == AreaType.GO_TO) {
                                GoToGreenBg
                            } else {
                                NoGoRedBg
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selectedType == AreaType.GO_TO) {
                                GoToGreenBorder
                            } else {
                                NoGoRedBorder
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            if (selectedType == AreaType.GO_TO) {
                                "No Go-To groups. Tap + to create one."
                            } else {
                                "No No-Go groups. Tap + to create one."
                            },
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = visibleGroups,
                    key = { _, group -> group.id }
                ) { _, group ->
                    CompactAreaGroupCard(
                        group = group,
                        onEdit = {
                            editorGroup = group
                            showNewEditor = false
                        },
                        onToggle = {
                            if (group.type == AreaType.GO_TO) {
                                persist(
                                    goToGroups.map {
                                        if (it.id == group.id) {
                                            it.copy(isEnabled = !it.isEnabled)
                                        } else {
                                            it
                                        }
                                    },
                                    noGoGroups
                                )
                            } else {
                                persist(
                                    goToGroups,
                                    noGoGroups.map {
                                        if (it.id == group.id) {
                                            it.copy(isEnabled = !it.isEnabled)
                                        } else {
                                            it
                                        }
                                    }
                                )
                            }
                        },
                        onDelete = {
                            deleteGroup = group
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(86.dp))
            }
        }
    }

    if (showNewEditor || editorGroup != null) {
        GroupEditorDialog(
            group = editorGroup,
            type = editorGroup?.type ?: selectedType,
            onDismiss = {
                editorGroup = null
                showNewEditor = false
            },
            onSave = { saved ->
                saveGroup(saved)
                editorGroup = null
                showNewEditor = false
            }
        )
    }

    deleteGroup?.let { target ->
        Dialog(
            onDismissRequest = {
                deleteGroup = null
            }
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "Delete Group?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Are you sure you want to delete '${target.name}'? All area names inside this group will also be deleted.",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                deleteGroup = null
                            }
                        ) {
                            Text(
                                text = "No",
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (target.type == AreaType.GO_TO) {
                                    persist(
                                        goToGroups.filterNot { it.id == target.id },
                                        noGoGroups
                                    )
                                } else {
                                    persist(
                                        goToGroups,
                                        noGoGroups.filterNot { it.id == target.id }
                                    )
                                }
                                deleteGroup = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NoGoRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Yes, Delete",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAreaTab(
    text: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) color.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 13.sp,
                color = if (selected) color else Color(0xFF64748B),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CompactAreaGroupCard(
    group: AreaGroup,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    // AREA_CARD_SYSTEM_V3
    // OFF = white.
    // ON = Go-To blue / No-Go red.

    val isGoTo =
        group.type == AreaType.GO_TO

    val accent =
        if (isGoTo) {
            GoToGreen
        } else {
            NoGoRed
        }

    val activeBackground =
        if (isGoTo) {
            GoToGreenBg
        } else {
            NoGoRedBg
        }

    val cardBackground =
        if (group.isEnabled) {
            activeBackground
        } else {
            Color.White
        }

    val cardBorder =
        if (group.isEnabled) {
            accent
        } else {
            Color(0xFFE2E8F0)
        }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    cardBackground
            ),
        border =
            BorderStroke(
                1.dp,
                cardBorder
            ),
        shape =
            RoundedCornerShape(14.dp),
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(11.dp),
            verticalArrangement =
                Arrangement.spacedBy(9.dp)
        ) {

            // Header
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
                        modifier =
                            Modifier
                                .background(
                                    Color.White.copy(
                                        alpha = 0.78f
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
                            text =
                                if (isGoTo) {
                                    "GO TO"
                                } else {
                                    "NO GO"
                                },
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Bold,
                            color = accent
                        )
                    }

                    Spacer(
                        Modifier.height(7.dp)
                    )

                    Text(
                        text = group.name,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 15.sp,
                        color =
                            Color(0xFF0F172A),
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Text(
                        text =
                            if (isGoTo) {
                                "${group.keywords.size} destination areas"
                            } else {
                                "${group.keywords.size} blocked areas"
                            },
                        fontSize = 11.sp,
                        color =
                            Color(0xFF64748B)
                    )
                }

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            if (group.isEnabled) {
                                "ON"
                            } else {
                                "OFF"
                            },
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            if (group.isEnabled) {
                                accent
                            } else {
                                Color(0xFF64748B)
                            }
                    )

                    Spacer(
                        Modifier.width(7.dp)
                    )

                    Switch(
                        checked =
                            group.isEnabled,
                        onCheckedChange = {
                            onToggle()
                        },
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor =
                                    accent,
                                checkedThumbColor =
                                    Color.White,
                                uncheckedTrackColor =
                                    Color(0xFFBDBDBD),
                                uncheckedThumbColor =
                                    Color.White
                            )
                    )
                }
            }

            // Area preview
            if (group.keywords.isNotEmpty()) {

                Surface(
                    color =
                        Color.White.copy(
                            alpha =
                                if (group.isEnabled) {
                                    0.65f
                                } else {
                                    1f
                                }
                        ),
                    shape =
                        RoundedCornerShape(10.dp),
                    border =
                        if (group.isEnabled) {
                            null
                        } else {
                            BorderStroke(
                                1.dp,
                                Color(0xFFE2E8F0)
                            )
                        },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            group.keywords
                                .take(6)
                                .joinToString(
                                    "   \u2022   "
                                ),
                        modifier =
                            Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 7.dp
                            ),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color =
                            Color(0xFF475569),
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }

            // Go-To rules / No-Go rule
            if (isGoTo) {

                val goToMinFare =
                    if (group.maxFare > 0f) {
                        group.maxFare
                    } else {
                        group.minFare
                    }

                Text(
                    text =
                        "Fare + Pickup + Drop area rules",
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        if (group.isEnabled) {
                            accent
                        } else {
                            Color(0xFF64748B)
                        }
                )

                Surface(
                    color =
                        Color.White.copy(
                            alpha =
                                if (group.isEnabled) {
                                    0.65f
                                } else {
                                    1f
                                }
                        ),
                    shape =
                        RoundedCornerShape(10.dp),
                    border =
                        if (group.isEnabled) {
                            null
                        } else {
                            BorderStroke(
                                1.dp,
                                Color(0xFFE2E8F0)
                            )
                        },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "Min Fare: ${
                                if (goToMinFare > 0f) {
                                    "\u20B9${goToMinFare.toInt()}"
                                } else {
                                    "No minimum"
                                }
                            }   |   Pickup: ${
                                if (group.maxPickupKm > 0f) {
                                    "${group.maxPickupKm} km"
                                } else {
                                    "No limit"
                                }
                            }   |   Drop: ${
                                if (group.maxDropKm > 0f) {
                                    "${group.maxDropKm} km"
                                } else {
                                    "No limit"
                                }
                            }",
                        modifier =
                            Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 7.dp
                            ),
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.Medium,
                        color =
                            if (group.isEnabled) {
                                accent
                            } else {
                                Color(0xFF64748B)
                            },
                        maxLines = 2
                    )
                }

            } else {

                Text(
                    text =
                        "Matching destination areas will be blocked",
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        if (group.isEnabled) {
                            accent
                        } else {
                            Color(0xFF64748B)
                        }
                )
            }

            // Edit and delete
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Surface(
                    color =
                        Color.White.copy(
                            alpha = 0.80f
                        ),
                    shape =
                        RoundedCornerShape(10.dp),
                    border =
                        BorderStroke(
                            1.dp,
                            Color(0xFFE2E8F0)
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                onEdit()
                            }
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                vertical = 8.dp
                            ),
                        horizontalArrangement =
                            Arrangement.Center,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.Edit,
                            contentDescription =
                                "Edit group",
                            tint =
                                Color(0xFF475569),
                            modifier =
                                Modifier.size(16.dp)
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Text(
                            text = "Edit",
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                Color(0xFF475569)
                        )
                    }
                }

                Surface(
                    color =
                        Color.White.copy(
                            alpha = 0.80f
                        ),
                    shape =
                        RoundedCornerShape(10.dp),
                    border =
                        BorderStroke(
                            1.dp,
                            Color(0xFFE2E8F0)
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                onDelete()
                            }
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                vertical = 8.dp
                            ),
                        horizontalArrangement =
                            Arrangement.Center,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Default.Delete,
                            contentDescription =
                                "Delete group",
                            tint = NoGoRed,
                            modifier =
                                Modifier.size(16.dp)
                        )

                        Spacer(
                            Modifier.width(6.dp)
                        )

                        Text(
                            text = "Delete",
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.SemiBold,
                            color = NoGoRed
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun GroupEditorDialog(
    group: AreaGroup?,
    type: AreaType,
    onDismiss: () -> Unit,
    onSave: (AreaGroup) -> Unit
) {
    val isGoTo = type == AreaType.GO_TO
    val accent = if (isGoTo) GoToGreen else NoGoRed
    val bg = if (isGoTo) GoToGreenBg else NoGoRedBg
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val initialAreas = remember(group?.id, type) {
        if (isGoTo) {
            val saved = group?.areas.orEmpty()

            if (saved.isNotEmpty()) {
                saved
            } else {
                group?.keywords.orEmpty()
                    .flatMap { raw ->
                        raw.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                    }
                    .distinctBy { it.lowercase() }
                    .map { areaName ->
                        com.example.model.AreaEntry(
                            name = areaName,
                            minFare = group?.minFare ?: 0f,
                            maxPickupKm = group?.maxPickupKm ?: 0f,
                            maxDropKm = group?.maxDropKm ?: 0f
                        )
                    }
            }
        } else {
            emptyList()
        }
    }

    val initialKeywords = remember(group?.id, type) {
        if (isGoTo && initialAreas.isNotEmpty()) {
            initialAreas.map { it.name }
        } else {
            group?.keywords.orEmpty()
                .flatMap { raw ->
                    if (isGoTo) raw.split(",") else listOf(raw)
                }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
        }
    }

    var name by remember(group?.id, type) {
        mutableStateOf(group?.name.orEmpty())
    }

    var keywords by remember(group?.id, type) {
        mutableStateOf(initialKeywords)
    }

    var areaEntries by remember(group?.id, type) {
        mutableStateOf(initialAreas)
    }

    var areaInput by remember(group?.id, type) {
        mutableStateOf("")
    }

    var expandedIndex by remember(group?.id, type) {
        mutableStateOf<Int?>(null)
    }

    fun ensureEntriesSize() {
        if (!isGoTo) return

        val list = areaEntries.toMutableList()

        while (list.size < keywords.size) {
            val idx = list.size
            list.add(
                com.example.model.AreaEntry(
                    name = keywords
                        .getOrNull(idx)
                        .orEmpty()
                )
            )
        }

        areaEntries = list
    }

    fun submitArea() {
        val names = areaInput
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.equals(
                    name.trim(),
                    ignoreCase = true
                )
            }
            .distinctBy { it.lowercase() }

        if (names.isEmpty()) return

        val updatedKeywords =
            keywords.toMutableList()

        val updatedEntries =
            areaEntries.toMutableList()

        names.forEach { newName ->
            if (
                updatedKeywords.none {
                    it.equals(
                        newName,
                        ignoreCase = true
                    )
                }
            ) {
                updatedKeywords.add(newName)

                if (isGoTo) {
                    updatedEntries.add(
                        com.example.model.AreaEntry(
                            name = newName
                        )
                    )
                }
            }
        }

        keywords = updatedKeywords

        if (isGoTo) {
            areaEntries = updatedEntries
        }

        areaInput = ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false
            )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            tonalElevation = 7.dp,
            modifier =
                Modifier
                    .fillMaxWidth(0.88f)
                    .heightIn(
                        max = screenHeight * 0.92f
                    )
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 12.dp
                    )
            ) {
                Text(
                    text =
                        if (group == null) {
                            if (isGoTo) {
                                "New GO TO Areas"
                            } else {
                                "New NO GO Areas"
                            }
                        } else {
                            if (isGoTo) {
                                "Edit GO TO Areas"
                            } else {
                                "Edit NO GO Areas"
                            }
                        },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF111827)
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text(
                            "Group Name",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                    textStyle =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            cursorColor = accent
                        )
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text =
                            if (isGoTo) {
                                "Destination Areas (${keywords.size})"
                            } else {
                                "Blocked Areas (${keywords.size})"
                            },
                        modifier =
                            Modifier.weight(1f),
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF111827)
                    )
                }

                Spacer(Modifier.height(5.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = areaInput,
                        onValueChange = {
                            areaInput = it
                        },
                        placeholder = {
                            Text(
                                if (isGoTo) {
                                    "Hyderabad, Airport, etc"
                                } else {
                                    "e.g. Shaheen Nagar, Yetru Hills, Rizwan Colony"
                                },
                                fontSize = 12.sp
                            )
                        },
                        singleLine = isGoTo,
                        minLines =
                            if (isGoTo) 1 else 2,
                        maxLines =
                            if (isGoTo) 1 else 2,
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    submitArea()
                                }
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(
                                    if (isGoTo) 50.dp
                                    else 68.dp
                                ),
                        textStyle =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp
                            ),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                cursorColor = accent
                            )
                    )

                    Spacer(Modifier.width(7.dp))

                    Button(
                        onClick = {
                            submitArea()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = accent
                            ),
                        shape =
                            RoundedCornerShape(14.dp),
                        modifier =
                            Modifier
                                .width(76.dp)
                                .height(46.dp)
                    ) {
                        Text(
                            "Add",
                            fontSize = 13.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (keywords.isEmpty()) {
                    Surface(
                        color = bg.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Text(
                                "No areas added yet",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        verticalArrangement =
                            Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(
                            items = keywords,
                            key = { _, word ->
                                word.lowercase()
                            }
                        ) { index, word ->

                            if (isGoTo) {
                                ensureEntriesSize()

                                val currentEntry =
                                    areaEntries
                                        .getOrNull(index)
                                        ?: com.example.model.AreaEntry(
                                            name = word
                                        )

                                var fareText by remember(
                                    group?.id,
                                    word
                                ) {
                                    mutableStateOf(
                                        currentEntry.minFare
                                            .takeIf {
                                                it > 0f
                                            }
                                            ?.let {
                                                if (
                                                    it % 1f ==
                                                    0f
                                                ) {
                                                    it
                                                        .toInt()
                                                        .toString()
                                                } else {
                                                    it
                                                        .toString()
                                                }
                                            }
                                            .orEmpty()
                                    )
                                }

                                var pickupText by remember(
                                    group?.id,
                                    word
                                ) {
                                    mutableStateOf(
                                        currentEntry.maxPickupKm
                                            .takeIf {
                                                it > 0f
                                            }
                                            ?.let {
                                                if (
                                                    it % 1f ==
                                                    0f
                                                ) {
                                                    it
                                                        .toInt()
                                                        .toString()
                                                } else {
                                                    it
                                                        .toString()
                                                }
                                            }
                                            .orEmpty()
                                    )
                                }

                                var dropText by remember(
                                    group?.id,
                                    word
                                ) {
                                    mutableStateOf(
                                        currentEntry.maxDropKm
                                            .takeIf {
                                                it > 0f
                                            }
                                            ?.let {
                                                if (
                                                    it % 1f ==
                                                    0f
                                                ) {
                                                    it
                                                        .toInt()
                                                        .toString()
                                                } else {
                                                    it
                                                        .toString()
                                                }
                                            }
                                            .orEmpty()
                                    )
                                }

                                fun updateEntry(
                                    transform:
                                        (
                                            com.example.model.AreaEntry
                                        ) ->
                                            com.example.model.AreaEntry
                                ) {
                                    val list =
                                        areaEntries
                                            .toMutableList()

                                    while (
                                        list.size <= index
                                    ) {
                                        val idx = list.size

                                        list.add(
                                            com.example.model.AreaEntry(
                                                name =
                                                    keywords
                                                        .getOrNull(
                                                            idx
                                                        )
                                                        .orEmpty()
                                            )
                                        )
                                    }

                                    list[index] =
                                        transform(
                                            list[index].copy(
                                                name = word
                                            )
                                        )

                                    areaEntries = list
                                }

                                Surface(
                                    color =
                                        bg.copy(
                                            alpha = 0.48f
                                        ),
                                    shape =
                                        RoundedCornerShape(
                                            11.dp
                                        ),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.padding(
                                                horizontal =
                                                    10.dp,
                                                vertical =
                                                    5.dp
                                            )
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp),
                                            verticalAlignment =
                                                Alignment
                                                    .CenterVertically
                                        ) {
                                            Text(
                                                text = word,
                                                modifier =
                                                    Modifier
                                                        .weight(1f),
                                                fontSize =
                                                    13.sp,
                                                fontWeight =
                                                    FontWeight
                                                        .SemiBold,
                                                maxLines = 1,
                                                overflow =
                                                    TextOverflow
                                                        .Ellipsis
                                            )

                                            IconButton(
                                                onClick = {
                                                    expandedIndex =
                                                        if (
                                                            expandedIndex ==
                                                            index
                                                        ) {
                                                            null
                                                        } else {
                                                            index
                                                        }
                                                },
                                                modifier =
                                                    Modifier
                                                        .size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription =
                                                        "Edit filters",
                                                    tint = accent,
                                                    modifier =
                                                        Modifier
                                                            .size(
                                                                17.dp
                                                            )
                                                )
                                            }

                                            Spacer(
                                                Modifier.width(
                                                    2.dp
                                                )
                                            )

                                            IconButton(
                                                onClick = {
                                                    keywords =
                                                        keywords
                                                            .toMutableList()
                                                            .also {
                                                                if (
                                                                    index in
                                                                    it.indices
                                                                ) {
                                                                    it
                                                                        .removeAt(
                                                                            index
                                                                        )
                                                                }
                                                            }

                                                    areaEntries =
                                                        areaEntries
                                                            .toMutableList()
                                                            .also {
                                                                if (
                                                                    index in
                                                                    it.indices
                                                                ) {
                                                                    it
                                                                        .removeAt(
                                                                            index
                                                                        )
                                                                }
                                                            }

                                                    expandedIndex =
                                                        null
                                                },
                                                modifier =
                                                    Modifier
                                                        .size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription =
                                                        "Delete area",
                                                    tint = NoGoRed,
                                                    modifier =
                                                        Modifier
                                                            .size(
                                                                17.dp
                                                            )
                                                )
                                            }
                                        }

                                        if (
                                            expandedIndex ==
                                            index
                                        ) {
                                            Spacer(
                                                Modifier.height(
                                                    5.dp
                                                )
                                            )

                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth(),
                                                horizontalArrangement =
                                                    Arrangement
                                                        .spacedBy(
                                                            6.dp
                                                        )
                                            ) {
                                                OutlinedTextField(
                                                    value =
                                                        fareText,
                                                    onValueChange = {
                                                        raw ->
                                                        val input =
                                                            raw.replace(
                                                                ',',
                                                                '.'
                                                            )

                                                        if (
                                                            input
                                                                .isEmpty() ||
                                                            input
                                                                .matches(
                                                                    Regex(
                                                                        """^\d*\.?\d*$"""
                                                                    )
                                                                )
                                                        ) {
                                                            fareText =
                                                                input

                                                            updateEntry {
                                                                it.copy(
                                                                    minFare =
                                                                        input
                                                                            .toFloatOrNull()
                                                                            ?: 0f
                                                                )
                                                            }
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            "Min Fare",
                                                            fontSize =
                                                                9.sp
                                                        )
                                                    },
                                                    singleLine =
                                                        true,
                                                    keyboardOptions =
                                                        KeyboardOptions(
                                                            keyboardType =
                                                                KeyboardType
                                                                    .Decimal
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .weight(
                                                                1f
                                                            )
                                                            .height(
                                                                54.dp
                                                            ),
                                                    textStyle =
                                                        androidx.compose.ui.text.TextStyle(
                                                            fontSize =
                                                                13.sp
                                                        )
                                                )

                                                OutlinedTextField(
                                                    value =
                                                        pickupText,
                                                    onValueChange = {
                                                        raw ->
                                                        val input =
                                                            raw.replace(
                                                                ',',
                                                                '.'
                                                            )

                                                        if (
                                                            input
                                                                .isEmpty() ||
                                                            input
                                                                .matches(
                                                                    Regex(
                                                                        """^\d*\.?\d*$"""
                                                                    )
                                                                )
                                                        ) {
                                                            pickupText =
                                                                input

                                                            updateEntry {
                                                                it.copy(
                                                                    maxPickupKm =
                                                                        input
                                                                            .toFloatOrNull()
                                                                            ?: 0f
                                                                )
                                                            }
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            "Max Pickup",
                                                            fontSize =
                                                                8.5.sp,
                                                            maxLines =
                                                                1
                                                        )
                                                    },
                                                    singleLine =
                                                        true,
                                                    keyboardOptions =
                                                        KeyboardOptions(
                                                            keyboardType =
                                                                KeyboardType
                                                                    .Decimal
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .weight(
                                                                1f
                                                            )
                                                            .height(
                                                                54.dp
                                                            ),
                                                    textStyle =
                                                        androidx.compose.ui.text.TextStyle(
                                                            fontSize =
                                                                13.sp
                                                        )
                                                )

                                                OutlinedTextField(
                                                    value =
                                                        dropText,
                                                    onValueChange = {
                                                        raw ->
                                                        val input =
                                                            raw.replace(
                                                                ',',
                                                                '.'
                                                            )

                                                        if (
                                                            input
                                                                .isEmpty() ||
                                                            input
                                                                .matches(
                                                                    Regex(
                                                                        """^\d*\.?\d*$"""
                                                                    )
                                                                )
                                                        ) {
                                                            dropText =
                                                                input

                                                            updateEntry {
                                                                it.copy(
                                                                    maxDropKm =
                                                                        input
                                                                            .toFloatOrNull()
                                                                            ?: 0f
                                                                )
                                                            }
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            "Max Drop",
                                                            fontSize =
                                                                9.sp,
                                                            maxLines =
                                                                1
                                                        )
                                                    },
                                                    singleLine =
                                                        true,
                                                    keyboardOptions =
                                                        KeyboardOptions(
                                                            keyboardType =
                                                                KeyboardType
                                                                    .Decimal
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .weight(
                                                                1f
                                                            )
                                                            .height(
                                                                54.dp
                                                            ),
                                                    textStyle =
                                                        androidx.compose.ui.text.TextStyle(
                                                            fontSize =
                                                                13.sp
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color =
                                        bg.copy(
                                            alpha = 0.50f
                                        ),
                                    shape =
                                        RoundedCornerShape(
                                            11.dp
                                        ),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .padding(
                                                    horizontal =
                                                        10.dp
                                                ),
                                        verticalAlignment =
                                            Alignment
                                                .CenterVertically
                                    ) {
                                        Text(
                                            word,
                                            modifier =
                                                Modifier
                                                    .weight(1f),
                                            fontSize =
                                                13.sp,
                                            fontWeight =
                                                FontWeight
                                                    .Medium,
                                            maxLines = 1,
                                            overflow =
                                                TextOverflow
                                                    .Ellipsis
                                        )

                                        IconButton(
                                            onClick = {
                                                keywords =
                                                    keywords
                                                        .toMutableList()
                                                        .also {
                                                            if (
                                                                index in
                                                                it.indices
                                                            ) {
                                                                it
                                                                    .removeAt(
                                                                        index
                                                                    )
                                                            }
                                                        }
                                            },
                                            modifier =
                                                Modifier
                                                    .size(
                                                        30.dp
                                                    )
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription =
                                                    "Delete area",
                                                tint = NoGoRed,
                                                modifier =
                                                    Modifier
                                                        .size(
                                                            17.dp
                                                        )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .weight(0.42f)
                                .height(44.dp)
                    ) {
                        Text(
                            "Cancel",
                            color =
                                Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight =
                                FontWeight.Medium
                        )
                    }

                    Button(
                        enabled =
                            name.trim().isNotBlank() &&
                                keywords.isNotEmpty(),
                        onClick = {
                            val cleanKeywords =
                                keywords
                                    .map {
                                        it.trim()
                                    }
                                    .filter {
                                        it.isNotBlank()
                                    }
                                    .distinctBy {
                                        it.lowercase()
                                    }

                            val cleanAreas =
                                if (isGoTo) {
                                    cleanKeywords.mapIndexed {
                                        index,
                                        areaName ->

                                        areaEntries
                                            .getOrNull(index)
                                            ?.copy(
                                                name =
                                                    areaName
                                            )
                                            ?: com.example.model.AreaEntry(
                                                name =
                                                    areaName
                                            )
                                    }
                                } else {
                                    group?.areas
                                        .orEmpty()
                                }

                            val firstArea =
                                cleanAreas
                                    .firstOrNull()

                            onSave(
                                AreaGroup(
                                    id =
                                        group?.id
                                            ?: UUID
                                                .randomUUID()
                                                .toString(),
                                    name =
                                        name.trim(),
                                    isEnabled =
                                        group?.isEnabled
                                            ?: true,
                                    type = type,
                                    keywords =
                                        cleanKeywords,
                                    areas =
                                        cleanAreas,
                                    filtersEnabled =
                                        false,
                                    minFare =
                                        if (isGoTo) {
                                            firstArea
                                                ?.minFare
                                                ?: 0f
                                        } else {
                                            group
                                                ?.minFare
                                                ?: 0f
                                        },
                                    maxFare =
                                        if (isGoTo) {
                                            0f
                                        } else {
                                            group
                                                ?.maxFare
                                                ?: 0f
                                        },
                                    minPickupKm =
                                        group
                                            ?.minPickupKm
                                            ?: 0f,
                                    maxPickupKm =
                                        if (isGoTo) {
                                            firstArea
                                                ?.maxPickupKm
                                                ?: 0f
                                        } else {
                                            group
                                                ?.maxPickupKm
                                                ?: 0f
                                        },
                                    maxDropKm =
                                        if (isGoTo) {
                                            firstArea
                                                ?.maxDropKm
                                                ?: 0f
                                        } else {
                                            group
                                                ?.maxDropKm
                                                ?: 0f
                                        }
                                )
                            )
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    accent
                            ),
                        shape =
                            RoundedCornerShape(
                                18.dp
                            ),
                        modifier =
                            Modifier
                                .weight(0.58f)
                                .height(44.dp)
                    ) {
                        Text(
                            "Save Group",
                            fontSize = 13.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
