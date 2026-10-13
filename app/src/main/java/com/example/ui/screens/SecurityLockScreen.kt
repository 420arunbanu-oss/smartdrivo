package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

@Composable
fun SecurityLockScreen(
    profile: UserProfile,
    offlineVerificationExpired: Boolean = false,
    sessionInvalidated: Boolean = false,
    sessionReason: String = "",

    onLogout: () -> Unit
) {
    // BLOCKED_USER_DEVICE_BLACKLIST_V1
    val accountBlocked =
        profile.isBlocked

    val adminLocked =
        !profile.isActive

    val title =
        when {
            accountBlocked ->
                "Account Blocked"

            adminLocked ->
                "SmartDrivo Locked"

            sessionInvalidated ->
                "Session Ended"

            offlineVerificationExpired ->
                "Internet Verification Required"

            else ->
                "Account Verification Required"
        }

    val message =
        when {
            accountBlocked ->
                profile.blockReason
                    .takeIf { it.isNotBlank() }
                    ?: "This SmartDrivo account has been permanently blocked by SmartDrivo Admin."

            adminLocked ->
                "This account has been locked by SmartDrivo Admin. Auto Accept and assistant functions are disabled."

            sessionInvalidated ->
                sessionReason
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "This SmartDrivo account was signed in on another device. Log in again to continue on this phone."

            offlineVerificationExpired ->
                "SmartDrivo could not verify this account within the offline grace period. Connect to the internet. Access will restore automatically after server verification."

            else ->
                "Access is restricted. Please contact SmartDrivo Support."
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8FB))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // SECURITY_VECTOR_LOCK_ICON_V2
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "SmartDrivo security lock",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(54.dp)
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = title,
                    color = TextDarkPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = message,
                    color = TextDarkSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(18.dp)
                )

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text =
                            if (sessionInvalidated)
                                "Log In Again"
                            else
                                "Log Out",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
