package com.example.facelogin2.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facelogin2.User
import com.example.facelogin2.ui.theme.VGPrimary
import com.example.facelogin2.ui.theme.VGSecondary

/**
 * Modern User Info Panel - Glassmorphism Card
 * Copied directly from VG-CAMERA
 */
@Composable
fun ModernUserInfoPanel(
    user: User?,
    isVisible: Boolean,
    uiState: Int = 4
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Glassmorphism Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                val isError = uiState == 5
                // Header Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isError) "RECOGNITION FAILED" else "LOGIN SUCCESSFUL",
                            color = if (isError) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // User Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.name ?: "Unknown User",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1D1D1F),
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EMP ID:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8E8E93)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = user?.cardId ?: "------",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF636366)
                            )
                        }
                    }

                    // Matching Rate Circle/Badge
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "MATCH RATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E8E93)
                        )
                        Text(
                            text = user?.similarity ?: "0%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isError) Color(0xFFD32F2F) else VGPrimary
                        )
                    }
                }
            }
        }
    }
}
