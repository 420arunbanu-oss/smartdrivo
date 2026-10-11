package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

@Composable
fun MembershipLockedOverlay(
    onViewPlans: () -> Unit
) {
    // MEMBERSHIP_LOCKED_PREVIEW_V1
    // Full-screen click shield: underlying ride controls remain visible
    // but cannot be used until membership becomes active.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFFF8FAFC).copy(alpha = 0.90f)
            )
            .clickable(
                onClick = {}
            )
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 42.sp
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Membership Required",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDarkPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "Login successful. SmartDrivo is open, but Auto Accept, filters, areas and ride tools stay locked until you activate a membership.",
                    fontSize = 13.sp,
                    color = TextDarkSecondary,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                Button(
                    onClick = onViewPlans,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "View Membership Plans",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "More remains available for membership, support and logout.",
                    fontSize = 11.sp,
                    color = BluePrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
