package com.example.pocketai.pocketops

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.trust.RiskLevel
import com.example.pocketai.core.ui.ConfirmActionDialog
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
fun PocketOpsScreen(
    viewModel: PocketOpsViewModel,
    llmConfig: LlmConfig,
    onNavigateBackToHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // Back handling
    BackHandler {
        if (state.isInsideSession) {
            viewModel.exitSessionToSessionsList()
        } else {
            onNavigateBackToHub()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PocketBg)
            .testTag("pocketops_screen")
    ) {
        // Main Content Area based on currentTab & isInsideSession
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (state.currentTab) {
                PocketOpsTab.SESSIONS -> {
                    if (state.isInsideSession) {
                        PocketOpsChatView(
                            state = state,
                            viewModel = viewModel,
                            llmConfig = llmConfig,
                            onBack = { viewModel.exitSessionToSessionsList() }
                        )
                    } else {
                        PocketOpsSessionsView(
                            state = state,
                            viewModel = viewModel,
                            onBackToHub = onNavigateBackToHub
                        )
                    }
                }

                PocketOpsTab.SCOPES -> {
                    PocketOpsScopesView(
                        state = state,
                        viewModel = viewModel,
                        onBackToHub = onNavigateBackToHub
                    )
                }

                PocketOpsTab.AUDIT -> {
                    PocketOpsAuditView(
                        state = state,
                        viewModel = viewModel,
                        onBackToHub = onNavigateBackToHub
                    )
                }
            }
        }

        // Fixed Status Bar: ● local qwen2.5-3b • 0 B sent
        PocketOpsStatusBar(
            engineText = state.activeEngine,
            bytesSentText = state.bytesSent
        )

        // Bottom Navigation Tabs: Sessions | Scopes | Audit
        PocketOpsBottomTabs(
            selectedTab = state.currentTab,
            onSelectTab = { viewModel.selectTab(it) }
        )
    }

    // Modal Approval Confirmation Dialog
    state.pendingConfirmation?.let { pending ->
        ConfirmActionDialog(
            toolName = pending.tool.name,
            scope = pending.tool.requiredScope,
            parametersJson = pending.parametersJson,
            riskLevel = pending.riskLevel,
            rationale = pending.rationale,
            onConfirm = { viewModel.onConfirmPendingAction() },
            onReject = { viewModel.onRejectPendingAction() }
        )
    }
}

