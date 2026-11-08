package com.example.pocketai.pocketops

import com.example.pocketai.core.llm.LlmToolCall
import org.json.JSONArray
import org.json.JSONObject

data class ParsedPlan(
    val explanation: String,
    val toolCalls: List<LlmToolCall>
)

/**
 * Robust JSON Plan Parser capable of extracting structured tool calls from both
 * native tool call objects and LLM text completions containing embedded JSON.
 */
object PlanParser {

    /**
     * Parses native tool calls and fallback text completions into a normalized [ParsedPlan].
     */
    fun parse(content: String, nativeToolCalls: List<LlmToolCall> = emptyList()): ParsedPlan {
        if (nativeToolCalls.isNotEmpty()) {
            return ParsedPlan(explanation = content, toolCalls = nativeToolCalls)
        }

        if (content.isBlank()) {
            return ParsedPlan(explanation = "", toolCalls = emptyList())
        }

        // Try extracting embedded JSON blocks from text
        val jsonPattern = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```")
        val match = jsonPattern.find(content)
        val candidateJson = match?.groupValues?.get(1)?.trim() ?: content.trim()

        val extractedCalls = mutableListOf<LlmToolCall>()

        // 1. Try parsing candidate as JSON Object
        try {
            if (candidateJson.startsWith("{") && candidateJson.endsWith("}")) {
                val obj = JSONObject(candidateJson)

                // Check for {"tool": "name", "parameters": {...}}
                if (obj.has("tool") || obj.has("name")) {
                    val name = if (obj.has("tool")) obj.getString("tool") else obj.getString("name")
                    val params = if (obj.has("parameters")) obj.opt("parameters") else obj.opt("arguments")
                    val paramsJson = when (params) {
                        is JSONObject -> params.toString().replace("\\/", "/")
                        is String -> params.replace("\\/", "/")
                        else -> "{}"
                    }
                    extractedCalls.add(LlmToolCall(name = name, argumentsJson = paramsJson))
                } else if (obj.has("tools") || obj.has("tool_calls") || obj.has("plan")) {
                    val array = obj.optJSONArray("tools") ?: obj.optJSONArray("tool_calls") ?: obj.optJSONArray("plan")
                    if (array != null) {
                        parseJsonArray(array, extractedCalls)
                    }
                }
            }
        } catch (e: Exception) {
            // Not a valid JSON object, proceed
        }

        // 2. Try parsing candidate as JSON Array
        if (extractedCalls.isEmpty()) {
            try {
                if (candidateJson.startsWith("[") && candidateJson.endsWith("]")) {
                    val arr = JSONArray(candidateJson)
                    parseJsonArray(arr, extractedCalls)
                }
            } catch (e: Exception) {
                // Not a valid JSON array
            }
        }

        val explanationCleaned = if (extractedCalls.isNotEmpty()) {
            content.replace(jsonPattern, "").trim().ifEmpty { "Executing planned actions." }
        } else {
            content
        }

        return ParsedPlan(
            explanation = explanationCleaned,
            toolCalls = extractedCalls
        )
    }

    private fun parseJsonArray(arr: JSONArray, destination: MutableList<LlmToolCall>) {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val name = item.optString("tool", item.optString("name", ""))
            val params = item.opt("parameters") ?: item.opt("arguments") ?: item.opt("args")
            val paramsJson = when (params) {
                is JSONObject -> params.toString().replace("\\/", "/")
                is String -> params.replace("\\/", "/")
                else -> "{}"
            }
            if (name.isNotBlank()) {
                destination.add(LlmToolCall(name = name, argumentsJson = paramsJson))
            }
        }
    }
}
