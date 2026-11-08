package com.example.pocketai.core.llm

import java.util.UUID

/**
 * Chat message representation for LLM interactions.
 */
data class LlmMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String
)

/**
 * Definition of an actionable tool provided to the LLM planner.
 */
data class LlmToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String
)

/**
 * Parsed tool call emitted by an LLM engine or local rule-based planner.
 */
data class LlmToolCall(
    val name: String,
    val argumentsJson: String,
    val id: String = UUID.randomUUID().toString()
)

/**
 * Comprehensive request sent through the LlmRouter.
 *
 * @param isSensitive If true, this request MUST NEVER be routed to any cloud or external endpoint.
 */
data class LlmRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val messages: List<LlmMessage> = emptyList(),
    val tools: List<LlmToolDefinition> = emptyList(),
    val isSensitive: Boolean = false,
    val temperature: Float = 0.7f
)

/**
 * Structured response from an LLM engine.
 */
data class LlmResponse(
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val engineUsed: String,
    val isLocal: Boolean,
    val latencyMs: Long = 0
)

/**
 * In-app configuration for LLM routing and OpenAI-compatible cloud fallback.
 */
data class LlmConfig(
    val endpointUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o-mini",
    val preferLocal: Boolean = true
)

/**
 * Contract implemented by both on-device and remote LLM engines.
 */
interface LlmEngine {
    val name: String
    val isLocal: Boolean

    suspend fun generate(request: LlmRequest, config: LlmConfig): LlmResponse
}
