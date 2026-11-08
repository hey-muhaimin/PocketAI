package com.example.pocketai.engine

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Telephony
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis

class LocalMcpRouter(private val appContext: Context) {

    private val _telemetryState = MutableStateFlow(TelemetryLog(durationMs = 0L, tokensProcessed = 0))
    val telemetryState: StateFlow<TelemetryLog> = _telemetryState.asStateFlow()

    /**
     * Routes expanded structural tool queries natively directly on background IO workers.
     * Evaluates speed in milliseconds to match the design's tracking metrics (e.g., 'ok 42 ms').
     */
    suspend fun routeToolCall(toolCall: ToolCallSchema): AgentTurnState = withContext(Dispatchers.IO) {
        var summaryResult = ""
        var wasExecutionSuccessful = false
        
        val runtimeDuration = measureTimeMillis {
            runCatching {
                when (toolCall.name) {
                    "set_alarm" -> {
                        val hour = toolCall.arguments["hour"]?.toIntOrNull() ?: throw IllegalArgumentException("Missing parameter: hour")
                        val minute = toolCall.arguments["minute"]?.toIntOrNull() ?: 0
                        val message = toolCall.arguments["message"] ?: "Wake up"
                        
                        executeNativeAndroidAlarm(hour, minute, message)
                        summaryResult = "Alarm programmed successfully for %02d:%02d".format(hour, minute)
                        wasExecutionSuccessful = true
                    }
                    "search_memory" -> {
                        val queryText = toolCall.arguments["query"] ?: throw IllegalArgumentException("Missing parameter: query")
                        summaryResult = performLocalWorkspaceFileSearch(queryText)
                        wasExecutionSuccessful = true
                    }
                    "read_sms" -> {
                        val limit = toolCall.arguments["limit"]?.toIntOrNull() ?: 5
                        summaryResult = queryNativeDeviceSmsLogs(limit)
                        wasExecutionSuccessful = true
                    }
                    "process_media" -> {
                        val extensionFilter = toolCall.arguments["filter"] ?: "jpg"
                        summaryResult = indexLocalMediaSandboxFiles(extensionFilter)
                        wasExecutionSuccessful = true
                    }
                    "check_accessibility" -> {
                        summaryResult = inspectRunningAccessibilityServices()
                        wasExecutionSuccessful = true
                    }
                    else -> {
                        summaryResult = "Error: Unsupported tool command target profile: '${toolCall.name}'"
                        wasExecutionSuccessful = false
                    }
                }
            }.onFailure { err ->
                summaryResult = "Execution Exception: ${err.localizedMessage}"
                wasExecutionSuccessful = false
            }
        }

        _telemetryState.value = TelemetryLog(
            durationMs = runtimeDuration,
            tokensProcessed = 0,
            modelTag = "qwen2.5-3b",
            indexStatus = "index ready"
        )

        return@withContext AgentTurnState.ToolResponse(
            toolName = toolCall.name,
            resultSummary = summaryResult,
            durationMs = runtimeDuration,
            isSuccess = wasExecutionSuccessful
        )
    }

    private fun executeNativeAndroidAlarm(hour: Int, minute: Int, message: String) {
        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (alarmIntent.resolveActivity(appContext.packageManager) != null) {
            appContext.startActivity(alarmIntent)
        }
    }

    private fun performLocalWorkspaceFileSearch(query: String): String {
        val rootDir = appContext.filesDir
        var matchCounter = 0
        rootDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "md" || file.extension == "txt")) {
                if (file.readText().contains(query, ignoreCase = true)) matchCounter++
            }
        }
        return "Found $matchCounter matches across custom local text logs for query: \"$query\""
    }

    /**
     * Reads recent incoming system messages directly from the device's content provider databases.
     * Maps visually to the READ_SMS context indicators highlighted in the Guardian layout.
     */
    private fun queryNativeDeviceSmsLogs(limit: Int): String {
        val smsUri: Uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val sortedOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"
        
        val smsSummaryBuilder = StringBuilder()
        var extractedCount = 0

        appContext.contentResolver.query(smsUri, projection, null, null, sortedOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                
                do {
                    val sender = cursor.getString(addressIdx)
                    val messageBody = cursor.getString(bodyIdx).take(35) // Truncate content logs safely
                    extractedCount++
                    smsSummaryBuilder.append("[$sender: $messageBody...] ")
                } while (cursor.moveToNext())
            }
        }

        return if (extractedCount > 0) {
            "Successfully extracted $extractedCount secure message items: ${smsSummaryBuilder.toString().trim()}"
        } else {
            "Zero text messages accessible or READ_SMS runtime permission denied by platform sandbox."
        }
    }

    /**
     * Indexes local storage blocks to gather media metadata metrics.
     * Feeds the internal agent pipeline for automated photo and video compression audits.
     */
    private fun indexLocalMediaSandboxFiles(filterExtension: String): String {
        val targetDirectory = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        var scannedFilesCount = 0
        var totalAccumulatedBytes = 0L

        targetDirectory.walkTopDown().forEach { currentFile ->
            if (currentFile.isFile && currentFile.extension.equals(filterExtension, ignoreCase = true)) {
                scannedFilesCount++
                totalAccumulatedBytes += currentFile.length()
            }
        }

        val sizeInMegaBytes = totalAccumulatedBytes / (1024 * 1024)
        return "Indexed $scannedFilesCount media resources matching [.$filterExtension]. Spatial volume footprint: $sizeInMegaBytes MB."
    }

    /**
     * Programmatically scans for active third-party accessibility tools running on the phone.
     * Replicates the exact system telemetry shown in your 'Quick Torch' security risk metrics screen (+30 risk points).
     */
    private fun inspectRunningAccessibilityServices(): String {
        val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val runningServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        if (runningServices.isNullOrEmpty()) {
            return "Security Assessment: Clean. Zero custom running accessibility hooks intercepted."
        }

        val servicesSummary = StringBuilder()
        for (service in runningServices) {
            val componentInfo = service.resolveInfo?.serviceInfo
            if (componentInfo != null) {
                servicesSummary.append("[${componentInfo.packageName}] ")
            }
        }

        return "Flagged potential structural risk: ${runningServices.size} active accessibility monitors active: ${servicesSummary.toString().trim()}"
    }
}
