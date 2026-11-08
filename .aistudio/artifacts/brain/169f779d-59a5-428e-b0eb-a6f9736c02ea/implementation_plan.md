# Production Google AI Edge (LiteRT-LM) & Room FTS4 Integration Plan

This plan outlines the replacement of development mock components with production-grade on-device systems: a hardware-accelerated local model asset loader, an indexed SQLite Room FTS4 document search engine, and a dual-mode streaming pipeline.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on your preferences:
> - **Model Detection & Fallback Mode**: The system will automatically check for the presence of the quantized model binary (`qwen2.5-3b-quant.litertlm`) in `context.filesDir` and `assets/`. If found, it runs hardware-accelerated LiteRT-LM inference; if absent (during prototyping or before binary sideloading), it seamlessly falls back to the on-device character token stream emulator so testing never crashes.
> - **Room FTS4 Document Indexing**: The Room FTS4 virtual table will be automatically seeded with default workplace documents (`supplier-agreement.md`, `terms-notes.txt`), providing sub-60ms full-text search matches out of the box while supporting continuous SAF folder ingestion.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades PocketAI / PocketOps from simulated text streaming and line-by-line file scans to a production-ready on-device AI runtime with hardware-accelerated inference readiness and indexed SQLite FTS4 virtual table search.
- **Target Persona**: Mobile users and security engineers requiring strict zero-cloud egress, deterministic tool execution, and instantaneous document retrieval on local hardware.
- **Key Value**: Replaces naive disk I/O with millisecond SQLite MATCH indexing and decouples multi-gigabyte model binary management from UI rendering.

---

## 2. User Experience & Visual Design

The UI preserves the exact **pocketai Design System** tokens:
- **Palette**: `#201d1d` background, `#302c2c` flat surfaces, `#464343` crisp 1dp borders, `#fdfcfc` active text, `#9a9898` muted labels, `#30d158` success badges.
- **Typography**: IBM Plex Mono across display titles (20sp bold), section headers (14sp semibold), body text (13sp), and metadata tags (11sp with letter spacing).
- **Execution Trays**: Tool cards displaying `▸ search_memory` with a vertical accent guide `│` and green latency pill (`ok 61 ms`), accompanied by the live bottom status pill (`● on-device  •  local qwen2.5-3b  •  0 B sent`).
- **Telemetry & Status**: The footer immediately reflects model readiness (`ready` vs `fallback simulated`) and active indexed passage counts.

---

## 3. Key Product Decisions & Trade-Offs

- **Asset Migration (`ModelAssetManager`)**:
  - *Chosen Approach*: Stream chunks asynchronously (64KB buffer on `Dispatchers.IO`) from `assets/` to `context.filesDir` only when missing or corrupt.
  - *Why*: Android cannot memory-map raw compressed assets directly from APK containers for ML runtimes.
  - *Trade-off*: Adds an initial one-time extraction step if packaged in assets, while also supporting direct external file placement in `filesDir`.
- **Database Architecture (`Room FTS4`)**:
  - *Chosen Approach*: `DocumentEntity` paired with a shadow virtual `DocumentFtsEntity` using `@Fts4(contentEntity = DocumentEntity::class)`.
  - *Why*: Provides sub-10ms full-text search across thousands of indexed passages without iterating through raw files, satisfying the `ok 61 ms` mockup constraint.
- **Dual-Mode Engine Execution**:
  - *Chosen Approach*: `PocketOpsViewModel` checks `isModelInitialized`. If initialized, it binds to `LlmInference`; otherwise, it uses the responsive streaming token pipeline with live thinking expansion.

---

## 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────┐
│                    PocketOps Compose UI                      │
│   (Chat Stream • Tool Execution Trays • Telemetry Footer)    │
└──────────────────────────────▲───────────────────────────────┘
                               │ StateFlow (engineState, telemetry)
┌──────────────────────────────┴───────────────────────────────┐
│                     PocketOpsViewModel                       │
│  - Checks Model Initialization                               │
│  - Triggers ModelAssetManager extraction on IO               │
│  - Bridges LiteRT-LM Flow or Fallback Stream to Parser       │
└──────────────┬───────────────────────────────▲───────────────┘
               │ Token Stream                  │ Tool Responses
┌──────────────▼───────────────┐ ┌─────────────┴───────────────┐
│       EdgeTokenParser        │ │       LocalMcpRouter        │
│  - Thought Boundary Splitter │ │  - Tool Interceptor         │
│  - <tool_call> JSON Parser   │─┼─►- AlarmClock Hardware Intent│
│  - Atomic Interruption Scope │ │  - Room FTS4 Database Search│
└──────────────────────────────┘ └─────────────┬───────────────┘
                                               │ SQLite MATCH
                                 ┌─────────────▼───────────────┐
                                 │    AppDocumentDatabase      │
                                 │  - documents (Content)      │
                                 │  - documents_fts (FTS4)     │
                                 └─────────────────────────────┘
```

### Component Breakdown
1. **`ModelAssetManager.kt`**: High-throughput file validator and asset extractor with automatic cleanup on storage faults.
2. **`DocumentDatabase.kt`**: Room entities (`DocumentEntity`, `DocumentFtsEntity`), `DocumentDao` with SQLite MATCH query, and database factory with initial seed data.
3. **`LocalMcpRouter.kt`**: Updated `performLocalWorkspaceFileSearch` connecting directly to `DocumentDao.searchIndexedDocuments(sanitizedQuery)` with millisecond execution timing.
4. **`PocketOpsViewModel.kt`**: Wire `ModelAssetManager`, `LlmInference` lifecycle management, and user instruction handling with dual-mode streaming.

---

## Verification & Validation Plan
1. **Compilation Check**: Run `compile_applet` to verify Room FTS4 schema compilation, KSP processing, and dependencies.
2. **Unit & Integration Tests**: Run `gradle :app:testDebugUnitTest` to verify FTS4 search queries, SQLite MATCH sanitization, and model migration logic.
3. **Packaging**: Run `gradle exportDebugApk` to produce the updated standalone `PocketAI-OnDeviceAgent-Debug.apk`.
