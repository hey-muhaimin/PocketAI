package com.example.pocketai.memory

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pocketai.core.context.SearchResultItem
import com.example.pocketai.core.llm.LlmConfig
import com.example.ui.theme.IbmPlexMono
import com.example.ui.theme.PocketAccent
import com.example.ui.theme.PocketBg
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketDanger
import com.example.ui.theme.PocketMuted
import com.example.ui.theme.PocketSuccess
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketText
import com.example.ui.theme.PocketWarning

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    llmConfig: LlmConfig,
    onNavigateBackToHub: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler {
        if (state.isViewingAnswer) {
            viewModel.exitAnswerView()
        } else {
            onNavigateBackToHub()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PocketBg)
            .testTag("memory_screen")
    ) {
        // Main view content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.isViewingAnswer) {
                MemoryAnswerView(
                    state = state,
                    viewModel = viewModel,
                    llmConfig = llmConfig,
                    onBack = { viewModel.exitAnswerView() },
                    onOpenModelSettings = onOpenModelSettings
                )
            } else {
                when (state.currentTab) {
                    MemoryTab.SEARCH -> {
                        MemorySearchView(
                            state = state,
                            viewModel = viewModel,
                            llmConfig = llmConfig,
                            onBackToHub = onNavigateBackToHub,
                            onOpenModelSettings = onOpenModelSettings
                        )
                    }

                    MemoryTab.SOURCES -> {
                        MemorySourcesView(
                            state = state,
                            viewModel = viewModel,
                            onBackToHub = onNavigateBackToHub
                        )
                    }
                }
            }
        }

        // Fixed Status Bar
        MemoryStatusBar(
            statusText = when (state.currentTab) {
                MemoryTab.SEARCH -> if (state.isViewingAnswer) "local   qwen2.5-3b   0 B sent" else "local   qwen2.5-3b   index ready"
                MemoryTab.SOURCES -> "local   hashing embedder   14 MB"
            }
        )

        // Bottom Navigation: Search | Sources
        MemoryBottomTabs(
            selectedTab = state.currentTab,
            onSelectTab = {
                viewModel.exitAnswerView()
                viewModel.selectTab(it)
            }
        )
    }
}

