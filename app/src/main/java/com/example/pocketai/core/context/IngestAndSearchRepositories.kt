package com.example.pocketai.core.context

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.llm.LlmRequest
import com.example.pocketai.core.llm.LlmRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class SearchResultItem(
    val chunkId: Long,
    val documentId: Long,
    val documentTitle: String,
    val chunkIndex: Int,
    val textSnippet: String,
    val rrfScore: Double,
    val ftsRank: Int?,
    val vectorRank: Int?,
    val citationTag: String
)

data class GroundedAnswer(
    val question: String,
    val answer: String,
    val sources: List<SearchResultItem>,
    val engineUsed: String,
    val isLocal: Boolean,
    val latencyMs: Long
)

/**
 * Ingest Repository orchestrating document reading, chunking, embedding, and storage.
 */
class IngestRepository(
    private val context: Context,
    private val memoryDao: MemoryDao,
    private val textChunker: TextChunker = TextChunker(windowSizeWords = 256, overlapWords = 64),
    private val embedder: FeatureHashEmbedder = FeatureHashEmbedder(dimension = 256)
) {
    fun getAllDocuments(): Flow<List<DocumentEntity>> = memoryDao.getAllDocuments()

    suspend fun getStats(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        Pair(memoryDao.getDocumentCount(), memoryDao.getChunkCount())
    }

    /**
     * Ingests a raw text source with title and URI.
     */
    suspend fun ingestText(title: String, uri: String, content: String): Long = withContext(Dispatchers.IO) {
        val chunks = textChunker.chunk(content)
        val chunkPairs = chunks.map { chunk ->
            val embedding = embedder.embed(chunk.text)
            val bytes = FeatureHashEmbedder.toByteArray(embedding)
            Pair(chunk.text, bytes)
        }

        val doc = DocumentEntity(
            title = title,
            uri = uri,
            sizeBytes = content.toByteArray().size.toLong(),
            chunkCount = chunks.size
        )

        memoryDao.insertDocumentWithChunks(doc, chunkPairs)
    }

    /**
     * Traverses a folder selected through the Storage Access Framework (SAF)
     * and indexes all readable text documents (.txt, .md, .json, .csv, code files).
     */
    suspend fun ingestFolderUri(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return@withContext 0
        } ?: return@withContext 0

        var indexedCount = 0
        val queue = ArrayDeque<String>()
        queue.add(rootDocId)
        val resolver = context.contentResolver

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (queue.isNotEmpty()) {
            val parentDocId = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

            try {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: "Untitled"
                        val mime = cursor.getString(mimeCol) ?: ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(docId)
                        } else if (isIndexable(name)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val content = readFileContent(docUri)
                            if (content.isNotBlank()) {
                                ingestText(
                                    title = name,
                                    uri = docUri.toString(),
                                    content = content
                                )
                                indexedCount++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore inaccessible directory branches
            }
        }

        indexedCount
    }

    private fun isIndexable(filename: String): Boolean {
        val lower = filename.lowercase()
        val indexableExts = listOf(".txt", ".md", ".json", ".csv", ".xml", ".kt", ".java", ".py", ".html", ".log", ".conf")
        return indexableExts.any { lower.endsWith(it) }
    }

    private fun readFileContent(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Seeds initial knowledge base documents if database is currently empty.
     */
    suspend fun seedSampleDocumentsIfEmpty() = withContext(Dispatchers.IO) {
        if (memoryDao.getDocumentCount() == 0) {
            ingestText(
                title = "PocketLoop Architecture Spec.md",
                uri = "asset://internal/spec.md",
                content = """
                    PocketLoop is an on-device private AI runtime built for Android devices.
                    Key Principles:
                    1. Zero Telemetry Leakage: Sensitive operations and document contexts are confined to local execution.
                    2. Hybrid Search: Uses SQLite FTS4 virtual tables for keyword exact matches and 256-dimensional feature hashing for semantic dense vector representations, merged using Reciprocal Rank Fusion (RRF k=60).
                    3. Guarded Actions: Every tool execution is evaluated by a deterministic risk classifier. High and medium-risk operations require explicit user approval.
                    4. Cryptographic Audit Log: Every action appends to a SHA-256 hash-chained ledger that can be verified at any moment to guarantee tamper resistance.
                """.trimIndent()
            )

            ingestText(
                title = "Android Security & Permissions Guide.txt",
                uri = "asset://internal/security.txt",
                content = """
                    Android Application Security Best Practices:
                    - Dangerous Permissions: Permissions like CAMERA, RECORD_AUDIO, and ACCESS_FINE_LOCATION must be requested at runtime and used only when foreground tasks require them.
                    - Accessibility Services: These services possess full visibility into screen content and can intercept user keystrokes. Guardian scores them with a high risk penalty of +25 points.
                    - Notification Listeners: Can read notification payloads, including two-factor authentication (2FA) SMS codes and private chat messages.
                    - SAF (Storage Access Framework): Provides scoped, user-controlled file and directory access without requiring broad external storage permissions.
                """.trimIndent()
            )
        }
    }

    suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteFtsByDocumentId(id)
        memoryDao.deleteChunksByDocumentId(id)
        memoryDao.deleteDocument(id)
    }
}

