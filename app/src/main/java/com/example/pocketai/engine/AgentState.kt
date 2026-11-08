package com.example.pocketai.engine

sealed interface AgentTurnState {
    object Idle : AgentTurnState
    data class Thinking(val content: String) : AgentTurnState
    data class ExecutingTool(val toolName: String, val argumentJson: String) : AgentTurnState
    data class ToolResponse(val toolName: String, val resultSummary: String, val durationMs: Long, val isSuccess: Boolean) : AgentTurnState
    data class Completed(val finalResponse: String) : AgentTurnState
    data class Error(val errorMessage: String) : AgentTurnState
}

data class TelemetryLog(
    val durationMs: Long,
    val tokensProcessed: Int,
    val modelTag: String = "qwen2.5-3b",
    val indexStatus: String = "index ready"
)

data class ToolCallSchema(
    val name: String,
    val arguments: Map<String, String>
)

data class ToolCallListSchema(
    val modelSignature: String = "qwen2.5-7b", // Dynamically switches tracking signatures
    val calls: List<ToolCallSchema>
)
