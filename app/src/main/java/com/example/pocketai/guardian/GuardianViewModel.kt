package com.example.pocketai.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketai.core.llm.LlmConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GuardianTab {
    OVERVIEW,
    APPS
}

data class GuardianUiState(
    val currentTab: GuardianTab = GuardianTab.OVERVIEW,
    val selectedApp: AuditedApp? = null,
    val auditedApps: List<AuditedApp> = emptyList(),
    val filteredApps: List<AuditedApp> = emptyList(),
    val filterCategory: String = "All", // "All", "High", "Medium", "Low"
    val isScanning: Boolean = false,
    val isGeneratingExplanation: Boolean = false,
    val selectedAppExplanation: String? = null,
    val deviceRiskScore: Int = 38,
    val sensitivePermissionsCount: Int = 14,
    val accessibilityServicesCount: Int = 1,
    val notificationListenersCount: Int = 2,
    val sideloadedCount: Int = 3,
    val statusMessage: String? = null
)

class GuardianViewModel(
    private val auditEngine: AppAuditEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardianUiState())
    val uiState: StateFlow<GuardianUiState> = _uiState.asStateFlow()

    init {
        initializeSampleData()
        runAuditScan()
    }

    private fun initializeSampleData() {
        val sampleApps = listOf(
            AuditedApp(
                packageName = "com.utility.quicktorch",
                appName = "Quick Torch",
                riskScore = 70,
                riskLevel = "HIGH",
                dangerousPermissions = listOf("android.permission.RECORD_AUDIO", "android.permission.READ_SMS"),
                hasAccessibilityService = true,
                hasNotificationListener = true,
                isSystemApp = false,
                isSideloaded = true,
                scoreBreakdown = listOf(
                    "+25 Accessibility service active",
                    "+15 Notification listener active",
                    "+30 Dangerous permissions (SMS, RECORD_AUDIO)"
                )
            ),
            AuditedApp(
                packageName = "com.keyboard.swiftcustom",
                appName = "Swift Custom Key",
                riskScore = 55,
                riskLevel = "MEDIUM",
                dangerousPermissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                hasAccessibilityService = true,
                hasNotificationListener = false,
                isSystemApp = false,
                isSideloaded = false,
                scoreBreakdown = listOf(
                    "+25 Accessibility service active",
                    "+15 Sensitive permissions"
                )
            ),
            AuditedApp(
                packageName = "com.social.chatpulse",
                appName = "ChatPulse",
                riskScore = 40,
                riskLevel = "MEDIUM",
                dangerousPermissions = listOf("android.permission.READ_CONTACTS", "android.permission.CAMERA"),
                hasAccessibilityService = false,
                hasNotificationListener = true,
                isSystemApp = false,
                isSideloaded = false,
                scoreBreakdown = listOf(
                    "+15 Notification listener active",
                    "+10 Camera and contacts access"
                )
            ),
            AuditedApp(
                packageName = "com.calculator.clean",
                appName = "Clean Calc",
                riskScore = 0,
                riskLevel = "LOW",
                dangerousPermissions = emptyList(),
                hasAccessibilityService = false,
                hasNotificationListener = false,
                isSystemApp = false,
                isSideloaded = false,
                scoreBreakdown = emptyList()
            )
        )

        _uiState.value = _uiState.value.copy(
            auditedApps = sampleApps,
            filteredApps = sampleApps
        )
    }

    fun selectTab(tab: GuardianTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun setFilterCategory(category: String) {
        val filtered = when (category) {
            "High" -> _uiState.value.auditedApps.filter { it.riskLevel == "HIGH" || it.riskScore >= 60 }
            "Medium" -> _uiState.value.auditedApps.filter { it.riskLevel == "MEDIUM" || (it.riskScore in 30..59) }
            "Low" -> _uiState.value.auditedApps.filter { it.riskLevel == "LOW" || it.riskScore < 30 }
            else -> _uiState.value.auditedApps
        }
        _uiState.value = _uiState.value.copy(
            filterCategory = category,
            filteredApps = filtered
        )
    }

    fun runAuditScan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            try {
                val apps = auditEngine.auditInstalledApps()
                if (apps.isNotEmpty()) {
                    val highCount = apps.count { it.riskScore >= 60 }
                    val medCount = apps.count { it.riskScore in 30..59 }
                    val totalRisk = if (apps.isNotEmpty()) (apps.sumOf { it.riskScore } / apps.size) + (highCount * 10) else 38
                    val clampedRisk = totalRisk.coerceIn(0, 100)

                    _uiState.value = _uiState.value.copy(
                        auditedApps = apps,
                        filteredApps = apps,
                        deviceRiskScore = clampedRisk,
                        isScanning = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isScanning = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun selectAppForExplanation(app: AuditedApp, config: LlmConfig) {
        _uiState.value = _uiState.value.copy(
            selectedApp = app,
            isGeneratingExplanation = true,
            selectedAppExplanation = null
        )
        viewModelScope.launch {
            try {
                val explanation = auditEngine.generateExplanation(app, config)
                _uiState.value = _uiState.value.copy(
                    isGeneratingExplanation = false,
                    selectedAppExplanation = explanation
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingExplanation = false,
                    selectedAppExplanation = "This application declares elevated permissions (${app.dangerousPermissions.joinToString()}) and privileged accessibility/notification bindings that deviate from typical utility behavior."
                )
            }
        }
    }

    fun dismissSelectedApp() {
        _uiState.value = _uiState.value.copy(
            selectedApp = null,
            selectedAppExplanation = null
        )
    }
}