/**
 * Search Repository orchestrating FTS keyword search, vector similarity, and RRF fusion.
 */
class SearchRepository(
    private val memoryDao: MemoryDao,
    private val embedder: FeatureHashEmbedder = FeatureHashEmbedder(dimension = 256)
) {

    /**
     * Executes hybrid search:
     * 1. FTS4 inverted index keyword match
     * 2. Vector cosine similarity against all chunk embeddings
     * 3. RRF fusion to produce unified ranked results with citations
     */
    suspend fun hybridSearch(query: String, limit: Int = 5): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1. Keyword search with FTS4
        val sanitizedFts = FtsQuerySanitizer.sanitize(query)
        val ftsChunkIds = if (sanitizedFts.isNotBlank()) {
            try {
                memoryDao.searchFtsRowIds(sanitizedFts, limit = 50)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // 2. Vector search with Feature-Hashing cosine similarity
        val queryVector = embedder.embed(query)
        val allChunks = memoryDao.getAllChunks()
        val vectorRankedChunks = allChunks.map { chunk ->
            val chunkVector = FeatureHashEmbedder.fromByteArray(chunk.embeddingBlob)
            val sim = FeatureHashEmbedder.cosineSimilarity(queryVector, chunkVector)
            Pair(chunk.id, sim)
        }
            .filter { it.second > 0.05f }
            .sortedByDescending { it.second }
            .take(50)
            .map { it.first }

        // 3. Reciprocal Rank Fusion
        val fused = ReciprocalRankFusion.fuse(
            ftsRankings = ftsChunkIds,
            vectorRankings = vectorRankedChunks,
            k = ReciprocalRankFusion.DEFAULT_K
        )

        val topResults = fused.take(limit)
        if (topResults.isEmpty()) return@withContext emptyList()

        val topIds = topResults.map { it.item }
        val chunkEntities = memoryDao.getChunksByIds(topIds).associateBy { it.id }

        topResults.mapNotNull { result ->
            val chunk = chunkEntities[result.item] ?: return@mapNotNull null
            val doc = memoryDao.getDocumentById(chunk.documentId)
            val docTitle = doc?.title ?: "Document #${chunk.documentId}"

            SearchResultItem(
                chunkId = chunk.id,
                documentId = chunk.documentId,
                documentTitle = docTitle,
                chunkIndex = chunk.chunkIndex,
                textSnippet = chunk.text,
                rrfScore = result.score,
                ftsRank = result.ftsRank,
                vectorRank = result.vectorRank,
                citationTag = "[Source: $docTitle, Chunk #${chunk.chunkIndex + 1}]"
            )
        }
    }

    /**
     * Answers questions grounded strictly in retrieved Memory sources.
     */
    suspend fun answerQuestion(
        question: String,
        llmRouter: LlmRouter,
        config: LlmConfig
    ): GroundedAnswer = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val sources = hybridSearch(question, limit = 4)

        val contextPrompt = if (sources.isNotEmpty()) {
            buildString {
                appendLine("USER QUESTION: $question")
                appendLine("\nRETRIEVED DOCUMENT CONTEXTS:")
                sources.forEachIndexed { i, s ->
                    appendLine("--- Source [${i + 1}]: ${s.documentTitle} (Chunk #${s.chunkIndex + 1}) ---")
                    appendLine(s.textSnippet)
                }
                appendLine("\nINSTRUCTIONS: Answer the question accurately using ONLY the retrieved contexts. Explicitly cite your sources.")
            }
        } else {
            "USER QUESTION: $question\nNo matching documents found in Memory. Please inform the user to index folders first."
        }

        // Enforce isSensitive = true: Document content NEVER leaves the device
        val request = LlmRequest(
            prompt = contextPrompt,
            systemPrompt = "You are PocketLoop Memory Assistant. Answer questions based on provided documents with precise citations.",
            isSensitive = true
        )

        val response = llmRouter.route(request, config)
        val latency = System.currentTimeMillis() - startTime

        GroundedAnswer(
            question = question,
            answer = response.content,
            sources = sources,
            engineUsed = response.engineUsed,
            isLocal = response.isLocal,
            latencyMs = latency
        )
    }
}
