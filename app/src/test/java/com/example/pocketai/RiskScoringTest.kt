package com.example.pocketai

import com.example.pocketai.guardian.AppAuditEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScoringTest {

    @Test
    fun testBenignAppZeroScore() {
        val (score, breakdown) = AppAuditEngine.calculateDeterministicScore(
            permissions = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"),
            hasAccessibility = false,
            hasNotificationListener = false,
            isSideloaded = false
        )
        assertEquals(0, score)
        assertTrue(breakdown.isEmpty())
    }

    @Test
    fun testDangerousHardwarePermissions() {
        val (score, breakdown) = AppAuditEngine.calculateDeterministicScore(
            permissions = listOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA"),
            hasAccessibility = false,
            hasNotificationListener = false,
            isSideloaded = false
        )
        // RECORD_AUDIO: +20, CAMERA: +15 = 35
        assertEquals(35, score)
        assertEquals(2, breakdown.size)
    }

    @Test
    fun testAccessibilityAndNotificationExposure() {
        val (score, breakdown) = AppAuditEngine.calculateDeterministicScore(
            permissions = emptyList(),
            hasAccessibility = true,
            hasNotificationListener = true,
            isSideloaded = true
        )
        // Accessibility: +25, Notification: +20, Sideloaded: +10 = 55
        assertEquals(55, score)
        assertEquals(3, breakdown.size)
    }

    @Test
    fun testScoreCappedAt100() {
        val (score, _) = AppAuditEngine.calculateDeterministicScore(
            permissions = listOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.READ_SMS",
                "android.permission.SEND_SMS",
                "android.permission.READ_CONTACTS"
            ),
            hasAccessibility = true,
            hasNotificationListener = true,
            isSideloaded = true
        )
        assertEquals(100, score)
    }
}
