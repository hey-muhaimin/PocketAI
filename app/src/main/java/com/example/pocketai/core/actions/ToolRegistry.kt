package com.example.pocketai.core.actions

import com.example.pocketai.core.llm.LlmToolDefinition

/**
 * Registry containing all available Tools, supporting constructor injection and Hilt set multibinding.
 */
class ToolRegistry(
    tools: Set<Tool> = emptySet()
) {
    private val toolMap = mutableMapOf<String, Tool>()

    init {
        tools.forEach { register(it) }
    }

    fun register(tool: Tool) {
        toolMap[tool.name.lowercase()] = tool
    }

    fun getTool(name: String): Tool? = toolMap[name.lowercase()]

    fun getAllTools(): List<Tool> = toolMap.values.toList()

    fun getLlmToolDefinitions(): List<LlmToolDefinition> {
        return toolMap.values.map { tool ->
            LlmToolDefinition(
                name = tool.name,
                description = tool.description,
                parametersJsonSchema = tool.parametersJsonSchema
            )
        }
    }
}
