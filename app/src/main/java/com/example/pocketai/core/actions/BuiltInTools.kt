package com.example.pocketai.core.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.example.pocketai.core.trust.RiskLevel
import org.json.JSONObject
import java.net.URI
import java.util.Calendar

/**
 * Built-in tool for setting system alarms.
 */
class SetAlarmTool(private val context: Context) : Tool {
    override val name: String = "set_alarm"
    override val description: String = "Sets a system alarm for the specified hour and minute."
    override val requiredScope: String = "alarm:set"
    override val defaultRisk: RiskLevel = RiskLevel.MEDIUM
    override val parametersJsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "hour": {"type": "integer", "minimum": 0, "maximum": 23, "description": "Hour in 24-hour format"},
            "minute": {"type": "integer", "minimum": 0, "maximum": 59, "description": "Minute (0-59)"},
            "message": {"type": "string", "description": "Optional label for the alarm"}
          },
          "required": ["hour", "minute"]
        }
    """.trimIndent()

    override suspend fun execute(parametersJson: String): ToolResult {
        return try {
            val json = JSONObject(parametersJson)
            val hour = json.getInt("hour")
            val minute = json.getInt("minute")
            val message = json.optString("message", "PocketLoop Alarm")

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolResult(
                success = true,
                output = "System alarm successfully dispatched for %02d:%02d with label '$message'.".format(hour, minute)
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                output = "",
                error = "Failed to set alarm: ${e.message}"
            )
        }
    }
}

/**
 * Built-in tool for opening strictly validated HTTPS web URLs.
 */
class OpenUrlTool(private val context: Context) : Tool {
    override val name: String = "open_url"
    override val description: String = "Opens an HTTPS URL in the default Android system browser."
    override val requiredScope: String = "url:open_https"
    override val defaultRisk: RiskLevel = RiskLevel.MEDIUM
    override val parametersJsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "url": {"type": "string", "description": "Strict HTTPS URL to navigate to"}
          },
          "required": ["url"]
        }
    """.trimIndent()

    override suspend fun execute(parametersJson: String): ToolResult {
        return try {
            val json = JSONObject(parametersJson)
            val rawUrl = json.getString("url").trim()

            val uri = URI.create(rawUrl)
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return ToolResult(
                    success = false,
                    output = "",
                    error = "Security Policy Violation: Only HTTPS URLs are permitted. Attempted: $rawUrl"
                )
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolResult(
                success = true,
                output = "Dispatched system browser to open $rawUrl"
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                output = "",
                error = "Failed to open URL: ${e.message}"
            )
        }
    }
}

/**
 * Built-in tool for creating Calendar events.
 */
class CreateCalendarEventTool(private val context: Context) : Tool {
    override val name: String = "create_calendar_event"
    override val description: String = "Schedules a calendar event with title, description, and duration."
    override val requiredScope: String = "calendar:create_event"
    override val defaultRisk: RiskLevel = RiskLevel.HIGH
    override val parametersJsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "title": {"type": "string", "description": "Title or summary of the event"},
            "description": {"type": "string", "description": "Optional notes or details"},
            "durationMinutes": {"type": "integer", "description": "Duration in minutes (default 60)"}
          },
          "required": ["title"]
        }
    """.trimIndent()

    override suspend fun execute(parametersJson: String): ToolResult {
        return try {
            val json = JSONObject(parametersJson)
            val title = json.getString("title")
            val description = json.optString("description", "Scheduled via PocketOps")
            val durationMinutes = json.optInt("durationMinutes", 60)

            val beginTime = Calendar.getInstance()
            // Schedule for next top of hour by default
            beginTime.add(Calendar.HOUR_OF_DAY, 1)
            beginTime.set(Calendar.MINUTE, 0)
            val endTime = beginTime.clone() as Calendar
            endTime.add(Calendar.MINUTE, durationMinutes)

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime.timeInMillis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ToolResult(
                success = true,
                output = "Calendar event intent launched for '$title' (${durationMinutes} mins)."
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                output = "",
                error = "Failed to launch calendar event: ${e.message}"
            )
        }
    }
}

/**
 * Built-in tool for querying the local Room Memory hybrid search index.
 */
class SearchMemoryTool(
    private val searchExecutor: suspend (query: String, limit: Int) -> String
) : Tool {
    override val name: String = "search_memory"
    override val description: String = "Performs hybrid FTS4 and vector search across user indexed local documents in Memory."
    override val requiredScope: String = "memory:read"
    override val defaultRisk: RiskLevel = RiskLevel.LOW
    override val parametersJsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Search keyword or concept"},
            "limit": {"type": "integer", "description": "Maximum source citations to return"}
          },
          "required": ["query"]
        }
    """.trimIndent()

    override suspend fun execute(parametersJson: String): ToolResult {
        return try {
            val json = JSONObject(parametersJson)
            val query = json.getString("query")
            val limit = json.optInt("limit", 3)

            val resultText = searchExecutor(query, limit)
            ToolResult(
                success = true,
                output = resultText
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                output = "",
                error = "Failed to search memory: ${e.message}"
            )
        }
    }
}
