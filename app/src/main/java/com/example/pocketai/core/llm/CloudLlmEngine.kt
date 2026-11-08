package com.example.pocketai.core.llm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Remote cloud LLM engine communicating with any OpenAI-compatible endpoint.
 * Configured via custom base URL, model name, and optional API key.
 */
class CloudLlmEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : LlmEngine {
    override val name: String = "Cloud Engine (OpenAI Compatible)"
    override val isLocal: Boolean = false

    override suspend fun generate(request: LlmRequest, config: LlmConfig): LlmResponse {
        val startTime = System.currentTimeMillis()

        if (config.apiKey.isBlank() && !config.endpointUrl.contains("10.0.2.2") && !config.endpointUrl.contains("localhost")) {
            throw IllegalStateException("API key is required for external cloud endpoint ${config.endpointUrl}")
        }

        val baseUrl = config.endpointUrl.trimEnd('/')
        val completionsUrl = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val rootJson = JSONObject().apply {
            put("model", config.modelName)
            put("temperature", request.temperature)

            val messagesArray = JSONArray()
            request.systemPrompt?.let { sys ->
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", sys)
                })
            }

            request.messages.forEach { msg ->
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }

            // Append user prompt as latest message
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", request.prompt)
            })

            put("messages", messagesArray)

            // Pass tools if available
            if (request.tools.isNotEmpty()) {
                val toolsArray = JSONArray()
                request.tools.forEach { toolDef ->
                    val toolJson = JSONObject().apply {
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", toolDef.name)
                            put("description", toolDef.description)
                            try {
                                put("parameters", JSONObject(toolDef.parametersJsonSchema))
                            } catch (e: Exception) {
                                put("parameters", JSONObject())
                            }
                        })
                    }
                    toolsArray.put(toolJson)
                }
                put("tools", toolsArray)
                put("tool_choice", "auto")
            }
        }

        val requestBody = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val reqBuilder = Request.Builder()
            .url(completionsUrl)
            .post(requestBody)

        if (config.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }

        val httpRequest = reqBuilder.build()

        val response = try {
            httpClient.newCall(httpRequest).execute()
        } catch (e: IOException) {
            throw IOException("Network error connecting to $completionsUrl: ${e.message}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string().orEmpty()
                throw IOException("HTTP ${resp.code} from $completionsUrl: $errorBody")
            }

            val bodyString = resp.body?.string().orEmpty()
            val parsedResponse = JSONObject(bodyString)
            val choices = parsedResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw IOException("Empty choices received from LLM endpoint")
            }

            val firstChoice = choices.getJSONObject(0)
            val messageObj = firstChoice.optJSONObject("message") ?: JSONObject()
            val content = messageObj.optString("content", "")
            val parsedToolCalls = mutableListOf<LlmToolCall>()

            val rawTools = messageObj.optJSONArray("tool_calls")
            if (rawTools != null) {
                for (i in 0 until rawTools.length()) {
                    val tc = rawTools.getJSONObject(i)
                    val fn = tc.optJSONObject("function") ?: JSONObject()
                    val name = fn.optString("name")
                    val args = fn.optString("arguments", "{}")
                    val id = tc.optString("id", java.util.UUID.randomUUID().toString())
                    if (name.isNotBlank()) {
                        parsedToolCalls.add(LlmToolCall(name = name, argumentsJson = args, id = id))
                    }
                }
            }

            val latency = System.currentTimeMillis() - startTime
            return LlmResponse(
                content = content,
                toolCalls = parsedToolCalls,
                engineUsed = "${name} (${config.modelName})",
                isLocal = false,
                latencyMs = latency
            )
        }
    }
}
