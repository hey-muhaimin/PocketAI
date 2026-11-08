package com.example.pocketai.core.actions

import com.example.pocketai.core.trust.AuditChainRepository
import com.example.pocketai.core.trust.AuditLogEntity
import com.example.pocketai.core.trust.RiskEvaluator
import com.example.pocketai.core.trust.RiskLevel

/**
 * Outcome returned by GuardedExecutor.
 */
sealed interface GuardedExecutionOutcome {
    data class Completed(
        val toolResult: ToolResult,
        val auditRecord: AuditLogEntity
    ) : GuardedExecutionOutcome

    data class ConfirmationRequired(
        val tool: Tool,
        val parametersJson: String,
        val riskLevel: RiskLevel,
        val rationale: String
    ) : GuardedExecutionOutcome

    data class Blocked(
        val reason: String,
        val auditRecord: AuditLogEntity
    ) : GuardedExecutionOutcome

    data class ToolNotFound(
        val requestedName: String
    ) : GuardedExecutionOutcome
}

/**
 * The ONLY authorized path for executing tools in PocketAI.
 *
 * Invariants:
 * 1. Resolves tool from registry.
 * 2. Enforces deterministic scope allowlist & parameter security rules.
 * 3. Enforces risk-based user confirmation for Medium and High risk tiers.
 * 4. Records every single execution attempt, rejection, and outcome in the
 *    cryptographic hash-chained audit log.
 */
class GuardedExecutor(
    private val toolRegistry: ToolRegistry,
    private val auditChainRepository: AuditChainRepository
) {

    /**
     * Executes a tool under guarded trust policies.
     *
     * @param toolName Name of the tool to execute.
     * @param parametersJson JSON payload of parameters.
     * @param userConfirmed True if the user has explicitly accepted the confirmation dialog.
     */
    suspend fun execute(
        toolName: String,
        parametersJson: String,
        userConfirmed: Boolean = false
    ): GuardedExecutionOutcome {
        val tool = toolRegistry.getTool(toolName)
            ?: return GuardedExecutionOutcome.ToolNotFound(toolName)

        // Step 1: Trust & Security Evaluation
        val eval = RiskEvaluator.evaluate(
            toolName = tool.name,
            scope = tool.requiredScope,
            parametersJson = parametersJson
        )

        if (!eval.isPermitted || eval.effectiveRisk == RiskLevel.BLOCKED) {
            val auditEntry = auditChainRepository.append(
                toolName = tool.name,
                parametersJson = parametersJson,
                riskLevel = RiskLevel.BLOCKED,
                status = "BLOCKED_BY_TRUST_POLICY",
                resultSummary = "Blocked: ${eval.rationale}"
            )
            return GuardedExecutionOutcome.Blocked(
                reason = eval.rationale,
                auditRecord = auditEntry
            )
        }

        // Step 2: Risk-Based User Confirmation Gate
        if (eval.effectiveRisk.requiresConfirmation && !userConfirmed) {
            return GuardedExecutionOutcome.ConfirmationRequired(
                tool = tool,
                parametersJson = parametersJson,
                riskLevel = eval.effectiveRisk,
                rationale = eval.rationale
            )
        }

        // Step 3: Tool Execution
        val startTime = System.currentTimeMillis()
        val result = try {
            tool.execute(parametersJson)
        } catch (e: Exception) {
            ToolResult(
                success = false,
                output = "",
                error = "Unexpected execution error: ${e.message}"
            )
        }
        val durationMs = System.currentTimeMillis() - startTime

        // Step 4: Append to Cryptographic Audit Hash Chain
        val status = if (result.success) {
            if (eval.effectiveRisk.requiresConfirmation) "CONFIRMED_AND_EXECUTED" else "EXECUTED"
        } else {
            "FAILED"
        }

        val summary = if (result.success) {
            result.output.take(150)
        } else {
            "Error: ${result.error.orEmpty().take(150)}"
        }

        val auditEntry = auditChainRepository.append(
            toolName = tool.name,
            parametersJson = parametersJson,
            riskLevel = eval.effectiveRisk,
            status = status,
            resultSummary = "$summary (took ${durationMs}ms)"
        )

        return GuardedExecutionOutcome.Completed(
            toolResult = result,
            auditRecord = auditEntry
        )
    }

    /**
     * Records a user rejection when they dismiss the confirmation curtain.
     */
    suspend fun recordUserRejection(
        toolName: String,
        parametersJson: String,
        riskLevel: RiskLevel
    ): AuditLogEntity {
        return auditChainRepository.append(
            toolName = toolName,
            parametersJson = parametersJson,
            riskLevel = riskLevel,
            status = "REJECTED_BY_USER",
            resultSummary = "User rejected confirmation prompt for $toolName"
        )
    }
}
