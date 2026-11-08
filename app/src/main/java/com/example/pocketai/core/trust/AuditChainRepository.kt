package com.example.pocketai.core.trust

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result returned by the cryptographic audit chain verification function.
 */
sealed interface AuditVerificationResult {
    data class Valid(val totalEntries: Int, val headHash: String) : AuditVerificationResult
    data class Tampered(
        val brokenIndex: Long,
        val expectedHash: String,
        val actualHash: String,
        val reason: String
    ) : AuditVerificationResult
}

/**
 * Thread-safe repository managing the hash-chained audit log with cryptographic verification.
 */
class AuditChainRepository {

    private val mutex = Mutex()
    private val entries = mutableListOf<AuditLogEntity>()
    private val _entriesFlow = MutableStateFlow<List<AuditLogEntity>>(emptyList())
    val entriesFlow: StateFlow<List<AuditLogEntity>> = _entriesFlow.asStateFlow()

    init {
        // Automatically initialize with a genesis record
        appendGenesisEntry()
    }

    private fun appendGenesisEntry() {
        val timestamp = System.currentTimeMillis()
        val index = 0L
        val toolName = "SYSTEM_INIT"
        val params = "{}"
        val paramsHash = AuditLogEntity.sha256(params)
        val riskLevel = RiskLevel.LOW.name
        val status = "INITIALIZED"
        val resultSummary = "PocketLoop Trust & Audit Subsystem Initialized"
        val resultHash = AuditLogEntity.sha256(resultSummary)
        val prevHash = AuditLogEntity.GENESIS_PREV_HASH

        val hash = AuditLogEntity.computeBlockHash(
            prevHash = prevHash,
            chainIndex = index,
            timestamp = timestamp,
            toolName = toolName,
            parametersHash = paramsHash,
            riskLevel = riskLevel,
            status = status,
            resultHash = resultHash
        )

        val genesis = AuditLogEntity(
            id = 1,
            chainIndex = index,
            timestamp = timestamp,
            toolName = toolName,
            parametersJson = params,
            parametersHash = paramsHash,
            riskLevel = riskLevel,
            status = status,
            resultSummary = resultSummary,
            resultHash = resultHash,
            prevHash = prevHash,
            hash = hash
        )
        entries.add(genesis)
        _entriesFlow.value = entries.toList()
    }

    /**
     * Appends a new audit record to the cryptographic hash chain.
     */
    suspend fun append(
        toolName: String,
        parametersJson: String,
        riskLevel: RiskLevel,
        status: String,
        resultSummary: String
    ): AuditLogEntity = mutex.withLock {
        val previous = entries.last()
        val nextIndex = previous.chainIndex + 1
        val timestamp = System.currentTimeMillis()
        val paramsHash = AuditLogEntity.sha256(parametersJson)
        val resultHash = AuditLogEntity.sha256(resultSummary)
        val prevHash = previous.hash

        val currentHash = AuditLogEntity.computeBlockHash(
            prevHash = prevHash,
            chainIndex = nextIndex,
            timestamp = timestamp,
            toolName = toolName,
            parametersHash = paramsHash,
            riskLevel = riskLevel.name,
            status = status,
            resultHash = resultHash
        )

        val newEntry = AuditLogEntity(
            id = (entries.size + 1).toLong(),
            chainIndex = nextIndex,
            timestamp = timestamp,
            toolName = toolName,
            parametersJson = parametersJson,
            parametersHash = paramsHash,
            riskLevel = riskLevel.name,
            status = status,
            resultSummary = resultSummary,
            resultHash = resultHash,
            prevHash = prevHash,
            hash = currentHash
        )

        entries.add(newEntry)
        _entriesFlow.value = entries.toList()
        newEntry
    }

    /**
     * Cryptographically verifies the integrity of the audit chain from genesis to head.
     * Detects any retroactive modification or corrupted links.
     */
    suspend fun verifyIntegrity(): AuditVerificationResult = mutex.withLock {
        if (entries.isEmpty()) {
            return AuditVerificationResult.Valid(0, AuditLogEntity.GENESIS_PREV_HASH)
        }

        var expectedPrevHash = AuditLogEntity.GENESIS_PREV_HASH

        for ((i, entry) in entries.withIndex()) {
            // Check link to previous block
            if (entry.prevHash != expectedPrevHash) {
                return AuditVerificationResult.Tampered(
                    brokenIndex = entry.chainIndex,
                    expectedHash = expectedPrevHash,
                    actualHash = entry.prevHash,
                    reason = "Chain break at index ${entry.chainIndex}: prevHash does not match previous entry's hash."
                )
            }

            // Check payload integrity against recorded hashes
            val actualParamsHash = AuditLogEntity.sha256(entry.parametersJson)
            val actualResultHash = AuditLogEntity.sha256(entry.resultSummary)
            if (entry.parametersHash != actualParamsHash || entry.resultHash != actualResultHash) {
                return AuditVerificationResult.Tampered(
                    brokenIndex = entry.chainIndex,
                    expectedHash = actualResultHash,
                    actualHash = entry.resultHash,
                    reason = "Tampering detected at index ${entry.chainIndex}: Payload content was altered."
                )
            }

            // Recompute current hash
            val recomputedHash = AuditLogEntity.computeBlockHash(
                prevHash = entry.prevHash,
                chainIndex = entry.chainIndex,
                timestamp = entry.timestamp,
                toolName = entry.toolName,
                parametersHash = entry.parametersHash,
                riskLevel = entry.riskLevel,
                status = entry.status,
                resultHash = entry.resultHash
            )

            if (recomputedHash != entry.hash) {
                return AuditVerificationResult.Tampered(
                    brokenIndex = entry.chainIndex,
                    expectedHash = recomputedHash,
                    actualHash = entry.hash,
                    reason = "Tampering detected at index ${entry.chainIndex}: Content was altered, resulting in hash mismatch."
                )
            }

            expectedPrevHash = entry.hash
        }

        return AuditVerificationResult.Valid(
            totalEntries = entries.size,
            headHash = entries.last().hash
        )
    }

    /**
     * For unit testing: simulates deliberate tampering with an entry to verify detection.
     */
    fun simulateTampering(index: Int, modifiedSummary: String) {
        if (index in entries.indices) {
            val original = entries[index]
            val tampered = original.copy(resultSummary = modifiedSummary)
            entries[index] = tampered
            _entriesFlow.value = entries.toList()
        }
    }
}
