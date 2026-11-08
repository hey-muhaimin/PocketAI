package com.example.pocketai.guardian

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.llm.LlmRequest
import com.example.pocketai.core.llm.LlmRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuditedApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val riskScore: Int, // 0..100
    val riskLevel: String, // "LOW", "MODERATE", "CRITICAL"
    val dangerousPermissions: List<String>,
    val hasAccessibilityService: Boolean,
    val hasNotificationListener: Boolean,
    val isSideloaded: Boolean,
    val scoreBreakdown: List<String>,
    val llmExplanation: String? = null
)

/**
 * Deterministic security and privacy audit engine for installed Android applications.
 */
class AppAuditEngine(
    private val context: Context,
    private val llmRouter: LlmRouter
) {

    companion object {
        /**
         * Deterministic Scoring Matrix:
         * Weights each permission and system capability based on security exposure.
         */
        val PERMISSION_WEIGHTS = mapOf(
            "android.permission.RECORD_AUDIO" to 20,
            "android.permission.CAMERA" to 15,
            "android.permission.ACCESS_FINE_LOCATION" to 15,
            "android.permission.ACCESS_COARSE_LOCATION" to 10,
            "android.permission.READ_SMS" to 20,
            "android.permission.SEND_SMS" to 20,
            "android.permission.READ_CONTACTS" to 15,
            "android.permission.READ_CALL_LOG" to 15,
            "android.permission.SYSTEM_ALERT_WINDOW" to 15,
            "android.permission.MANAGE_EXTERNAL_STORAGE" to 15,
            "android.permission.PACKAGE_USAGE_STATS" to 15
        )

        const val WEIGHT_ACCESSIBILITY_SERVICE = 25
        const val WEIGHT_NOTIFICATION_LISTENER = 20
        const val WEIGHT_SIDELOADED = 10

        fun calculateDeterministicScore(
            permissions: List<String>,
            hasAccessibility: Boolean,
            hasNotificationListener: Boolean,
            isSideloaded: Boolean
        ): Pair<Int, List<String>> {
            var score = 0
            val breakdown = mutableListOf<String>()

            for (perm in permissions) {
                val weight = PERMISSION_WEIGHTS[perm]
                if (weight != null) {
                    score += weight
                    val cleanName = perm.substringAfterLast(".")
                    breakdown.add("+$weight: Dangerous permission $cleanName")
                }
            }

            if (hasAccessibility) {
                score += WEIGHT_ACCESSIBILITY_SERVICE
                breakdown.add("+$WEIGHT_ACCESSIBILITY_SERVICE: Enabled Accessibility Service (observes all screen & keyboard events)")
            }

            if (hasNotificationListener) {
                score += WEIGHT_NOTIFICATION_LISTENER
                breakdown.add("+$WEIGHT_NOTIFICATION_LISTENER: Enabled Notification Listener (reads 2FA codes & previews)")
            }

            if (isSideloaded) {
                score += WEIGHT_SIDELOADED
                breakdown.add("+$WEIGHT_SIDELOADED: Unknown/sideloaded installation source")
            }

            val finalScore = score.coerceIn(0, 100)
            return Pair(finalScore, breakdown)
        }
    }

    /**
     * Scans installed applications and performs deterministic risk scoring.
     */
    suspend fun auditInstalledApps(): List<AuditedApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        // Query enabled accessibility services
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledAccessibilityPackages = try {
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.map { it.resolveInfo.serviceInfo.packageName }
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }

        // Query enabled notification listeners
        val enabledNotificationPackages = try {
            NotificationManagerCompat.getEnabledListenerPackages(context)
        } catch (e: Exception) {
            emptySet()
        }

        val installedPackages: List<PackageInfo> = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            emptyList()
        }

        val audited = installedPackages.map { pkg ->
            val appInfo = pkg.applicationInfo
            val appName = try {
                appInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
            } catch (e: Exception) {
                pkg.packageName
            }
            val isSystem = (appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) != 0

            val requestedPermissions = pkg.requestedPermissions?.toList() ?: emptyList()
            val hasAccessibility = enabledAccessibilityPackages.contains(pkg.packageName)
            val hasNotification = enabledNotificationPackages.contains(pkg.packageName)
            val isSideloaded = !isSystem

            val (score, breakdown) = calculateDeterministicScore(
                permissions = requestedPermissions,
                hasAccessibility = hasAccessibility,
                hasNotificationListener = hasNotification,
                isSideloaded = isSideloaded
            )

            val level = when {
                score >= 55 -> "CRITICAL"
                score >= 25 -> "MODERATE"
                else -> "LOW"
            }

            AuditedApp(
                packageName = pkg.packageName,
                appName = appName,
                isSystemApp = isSystem,
                riskScore = score,
                riskLevel = level,
                dangerousPermissions = requestedPermissions.filter { PERMISSION_WEIGHTS.containsKey(it) },
                hasAccessibilityService = hasAccessibility,
                hasNotificationListener = hasNotification,
                isSideloaded = isSideloaded,
                scoreBreakdown = breakdown
            )
        }

        audited.sortedByDescending { it.riskScore }
    }

    /**
     * Uses the on-device LLM to generate an explainable summary of the findings.
     * Enforces isSensitive = true so device telemetry NEVER leaves the phone.
     */
    suspend fun generateExplanation(app: AuditedApp, config: LlmConfig): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("GUARDIAN PRIVACY AUDIT EXPLANATION REQUEST")
            appendLine("Application: ${app.appName} (${app.packageName})")
            appendLine("Deterministic Risk Score: ${app.riskScore}/100 (${app.riskLevel})")
            appendLine("Findings Breakdown:")
            app.scoreBreakdown.forEach { appendLine("• $it") }
            appendLine("\nExplain why this application received this score and provide actionable privacy guidance.")
        }

        val request = LlmRequest(
            prompt = prompt,
            systemPrompt = "You are Guardian, an on-device Android privacy auditor. Explain security scores deterministically and concisely.",
            isSensitive = true // GUARANTEE: Never sent to cloud
        )

        val response = llmRouter.route(request, config)
        response.content
    }
}