// ============================================================================
// 1. SEARCH VIEW (Clones 'Memory · Search.png')
// ============================================================================
@Composable
private fun MemorySearchView(
    state: MemoryUiState,
    viewModel: MemoryViewModel,
    llmConfig: LlmConfig,
    onBackToHub: () -> Unit,
    onOpenModelSettings: () -> Unit
) {
    var queryInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top breadcrumb & "model" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹ suite",
                        color = PocketAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier
                            .clickable { onBackToHub() }
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "pocketloop  /  memory",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Search",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            Text(
                text = "model",
                color = PocketAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IbmPlexMono,
                modifier = Modifier
                    .clickable { onOpenModelSettings() }
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Input Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(PocketSurface, RoundedCornerShape(6.dp))
                .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = queryInput,
                onValueChange = {
                    queryInput = it
                    viewModel.onSearchQueryChanged(it)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = PocketText,
                    fontSize = 13.sp,
                    fontFamily = IbmPlexMono
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_search_input"),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "›  ",
                            color = PocketMuted,
                            fontSize = 13.sp,
                            fontFamily = IbmPlexMono
                        )
                        if (queryInput.isEmpty()) {
                            Text(
                                text = "ask or search your files",
                                color = PocketMuted,
                                fontSize = 13.sp,
                                fontFamily = IbmPlexMono
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Tags: [All] [Notes] [Contracts] [Reports]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Notes", "Contracts", "Reports").forEach { filter ->
                val isSelected = state.searchFilter == filter
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) PocketText else PocketSurface,
                            RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.selectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) PocketBg else PocketText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Three Stat Cards: 128 files | 2,340 passages | 14 MB index
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatBox(
                value = "${state.totalDocuments}",
                label = "files",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                value = "2,340",
                label = "passages",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                value = state.indexSizeBytes,
                label = "index",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "RECENT",
            color = PocketMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = IbmPlexMono,
            letterSpacing = 0.08.em
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Recent items list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.recentSearches) { item ->
                RecentSearchCard(item = item, onClick = { viewModel.openRecentSearch(item) })
            }

            // Cloud model off / private telemetry tile
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Cloud model off",
                            color = PocketText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "0 B sent this week",
                                color = PocketMuted,
                                fontSize = 12.sp,
                                fontFamily = IbmPlexMono
                            )
                            Box(
                                modifier = Modifier
                                    .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRIVATE",
                                    color = PocketSuccess,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        Column {
            Text(
                text = value,
                color = PocketText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IbmPlexMono
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = PocketMuted,
                fontSize = 11.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

@Composable
private fun RecentSearchCard(item: RecentSearchItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.query,
                color = PocketText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IbmPlexMono
            )
            Text(
                text = "${item.engine}  ·  ${item.sourceCount} sources  ·  ${item.timeLabel}",
                color = PocketMuted,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

// ============================================================================
// 2. ANSWER VIEW (Clones 'Memory · Answer.png')
// ============================================================================
@Composable
private fun MemoryAnswerView(
    state: MemoryUiState,
    viewModel: MemoryViewModel,
    llmConfig: LlmConfig,
    onBack: () -> Unit,
    onOpenModelSettings: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹ back",
                        color = PocketAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = "pocketloop  /  memory",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Answer",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            Text(
                text = "model",
                color = PocketAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IbmPlexMono,
                modifier = Modifier
                    .clickable { onOpenModelSettings() }
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // User question
            item {
                Text(
                    text = "›  ${state.activeQuestion}",
                    color = PocketText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = IbmPlexMono
                )
            }

            // Step 1 Tray: ▸ retrieve
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "▸  retrieve",
                                color = PocketText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMono
                            )
                            Box(
                                modifier = Modifier
                                    .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ok 42 ms",
                                    color = PocketSuccess,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(PocketBorder)
                        )

                        Text(
                            text = "hybrid  ·  fts4 + vectors  ·  rrf",
                            color = PocketMuted,
                            fontSize = 12.sp,
                            fontFamily = IbmPlexMono
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(PocketBorder)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "5 passages from 3 files",
                                color = PocketText,
                                fontSize = 12.sp,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }
                }
            }

            // Step 2 Tray: ▸ generate
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "▸  generate",
                                color = PocketText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMono
                            )
                            Box(
                                modifier = Modifier
                                    .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ok 2.1 s",
                                    color = PocketSuccess,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(PocketBorder)
                        )

                        Text(
                            text = "local  ·  qwen2.5-3b  ·  312 tok",
                            color = PocketMuted,
                            fontSize = 12.sp,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }

            // Answer Text
            item {
                Text(
                    text = state.activeAnswerText,
                    color = PocketText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = IbmPlexMono
                )
            }

            // SOURCES header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SOURCES",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )
            }

            // Source cards
            items(state.activeSources) { source ->
                SourceCitationCard(source = source)
            }

            // Action buttons: [Copy] [Ask cloud (off)]
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(PocketSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(state.activeAnswerText))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Copy",
                            color = PocketText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(PocketSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                // Inform user that cloud is strictly off
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ask cloud (off)",
                            color = PocketMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCitationCard(source: SearchResultItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(PocketAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = source.citationTag.replace("[", "").replace("]", ""),
                            color = PocketAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = source.documentTitle,
                        color = PocketText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Documents/Contracts  ·  ${String.format("%.2f", source.rrfScore)}",
                    color = PocketMuted,
                    fontSize = 12.sp,
                    fontFamily = IbmPlexMono
                )
            }

            Text(
                text = "open",
                color = PocketAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IbmPlexMono,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ============================================================================
// 3. SOURCES VIEW (Clones 'Memory · Sources.png')
// ============================================================================
@Composable
private fun MemorySourcesView(
    state: MemoryUiState,
    viewModel: MemoryViewModel,
    onBackToHub: () -> Unit
) {
    // SAF Folder Picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with "+ add folder"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹ suite",
                        color = PocketAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier
                            .clickable { onBackToHub() }
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "pocketloop  /  memory",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sources",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            Text(
                text = "+ add folder",
                color = PocketAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IbmPlexMono,
                modifier = Modifier
                    .clickable { folderPickerLauncher.launch(null) }
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Folders list
            items(state.folders) { folder ->
                FolderCard(folder = folder)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "EXTRACTORS",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )
            }

            // Extractors card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Plain text, markdown, csv, json",
                                color = PocketText,
                                fontSize = 13.sp,
                                fontFamily = IbmPlexMono
                            )
                            Box(
                                modifier = Modifier
                                    .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ON",
                                    color = PocketSuccess,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PDF",
                                color = PocketMuted,
                                fontSize = 13.sp,
                                fontFamily = IbmPlexMono
                            )
                            Box(
                                modifier = Modifier
                                    .background(PocketBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NEXT",
                                    color = PocketMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Images (OCR)",
                                color = PocketMuted,
                                fontSize = 13.sp,
                                fontFamily = IbmPlexMono
                            )
                            Box(
                                modifier = Modifier
                                    .background(PocketBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NEXT",
                                    color = PocketMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }
                        }
                    }
                }
            }

            // Setting: Index only when idle
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Index only when idle",
                                color = PocketText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMono
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "battery not low  ·  storage not low",
                                color = PocketMuted,
                                fontSize = 12.sp,
                                fontFamily = IbmPlexMono
                            )
                        }

                        Switch(
                            checked = state.indexOnlyWhenIdle,
                            onCheckedChange = { viewModel.setIndexOnlyWhenIdle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PocketText,
                                checkedTrackColor = PocketAccent,
                                uncheckedThumbColor = PocketMuted,
                                uncheckedTrackColor = PocketBg
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(folder: IndexedFolderItem) {
    val (statusBg, statusFg) = when (folder.status) {
        "DONE" -> Pair(PocketSuccess.copy(alpha = 0.15f), PocketSuccess)
        "RUNNING" -> Pair(PocketAccent.copy(alpha = 0.2f), PocketAccent)
        else -> Pair(PocketBg, PocketMuted)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder.name,
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )

                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(4.dp))
                        .border(1.dp, if (folder.status == "QUEUED") PocketBorder else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = folder.status,
                        color = statusFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }
            }

            Text(
                text = if (folder.fileCount > 0) "${folder.fileCount} files  ·  ${folder.timeLabel}" else folder.timeLabel,
                color = PocketMuted,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )

            if (folder.status == "RUNNING") {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { folder.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = PocketAccent,
                    trackColor = PocketBg
                )
            }
        }
    }
}

// ============================================================================
// 4. MEMORY BOTTOM STATUS BAR & TABS
// ============================================================================
@Composable
fun MemoryStatusBar(statusText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(PocketBg)
            .border(1.dp, PocketBorder)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(PocketSuccess, CircleShape)
            )
            Text(
                text = statusText,
                color = PocketMuted,
                fontSize = 11.sp,
                fontFamily = IbmPlexMono
            )
        }
    }
}

@Composable
fun MemoryBottomTabs(
    selectedTab: MemoryTab,
    onSelectTab: (MemoryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PocketBg)
            .border(1.dp, PocketBorder)
    ) {
        MemoryTab.values().forEach { tab ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (isSelected) PocketText else PocketMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = IbmPlexMono
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(2.dp)
                                .background(PocketAccent)
                        )
                    }
                }
            }
        }
    }
}
