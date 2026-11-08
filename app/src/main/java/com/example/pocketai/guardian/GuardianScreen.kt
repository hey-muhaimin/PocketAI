package com.example.pocketai.guardian

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pocketai.core.llm.LlmConfig
import com.example.ui.theme.IbmPlexMono
import com.example.ui.theme.PocketAccent
import com.example.ui.theme.PocketBg
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketDanger
import com.example.ui.theme.PocketMuted
import com.example.ui.theme.PocketSuccess
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketText
import com.example.ui.theme.PocketWarning

@Composable
fun GuardianScreen(
    viewModel: GuardianViewModel,
    llmConfig: LlmConfig,
    onNavigateBackToHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler {
        if (state.selectedApp != null) {
            viewModel.dismissSelectedApp()
        } else {
            onNavigateBackToHub()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PocketBg)
            .testTag("guardian_screen")
    ) {
        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.selectedApp != null) {
                GuardianAppDetailView(
                    app = state.selectedApp!!,
                    explanation = state.selectedAppExplanation,
                    isGenerating = state.isGeneratingExplanation,
                    onBack = { viewModel.dismissSelectedApp() },
                    onReExplain = { viewModel.selectAppForExplanation(state.selectedApp!!, llmConfig) }
                )
            } else {
                when (state.currentTab) {
                    GuardianTab.OVERVIEW -> {
                        GuardianOverviewView(
                            state = state,
                            viewModel = viewModel,
                            llmConfig = llmConfig,
                            onBackToHub = onNavigateBackToHub
                        )
                    }

                    GuardianTab.APPS -> {
                        GuardianAppsListView(
                            state = state,
                            viewModel = viewModel,
                            llmConfig = llmConfig,
                            onBackToHub = onNavigateBackToHub
                        )
                    }
                }
            }
        }

        // Fixed Status Bar
        GuardianStatusBar(statusText = "local   qwen2.5-3b   0 B sent")

        // Bottom Navigation Tabs: Overview | Apps
        GuardianBottomTabs(
            selectedTab = state.currentTab,
            onSelectTab = {
                viewModel.dismissSelectedApp()
                viewModel.selectTab(it)
            }
        )
    }
}

// ============================================================================
// 1. OVERVIEW VIEW (Clones 'Guardian · Overview.png')
// ============================================================================
@Composable
private fun GuardianOverviewView(
    state: GuardianUiState,
    viewModel: GuardianViewModel,
    llmConfig: LlmConfig,
    onBackToHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ suite",
                color = PocketAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IbmPlexMono,
                modifier = Modifier
                    .clickable { onBackToHub() }
                    .padding(end = 8.dp)
            )
            Text(
                text = "pocketai  /  guardian",
                color = PocketMuted,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Overview",
            color = PocketText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IbmPlexMono
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card 1: DEVICE RISK
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DEVICE RISK",
                                color = PocketMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = IbmPlexMono,
                                letterSpacing = 0.08.em
                            )

                            Box(
                                modifier = Modifier
                                    .background(PocketWarning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "MEDIUM",
                                    color = PocketWarning,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }

                        Text(
                            text = "${state.deviceRiskScore} / 100",
                            color = PocketText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )

                        LinearProgressIndicator(
                            progress = { state.deviceRiskScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = PocketWarning,
                            trackColor = PocketBg
                        )

                        Text(
                            text = "4 apps have elevated access  ·  0 critical alerts",
                            color = PocketMuted,
                            fontSize = 12.sp,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }

            // Card 2: SIGNALS EVALUATED
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "SIGNALS EVALUATED",
                            color = PocketMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = IbmPlexMono,
                            letterSpacing = 0.08.em
                        )

                        SignalRow(label = "Sensitive permissions (SMS, Mic, Cam)", value = "${state.sensitivePermissionsCount}", isWarning = false)
                        SignalRow(label = "Enabled Accessibility services", value = "${state.accessibilityServicesCount}", isWarning = true)
                        SignalRow(label = "Active Notification listeners", value = "${state.notificationListenersCount}", isWarning = true)
                        SignalRow(label = "Sideloaded APK sources", value = "${state.sideloadedCount}", isWarning = false)
                    }
                }
            }

            // Section: NEEDS REVIEW
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NEEDS REVIEW",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )
            }

            // High/Medium Risk App cards
            val reviewApps = state.auditedApps.filter { it.riskScore >= 40 }
            items(reviewApps) { app ->
                AppSummaryCard(app = app, onClick = { viewModel.selectAppForExplanation(app, llmConfig) })
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String, isWarning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = PocketText,
            fontSize = 12.sp,
            fontFamily = IbmPlexMono
        )
        Text(
            text = value,
            color = if (isWarning) PocketWarning else PocketText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IbmPlexMono
        )
    }
}

// ============================================================================
// 2. APPS LIST VIEW (Clones 'Guardian · Apps.png')
// ============================================================================
@Composable
private fun GuardianAppsListView(
    state: GuardianUiState,
    viewModel: GuardianViewModel,
    llmConfig: LlmConfig,
    onBackToHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ suite",
                color = PocketAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IbmPlexMono,
                modifier = Modifier
                    .clickable { onBackToHub() }
                    .padding(end = 8.dp)
            )
            Text(
                text = "pocketai  /  guardian",
                color = PocketMuted,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Apps",
            color = PocketText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IbmPlexMono
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter chips: [All 86] [High 3] [Medium 4] [Low 79]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chips = listOf(
                Pair("All", "All 86"),
                Pair("High", "High 3"),
                Pair("Medium", "Medium 4"),
                Pair("Low", "Low 79")
            )

            chips.forEach { (cat, label) ->
                val isSelected = state.filterCategory == cat
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) PocketText else PocketSurface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.setFilterCategory(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) PocketBg else PocketText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.filteredApps) { app ->
                AppSummaryCard(app = app, onClick = { viewModel.selectAppForExplanation(app, llmConfig) })
            }
        }
    }
}

