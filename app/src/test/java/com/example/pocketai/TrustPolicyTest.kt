package com.example.pocketai

import com.example.pocketai.core.trust.RiskEvaluator
import com.example.pocketai.core.trust.RiskLevel
import com.example.pocketai.core.trust.ScopeAllowList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrustPolicyTest {

    @Test
    fun testEverythingDeniedByDefault() {
        // Unlisted scope must be blocked
        assertFalse(ScopeAllowList.isScopeAllowed("arbitrary:shell_exec"))
        assertEquals(RiskLevel.BLOCKED, ScopeAllowList.getBaselineRisk("arbitrary:shell_exec"))

        val eval = RiskEvaluator.evaluate(
            toolName = "shell_exec",
            scope = "arbitrary:shell_exec",
            parametersJson = "{}"
        )
        assertEquals(RiskLevel.BLOCKED, eval.effectiveRisk)
        assertFalse(eval.isPermitted)
    }

    @Test
    fun testHttpsUrlPermittedButHttpBlocked() {
        val validHttps = RiskEvaluator.evaluate(
            toolName = "open_url",
            scope = "url:open_https",
            parametersJson = """{"url": "https://developer.android.com"}"""
        )
        assertEquals(RiskLevel.MEDIUM, validHttps.effectiveRisk)
        assertTrue(validHttps.isPermitted)

        // Cleartext http:// must be blocked
        val invalidHttp = RiskEvaluator.evaluate(
            toolName = "open_url",
            scope = "url:open_https",
            parametersJson = """{"url": "http://insecure.example.com"}"""
        )
        assertEquals(RiskLevel.BLOCKED, invalidHttp.effectiveRisk)
        assertFalse(invalidHttp.isPermitted)
    }

    @Test
    fun testInvalidAlarmParametersBlocked() {
        val invalidHour = RiskEvaluator.evaluate(
            toolName = "set_alarm",
            scope = "alarm:set",
            parametersJson = """{"hour": 25, "minute": 30}"""
        )
        assertEquals(RiskLevel.BLOCKED, invalidHour.effectiveRisk)
        assertFalse(invalidHour.isPermitted)
    }
}
