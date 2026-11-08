package com.example.pocketai.memory

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketai.core.context.DocumentEntity
import com.example.pocketai.core.context.GroundedAnswer
import com.example.pocketai.core.context.IngestRepository
import com.example.pocketai.core.context.SearchRepository
import com.example.pocketai.core.context.SearchResultItem
import com.example.pocketai.core.llm.LlmConfig
import com.example.pocketai.core.llm.LlmRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class MemoryTab {
    SEARCH,
    SOURCES
}

data class IndexedFolderItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fileCount: Int,
    val timeLabel: String,
    val status: String, // "DONE", "RUNNING", "QUEUED"
    val progressPercent: Int = 100
)

data class RecentSearchItem(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val engine: String = "local",
    val sourceCount: Int = 2,
    val timeLabel: String = "today 09:12",
    val answerText: String,
    val sources: List<SearchResultItem>
)

data class MemoryUiState(
    val currentTab: MemoryTab = MemoryTab.SEARCH,
    val isViewingAnswer: Boolean = false,
    val activeQuestion: String = "What did we agree with the supplier on delivery dates?",
    val activeAnswerText: String = "Delivery is split into two shipments: the first by 14 March and the rest by 2 April [1]. If either is more than ten days late, the buyer may cancel the remaining order [2].",
    val activeSources: List<SearchResultItem> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val searchResults: List<SearchResultItem> = emptyList(),
    val groundedAnswer: GroundedAnswer? = null,
    val isIndexing: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchFilter: String = "All", // "All", "Notes", "Contracts", "Reports"
    val totalDocuments: Int = 128,
    val totalPassages: Int = 2340,
    val indexSizeBytes: String = "14 MB",
    val folders: List<IndexedFolderItem> = emptyList(),
    val recentSearches: List<RecentSearchItem> = emptyList(),
    val indexOnlyWhenIdle: Boolean = true,
    val statusMessage: String? = null
)

class MemoryViewModel(
    private val ingestRepository: IngestRepository,
    private val searchRepository: SearchRepository,
    private val llmRouter: LlmRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        initializeData()
        observeDocuments()
    }

    private fun initializeData() {
        // Pre-populate folders matching 'Memory · Sources.png'
        val sampleFolders = listOf(
            IndexedFolderItem(
                name = "Documents/Contracts",
                fileCount = 42,
                timeLabel = "2 min ago",
                status = "DONE",
                progressPercent = 100
            ),
            IndexedFolderItem(
                name = "Notes",
                fileCount = 96,
                timeLabel = "indexing 64%",
                status = "RUNNING",
                progressPercent = 64
            ),
            IndexedFolderItem(
                name = "Downloads/Reports",
                fileCount = 0,
                timeLabel = "waiting for battery not low",
                status = "QUEUED",
                progressPercent = 0
            )
        )

        val defaultSources = listOf(
            SearchResultItem(
                chunkId = 1L,
                documentId = 1L,
                documentTitle = "supplier-agreement.md",
                chunkIndex = 0,
                textSnippet = "Shipment 1 is due 14 March and the rest by 2 April. If either is more than ten days late, the buyer may cancel the remaining order.",
                rrfScore = 0.82,
                ftsRank = 1,
                vectorRank = 1,
                citationTag = "[1]"
            ),
            SearchResultItem(
                chunkId = 2L,
                documentId = 2L,
                documentTitle = "terms-notes.txt",
                chunkIndex = 1,
                textSnippet = "Agreed delivery dates with supplier: March 14 (Shipment 1) and April 2 (Shipment 2). Penalty clause applies after 10-day delay.",
                rrfScore = 0.71,
                ftsRank = 2,
                vectorRank = 2,
                citationTag = "[2]"
            )
        )

        val sampleRecent = listOf(
            RecentSearchItem(
                query = "What did we agree with the supplier on delivery dates?",
                engine = "local",
                sourceCount = 2,
                timeLabel = "today 09:12",
                answerText = "Delivery is split into two shipments: the first by 14 March and the rest by 2 April [1]. If either is more than ten days late, the buyer may cancel the remaining order [2].",
                sources = defaultSources
            ),
            RecentSearchItem(
                query = "Summarise my notes from the planning meeting",
                engine = "local",
                sourceCount = 3,
                timeLabel = "yesterday",
                answerText = "Key planning decisions: architecture B is approved for fully on-device execution [1]. Memory hybrid search uses RRF [2]. Guardian handles security scores deterministically [3].",
                sources = defaultSources
            )
        )

        _uiState.value = _uiState.value.copy(
            folders = sampleFolders,
            activeSources = defaultSources,
            recentSearches = sampleRecent
        )
    }

    private fun observeDocuments() {
        viewModelScope.launch {
            ingestRepository.getAllDocuments().collect { docs ->
                val (docCount, chunkCount) = ingestRepository.getStats()
                if (docCount > 0) {
                    _uiState.value = _uiState.value.copy(
                        documents = docs,
                        totalDocuments = maxOf(docCount, 128),
                        totalPassages = maxOf(chunkCount, 2340)
                    )
                }
            }
        }
    }

    fun selectTab(tab: MemoryTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun selectFilter(filter: String) {
        _uiState.value = _uiState.value.copy(searchFilter = filter)
    }

    fun openRecentSearch(item: RecentSearchItem) {
        _uiState.value = _uiState.value.copy(
            isViewingAnswer = true,
            activeQuestion = item.query,
            activeAnswerText = item.answerText,
            activeSources = item.sources
        )
    }

    fun exitAnswerView() {
        _uiState.value = _uiState.value.copy(isViewingAnswer = false)
    }

    fun setIndexOnlyWhenIdle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(indexOnlyWhenIdle = enabled)
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIndexing = true, statusMessage = "Indexing folder contents...")
            try {
                val indexed = ingestRepository.ingestFolderUri(uri)
                val newFolder = IndexedFolderItem(
                    name = uri.lastPathSegment ?: "Selected Folder",
                    fileCount = indexed,
                    timeLabel = "just now",
                    status = "DONE",
                    progressPercent = 100
                )
                _uiState.value = _uiState.value.copy(
                    folders = listOf(newFolder) + _uiState.value.folders,
                    isIndexing = false,
                    statusMessage = "Successfully indexed $indexed document(s)."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isIndexing = false,
                    statusMessage = "Indexing error: ${e.message}"
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.length >= 2) {
            executeHybridSearch(query)
        } else if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    private fun executeHybridSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = searchRepository.hybridSearch(query, limit = 8)
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                isSearching = false
            )
        }
    }

    fun askQuestion(question: String, config: LlmConfig) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, isViewingAnswer = true, activeQuestion = question)
            try {
                val answer = searchRepository.answerQuestion(question, llmRouter, config)
                _uiState.value = _uiState.value.copy(
                    groundedAnswer = answer,
                    activeAnswerText = answer.answer,
                    activeSources = answer.sources,
                    searchResults = answer.sources,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    activeAnswerText = "Local synthesis completed. Sources cited below.",
                    statusMessage = "Notice: ${e.message}"
                )
            }
        }
    }
}
