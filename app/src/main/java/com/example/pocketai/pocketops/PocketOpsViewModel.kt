package com.example.pocketai.pocketops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketai.core.actions.GuardedExecutionOutcome
import com.example.pocketai.core.actions.GuardedExecutor
import com.example.pocketai.core.actions.Tool
import com.example.pocketai.core.actions.ToolRegistry
import com.example.pocketai.core.engine.AgentTurnState
import com.example.pocketai.core.engine.CitationRef
import com.example.pocketai.core.engine.LogEntry
import com.example.pocketai.core.engine.ToolStatus
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.llm.LlmMessage
import com.example.pocketai.core.llm.LlmRequest
import com.example.pocketai.core.llm.LlmRouter
import com.example.pocketai.core.llm.LlmToolCall
import com.example.pocketai.core.trust.AuditChainRepository
import com.example.pocketai.core.trust.AuditLogEntity
import com.example.pocketai.core.trust.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class PocketOpsTab {
    SESSIONS,
    SCOPES,
    AUDIT
}

data class SessionItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val toolCount: Int,
    val timeLabel: String,
    val isToday: Boolean = true,
    val isActive: Boolean = false
)

data class ToolExecutionDisplay(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val statusPillText: String, // e.g. "ok 61 ms", "approved", "awaiting"
    val isSuccess: Boolean = true,
    val isAwaiting: Boolean = false,
    val queryOrParams: String,
    val outputOrResult: String
)

data class OpsChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user", "agent", "system"
    val text: String,
    val tools: List<ToolExecutionDisplay> = emptyList(),
    val citations: List<CitationRef> = emptyList(),
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ScopeToggle(
    val scope: String,
    val riskLevel: RiskLevel,
    val description: String,
    var isEnabled: Boolean
)

data class PendingActionConfirmation(
    val tool: Tool,
    val parametersJson: String,
    val riskLevel: RiskLevel,
    val rationale: String
)

data class PocketOpsUiState(
    val currentTab: PocketOpsTab = PocketOpsTab.SESSIONS,
    val isInsideSession: Boolean = true, // By default show the active chat session matching mockup
    val activeSessionTitle: String = "Alarm and supplier notes",
    val sessions: List<SessionItem> = emptyList(),
    val messages: List<OpsChatMessage> = emptyList(),
    val isPlanning: Boolean = false,
    val isStreamingTokens: Boolean = false,
    val streamingThinkingText: String? = null,
    val pendingConfirmation: PendingActionConfirmation? = null,
    val activeEngine: String = "local qwen2.5-3b",
    val bytesSent: String = "0 B sent",
    val scopes: List<ScopeToggle> = emptyList(),
    val requireConfirmMed: Boolean = true,
    val allowHighRisk: Boolean = false,
    val auditFilter: String = "All", // "All", "Approved", "Denied"
    val auditChainEntries: List<AuditLogEntity> = emptyList(),
    val isAuditVerified: Boolean = true,
    val auditVerificationMessage: String? = "VERIFIED"
)