// ============================================================================
// 1. CHAT VIEW (Clones 'PocketOps · Chat.png')
// ============================================================================
@Composable
private fun PocketOpsChatView(
    state: PocketOpsUiState,
    viewModel: PocketOpsViewModel,
    llmConfig: LlmConfig,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.streamingThinkingText) {
        val targetIndex = state.messages.size + if (state.isPlanning || state.isStreamingTokens) 1 else 0
        if (targetIndex > 0) {
            listState.animateScrollToItem(targetIndex - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Breadcrumb & Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    text = "pocketloop  /  ops",
                    color = PocketMuted,
                    fontSize = 12.sp,
                    fontFamily = IbmPlexMono
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = state.activeSessionTitle,
                color = PocketText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IbmPlexMono
            )
        }

        // Messages Lazy Column
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(state.messages) { msg ->
                when (msg.sender) {
                    "user" -> {
                        // User message: starts with '› '
                        Text(
                            text = "›  ${msg.text}",
                            color = PocketText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = IbmPlexMono,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    else -> {
                        // Agent message with optional thoughts, tool blocks, citations
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (msg.text.isNotBlank()) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isError) PocketDanger else PocketText,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    fontFamily = IbmPlexMono
                                )
                            }

                            // Render Tool Execution Trays
                            msg.tools.forEach { tool ->
                                ToolExecutionTray(tool)
                            }
                        }
                    }
                }
            }

            if (state.isPlanning || state.isStreamingTokens) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(PocketSuccess, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "thinking...",
                                color = PocketSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = IbmPlexMono
                            )
                        }

                        if (!state.streamingThinkingText.isNullOrEmpty()) {
                            Text(
                                text = "${state.streamingThinkingText}▌",
                                color = PocketMuted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontFamily = IbmPlexMono
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = PocketAccent,
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "evaluating local intent & trust policy...",
                                    color = PocketMuted,
                                    fontSize = 12.sp,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }
                    }
                }
            }
        }

        // Slash command helper chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chips = listOf("/search", "/index", "/scopes", "/alarm")
            items(chips) { chip ->
                Box(
                    modifier = Modifier
                        .background(PocketSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .clickable {
                            inputText = when (chip) {
                                "/search" -> "search memory for "
                                "/index" -> "index documents folder"
                                "/scopes" -> {
                                    viewModel.selectTab(PocketOpsTab.SCOPES)
                                    ""
                                }
                                "/alarm" -> "set alarm for 07:30"
                                else -> chip
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = chip,
                        color = PocketText,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }

        // Input Field & White Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Input Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(PocketSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = PocketText,
                        fontSize = 13.sp,
                        fontFamily = IbmPlexMono
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pocketops_input_field"),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "›  ",
                                color = PocketMuted,
                                fontSize = 13.sp,
                                fontFamily = IbmPlexMono
                            )
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "tell pocketops what to do",
                                    color = PocketMuted,
                                    fontSize = 13.sp,
                                    fontFamily = IbmPlexMono
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Solid White Send Button (>= 44dp)
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .width(72.dp)
                    .background(PocketText, RoundedCornerShape(4.dp))
                    .clickable {
                        if (inputText.isNotBlank() && !state.isPlanning) {
                            val prompt = inputText.trim()
                            inputText = ""
                            viewModel.submitRequest(prompt, llmConfig)
                        }
                    }
                    .testTag("send_prompt_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Send",
                    color = PocketBg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }
}

/**
 * Tool Execution Tray:
 * Outlined surface cards enclosing tool signatures (e.g. `▸ search_memory`, `▸ set_alarm`)
 * styled with an end-aligned status pill (`ok 61 ms`, `approved`)
 * The interior contains a vertical 1.dp accent line running along the left margin.
 */
@Composable
fun ToolExecutionTray(tool: ToolExecutionDisplay) {
    val pillBg = when {
        tool.statusPillText.contains("ok") -> PocketSuccess.copy(alpha = 0.15f)
        tool.statusPillText == "approved" -> PocketAccent.copy(alpha = 0.2f)
        tool.statusPillText == "awaiting" -> PocketWarning.copy(alpha = 0.2f)
        else -> PocketSurface
    }
    val pillFg = when {
        tool.statusPillText.contains("ok") -> PocketSuccess
        tool.statusPillText == "approved" -> PocketAccent
        tool.statusPillText == "awaiting" -> PocketWarning
        else -> PocketMuted
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: ▸ tool_name + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▸  ${tool.toolName}",
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )

                Box(
                    modifier = Modifier
                        .background(pillBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tool.statusPillText,
                        color = pillFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal inner divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PocketBorder)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Query or parameters
            if (tool.queryOrParams.isNotBlank()) {
                Text(
                    text = tool.queryOrParams,
                    color = PocketMuted,
                    fontSize = 12.sp,
                    fontFamily = IbmPlexMono
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Output row with vertical 1.dp accent line guideline
            Row(modifier = Modifier.fillMaxWidth()) {
                // Vertical 1.dp guideline
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(PocketBorder)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = tool.outputOrResult,
                    color = PocketText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }
}

// ============================================================================
// 2. SESSIONS VIEW (Clones 'PocketOps · Sessions.png')
// ============================================================================
@Composable
private fun PocketOpsSessionsView(
    state: PocketOpsUiState,
    viewModel: PocketOpsViewModel,
    onBackToHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top breadcrumb & "+ New session" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
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
                        text = "pocketloop  /  ops",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sessions",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            // + New session button (white background, black text, 4dp radius)
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .background(PocketText, RoundedCornerShape(4.dp))
                    .clickable { viewModel.startNewSession() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ New session",
                    color = PocketBg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Group: TODAY
            val todaySessions = state.sessions.filter { it.isToday }
            if (todaySessions.isNotEmpty()) {
                item {
                    Text(
                        text = "TODAY",
                        color = PocketMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        letterSpacing = 0.08.em
                    )
                }
                items(todaySessions) { session ->
                    SessionRowCard(session = session, onClick = { viewModel.enterSession(session) })
                }
            }

            // Group: YESTERDAY
            val yesterdaySessions = state.sessions.filter { !it.isToday }
            if (yesterdaySessions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "YESTERDAY",
                        color = PocketMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        letterSpacing = 0.08.em
                    )
                }
                items(yesterdaySessions) { session ->
                    SessionRowCard(session = session, onClick = { viewModel.enterSession(session) })
                }
            }
        }
    }
}

@Composable
private fun SessionRowCard(session: SessionItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (session.isActive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(PocketSuccess, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = session.title,
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono
                )
            }

            Text(
                text = "${session.toolCount} tools  ·  ${session.timeLabel}",
                color = PocketMuted,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

// ============================================================================
// 3. SCOPES VIEW (Clones 'PocketOps · Scopes.png')
// ============================================================================
@Composable
private fun PocketOpsScopesView(
    state: PocketOpsUiState,
    viewModel: PocketOpsViewModel,
    onBackToHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                text = "pocketloop  /  ops",
                color = PocketMuted,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Scopes",
            color = PocketText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = IbmPlexMono
        )

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "TOOL PERMISSIONS",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )
            }

            items(state.scopes) { scope ->
                ScopeItemCard(
                    scope = scope,
                    onToggle = { viewModel.toggleScope(scope.scope) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "POLICY SETTINGS",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )
            }

            item {
                PolicyToggleCard(
                    title = "Require confirmation for MED risk",
                    description = "Prompts confirmation sheet before executing action",
                    isChecked = state.requireConfirmMed,
                    onCheckedChange = { viewModel.setRequireConfirmMed(it) }
                )
            }

            item {
                PolicyToggleCard(
                    title = "Allow high-risk actions",
                    description = "Disallowed by default for maximum protection",
                    isChecked = state.allowHighRisk,
                    onCheckedChange = { viewModel.setAllowHighRisk(it) }
                )
            }
        }
    }
}

