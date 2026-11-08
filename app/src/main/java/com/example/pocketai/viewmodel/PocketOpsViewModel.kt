package com.example.pocketai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketai.engine.AgentTurnState
import com.example.pocketai.engine.EdgeTokenParser
import com.example.pocketai.engine.LocalMcpRouter
import com.example.pocketai.engine.TelemetryLog
import com.example.pocketai.pocketops.ui.HistoricalLogItem
import com.example.pocketai.storage.ModelAssetManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PocketOpsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val modelAssetManager = ModelAssetManager(context)
    private val mcpRouter = LocalMcpRouter(context)
    private val tokenParser = EdgeTokenParser(mcpRouter, viewModelScope)

    val engineState: StateFlow<AgentTurnState> = tokenParser.engineState
    val telemetryState: StateFlow<TelemetryLog> = mcpRouter.telemetryState

    private val _historicalLogs = MutableStateFlow<List<HistoricalLogItem>>(emptyList())
    val historicalLogs: StateFlow<List<HistoricalLogItem>> = _historicalLogs.asStateFlow()

    private var llmInference: LlmInference? = null
    val isModelInitialized = MutableStateFlow(false)

    init {
        initializeLocalEdgeModel()
    }

    /**
     * Production Setup: Instantiates the hardware-accelerated local model file 
     * stored securely inside the app's sandboxed data partition directory.
     * Uses ModelAssetManager to extract from assets or validates direct placement.
     */
    private fun initializeLocalEdgeModel() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val targetModelName = "qwen2.5-3b-quant.litertlm"

                // Check if model file exists on storage or in packaged assets
                if (modelAssetManager.isModelAvailable(targetModelName)) {
                    val extractedModelFile = modelAssetManager.secureLocalModelInstance(targetModelName)

                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(extractedModelFile.absolutePath)
                        .setMaxTokens(1024)
                        .setTemperature(0.2f) // Low temperature for crisp, accurate tool syntax generation
                        .build()

                    llmInference = LlmInference.createFromOptions(context, options)
                    isModelInitialized.value = true
                } else {
                    // Fallback mode: Mark engine as ready for simulated streaming when file is not yet present
                    isModelInitialized.value = false
                }
            }.onFailure {
                isModelInitialized.value = false
            }
        }
    }

    /**
     * Processes live local inference token updates 
     * and passes the character stream straight to the engine state machine.
     * Auto-detects model file and seamlessly falls back to simulated character streaming if missing.
     */
    fun handleUserInstruction(prompt: String) {
        val userLogId = UUID.randomUUID().toString()
        val currentHistory = _historicalLogs.value.toMutableList()
        currentHistory.add(HistoricalLogItem(userLogId, isUser = true, prompt, AgentTurnState.Completed(prompt)))
        _historicalLogs.value = currentHistory

        viewModelScope.launch {
            val inferenceInstance = llmInference
            val liveTokenFlow = if (inferenceInstance != null && isModelInitialized.value) {
                // Build the live token flow directly from the model runtime callback hooks
                flow {
                    inferenceInstance.generateResponseAsync(prompt)
                    inferenceInstance.partialResultsFlow.collect { result ->
                        emit(result.partialReplyText)
                    }
                }.flowOn(Dispatchers.Default)
            } else {
                // Fallback stream for testing & pre-sideload verification
                flow {
                    val sampleResponse = if (prompt.contains("alarm", ignoreCase = true) || prompt.contains("wake", ignoreCase = true)) {
                        "I’ll search your notes, then set the alarm. <tool_call>{\"name\": \"search_memory\", \"arguments\": {\"query\": \"supplier call\"}}</tool_call><tool_call>{\"name\": \"set_alarm\", \"arguments\": {\"hour\": 7, \"minute\": 30, \"message\": \"Wake up\"}}</tool_call> Done. Your notes say the first shipment is due 14 March [1]."
                    } else if (prompt.contains("sms", ignoreCase = true)) {
                        "Checking recent device messages. <tool_call>{\"name\": \"read_sms\", \"arguments\": {\"limit\": 5}}</tool_call>"
                    } else {
                        "Local on-device agent evaluated \"$prompt\" with zero cloud egress."
                    }

                    for (char in sampleResponse) {
                        emit(char.toString())
                        delay(12)
                    }
                }.flowOn(Dispatchers.Default)
            }

            tokenParser.processTokenStream(liveTokenFlow)
        }
    }

    fun appendCompletedTurnToHistory(summary: String, finalState: AgentTurnState) {
        val currentHistory = _historicalLogs.value.toMutableList()
        currentHistory.add(HistoricalLogItem(UUID.randomUUID().toString(), isUser = false, summary, finalState))
        _historicalLogs.value = currentHistory
    }
}
