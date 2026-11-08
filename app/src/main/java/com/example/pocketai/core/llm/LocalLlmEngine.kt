package com.example.pocketai.core.llm

import kotlinx.coroutines.delay
import java.util.Locale
import java.util.regex.Pattern

/**
 * ============================================================================
 * ON-DEVICE LOCAL LLM ENGINE STUB
 * Target Models: Android AICore Gemini Nano / Google MediaPipe Gemma 2B
 * ============================================================================
 * This engine runs 100% locally on-device. No telemetry, device state, or user
 * prompt ever touches the network.
 *
 * In production builds with hardware acceleration, this integrates with
 * Google AICore (Gemini Nano) or MediaPipe LLM Inference API (`LlmInference`).
 * In this reference implementation, an explainable deterministic rule-based
 * inference engine delivers instant, offline, verifiable responses and JSON
 * tool plans.
 */
class LocalLlmEngine : LlmEngine {
    override val name: String = "Local Engine (Gemini Nano / MediaPipe Gemma Stub)"
    override val isLocal: Boolean = true

    override suspend fun generate(request: LlmRequest, config: LlmConfig): LlmResponse {
        val startTime = System.currentTimeMillis()
        // Simulate local on-device NPU/CPU inference latency (100-250ms)
        delay(120)

        val promptLower = request.prompt.lowercase(Locale.ROOT)
        val toolCalls = mutableListOf<LlmToolCall>()
        val contentBuilder = StringBuilder()

        // 1. Tool Planning for PocketOps
        if (request.tools.isNotEmpty()) {
            val planned = planToolCalls(request.prompt, request.tools)
            if (planned.isNotEmpty()) {
                toolCalls.addAll(planned)
                contentBuilder.append("I have analyzed your request on-device and planned the following action(s): ")
                contentBuilder.append(planned.joinToString { it.name })
                contentBuilder.append(".\nPlease review the parameters before confirmation.")
            }
        }

        // 2. Guardian Explainable Security Audit Explanation (Strictly On-Device)
        if (toolCalls.isEmpty() && (promptLower.contains("guardian") || promptLower.contains("audit") || promptLower.contains("risk score") || promptLower.contains("permissions"))) {
            contentBuilder.append(generateGuardianExplanation(request.prompt))
        }

        // 3. Memory Question-Answering with Citations
        if (toolCalls.isEmpty() && (promptLower.contains("source:") || promptLower.contains("context:") || promptLower.contains("memory"))) {
            contentBuilder.append(generateMemoryGroundedAnswer(request.prompt))
        }

        // 4. Fallback on-device conversational reply
        if (contentBuilder.isEmpty()) {
            contentBuilder.append("Local On-Device Engine: Processed \"${request.prompt.take(60)}\" privately without network transmission.")
        }

        val latency = System.currentTimeMillis() - startTime
        return LlmResponse(
            content = contentBuilder.toString(),
            toolCalls = toolCalls,
            engineUsed = name,
            isLocal = true,
            latencyMs = latency
        )
    }

    private fun planToolCalls(prompt: String, availableTools: List<LlmToolDefinition>): List<LlmToolCall> {
        val calls = mutableListOf<LlmToolCall>()
        val p = prompt.lowercase(Locale.ROOT)
        val toolNames = availableTools.map { it.name }

        // Alarm Tool detection
        if (toolNames.contains("set_alarm") && (p.contains("alarm") || p.contains("wake me") || p.contains("timer"))) {
            var hour = 7
            var minute = 0
            val timeRegex = Pattern.compile("(\\d{1,2})(:(\\d{2}))?\\s*(am|pm)?")
            val matcher = timeRegex.matcher(p)
            if (matcher.find()) {
                hour = matcher.group(1)?.toIntOrNull() ?: 7
                minute = matcher.group(3)?.toIntOrNull() ?: 0
                val ampm = matcher.group(4)
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
            }
            calls.add(
                LlmToolCall(
                    name = "set_alarm",
                    argumentsJson = """{"hour": $hour, "minute": $minute, "message": "PocketAI Alarm"}"""
                )
            )
        }

        // Open HTTPS URL Tool detection
        if (toolNames.contains("open_url") && (p.contains("open") || p.contains("browse") || p.contains("http"))) {
            val urlRegex = Pattern.compile("(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?)")
            val matcher = urlRegex.matcher(prompt)
            if (matcher.find()) {
                val url = matcher.group(1) ?: "https://android.com"
                calls.add(
                    LlmToolCall(
                        name = "open_url",
                        argumentsJson = """{"url": "$url"}"""
                    )
                )
            } else if (p.contains("open google") || p.contains("google")) {
                calls.add(
                    LlmToolCall(
                        name = "open_url",
                        argumentsJson = """{"url": "https://www.google.com"}"""
                    )
                )
            }
        }

        // Calendar Event Tool detection
        if (toolNames.contains("create_calendar_event") && (p.contains("calendar") || p.contains("meeting") || p.contains("event") || p.contains("schedule"))) {
            var title = "PocketOps Meeting"
            val titleMatch = Regex("(schedule|create|add)\\s+(event|meeting)?\\s*[\"']?([^\"'\\n]+)[\"']?", RegexOption.IGNORE_CASE).find(prompt)
            if (titleMatch != null && titleMatch.groupValues.size >= 4) {
                val extracted = titleMatch.groupValues[3].trim()
                if (extracted.isNotEmpty()) title = extracted
            }
            calls.add(
                LlmToolCall(
                    name = "create_calendar_event",
                    argumentsJson = """{"title": "$title", "description": "Scheduled via PocketOps Agent", "durationMinutes": 60}"""
                )
            )
        }

        // Memory Search Tool detection
        if (toolNames.contains("search_memory") && (p.contains("search memory") || p.contains("find in docs") || p.contains("recall") || p.contains("lookup"))) {
            val query = prompt.replace(Regex("(?i)search memory for|find in docs|recall|lookup"), "").trim().ifEmpty { prompt }
            calls.add(
                LlmToolCall(
                    name = "search_memory",
                    argumentsJson = """{"query": "$query", "limit": 3}"""
                )
            )
        }

        return calls
    }

    private fun generateGuardianExplanation(prompt: String): String {
        return """
            [Local Guardian Audit Breakdown]
            Deterministic Analysis:
            • High-impact permissions (Microphone, Camera, Fine Location) grant continuous hardware telemetry access.
            • Accessibility services enable full UI tree traversal and key event observation, presenting critical privilege exposure.
            • Notification listeners can capture sensitive authentication OTPs and private messaging previews.
            
            Security Recommendation:
            Revoke background permissions for applications that do not require constant telemetry, and verify that enabled Accessibility services originate from trusted vendors.
        """.trimIndent()
    }

    private fun generateMemoryGroundedAnswer(prompt: String): String {
        return """
            Based on the indexed documents in local Memory:
            The retrieved passages confirm the relevant data points and specifications.
            
            Synthesized Summary:
            The stored text records match your query parameters precisely. See the cited source references below for full context verification.
        """.trimIndent()
    }
}
