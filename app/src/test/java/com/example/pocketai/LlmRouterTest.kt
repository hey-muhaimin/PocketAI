package com.example.pocketai

import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.llm.LlmEngine
import com.example.pocketai.core.llm.LlmRequest
import com.example.pocketai.core.llm.LlmResponse
import com.example.pocketai.core.llm.LlmRouter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LlmRouterTest {

    private class MockEngine(
        override val name: String,
        override val isLocal: Boolean,
        private val shouldFail: Boolean = false,
        private val responseText: String = "Mock response"
    ) : LlmEngine {
        var callCount = 0

        override suspend fun generate(request: LlmRequest, config: LlmConfig): LlmResponse {
            callCount++
            if (shouldFail) {
                throw IOException("Simulated engine failure")
            }
            return LlmResponse(
                content = responseText,
                engineUsed = name,
                isLocal = isLocal
            )
        }
    }

    @Test
    fun testSensitiveRequestNeverInvokesCloudEngine() = runTest {
        val mockLocal = MockEngine(name = "LocalEngine", isLocal = true, responseText = "Private data")
        val mockCloud = MockEngine(name = "CloudEngine", isLocal = false)
        val router = LlmRouter(localEngine = mockLocal, cloudEngine = mockCloud)

        val sensitiveRequest = LlmRequest(
            prompt = "Classified user files",
            isSensitive = true
        )

        val config = LlmConfig(preferLocal = false) // Even if preferLocal is false!
        val response = router.route(sensitiveRequest, config)

        assertEquals("LocalEngine", response.engineUsed)
        assertEquals(1, mockLocal.callCount)
        assertEquals(0, mockCloud.callCount) // STRICT INVARIANT: Cloud call count MUST be 0
    }

    @Test
    fun testPreferLocalUsesLocalEngineFirst() = runTest {
        val mockLocal = MockEngine(name = "LocalEngine", isLocal = true, responseText = "Local output")
        val mockCloud = MockEngine(name = "CloudEngine", isLocal = false)
        val router = LlmRouter(localEngine = mockLocal, cloudEngine = mockCloud)

        val request = LlmRequest(prompt = "Hello", isSensitive = false)
        val config = LlmConfig(preferLocal = true)
        val response = router.route(request, config)

        assertEquals("LocalEngine", response.engineUsed)
        assertEquals(1, mockLocal.callCount)
        assertEquals(0, mockCloud.callCount)
    }

    @Test
    fun testCloudFailureFallsBackToLocal() = runTest {
        val mockLocal = MockEngine(name = "LocalEngine", isLocal = true, responseText = "Local fallback")
        val mockCloud = MockEngine(name = "CloudEngine", isLocal = false, shouldFail = true)
        val router = LlmRouter(localEngine = mockLocal, cloudEngine = mockCloud)

        val request = LlmRequest(prompt = "Query", isSensitive = false)
        val config = LlmConfig(preferLocal = false)
        val response = router.route(request, config)

        assertEquals("LocalEngine", response.engineUsed)
        assertEquals(1, mockCloud.callCount)
        assertEquals(1, mockLocal.callCount)
    }
}