@Composable
private fun AppSummaryCard(app: AuditedApp, onClick: () -> Unit) {
    val (riskBadgeText, riskBadgeBg, riskBadgeFg) = when {
        app.riskScore >= 60 -> Triple("HIGH ${app.riskScore}", PocketDanger.copy(alpha = 0.2f), PocketDanger)
        app.riskScore in 30..59 -> Triple("MED ${app.riskScore}", PocketWarning.copy(alpha = 0.2f), PocketWarning)
        else -> Triple("LOW ${app.riskScore}", PocketSuccess.copy(alpha = 0.2f), PocketSuccess)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar box with initial letter
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(PocketBg, RoundedCornerShape(4.dp))
                            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            color = PocketText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = app.appName,
                        color = PocketText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                Box(
                    modifier = Modifier
                        .background(riskBadgeBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = riskBadgeText,
                        color = riskBadgeFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }
            }

            Text(
                text = app.packageName,
                color = PocketMuted,
                fontSize = 11.sp,
                fontFamily = IbmPlexMono
            )

            // Tags row: e.g. [SMS] [MIC] [A11Y]
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (app.dangerousPermissions.any { it.contains("SMS") }) {
                    TagChip("SMS")
                }
                if (app.dangerousPermissions.any { it.contains("AUDIO") }) {
                    TagChip("MIC")
                }
                if (app.hasAccessibilityService) {
                    TagChip("A11Y")
                }
                if (app.hasNotificationListener) {
                    TagChip("NOTIF")
                }
                if (app.isSideloaded) {
                    TagChip("SIDELOAD")
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(PocketBg, RoundedCornerShape(4.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = PocketMuted,
            fontSize = 10.sp,
            fontFamily = IbmPlexMono
        )
    }
}

// ============================================================================
// 3. APP DETAIL VIEW (Clones 'Guardian · App detail.png')
// ============================================================================
@Composable
private fun GuardianAppDetailView(
    app: AuditedApp,
    explanation: String?,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onReExplain: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹ back",
                        color = PocketAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = "pocketai  /  guardian",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.appName,
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (app.riskScore >= 60) PocketDanger.copy(alpha = 0.2f) else PocketWarning.copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${app.riskScore} / 100 ${if (app.riskScore >= 60) "HIGH" else "MED"}",
                    color = if (app.riskScore >= 60) PocketDanger else PocketWarning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Assessment box: [on-device · deterministic rules]
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ASSESSMENT",
                                color = PocketMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = IbmPlexMono,
                                letterSpacing = 0.08.em
                            )
                            Text(
                                text = "on-device  ·  rules",
                                color = PocketSuccess,
                                fontSize = 11.sp,
                                fontFamily = IbmPlexMono
                            )
                        }

                        if (isGenerating) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = PocketAccent,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "generating on-device explanation...",
                                    color = PocketMuted,
                                    fontSize = 12.sp,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        } else {
                            Text(
                                text = explanation ?: "This flashlight utility declares SMS and microphone permissions and an accessibility service that are uncharacteristic of utility tools. This grants full UI inspection capability.",
                                color = PocketText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }
                }
            }

            // Evidence list
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "EVIDENCE BREAKDOWN",
                            color = PocketMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = IbmPlexMono,
                            letterSpacing = 0.08.em
                        )

                        if (app.scoreBreakdown.isNotEmpty()) {
                            app.scoreBreakdown.forEach { item ->
                                Text(
                                    text = item,
                                    color = PocketText,
                                    fontSize = 12.sp,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        } else {
                            Text(text = "No elevated risk factors detected.", color = PocketMuted, fontSize = 12.sp, fontFamily = IbmPlexMono)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(PocketBorder)
                        )

                        Text(
                            text = "total score: ${app.riskScore}",
                            color = PocketText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }

            // Action buttons
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Open App Settings (Solid white, black text)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .background(PocketText, RoundedCornerShape(4.dp))
                            .clickable {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Throwable) {}
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open app settings",
                            color = PocketBg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }

                    // Re-explain (Outline button)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(PocketSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                            .clickable { onReExplain() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Re-explain",
                            color = PocketText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. GUARDIAN BOTTOM STATUS BAR & TABS
// ============================================================================
@Composable
fun GuardianStatusBar(statusText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(PocketBg)
            .border(1.dp, PocketBorder)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(PocketSuccess, CircleShape)
            )
            Text(
                text = statusText,
                color = PocketMuted,
                fontSize = 11.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

@Composable
fun GuardianBottomTabs(
    selectedTab: GuardianTab,
    onSelectTab: (GuardianTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PocketBg)
            .border(1.dp, PocketBorder)
    ) {
        GuardianTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (isSelected) PocketText else PocketMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = IbmPlexMono
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(2.dp)
                                .background(PocketAccent)
                        )
                    }
                }
            }
        }
    }
}
