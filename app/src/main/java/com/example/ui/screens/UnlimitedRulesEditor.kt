package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UnlimitedFilterRule
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorderDefault
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

private val ActiveBlue = BluePrimary
private val ActiveBlueCard = BlueContainer
private val QuietBorder = CardBorderDefault

@Composable
internal fun UnlimitedRulesEditor(
    title: String,
    plus: Boolean,
    rules: List<UnlimitedFilterRule>,
    onRulesChange: (List<UnlimitedFilterRule>) -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val count = rules.count { it.enabled }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (count > 0) ActiveBlueCard else CardBackground
        ),
        border = BorderStroke(1.dp, if (count > 0) ActiveBlue else QuietBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            // UNLIMITED_COLLAPSED_PARENT_V3
            // Tap anywhere in header to reveal the saved rows.
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary,
                        maxLines = 2
                    )
                    Text(
                        "${rules.size} added | $count ON",
                        fontSize = 10.sp,
                        color = if (count > 0) ActiveBlue else TextDarkSecondary
                    )
                }
                TextButton(onClick = {
                    onRulesChange(rules + UnlimitedFilterRule.newRule(plus))
                    expanded = true
                }) { Text("+ Add", fontSize = 11.sp) }
                Text(
                    if (expanded) "Hide" else "Show",
                    modifier = Modifier.padding(start = 5.dp, end = 3.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveBlue
                )
            }
            if (expanded) {
                if (rules.isEmpty()) {
                    Text("Tap + Add for a new rule.", fontSize = 11.sp)
                }
                rules.forEach { rule ->
                    key(rule.id) {
                        CompactUnlimitedRuleRow(
                            rule = rule,
                            plus = plus,
                            onUpdate = { updated ->
                                onRulesChange(rules.map {
                                    if (it.id == updated.id) updated else it
                                })
                            },
                            onDelete = {
                                onRulesChange(rules.filterNot { it.id == rule.id })
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMinimumForDisplay(value: Float): String {
    val asText = value.toString()
    return if (asText.endsWith(".0")) asText.dropLast(2) else asText
}

@Composable
private fun CompactUnlimitedRuleRow(
    rule: UnlimitedFilterRule,
    plus: Boolean,
    onUpdate: (UnlimitedFilterRule) -> Unit,
    onDelete: () -> Unit
) {
    var minimum by rememberSaveable(rule.id) { mutableStateOf(formatMinimumForDisplay(rule.minimum)) }
    var pickup by rememberSaveable(rule.id) { mutableStateOf(rule.maxPickupKm.toString()) }
    var drop by rememberSaveable(rule.id) { mutableStateOf(rule.maxDropKm.toString()) }
    val amount = minimum.toFloatOrNull()
    val pick = pickup.toFloatOrNull()
    val destination = drop.toFloatOrNull()
    val valid = amount != null && amount.isFinite() && amount > 0f &&
        pick != null && pick.isFinite() && pick > 0f &&
        destination != null && destination.isFinite() && destination > 0f

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (rule.enabled) ActiveBlue else QuietBorder),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) ActiveBlueCard else CardBackground
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TinyRuleField(if (plus) "+₹ Min" else "₹ Min", minimum, { minimum = it }, Modifier.weight(1f))
                TinyRuleField("Pickup", pickup, { pickup = it }, Modifier.weight(1f))
                TinyRuleField("Drop", drop, { drop = it }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    enabled = valid,
                    onClick = {
                        if (valid) onUpdate(rule.copy(
                            enabled = !rule.enabled,
                            minimum = amount!!,
                            maxPickupKm = pick!!,
                            maxDropKm = destination!!
                        ))
                    }
                ) {
                    Text(
                        if (rule.enabled) "ON" else "OFF",
                        color = if (rule.enabled) ActiveBlue else TextDarkSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = {
                    if (valid) onUpdate(rule.copy(
                        minimum = amount!!,
                        maxPickupKm = pick!!,
                        maxDropKm = destination!!
                    ))
                }, enabled = valid) { Text("Save", fontSize = 10.sp) }
                TextButton(onClick = onDelete) {
                    Text("Delete", fontSize = 10.sp, color = Color(0xFFB91C1C))
                }
            }
        }
    }
}

@Composable
private fun TinyRuleField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.sp, maxLines = 1, color = TextDarkSecondary)
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("""^\d*\.?\d*$"""))) {
                    onValueChange(newValue)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDarkPrimary),
            modifier = Modifier.fillMaxWidth().height(30.dp)
                .border(1.dp, CardBorderDefault, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}