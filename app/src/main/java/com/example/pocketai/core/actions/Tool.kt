package com.example.pocketai.core.actions

import com.example.pocketai.core.trust.RiskLevel

/**
 * Result of tool execution.
 */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

/**
 * Pluggable Tool interface implemented by all executable capabilities in PocketAI.
 */
interface Tool {
    val name: String
    val description: String
    val requiredScope: String
    val defaultRisk: RiskLevel
    val parametersJsonSchema: String

    suspend fun execute(parametersJson: String): ToolResult
}
