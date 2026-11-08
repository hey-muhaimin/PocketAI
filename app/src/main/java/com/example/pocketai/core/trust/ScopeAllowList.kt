package com.example.pocketai.core.trust

/**
 * Strict Scope Allow-List.
 *
 * PRINCIPLE OF LEAST PRIVILEGE:
 * Everything is DENIED by default. Only explicitly registered scopes in the allowlist
 * can be executed. Any unknown or missing scope immediately returns [RiskLevel.BLOCKED].
 */
object ScopeAllowList {

    // Immutable whitelist of authorized system scopes
    private val allowedScopes: Map<String, RiskLevel> = mapOf(
        "memory:read" to RiskLevel.LOW,
        "url:open_https" to RiskLevel.MEDIUM,
        "alarm:set" to RiskLevel.MEDIUM,
        "calendar:create_event" to RiskLevel.HIGH
    )

    /**
     * Checks if the given scope is authorized.
     */
    fun isScopeAllowed(scope: String): Boolean {
        return allowedScopes.containsKey(scope.trim().lowercase())
    }

    /**
     * Returns the baseline risk tier for the scope, or [RiskLevel.BLOCKED] if unlisted.
     */
    fun getBaselineRisk(scope: String): RiskLevel {
        return allowedScopes[scope.trim().lowercase()] ?: RiskLevel.BLOCKED
    }

    /**
     * Exposes the immutable set of all allowed scopes for auditing.
     */
    fun getAllowedScopes(): Set<String> = allowedScopes.keys
}