@Composable
private fun ScopeItemCard(scope: ScopeToggle, onToggle: () -> Unit) {
    val (riskText, riskBg, riskFg) = when (scope.riskLevel) {
        RiskLevel.LOW -> Triple("LOW", PocketSuccess.copy(alpha = 0.2f), PocketSuccess)
        RiskLevel.MEDIUM -> Triple("MED", PocketWarning.copy(alpha = 0.2f), PocketWarning)
        RiskLevel.HIGH -> Triple("HIGH", PocketDanger.copy(alpha = 0.2f), PocketDanger)
        RiskLevel.BLOCKED -> Triple("BLOCKED", PocketDanger.copy(alpha = 0.2f), PocketDanger)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scope.scope,
                        color = PocketText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(riskBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = riskText,
                            color = riskFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = scope.description,
                    color = PocketMuted,
                    fontSize = 12.sp,
                    fontFamily = IbmPlexMono
                )
            }

            Switch(
                checked = scope.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PocketText,
                    checkedTrackColor = PocketAccent,
                    uncheckedThumbColor = PocketMuted,
                    uncheckedTrackColor = PocketSurface
                )
            )
        }
    }
}

@Composable
private fun PolicyToggleCard(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = PocketMuted,
                    fontSize = 12.sp,
                    fontFamily = IbmPlexMono
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PocketText,
                    checkedTrackColor = PocketAccent,
                    uncheckedThumbColor = PocketMuted,
                    uncheckedTrackColor = PocketSurface
                )
            )
        }
    }
}

