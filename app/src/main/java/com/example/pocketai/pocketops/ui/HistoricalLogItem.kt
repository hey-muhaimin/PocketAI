package com.example.pocketai.pocketops.ui

import com.example.pocketai.engine.AgentTurnState

data class HistoricalLogItem(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val finalState: AgentTurnState,
    val timestamp: Long = System.currentTimeMillis()
)
