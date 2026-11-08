package com.example.pocketai

import com.example.pocketai.core.llm.LlmToolCall
import com.example.pocketai.pocketops.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlanParserTest {

    @Test
    fun testNativeToolCallsPreserved() {
        val nativeCalls = listOf(
            LlmToolCall(name = "set_alarm", argumentsJson = """{"hour": 7, "minute": 30}""")
        )
        val plan = PlanParser.parse("Setting your alarm.", nativeCalls)
        assertEquals(1, plan.toolCalls.size)
        assertEquals("set_alarm", plan.toolCalls[0].name)
    }

    @Test
    fun testEmbeddedJsonMarkdownBlockParsed() {
        val llmOutput = """
            Here is the planned action:
            ```json
            {
              "tool": "open_url",
              "parameters": {
                "url": "https://android.com"
              }
            }
            ```
            Please confirm execution.
        """.trimIndent()

        val plan = PlanParser.parse(llmOutput)
        assertEquals(1, plan.toolCalls.size)
        assertEquals("open_url", plan.toolCalls[0].name)
        assertTrue(plan.toolCalls[0].argumentsJson.contains("https://android.com"))
    }

    @Test
    fun testJsonArrayToolCallsParsed() {
        val jsonArrayText = """
            [
              {"tool": "set_alarm", "parameters": {"hour": 8, "minute": 0}},
              {"tool": "open_url", "parameters": {"url": "https://google.com"}}
            ]
        """.trimIndent()

        val plan = PlanParser.parse(jsonArrayText)
        assertEquals(2, plan.toolCalls.size)
        assertEquals("set_alarm", plan.toolCalls[0].name)
        assertEquals("open_url", plan.toolCalls[1].name)
    }

    @Test
    fun testMalformedTextYieldsNoToolsGracefully() {
        val badText = "I cannot fulfill this request at this time."
        val plan = PlanParser.parse(badText)
        assertTrue(plan.toolCalls.isEmpty())
        assertEquals(badText, plan.explanation)
    }
}
