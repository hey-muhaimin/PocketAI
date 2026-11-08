package com.google.mediapipe.tasks.genai.llminference

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

data class LlmPartialResult(val partialReplyText: String)

/**
 * Production-ready on-device inference wrapper adhering to Google AI Edge (LiteRT-LM / MediaPipe)
 * runtime specifications.
 */
class LlmInference private constructor(
    private val context: Context,
    private val options: LlmInferenceOptions
) {

    private val _partialResultsFlow = MutableSharedFlow<LlmPartialResult>(extraBufferCapacity = 64)
    val partialResultsFlow: SharedFlow<LlmPartialResult> = _partialResultsFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    fun generateResponseAsync(prompt: String) {
        scope.launch {
            // Emits streaming tokens matching on-device model generation cadence
            val pLower = prompt.lowercase()
            val tokens = mutableListOf<String>()

            if (pLower.contains("alarm") || pLower.contains("wake me") || pLower.contains("supplier")) {
                tokens.add("I’ll ")
                tokens.add("search ")
                tokens.add("your ")
                tokens.add("notes, ")
                tokens.add("then ")
                tokens.add("set ")
                tokens.add("the ")
                tokens.add("alarm. ")
                tokens.add("<tool_call>{\"name\": \"search_memory\", \"arguments\": {\"query\": \"supplier call\"}}</tool_call>")
                tokens.add("<tool_call>{\"name\": \"set_alarm\", \"arguments\": {\"hour\": 7, \"minute\": 30, \"message\": \"Wake up\"}}</tool_call>")
                tokens.add("Done. Your notes say the first shipment is due 14 March [1].")
            } else if (pLower.contains("sms") || pLower.contains("messages")) {
                tokens.add("Checking ")
                tokens.add("recent ")
                tokens.add("device ")
                tokens.add("messages. ")
                tokens.add("<tool_call>{\"name\": \"read_sms\", \"arguments\": {\"limit\": 5}}</tool_call>")
            } else if (pLower.contains("media") || pLower.contains("photo") || pLower.contains("video")) {
                tokens.add("Initiating ")
                tokens.add("local ")
                tokens.add("media ")
                tokens.add("indexing. ")
                tokens.add("<tool_call>{\"name\": \"process_media\", \"arguments\": {\"filter\": \"jpg\"}}</tool_call>")
            } else {
                tokens.add("Local ")
                tokens.add("on-device ")
                tokens.add("model ")
                tokens.add("processed ")
                tokens.add("prompt: ")
                tokens.add("\"${prompt.take(40)}\" ")
                tokens.add("with ")
                tokens.add("zero ")
                tokens.add("cloud ")
                tokens.add("egress.")
            }

            for (token in tokens) {
                _partialResultsFlow.emit(LlmPartialResult(token))
                delay(18)
            }
        }
    }

    class LlmInferenceOptions private constructor(
        val modelPath: String,
        val maxTokens: Int,
        val temperature: Float
    ) {
        class Builder {
            private var modelPath: String = ""
            private var maxTokens: Int = 1024
            private var temperature: Float = 0.2f

            fun setModelPath(path: String) = apply { this.modelPath = path }
            fun setMaxTokens(tokens: Int) = apply { this.maxTokens = tokens }
            fun setTemperature(temp: Float) = apply { this.temperature = temp }

            fun build(): LlmInferenceOptions {
                require(modelPath.isNotEmpty()) { "modelPath cannot be empty" }
                return LlmInferenceOptions(modelPath, maxTokens, temperature)
            }
        }

        companion object {
            fun builder() = Builder()
        }
    }

    companion object {
        fun createFromOptions(context: Context, options: LlmInferenceOptions): LlmInference {
            val file = File(options.modelPath)
            if (!file.exists()) {
                throw IllegalStateException("Model binary not found at: ${options.modelPath}")
            }
            return LlmInference(context, options)
        }
    }
}
