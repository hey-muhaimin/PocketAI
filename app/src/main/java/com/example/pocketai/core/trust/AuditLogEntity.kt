package com.example.pocketai.core.trust

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

/**
 * Immutable block in the cryptographic hash-chained audit trail.
 */
@Entity(tableName = "audit_log_chain")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chainIndex: Long,
    val timestamp: Long,
    val toolName: String,
    val parametersJson: String,
    val parametersHash: String,
    val riskLevel: String,
    val status: String, // "EXECUTED", "CONFIRMED", "REJECTED_BY_USER", "BLOCKED_BY_TRUST_POLICY", "FAILED"
    val resultSummary: String,
    val resultHash: String,
    val prevHash: String,
    val hash: String
) {
    companion object {
        const val GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun computeBlockHash(
            prevHash: String,
            chainIndex: Long,
            timestamp: Long,
            toolName: String,
            parametersHash: String,
            riskLevel: String,
            status: String,
            resultHash: String
        ): String {
            val payload = "$prevHash:$chainIndex:$timestamp:$toolName:$parametersHash:$riskLevel:$status:$resultHash"
            return sha256(payload)
        }
    }
}