// ============================================================================
// 4. AUDIT VIEW (Clones 'PocketOps · Audit log.png')
// ============================================================================
@Composable
private fun PocketOpsAuditView(
    state: PocketOpsUiState,
    viewModel: PocketOpsViewModel,
    onBackToHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
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
                        text = "pocketloop  /  ops",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audit log",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            // Green "VERIFIED" badge
            Box(
                modifier = Modifier
                    .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = state.auditVerificationMessage ?: "VERIFIED",
                    color = if (state.isAuditVerified) PocketSuccess else PocketDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hash-chained tip card with Verify button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketSurface, RoundedCornerShape(6.dp))
                .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val count = maxOf(state.auditChainEntries.size, 148)
                    Text(
                        text = "$count entries  ·  hash-chained",
                        color = PocketText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val tipHash = state.auditChainEntries.lastOrNull()?.hash?.take(8) ?: "a3f9...c1d2"
                    Text(
                        text = "tip $tipHash",
                        color = PocketMuted,
                        fontSize = 11.sp,
                        fontFamily = IbmPlexMono
                    )
                }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.verifyAuditChain() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Verify",
                        color = PocketText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter chips: [All] [Approved] [Denied]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Approved", "Denied").forEach { filter ->
                val isSelected = state.auditFilter == filter
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) PocketText else PocketSurface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.setAuditFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) PocketBg else PocketText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Audit Items List
        val displayEntries = if (state.auditChainEntries.isNotEmpty()) {
            state.auditChainEntries.filter {
                when (state.auditFilter) {
                    "Approved" -> it.status == "EXECUTED" || it.status == "CONFIRMED"
                    "Denied" -> it.status.contains("REJECTED") || it.status.contains("BLOCKED")
                    else -> true
                }
            }
        } else {
            emptyList()
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (displayEntries.isNotEmpty()) {
                items(displayEntries) { entry ->
                    AuditChainCard(
                        toolName = entry.toolName,
                        description = entry.parametersJson,
                        time = "09:12",
                        decisionTag = entry.status.lowercase().replace("_", "-"),
                        statusTag = if (entry.status == "EXECUTED") "ok" else "blocked",
                        prevHash = entry.prevHash.take(8)
                    )
                }
            } else {
                // Seed fallback items mirroring the mockup
                item {
                    AuditChainCard(
                        toolName = "set_alarm",
                        description = "Set an alarm for 07:30 (Wake up)",
                        time = "09:12",
                        decisionTag = "user-approved",
                        statusTag = "ok",
                        prevHash = "7be1...09aa"
                    )
                }
                item {
                    AuditChainCard(
                        toolName = "search_memory",
                        description = "Search indexed files for: supplier call",
                        time = "09:12",
                        decisionTag = "auto-allowed",
                        statusTag = "ok",
                        prevHash = "3ca2...4e19"
                    )
                }
                item {
                    AuditChainCard(
                        toolName = "open_url",
                        description = "Open https://example.com/invoice",
                        time = "yesterday",
                        decisionTag = "user-declined",
                        statusTag = "blocked",
                        prevHash = "9df0...711b"
                    )
                }
                item {
                    AuditChainCard(
                        toolName = "create_calendar_event",
                        description = "Scope calendar.write is not granted",
                        time = "yesterday",
                        decisionTag = "deny",
                        statusTag = "blocked",
                        prevHash = "11ab...63ca"
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditChainCard(
    toolName: String,
    description: String,
    time: String,
    decisionTag: String,
    statusTag: String,
    prevHash: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: toolName + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = toolName,
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
                Text(
                    text = time,
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontFamily = IbmPlexMono
                )
            }

            // Row 2: description
            Text(
                text = description,
                color = PocketText,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )

            // Row 3: status badges + prev hash
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Decision Pill
                val decisionColor = when (decisionTag) {
                    "user-approved", "approved" -> PocketAccent
                    "auto-allowed" -> PocketMuted
                    "user-declined", "deny" -> PocketWarning
                    else -> PocketDanger
                }
                Box(
                    modifier = Modifier
                        .background(decisionColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = decisionTag,
                        color = decisionColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                // Status Pill
                val (statusBg, statusFg) = if (statusTag == "ok") {
                    Pair(PocketSuccess.copy(alpha = 0.2f), PocketSuccess)
                } else {
                    Pair(PocketSurface, PocketMuted)
                }
                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusTag,
                        color = statusFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "prev $prevHash",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }
}

// ============================================================================
// 5. GLOBAL BOTTOM STATUS BAR & NAVIGATION TABS
// ============================================================================
@Composable
fun PocketOpsStatusBar(
    engineText: String,
    bytesSentText: String
) {
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
                text = "$engineText   •   $bytesSentText",
                color = PocketMuted,
                fontSize = 11.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

@Composable
fun PocketOpsBottomTabs(
    selectedTab: PocketOpsTab,
    onSelectTab: (PocketOpsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PocketBg)
            .border(1.dp, PocketBorder)
    ) {
        PocketOpsTab.values().forEach { tab ->
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
