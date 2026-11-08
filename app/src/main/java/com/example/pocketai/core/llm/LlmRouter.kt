package com.example.pocketai.core.llm

/**
 * Intelligent Router for LLM requests across on-device and remote cloud endpoints.
 *
 * CRITICAL PRIVACY INVARIANT:
 * Any request marked with [LlmRequest.isSensitive] == true MUST NEVER be transmitted
 * to the cloud engine under any circumstances. If the local engine fails or cannot
 * fulfill a sensitive request, the router rejects the cloud fallback and throws
 * a SecurityException / returns a strictly local error response.
 */
class LlmRouter(
    val localEngine: LlmEngine = LocalLlmEngine(),
    val cloudEngine: LlmEngine = CloudLlmEngine()
) {
    companion object {
        private const val TAG = "LlmRouter"

        private fun logDebug(msg: String) {
            try {
                android.util.Log.d(TAG, msg)
            } catch (t: Throwable) {
                println("[$TAG] $msg")
            }
        }

        private fun logWarn(msg: String, error: Throwable? = null) {
            try {
                android.util.Log.w(TAG, msg, error)
            } catch (t: Throwable) {
                println("[$TAG] WARN: $msg ${error?.message.orEmpty()}")
            }
        }

        private fun logError(msg: String, error: Throwable? = null) {
            try {
                android.util.Log.e(TAG, msg, error)
            } catch (t: Throwable) {
                println("[$TAG] ERROR: $msg ${error?.message.orEmpty()}")
            }
        }
    }

    suspend fun route(request: LlmRequest, config: LlmConfig): LlmResponse {
        // Enforce Sensitive Data Boundary
        if (request.isSensitive) {
            logDebug("Request marked SENSITIVE. Strictly confining execution to on-device engine.")
            return localEngine.generate(request, config)
        }

        // Standard flow: User preference drives primary choice
        return if (config.preferLocal) {
            try {
                val localResponse = localEngine.generate(request, config)
                // If local engine generated content or tool calls, return it
                if (localResponse.content.isNotBlank() || localResponse.toolCalls.isNotEmpty()) {
                    localResponse
                } else {
                    fallbackToCloud(request, config)
                }
            } catch (e: Exception) {
                logWarn("Local engine error: ${e.message}. Attempting cloud fallback...", e)
                fallbackToCloud(request, config)
            }
        } else {
            try {
                cloudEngine.generate(request, config)
            } catch (e: Exception) {
                logWarn("Cloud engine error: ${e.message}. Falling back to on-device local engine...", e)
                localEngine.generate(request, config)
            }
        }
    }

    private suspend fun fallbackToCloud(request: LlmRequest, config: LlmConfig): LlmResponse {
        if (request.isSensitive) {
            throw SecurityException("Inadmissible routing: Sensitive requests cannot fall back to cloud endpoints.")
        }
        return try {
            cloudEngine.generate(request, config)
        } catch (e: Exception) {
            logError("Cloud fallback failed: ${e.message}. Executing local fallback.", e)
            localEngine.generate(request, config)
        }
    }
}