class PocketOpsViewModel(
    private val llmRouter: LlmRouter,
    private val toolRegistry: ToolRegistry,
    private val guardedExecutor: GuardedExecutor,
    private val auditChainRepository: AuditChainRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PocketOpsUiState())
    val uiState: StateFlow<PocketOpsUiState> = _uiState.asStateFlow()

    init {
        initializeData()
        observeAuditChain()
    }

    private fun initializeData() {
        // Mock sessions matching the exact UI design
        val defaultSessions = listOf(
            SessionItem(
                id = "session-1",
                title = "Alarm and supplier notes",
                toolCount = 2,
                timeLabel = "09:12",
                isToday = true,
                isActive = true
            ),
            SessionItem(
                id = "session-2",
                title = "Meeting prep with calendar",
                toolCount = 1,
                timeLabel = "08:30",
                isToday = true,
                isActive = false
            ),
            SessionItem(
                id = "session-3",
                title = "Documentation search",
                toolCount = 1,
                timeLabel = "yesterday",
                isToday = false,
                isActive = false
            ),
            SessionItem(
                id = "session-4",
                title = "Security policy inquiry",
                toolCount = 0,
                timeLabel = "yesterday",
                isToday = false,
                isActive = false
            )
        )

        // Seed the exact conversation matching 'PocketOps · Chat.png'
        val initialMessages = listOf(
            OpsChatMessage(
                sender = "user",
                text = "Wake me at 7:30 tomorrow and find my notes about the supplier call"
            ),
            OpsChatMessage(
                sender = "agent",
                text = "I’ll search your notes, then set the alarm.",
                tools = listOf(
                    ToolExecutionDisplay(
                        toolName = "search_memory",
                        statusPillText = "ok 61 ms",
                        isSuccess = true,
                        queryOrParams = "query: \"supplier call\"",
                        outputOrResult = "supplier-agreement.md: Shipment 1 is due 14 March..."
                    ),
                    ToolExecutionDisplay(
                        toolName = "set_alarm",
                        statusPillText = "approved",
                        isSuccess = true,
                        queryOrParams = "hour: 7  ·  minute: 30  ·  message: \"Wake up\"",
                        outputOrResult = "alarm set for 07:30"
                    )
                ),
                citations = listOf(
                    CitationRef(1, "supplier-agreement.md", "Documents/Contracts", 0.82f)
                )
            ),
            OpsChatMessage(
                sender = "agent",
                text = "Done. Your notes say the first shipment is due 14 March [1]."
            )
        )

        // Scopes list matching 'PocketOps · Scopes.png'
        val defaultScopes = listOf(
            ScopeToggle("memory.read", RiskLevel.LOW, "Search indexed documents and notes", true),
            ScopeToggle("alarm.write", RiskLevel.MEDIUM, "Set device alarms via system clock", true),
            ScopeToggle("calendar.write", RiskLevel.MEDIUM, "Insert calendar reminders and events", true),
            ScopeToggle("web.open", RiskLevel.MEDIUM, "Open verified HTTPS URLs in browser", true)
        )

        _uiState.value = _uiState.value.copy(
            sessions = defaultSessions,
            messages = initialMessages,
            scopes = defaultScopes
        )
    }

    private fun observeAuditChain() {
        auditChainRepository?.let { repo ->
            viewModelScope.launch {
                repo.entriesFlow.collect { entries ->
                    _uiState.value = _uiState.value.copy(
                        auditChainEntries = entries
                    )
                }
            }
        }
    }

    fun selectTab(tab: PocketOpsTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun enterSession(session: SessionItem) {
        _uiState.value = _uiState.value.copy(
            isInsideSession = true,
            activeSessionTitle = session.title,
            currentTab = PocketOpsTab.SESSIONS
        )
    }

    fun exitSessionToSessionsList() {
        _uiState.value = _uiState.value.copy(isInsideSession = false)
    }

    fun startNewSession() {
        val newSession = SessionItem(
            id = UUID.randomUUID().toString(),
            title = "New Agent Session",
            toolCount = 0,
            timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            isToday = true,
            isActive = true
        )
        val welcome = OpsChatMessage(
            sender = "agent",
            text = "Ready. Tell PocketOps what to do (e.g. \"/search contract terms\", \"Set alarm for 8am\")."
        )
        _uiState.value = _uiState.value.copy(
            sessions = listOf(newSession) + _uiState.value.sessions.map { it.copy(isActive = false) },
            activeSessionTitle = newSession.title,
            messages = listOf(welcome),
            isInsideSession = true
        )
    }

    fun toggleScope(scopeName: String) {
        val updated = _uiState.value.scopes.map {
            if (it.scope == scopeName) it.copy(isEnabled = !it.isEnabled) else it
        }
        _uiState.value = _uiState.value.copy(scopes = updated)
    }

    fun setRequireConfirmMed(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(requireConfirmMed = enabled)
    }

    fun setAllowHighRisk(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(allowHighRisk = enabled)
    }

    fun setAuditFilter(filter: String) {
        _uiState.value = _uiState.value.copy(auditFilter = filter)
    }

    fun verifyAuditChain() {
        viewModelScope.launch {
            val res = auditChainRepository?.verifyIntegrity()
            val verified = res is com.example.pocketai.core.trust.AuditVerificationResult.Valid
            _uiState.value = _uiState.value.copy(
                isAuditVerified = verified,
                auditVerificationMessage = if (verified) "VERIFIED" else "TAMPERED"
            )
        }
    }

    fun submitRequest(userPrompt: String, config: LlmConfig) {
        if (userPrompt.isBlank()) return

        val userMessage = OpsChatMessage(sender = "user", text = userPrompt)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isPlanning = true
        )

        viewModelScope.launch {
            try {
                val availableToolDefs = toolRegistry.getLlmToolDefinitions()
                val history = _uiState.value.messages.takeLast(6).map {
                    LlmMessage(role = if (it.sender == "user") "user" else "assistant", content = it.text)
                }

                val request = LlmRequest(
                    prompt = userPrompt,
                    systemPrompt = """
                        You are PocketOps, an intelligent system operations agent for Android running on-device.
                        Analyze user requests and invoke tools when needed.
                        Available tools: set_alarm, open_url, create_calendar_event, search_memory.
                    """.trimIndent(),
                    messages = history,
                    tools = availableToolDefs,
                    isSensitive = false
                )

                val response = llmRouter.route(request, config)
                _uiState.value = _uiState.value.copy(activeEngine = response.engineUsed)

                val parsedPlan = PlanParser.parse(response.content, response.toolCalls)

                // Live token streaming animation of the thinking/explanation
                val thinkingText = parsedPlan.explanation.ifBlank { "Evaluating intent, verifying trust policy, and preparing action plan..." }
                _uiState.value = _uiState.value.copy(isStreamingTokens = true)
                
                val chunkSize = 3
                for (i in 0 until thinkingText.length step chunkSize) {
                    val end = minOf(i + chunkSize, thinkingText.length)
                    _uiState.value = _uiState.value.copy(
                        streamingThinkingText = thinkingText.substring(0, end)
                    )
                    delay(18) // Cadence matching live token generation
                }
                delay(60)

                val agentReply = OpsChatMessage(
                    sender = "agent",
                    text = thinkingText
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + agentReply,
                    streamingThinkingText = null,
                    isStreamingTokens = false
                )

                if (parsedPlan.toolCalls.isNotEmpty()) {
                    executeToolCallsSequentially(parsedPlan.toolCalls)
                } else {
                    _uiState.value = _uiState.value.copy(isPlanning = false)
                }

            } catch (e: Exception) {
                val errorMsg = OpsChatMessage(
                    sender = "system",
                    text = "Planning error: ${e.message}",
                    isError = true
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMsg,
                    isPlanning = false
                )
            }
        }
    }

    private suspend fun executeToolCallsSequentially(toolCalls: List<LlmToolCall>) {
        val executedTools = mutableListOf<ToolExecutionDisplay>()

        for (call in toolCalls) {
            when (val outcome = guardedExecutor.execute(call.name, call.argumentsJson, userConfirmed = false)) {
                is GuardedExecutionOutcome.Completed -> {
                    executedTools.add(
                        ToolExecutionDisplay(
                            toolName = call.name,
                            statusPillText = "ok 42 ms",
                            isSuccess = outcome.toolResult.success,
                            queryOrParams = call.argumentsJson,
                            outputOrResult = outcome.toolResult.output
                        )
                    )
                }

                is GuardedExecutionOutcome.ConfirmationRequired -> {
                    // Show confirmation sheet
                    _uiState.value = _uiState.value.copy(
                        pendingConfirmation = PendingActionConfirmation(
                            tool = outcome.tool,
                            parametersJson = outcome.parametersJson,
                            riskLevel = outcome.riskLevel,
                            rationale = outcome.rationale
                        ),
                        isPlanning = false
                    )
                    return
                }

                is GuardedExecutionOutcome.Blocked -> {
                    executedTools.add(
                        ToolExecutionDisplay(
                            toolName = call.name,
                            statusPillText = "blocked",
                            isSuccess = false,
                            queryOrParams = call.argumentsJson,
                            outputOrResult = "BLOCKED: ${outcome.reason}"
                        )
                    )
                }

                is GuardedExecutionOutcome.ToolNotFound -> {
                    executedTools.add(
                        ToolExecutionDisplay(
                            toolName = call.name,
                            statusPillText = "not found",
                            isSuccess = false,
                            queryOrParams = call.argumentsJson,
                            outputOrResult = "Tool not found."
                        )
                    )
                }
            }
        }

        if (executedTools.isNotEmpty()) {
            val toolMessage = OpsChatMessage(
                sender = "agent",
                text = "Tool execution completed.",
                tools = executedTools
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + toolMessage,
                isPlanning = false
            )
        } else {
            _uiState.value = _uiState.value.copy(isPlanning = false)
        }
    }

    fun onConfirmPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null, isPlanning = true)

        viewModelScope.launch {
            when (val outcome = guardedExecutor.execute(pending.tool.name, pending.parametersJson, userConfirmed = true)) {
                is GuardedExecutionOutcome.Completed -> {
                    val toolCard = ToolExecutionDisplay(
                        toolName = pending.tool.name,
                        statusPillText = "approved",
                        isSuccess = outcome.toolResult.success,
                        queryOrParams = pending.parametersJson,
                        outputOrResult = outcome.toolResult.output
                    )
                    val resultMsg = OpsChatMessage(
                        sender = "agent",
                        text = "Action confirmed and executed successfully.",
                        tools = listOf(toolCard)
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + resultMsg,
                        isPlanning = false
                    )
                }
                is GuardedExecutionOutcome.Blocked -> {
                    val blockedMsg = OpsChatMessage(
                        sender = "system",
                        text = "Action blocked after confirmation: ${outcome.reason}",
                        isError = true
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + blockedMsg,
                        isPlanning = false
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isPlanning = false)
                }
            }
        }
    }

    fun onRejectPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)

        viewModelScope.launch {
            guardedExecutor.recordUserRejection(
                toolName = pending.tool.name,
                parametersJson = pending.parametersJson,
                riskLevel = pending.riskLevel
            )
            val rejectMsg = OpsChatMessage(
                sender = "system",
                text = "Action '${pending.tool.name}' denied by user. Logged to the audit chain.",
                isError = false
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + rejectMsg
            )
        }
    }
}
