package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UnlimitedFilterRule
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CardBorderDefault
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

// SMARTDRIVO_UNLIMITED_UI_V1
// Presentation only: existing rule callbacks and values preserved.

private val OrderAccent = BluePrimary
private val AmountAccent = Color(0xFF08A66C)
private val OrderSurface = Color(0xFFEBF6FF)
private val AmountSurface = Color(0xFFECFBF4)
private val RuleInactive = Color(0xFFF8FAFC)
private val RuleActive = Color(0xFFF2F8FF)
private val SaveSurface = Color(0xFFE5F3FF)
private val DeleteSurface = Color(0xFFFEEDEE)

@Composable
internal fun UnlimitedRulesEditor(
    title: String,
    plus: Boolean,
    rules: List<UnlimitedFilterRule>,
    onRulesChange: (List<UnlimitedFilterRule>, Boolean) -> Unit
) {
    var expanded by rememberSaveable(title) {
        mutableStateOf(true)
    }

    val count = rules.count { it.enabled }
    val accent = if (plus) AmountAccent else OrderAccent
    val parentColor = if (plus) AmountSurface else OrderSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, accent),
        colors = CardDefaults.cardColors(
            containerColor = parentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    if (plus) {
                        Text(
                            "₹",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (plus) 12.sp else 13.sp,
                        lineHeight = 15.sp,
                        color = TextDarkPrimary,
                        maxLines = 2
                    )
                    Text(
                        text = "${rules.size} added | $count ON",
                        fontSize = 10.sp,
                        color = TextDarkSecondary
                    )
                }

                HeaderAction(
                    text = "+ Add",
                    accent = accent,
                    onClick = {
                        onRulesChange(
                            rules + UnlimitedFilterRule.newRule(plus),
                            false
                        )
                        expanded = true
                    }
                )

                HeaderAction(
                    text = if (expanded) "⌃ Hide" else "⌄ Show",
                    accent = accent,
                    onClick = { expanded = !expanded }
                )
            }

            if (expanded) {
                if (rules.isEmpty()) {
                    Text(
                        text = "Tap + Add for a new rule.",
                        fontSize = 11.sp,
                        color = TextDarkSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                rules.forEach { rule ->
                    key(rule.id) {
                        CompactUnlimitedRuleRow(
                            rule = rule,
                            plus = plus,
                            onUpdate = { updated, notifySaved ->
                                onRulesChange(
                                    rules.map {
                                        if (it.id == updated.id) updated else it
                                    },
                                    notifySaved
                                )
                            },
                            onDelete = {
                                onRulesChange(
                                    rules.filterNot { it.id == rule.id }, false
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.83f))
            .border(
                1.dp,
                accent.copy(alpha = 0.24f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            color = accent,
            maxLines = 1
        )
    }
}

private fun formatMinimumForDisplay(value: Float): String {
    val asText = value.toString()
    return if (asText.endsWith(".0")) {
        asText.dropLast(2)
    } else {
        asText
    }
}

@Composable
private fun CompactUnlimitedRuleRow(
    rule: UnlimitedFilterRule,
    plus: Boolean,
    onUpdate: (UnlimitedFilterRule, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var minimum by rememberSaveable(rule.id) {
        mutableStateOf(formatMinimumForDisplay(rule.minimum))
    }
    var pickup by rememberSaveable(rule.id) {
        mutableStateOf(rule.maxPickupKm.toString())
    }
    var drop by rememberSaveable(rule.id) {
        mutableStateOf(rule.maxDropKm.toString())
    }

    // SMARTDRIVO_UNLIMITED_ACTIONS_V2
    var showDeleteConfirmation by rememberSaveable(rule.id) {
        mutableStateOf(false)
    }

    val amount = minimum.toFloatOrNull()
    val pick = pickup.toFloatOrNull()
    val destination = drop.toFloatOrNull()

    val valid =
        amount != null && amount.isFinite() && amount > 0f &&
        pick != null && pick.isFinite() && pick > 0f &&
        destination != null && destination.isFinite() && destination > 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (rule.enabled) Color(0xFFC7E4FA)
            else CardBorderDefault
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled)
                RuleActive else RuleInactive
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TinyRuleField(
                    label = if (plus) "+₹ Min" else "₹ Min",
                    value = minimum,
                    onValueChange = { minimum = it },
                    modifier = Modifier.weight(1f)
                )

                TinyRuleField(
                    label = "Pickup",
                    value = pickup,
                    onValueChange = { pickup = it },
                    modifier = Modifier.weight(1f)
                )

                TinyRuleField(
                    label = "Drop",
                    value = drop,
                    onValueChange = { drop = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier.width(112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                RuleStatusToggle(
                    checked = rule.enabled,
                    enabled = true,
                    onClick = {
                        // Only ON/OFF changes here.
                        // Numeric drafts remain unsaved until Save.
                        onUpdate(
                            rule.copy(enabled = !rule.enabled),
                            false
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SmallRuleButton(
                        label = "Save",
                        icon = Icons.Default.Save,
                        isDelete = false,
                        enabled = valid,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (valid) {
                                onUpdate(
                                    rule.copy(
                                        minimum = amount!!,
                                        maxPickupKm = pick!!,
                                        maxDropKm = destination!!
                                    ),
                                    true
                                )
                            }
                        }
                    )

                    SmallRuleButton(
                        label = "Delete",
                        icon = Icons.Default.DeleteOutline,
                        isDelete = true,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = { showDeleteConfirmation = true }
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text(
                    text = "Delete this filter?",
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this saved filter? This action cannot be undone.",
                    color = TextDarkSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(
                        "Delete",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun RuleStatusToggle(
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (checked) {
        OrderAccent
    } else {
        Color(0xFF94A3B8)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!checked) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.White, CircleShape)
            )
        }

        Text(
            text = if (checked) "ON" else "OFF",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        if (checked) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun SmallRuleButton(
    label: String,
    icon: ImageVector,
    isDelete: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)

    val foreground = if (isDelete) {
        Color(0xFFC62828)
    } else {
        OrderAccent
    }

    Row(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(
                if (isDelete) DeleteSurface else SaveSurface
            )
            .border(
                1.dp,
                foreground.copy(alpha = 0.22f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground.copy(
                alpha = if (enabled) 1f else 0.4f
            ),
            modifier = Modifier.size(12.dp)
        )

        Spacer(modifier = Modifier.width(2.dp))

        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            maxLines = 1,
            color = foreground.copy(
                alpha = if (enabled) 1f else 0.4f
            )
        )
    }
}

@Composable
private fun TinyRuleField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            maxLines = 1,
            color = TextDarkSecondary
        )

        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                if (
                    newValue.isEmpty() ||
                    newValue.matches(Regex("""^\d*\.?\d*$"""))
                ) {
                    onValueChange(newValue)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                    1.dp,
                    CardBorderDefault,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 5.dp, vertical = 8.dp)
        )
    }
}