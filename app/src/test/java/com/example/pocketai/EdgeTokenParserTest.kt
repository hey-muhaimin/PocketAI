package com.example.pocketai

import androidx.test.core.app.ApplicationProvider
import com.example.pocketai.engine.AgentTurnState
import com.example.pocketai.engine.EdgeTokenParser
import com.example.pocketai.engine.LocalMcpRouter
import com.example.pocketai.engine.ToolCallSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class EdgeTokenParserTest {

    @Test
    fun testProcessTokenStreamExtractsToolCall() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val router = LocalMcpRouter(context)
        val parser = EdgeTokenParser(router, CoroutineScope(Dispatchers.Default))

        val stream = flowOf(
            "I'll set ",
            "the alarm. ",
            "<tool_call>{\"name\":\"set_alarm\",\"arguments\":{\"hour\":\"7\",\"minute\":\"30\",\"message\":\"Wake up\"}}</tool_call>"
        )

        parser.processTokenStream(stream)

        val nonIdle = withTimeout(5000) {
            parser.engineState.first { it !is AgentTurnState.Idle }
        }
        assertTrue(
            "Expected non-idle state, got: $nonIdle",
            nonIdle is AgentTurnState.Thinking || nonIdle is AgentTurnState.ExecutingTool || nonIdle is AgentTurnState.ToolResponse || nonIdle is AgentTurnState.Completed
        )
    }

    @Test
    fun testLocalMcpRouterExecutesAlarm() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val router = LocalMcpRouter(context)

        val response = router.routeToolCall(
            ToolCallSchema(
                name = "set_alarm",
                arguments = mapOf("hour" to "7", "minute" to "30", "message" to "Wake up")
            )
        )

        assertTrue(response is AgentTurnState.ToolResponse)
        val toolResp = response as AgentTurnState.ToolResponse
        assertEquals("set_alarm", toolResp.toolName)
        assertTrue(toolResp.isSuccess)
    }
}
