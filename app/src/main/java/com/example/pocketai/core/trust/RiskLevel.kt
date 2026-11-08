package com.example.pocketai.core.trust

/**
 * Risk classification for actions and tools in PocketAI.
 */
enum class RiskLevel(val displayName: String, val requiresConfirmation: Boolean) {
    /**
     * Read-only or safe benign operations (e.g., local memory search).
     */
    LOW("Low", false),

    /**
     * Operations modifying system state or launching external apps (e.g., setting alarm, opening browser).
     * Mandatory user confirmation required prior to execution.
     */
    MEDIUM("Medium", true),

    /**
     * Operations altering personal data or scheduling system events (e.g., adding calendar meetings).
     * Mandatory user confirmation required prior to execution.
     */
    HIGH("High", true),

    /**
     * Dangerous, unauthorized, unlisted, or malicious operations.
     * Strictly blocked by trust policy and never executed.
     */
    BLOCKED("Blocked", false)
}
