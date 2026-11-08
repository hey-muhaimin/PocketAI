package com.example.pocketai.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class EdgeTokenParser(
    private val mcpRouter: LocalMcpRouter,
    private val externalScope: CoroutineScope
) {
    private val _engineState = MutableStateFlow<AgentTurnState>(AgentTurnState.Idle)
    val engineState: StateFlow<AgentTurnState> = _engineState.asStateFlow()

    private val accumulatedOutput = StringBuilder()
    private var isParsingToolCall = false
    private val toolCallBuffer = StringBuilder()

    private val toolStartTag = "<tool_call>"
    private val toolEndTag = "</tool_call>"

    /**
     * Consumes an incoming asynchronous character flow token stream from the on-device Edge model
     * and processes individual characters sequentially without locking the Android Main UI Thread.
     */
    fun processTokenStream(tokenFlow: Flow<String>) {
        externalScope.launch(Dispatchers.Default) {
            accumulatedOutput.clear()
            toolCallBuffer.clear()
            isParsingToolCall = false
            _engineState.value = AgentTurnState.Thinking("")

            tokenFlow.collect { rawChunk ->
                for (char in rawChunk) {
                    accumulatedOutput.append(char)
                    val absoluteString = accumulatedOutput.toString()

                    if (!isParsingToolCall && absoluteString.endsWith(toolStartTag)) {
                        isParsingToolCall = true
                        toolCallBuffer.clear()
                        // Cut off the tool tag prefix from the visible thinking workspace layout
                        val thinkingSegment = absoluteString.substring(0, absoluteString.length - toolStartTag.length)
                        _engineState.value = AgentTurnState.Thinking(thinkingSegment)
                        continue
                    }

                    if (isParsingToolCall) {
                        if (absoluteString.endsWith(toolEndTag)) {
                            isParsingToolCall = false
                            val fullJsonString = toolCallBuffer.toString().trim()
                            
                            // Extract cleanly the text content excluding everything up to the closed tool tag
                            val cutIndex = accumulatedOutput.indexOf(toolEndTag) + toolEndTag.length
                            if (cutIndex <= accumulatedOutput.length) {
                                accumulatedOutput.delete(0, cutIndex)
                            }
                            
                            executeExtractedTool(fullJsonString)
                        } else {
                            toolCallBuffer.append(char)
                        }
                    } else {
                        // Keep updating the dynamic thinking stream to refresh the custom typography containers
                        _engineState.value = AgentTurnState.Thinking(absoluteString)
                    }
                }
            }
            
            // Seal turn execution state once flow stream reaches normal completion boundaries
            val finalOutput = accumulatedOutput.toString()
            if (finalOutput.isNotEmpty() && _engineState.value !is AgentTurnState.Completed) {
                _engineState.value = AgentTurnState.Completed(finalOutput)
            }
        }
    }

    /**
     * Deserializes JSON schema structural blocks and triggers matching actions via the Local Android MCP Router.
     */
    private suspend fun executeExtractedTool(jsonString: String) {
        runCatching {
            val jsonObject = JSONObject(jsonString)
            val toolName = jsonObject.optString("name", "unknown")
            val argsObj = jsonObject.optJSONObject("arguments") ?: JSONObject()
            
            val argumentsMap = mutableMapOf<String, String>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                argumentsMap[key] = argsObj.optString(key, "")
            }

            _engineState.value = AgentTurnState.ExecutingTool(toolName, jsonString)
            
            val schema = ToolCallSchema(name = toolName, arguments = argumentsMap)
            val routerResponse = mcpRouter.routeToolCall(schema)
            
            _engineState.value = routerResponse
            
            // Re-prime layout context by converting tool outcome summaries back to assistant response pipelines
            if (routerResponse is AgentTurnState.ToolResponse && routerResponse.isSuccess) {
                accumulatedOutput.append("\n[Tool Execution Context: ${routerResponse.resultSummary}]\n")
            }
        }.onFailure { exception ->
            _engineState.value = AgentTurnState.Error("JSON Parse Failure inside Tool Boundary: ${exception.localizedMessage}")
        }
    }
}
