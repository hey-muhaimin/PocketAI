package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pocketai.core.actions.CreateCalendarEventTool
import com.example.pocketai.core.actions.GuardedExecutor
import com.example.pocketai.core.actions.OpenUrlTool
import com.example.pocketai.core.actions.SetAlarmTool
import com.example.pocketai.core.actions.SearchMemoryTool
import com.example.pocketai.core.actions.ToolRegistry
import com.example.pocketai.core.context.FeatureHashEmbedder
import com.example.pocketai.core.context.IngestRepository
import com.example.pocketai.core.context.MemoryDatabase
import com.example.pocketai.core.context.SearchRepository
import com.example.pocketai.core.context.TextChunker
import com.example.pocketai.core.llm.CloudLlmEngine
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.llm.LlmRouter
import com.example.pocketai.core.llm.LocalLlmEngine
import com.example.pocketai.core.trust.AuditChainRepository
import com.example.pocketai.guardian.AppAuditEngine
import com.example.pocketai.guardian.GuardianViewModel
import com.example.pocketai.hub.PocketAiHubScreen
import com.example.pocketai.memory.MemoryViewModel
import com.example.pocketai.pocketops.PocketOpsViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PocketBg

/**
 * PocketLoop Main Launcher Activity hosting the unified suite hub.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Core Data Persistence & Embedding
        val db = MemoryDatabase.getInstance(applicationContext)
        val memoryDao = db.memoryDao()
        val textChunker = TextChunker(windowSizeWords = 256, overlapWords = 64)
        val embedder = FeatureHashEmbedder(dimension = 256)

        // 2. Ingest and Search Repositories
        val ingestRepository = IngestRepository(
            context = applicationContext,
            memoryDao = memoryDao,
            textChunker = textChunker,
            embedder = embedder
        )
        val searchRepository = SearchRepository(
            memoryDao = memoryDao,
            embedder = embedder
        )

        // 3. LLM Routing & Privacy Interceptor
        val localEngine = LocalLlmEngine()
        val cloudEngine = CloudLlmEngine()
        val llmRouter = LlmRouter(localEngine = localEngine, cloudEngine = cloudEngine)

        // 4. Trust & Guarded Action Execution
        val auditChainRepository = AuditChainRepository()
        val toolRegistry = ToolRegistry()

        // Register built-in tools
        toolRegistry.register(SetAlarmTool(applicationContext))
        toolRegistry.register(OpenUrlTool(applicationContext))
        toolRegistry.register(CreateCalendarEventTool(applicationContext))
        toolRegistry.register(
            SearchMemoryTool { query, limit ->
                val results = searchRepository.hybridSearch(query, limit)
                if (results.isEmpty()) {
                    "No relevant passages found in local Memory for '$query'."
                } else {
                    results.joinToString("\n\n") { "${it.citationTag}:\n${it.textSnippet}" }
                }
            }
        )

        val guardedExecutor = GuardedExecutor(
            toolRegistry = toolRegistry,
            auditChainRepository = auditChainRepository
        )

        // 5. Guardian Engine
        val auditEngine = AppAuditEngine(
            context = applicationContext,
            llmRouter = llmRouter
        )

        // 6. ViewModels
        val memoryViewModel = MemoryViewModel(
            ingestRepository = ingestRepository,
            searchRepository = searchRepository,
            llmRouter = llmRouter
        )
        val guardianViewModel = GuardianViewModel(
            auditEngine = auditEngine
        )
        val pocketOpsViewModel = PocketOpsViewModel(
            llmRouter = llmRouter,
            toolRegistry = toolRegistry,
            guardedExecutor = guardedExecutor,
            auditChainRepository = auditChainRepository
        )

        val defaultLlmConfig = LlmConfig(
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "",
            modelName = "gpt-4o-mini",
            preferLocal = true
        )

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PocketBg
                ) {
                    PocketAiHubScreen(
                        memoryViewModel = memoryViewModel,
                        guardianViewModel = guardianViewModel,
                        pocketOpsViewModel = pocketOpsViewModel,
                        auditChainRepository = auditChainRepository,
                        initialLlmConfig = defaultLlmConfig
                    )
                }
            }
        }
    }
}
