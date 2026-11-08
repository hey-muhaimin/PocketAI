package com.example.pocketai.core.engine

import java.util.UUID

/**
 * Execution and authorization status of a tool in the PocketLoop engine.
 */
enum class ToolStatus(val label: String) {
    AWAITING_CONFIRMATION("awaiting"),
    APPROVED("approved"),
    EXECUTING("running"),
    SUCCESS("ok"),
    FAILED("failed"),
    BLOCKED("blocked"),
    REJECTED("denied")
}

/**
 * Historical record of a tool execution for UI thread rendering.
 */
data class ToolExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val argumentJson: String,
    val status: ToolStatus,
    val output: String? = null,
    val latencyMs: Long = 0
)

/**
 * Sealed class hierarchy representing active agent turn state during on-device execution.
 */
sealed interface AgentTurnState {
    data object Idle : AgentTurnState

    data class StreamingThought(
        val partialText: String
    ) : AgentTurnState

    data class ToolCalling(
        val toolName: String,
        val argumentJson: String,
        val status: ToolStatus,
        val executionOutput: String? = null,
        val latencyMs: Long = 0
    ) : AgentTurnState

    data class Completed(
        val fullResponse: String,
        val toolExecutions: List<ToolExecutionRecord> = emptyList(),
        val citations: List<CitationRef> = emptyList()
    ) : AgentTurnState

    data class Interrupted(
        val reason: String
    ) : AgentTurnState

    data class Error(
        val message: String
    ) : AgentTurnState
}

/**
 * Citation reference linking generated statements to grounded source documents.
 */
data class CitationRef(
    val index: Int,
    val filename: String,
    val folderPath: String,
    val relevanceScore: Float
)

/**
 * Telemetry log entry for the cryptographic audit trail matching the layout history.
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val chainIndex: Long,
    val timestamp: String, // e.g. "09:12" or "yesterday"
    val toolName: String,
    val summary: String,
    val approvalStatus: String, // "user-approved", "auto-allowed", "user-declined", "deny"
    val statusBadge: String, // "ok", "blocked", "failed"
    val prevHashPrefix: String, // e.g. "7be1...09aa"
    val latencyMs: Long = 0,
    val fullHash: String
)

/**
 * Agent chat session metadata.
 */
data class AgentSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timestamp: String,
    val toolCount: Int,
    val tokenCount: Int,
    val hasDenied: Boolean = false,
    val isToday: Boolean = true
)
