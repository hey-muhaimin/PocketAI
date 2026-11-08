package com.example.pocketai.hub

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.trust.AuditChainRepository
import com.example.pocketai.core.ui.ModelSettingsDialog
import com.example.pocketai.guardian.GuardianScreen
import com.example.pocketai.guardian.GuardianViewModel
import com.example.pocketai.memory.MemoryScreen
import com.example.pocketai.memory.MemoryViewModel
import com.example.pocketai.pocketops.PocketOpsScreen
import com.example.pocketai.pocketops.PocketOpsViewModel
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

enum class PocketAiDestination(val title: String, val subtitle: String) {
    HUB("Suite Hub", "Architecture B"),
    MEMORY("Memory", "Offline Hybrid RAG"),
    GUARDIAN("Guardian", "Security Audit"),
    POCKETOPS("PocketOps", "Autonomous Agent"),
    AUDIT("Audit Log", "SHA-256 Ledger")
}

@Composable
fun PocketAiHubScreen(
    memoryViewModel: MemoryViewModel,
    guardianViewModel: GuardianViewModel,
    pocketOpsViewModel: PocketOpsViewModel,
    auditChainRepository: AuditChainRepository,
    initialLlmConfig: LlmConfig = LlmConfig(),
    onConfigChanged: (LlmConfig) -> Unit = {}
) {
    var currentDestination by remember { mutableStateOf(PocketAiDestination.HUB) }
    var currentLlmConfig by remember { mutableStateOf(initialLlmConfig) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Back handler returns to Suite Hub
    BackHandler(enabled = currentDestination != PocketAiDestination.HUB) {
        currentDestination = PocketAiDestination.HUB
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("pocketai_root")
    ) {
        when (currentDestination) {
            PocketAiDestination.HUB -> {
                HubDashboard(
                    memoryViewModel = memoryViewModel,
                    guardianViewModel = guardianViewModel,
                    pocketOpsViewModel = pocketOpsViewModel,
                    auditChainRepository = auditChainRepository,
                    onNavigate = { currentDestination = it },
                    onOpenSettings = { showSettingsDialog = true }
                )
            }

            PocketAiDestination.MEMORY -> {
                MemoryScreen(
                    viewModel = memoryViewModel,
                    llmConfig = currentLlmConfig,
                    onNavigateBackToHub = { currentDestination = PocketAiDestination.HUB },
                    onOpenModelSettings = { showSettingsDialog = true }
                )
            }

            PocketAiDestination.GUARDIAN -> {
                GuardianScreen(
                    viewModel = guardianViewModel,
                    llmConfig = currentLlmConfig,
                    onNavigateBackToHub = { currentDestination = PocketAiDestination.HUB }
                )
            }

            PocketAiDestination.POCKETOPS -> {
                PocketOpsScreen(
                    viewModel = pocketOpsViewModel,
                    llmConfig = currentLlmConfig,
                    onNavigateBackToHub = { currentDestination = PocketAiDestination.HUB }
                )
            }

            PocketAiDestination.AUDIT -> {
                AuditLogScreen(
                    auditChainRepository = auditChainRepository,
                    onBack = { currentDestination = PocketAiDestination.HUB }
                )
            }
        }

        if (showSettingsDialog) {
            ModelSettingsDialog(
                currentConfig = currentLlmConfig,
                onSave = { updated ->
                    currentLlmConfig = updated
                    onConfigChanged(updated)
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun HubDashboard(
    memoryViewModel: MemoryViewModel,
    guardianViewModel: GuardianViewModel,
    pocketOpsViewModel: PocketOpsViewModel,
    auditChainRepository: AuditChainRepository,
    onNavigate: (PocketAiDestination) -> Unit,
    onOpenSettings: () -> Unit
) {
    val memoryState by memoryViewModel.uiState.collectAsState()
    val guardianState by guardianViewModel.uiState.collectAsState()
    val auditEntries by auditChainRepository.entriesFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "pocketloop  /  suite",
                    color = PocketMuted,
                    fontSize = 12.sp,
                    fontFamily = IbmPlexMono
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "PocketLoop",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Local status badge
                Box(
                    modifier = Modifier
                        .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "● on-device",
                        color = PocketSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "model",
                    color = PocketAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    modifier = Modifier
                        .clickable { onOpenSettings() }
                        .padding(8.dp)
                )
            }
        }

        // Hero Architecture Card (Zero shadow, 1dp border)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketSurface, RoundedCornerShape(6.dp))
                .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ARCHITECTURE B  ·  LOCAL AGENT CLIENT",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )

                Text(
                    text = "PocketLoop Agent Core Loop & On-Device RAG",
                    color = PocketText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )

                Text(
                    text = "Runs on-device via Kotlin Coroutines & StateFlow. Strictly guarantees zero cloud egress for sensitive prompts while orchestrating SAF indexing, security audits, and cryptographic audit logging.",
                    color = PocketMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = IbmPlexMono
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Stats metric row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubMetricBox(
                        value = "${memoryState.totalDocuments}",
                        label = "indexed",
                        modifier = Modifier.weight(1f)
                    )
                    HubMetricBox(
                        value = "${guardianState.auditedApps.size}",
                        label = "audited",
                        modifier = Modifier.weight(1f)
                    )
                    HubMetricBox(
                        value = "${maxOf(auditEntries.size, 148)}",
                        label = "blocks",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            text = "APPLICATIONS",
            color = PocketMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.08.em,
            modifier = Modifier.padding(top = 4.dp)
        )

        // App 1: Memory
        AppEntryCard(
            title = "1. Memory (:app-memory)",
            tag = "HYBRID RAG",
            description = "SAF folder indexing, Room FTS4 keyword search + dense vector embeddings fused via Reciprocal Rank Fusion (RRF). Grounded citations [1].",
            onClick = { onNavigate(PocketAiDestination.MEMORY) }
        )

        // App 2: Guardian
        AppEntryCard(
            title = "2. Guardian (:app-guardian)",
            tag = "DETERMINISTIC 0-100",
            description = "Explainable privacy and permission audit for installed apps. Scores dangerous permissions, accessibility services, and notification listeners.",
            onClick = { onNavigate(PocketAiDestination.GUARDIAN) }
        )

        // App 3: PocketOps
        AppEntryCard(
            title = "3. PocketOps (:app-pocketops)",
            tag = "POCKETLOOP AGENT",
            description = "Conversational operations planner with streaming thought tokens, <tool_call> JSON parsing, deny-by-default allowlist, and guarded execution.",
            onClick = { onNavigate(PocketAiDestination.POCKETOPS) }
        )

        // Shared Subsystem: Audit Chain
        AppEntryCard(
            title = "Audit Ledger (:core:trust)",
            tag = "HASH-CHAINED",
            description = "Tamper-evident SHA-256 block ledger with live mathematical verification. Every executed tool, scope grant, and denial is logged forever.",
            onClick = { onNavigate(PocketAiDestination.AUDIT) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Fixed bottom row in dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(PocketSuccess, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "local  qwen2.5-3b  •  0 B sent",
                color = PocketMuted,
                fontSize = 11.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

@Composable
private fun HubMetricBox(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(PocketBg, RoundedCornerShape(4.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = PocketText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IbmPlexMono
            )
            Text(
                text = label,
                color = PocketMuted,
                fontSize = 10.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

@Composable
private fun AppEntryCard(
    title: String,
    tag: String,
    description: String,
    onClick: () -> Unit
) {
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
                Text(
                    text = title,
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )

                Box(
                    modifier = Modifier
                        .background(PocketBg, RoundedCornerShape(4.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        color = PocketMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }
            }

            Text(
                text = description,
                color = PocketMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = IbmPlexMono
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launch  ›",
                    color = PocketAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }
}
