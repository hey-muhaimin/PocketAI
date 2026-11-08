package com.example.pocketai

import com.example.pocketai.core.trust.AuditChainRepository
import com.example.pocketai.core.trust.AuditVerificationResult
import com.example.pocketai.core.trust.RiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditHashChainTest {

    @Test
    fun testValidChainIntegrityVerification() = runTest {
        val repo = AuditChainRepository()

        // Append 3 valid entries
        repo.append("tool_a", "{}", RiskLevel.LOW, "EXECUTED", "Result A")
        repo.append("tool_b", "{\"param\": 1}", RiskLevel.MEDIUM, "CONFIRMED_AND_EXECUTED", "Result B")
        repo.append("tool_c", "{}", RiskLevel.HIGH, "CONFIRMED_AND_EXECUTED", "Result C")

        val result = repo.verifyIntegrity()
        assertTrue("Expected valid verification result, got $result", result is AuditVerificationResult.Valid)
        val valid = result as AuditVerificationResult.Valid
        assertEquals(4, valid.totalEntries) // 1 genesis + 3 appended
    }

    @Test
    fun testTamperedEntryDetected() = runTest {
        val repo = AuditChainRepository()

        repo.append("tool_a", "{}", RiskLevel.LOW, "EXECUTED", "Original Output")
        repo.append("tool_b", "{}", RiskLevel.LOW, "EXECUTED", "Output B")

        // Deliberately tamper with summary in block 1
        repo.simulateTampering(1, "TAMPERED ILLEGITIMATE SUMMARY")

        val result = repo.verifyIntegrity()
        assertTrue("Tampering must be detected", result is AuditVerificationResult.Tampered)
        val tampered = result as AuditVerificationResult.Tampered
        assertEquals(1L, tampered.brokenIndex)
    }
}
