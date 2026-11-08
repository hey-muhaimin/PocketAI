package com.example.pocketai.core.trust

import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * Deterministic risk evaluation engine enforcing fine-grained security policies
 * on action scopes and payload parameters.
 */
object RiskEvaluator {

    data class EvaluationResult(
        val effectiveRisk: RiskLevel,
        val isPermitted: Boolean,
        val rationale: String
    )

    /**
     * Evaluates a tool call against the allowlist and parameter constraints.
     */
    fun evaluate(toolName: String, scope: String, parametersJson: String): EvaluationResult {
        // 1. Strict allowlist check: Denied by default
        if (!ScopeAllowList.isScopeAllowed(scope)) {
            return EvaluationResult(
                effectiveRisk = RiskLevel.BLOCKED,
                isPermitted = false,
                rationale = "Scope '$scope' is not present in ScopeAllowList. Action rejected by default."
            )
        }

        val baseRisk = ScopeAllowList.getBaselineRisk(scope)

        // 2. Specific parameter-level security guards
        try {
            val json = JSONObject(if (parametersJson.isBlank()) "{}" else parametersJson)

            when (scope.lowercase(Locale.ROOT)) {
                "url:open_https" -> {
                    val rawUrl = json.optString("url", "").trim()
                    if (rawUrl.isBlank()) {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Parameter 'url' is missing or blank."
                        )
                    }

                    val uri = try {
                        URI.create(rawUrl)
                    } catch (e: Exception) {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Malformed URL format: $rawUrl"
                        )
                    }

                    val scheme = uri.scheme?.lowercase(Locale.ROOT)
                    if (scheme != "https") {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Insecure scheme '$scheme' detected. Only strict 'https://' URLs are authorized."
                        )
                    }

                    // Block suspicious localhost/internal loopback redirection attempts in production tool calls
                    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
                    if (host.contains("metadata.google.internal") || host == "169.254.169.254") {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Target host '$host' attempts internal cloud metadata exfiltration."
                        )
                    }
                }

                "alarm:set" -> {
                    val hour = json.optInt("hour", -1)
                    val minute = json.optInt("minute", -1)
                    if (hour !in 0..23 || minute !in 0..59) {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Invalid time parameters (hour=$hour, minute=$minute)."
                        )
                    }
                }

                "calendar:create_event" -> {
                    val title = json.optString("title", "").trim()
                    if (title.isBlank()) {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Calendar event title cannot be empty."
                        )
                    }
                }

                "memory:read" -> {
                    val query = json.optString("query", "").trim()
                    if (query.isBlank()) {
                        return EvaluationResult(
                            effectiveRisk = RiskLevel.BLOCKED,
                            isPermitted = false,
                            rationale = "Memory search query cannot be empty."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return EvaluationResult(
                effectiveRisk = RiskLevel.BLOCKED,
                isPermitted = false,
                rationale = "Failed to parse tool parameters JSON: ${e.message}"
            )
        }

        return EvaluationResult(
            effectiveRisk = baseRisk,
            isPermitted = true,
            rationale = "Action '$toolName' complies with security policy for scope '$scope'."
        )
    }
}
