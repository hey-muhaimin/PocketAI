# PocketAI: Private, Explainable, Action-Oriented On-Device Android AI Suite

PocketAI is a production-grade multi-module Android project featuring three distinct applications—**Memory** (offline document ingest, Room FTS4 keyword + vector hybrid search fused with Reciprocal Rank Fusion, grounded Q&A with citations), **Guardian** (explainable privacy and security auditor for installed apps and sensitive system services with deterministic scoring and local-only LLM explanations), and **PocketOps** (autonomous agent chat interface executing multi-step user workflows through trust policies, risk tiers, user confirmation curtains, and a SHA-256 hash-chained audit log)—sharing modular core libraries.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were aligned via interactive clarification and will drive the project implementation:

- **Confirmed Application Entry Point**: Unified Suite Hub within the primary runtime application (`:app`) that seamlessly hosts and navigates between **Memory**, **Guardian**, and **PocketOps**, while structuring the codebase into modular standalone sub-projects (`:app-memory`, `:app-guardian`, `:app-pocketops`, `:core:llm`, `:core:context`, `:core:trust`, `:core:actions`, `:core:ui`) so each can be built independently or run collectively in the emulator.
- **Confirmed Cloud LLM Fallback**: Flexible, configurable OpenAI-compatible remote endpoint (custom Base URL, model name, and API key) managed through an in-app settings sheet with sensitive-request hard interception preventing telemetry or private data leakage.
- **Confirmed Context Ingest & Embeddings**: Sliding token window chunker (256-word window, 64-word overlap) coupled with a deterministic 256-dimension feature-hashing vector embedder and cosine distance ranking fused via Reciprocal Rank Fusion ($k=60$) against SQLite FTS4 match scores.

---

## 1. Overview & Core Concept

### What It Does
PocketAI delivers a privacy-first AI runtime for Android devices:
1. **Memory (`:app-memory`)**: Connects to user documents via Storage Access Framework (SAF), chunks text into overlapping passages, computes feature-hashed embeddings, builds an SQLite FTS4 inverted index, runs hybrid search (BM25-equivalent + vector cosine) combined via RRF, and synthesizes answers citing exact source snippets and line numbers.
2. **Guardian (`:app-guardian`)**: Scans installed packages, evaluates dangerous permissions, notification listeners, and accessibility services using deterministic rule-based scoring (Risk 0–100), and invokes on-device LLM reasoning to generate human-readable explanations of privilege exposure without sending device telemetry to external clouds.
3. **PocketOps (`:app-pocketops`)**: Provides a conversational agent workspace where user requests are parsed into structured JSON action plans. Every tool invocation passes through a strict scope allowlist and deterministic risk classifier (Low, Medium, High). Medium and High-risk actions require interactive biometric/user confirmation before executing through a hardened `GuardedExecutor`. All executions append to a SHA-256 hash-chained audit log with cryptographic verification.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme
- **Aesthetic Direction**: High-tech tactile minimalism—inspired by precision instruments and terminal monitors. Clean dark surfaces with high-contrast semantic indicators (Emerald Green for Safe/Verified, Amber Yellow for Confirmation Needed, Crimson Red for High Risk/Blocked, Cyan for Vector/AI Operations).
- **Color Palette & Tokens**:
  - `Surface`: Deep obsidian `#0D1117` and elevated card surface `#161B22`
  - `Primary`: Electric Cobalt `#388BFD`
  - `Accent / Memory`: Neon Cyan `#58A6FF`
  - `Guardian / Security`: Vivid Emerald `#3FB950`
  - `Alert / Warning`: Caution Amber `#D29922`
  - `Danger / Blocked`: Crimson `#F85149`
- **Typography & Rhythm**:
  - Display headers: High-legibility modern sans-serif with monospace accents for hashes, risk scores, and tool parameters.
  - Interactive elements: Minimum 48dp touch targets, clear border elevation, spring micro-animations for risk level badges and confirmation curtains.

### Key User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PocketAI Hub (Main Entry)                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │   Memory Engine  │  │ Guardian Auditor │  │  PocketOps Operator  │  │
│  │  Hybrid Search   │  │  Privacy Radar   │  │   Guarded Actions    │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────────┬───────────┘  │
└───────────┼─────────────────────┼───────────────────────┼──────────────┘
            │                     │                       │
            ▼                     ▼                       ▼
   SAF Folder Picker       Installed Apps List     Chat + Tool Planner
            │                     │                       │
   WorkManager Ingest      Deterministic Rules     Risk Scorer + Trust Check
            │                     │                       │
   Room FTS4 + Embeddings  Local LLM Explanation   User Confirm Curtain (Med/Hi)
            │                     │                       │
   RRF Hybrid Query + Q&A  Remediation Guidance    GuardedExecutor -> Audit Chain
```

---

## 3. Key Product Decisions & Trade-Offs

1. **Monorepo / Multi-Module Project Structure**:
   - *Chosen Approach*: Multi-module Gradle configuration with `:core:llm`, `:core:context`, `:core:trust`, `:core:actions`, `:core:ui`, alongside dedicated application modules `:app` (Suite Hub & interactive runner), `:app-memory`, `:app-guardian`, and `:app-pocketops`.
   - *Rationale*: Allows single-click interactive testing in the browser emulator through the Suite Hub while strictly enforcing clean architecture boundaries and allowing standalone APK generation for each target application.
2. **Hybrid Search Fusion (RRF vs Weighted Sum)**:
   - *Chosen Approach*: Reciprocal Rank Fusion ($RRF(d) = \sum \frac{1}{k + r_i(d)}$ with $k=60$) over FTS4 keyword rank and vector cosine similarity rank.
   - *Rationale*: RRF normalizes across disparate score distributions (FTS match score vs vector cosine) without needing fragile calibration hyperparameters or external vector databases.
3. **On-Device LLM Router with Cloud Interceptor**:
   - *Chosen Approach*: `LlmRouter` prioritizes local inference (Gemini Nano / MediaPipe Gemma stub) and falls back to an OpenAI-compatible HTTP client. Requests marked `isSensitive = true` are strictly blocked from cloud fallback at the router level.
   - *Rationale*: Absolute guarantee that Guardian audit data and sensitive user files never leak off-device.
4. **Cryptographic Hash-Chained Audit Log**:
   - *Chosen Approach*: Append-only log where each record contains `prevHash`, `timestamp`, `toolName`, `parametersHash`, `riskLevel`, `resultHash`, and `currentHash = SHA256(prevHash + payload)`. A built-in `verify()` function iterates and detects any retroactive tampering.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                                 UI Layer                                          │
│  ┌─────────────────────────┐  ┌────────────────────────┐  ┌────────────────────┐  │
│  │     Memory Screen       │  │    Guardian Screen     │  │  PocketOps Screen  │  │
│  │ (SAF, Search, Q&A, Ref) │  │  (App Audit, Details)  │  │ (Chat, Confirm UI) │  │
│  └────────────┬────────────┘  └───────────┬────────────┘  └─────────┬──────────┘  │
└───────────────┼───────────────────────────┼─────────────────────────┼─────────────┘
                ▼                           ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                           Core Domain & Repositories                              │
│  ┌─────────────────────────┐  ┌────────────────────────┐  ┌────────────────────┐  │
│  │      :core:context      │  │       :core:trust      │  │   :core:actions    │  │
│  │ - TextChunker (Sliding) │  │ - ScopeAllowList       │  │ - Tool Interface   │  │
│  │ - FeatureHashEmbedder   │  │ - RiskScorer (0-100)   │  │ - ToolRegistry     │  │
│  │ - Room FTS4 + Vectors   │  │ - AuditHashChain       │  │ - GuardedExecutor  │  │
│  │ - Ingest & Hybrid Search│  │ - Tamper Verification  │  │   (Policy Enforcer)│  │
│  └────────────┬────────────┘  └───────────┬────────────┘  └─────────┬──────────┘  │
└───────────────┼───────────────────────────┼─────────────────────────┼─────────────┘
                ▼                           ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                                   :core:llm                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────┐  │
│  │ LlmRouter: Local Engine (Nano/Gemma Stub) -> Cloud Engine (OpenAI API)      │  │
│  │ Sensitive Interceptor: Blocks Cloud Routing if request.isSensitive == true  │  │
│  └─────────────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────────┘
```

### Module Responsibilities

1. **`:core:ui`**:
   - `PocketAiTheme`: Dark obsidian palette with M3 color schemes.
   - `ConfirmActionDialog`: Risk-tiered modal with parameter breakdown, risk badge, and execute/cancel triggers.
   - `ModelSettingsDialog`: Endpoint URL, API key, model selection, and local/cloud preference.
   - `FolderPickerContract`: SAF directory tree selector with persistent URI permissions.

2. **`:core:context`**:
   - `DocumentEntity` & `ChunkEntity`: Room database tables.
   - `ChunkFtsEntity`: SQLite FTS4 virtual table for full-text keyword indexing.
   - `TextChunker`: Sliding window text segmenter with configurable overlap and sentence preservation.
   - `FeatureHashEmbedder`: 256-dimensional deterministic n-gram hashing embedder with L2 normalization.
   - `HybridSearchEngine`: Computes FTS BM25 rank, vector cosine similarity, and evaluates RRF score.
   - `IngestRepository`: Background indexing orchestrator integrated with Android `WorkManager`.

3. **`:core:trust`**:
   - `RiskLevel`: `LOW`, `MEDIUM`, `HIGH`, `BLOCKED`.
   - `ScopeAllowList`: Explicit permission whitelist; unlisted capabilities default to `BLOCKED`.
   - `RiskEvaluator`: Evaluates action name, parameters, and system impact to assign risk tiers.
   - `AuditLogEntity`: Room table storing hash chain nodes (`index`, `timestamp`, `toolName`, `paramsJson`, `riskLevel`, `status`, `prevHash`, `hash`).
   - `AuditChainRepository`: Appends new records and exposes `verifyIntegrity(): VerificationResult`.

4. **`:core:actions`**:
   - `Tool` interface: `name`, `description`, `requiredScope`, `riskLevel`, `execute(params): ToolResult`.
   - Built-in tools:
     - `SetAlarmTool`: Sets system alarm through Android `AlarmClock.ACTION_SET_ALARM`.
     - `OpenUrlTool`: Opens HTTPS URLs (with strict scheme validation, rejecting arbitrary intents).
     - `CreateCalendarEventTool`: Inserts calendar events through `CalendarContract.Events`.
     - `SearchMemoryTool`: Queries `:core:context` hybrid index directly from agent plan.
   - `ToolRegistry`: Multibinding registry mapping tool identifiers.
   - `GuardedExecutor`: The singular, tamper-proof gate enforcing trust check, confirmation curtain, and audit hashing.

5. **`:core:llm`**:
   - `LlmRequest` (`prompt`, `systemPrompt`, `tools`, `isSensitive`, `temperature`).
   - `LlmResponse` (`content`, `toolCalls`, `usage`, `engineUsed`).
   - `LocalLlmEngine`: On-device engine stub for Gemini Nano / MediaPipe Gemma with deterministic local fallback.
   - `CloudLlmEngine`: OkHttp-based OpenAI-compatible client supporting custom endpoints.
   - `LlmRouter`: Orchestrates fallback order and enforces privacy isolation.

6. **`:app-guardian`**:
   - Package manager inspector querying installed applications, requested permissions, accessibility services, and notification listeners.
   - Deterministic risk scoring matrix (Dangerous permissions: SMS, Location, Contacts, Microphone, Camera, Accessibility).
   - On-device explanation generator analyzing permission bundles.

7. **`:app-pocketops`**:
   - Reactive chat ViewModel maintaining conversation state, active planner loop, and tool approval curtain.
   - JSON Plan Parser transforming LLM responses into executable tool actions.

---

## 5. Build, Packaging & CI Infrastructure

- **Gradle Configuration**:
  - `gradle/libs.versions.toml` with pinned dependencies (Room, OkHttp, Moshi, Kotlinx Serialization, Navigation Compose, WorkManager, Coroutines).
  - Multi-module `settings.gradle.kts` including all 8 modules.
  - ProGuard/R8 shrinking rules for release builds.
  - Network security config: Cleartext enabled for debug (supporting `10.0.2.2` local model testing) and strictly disabled in release builds.
- **Production Scripts**:
  - `scripts/bootstrap.sh`: Sets up environment and verifies JDK/Android SDK.
  - `scripts/generate_keystore.sh`: Creates production release keystore.
  - `scripts/build_release.sh`: Builds signed release AAB and APK.
  - `scripts/version_bump.sh`: Increments versionCode and versionName across project.
  - `scripts/ci_check.sh`: Runs lint, tests, and debug compilation.
- **GitHub Actions Workflows**:
  - `.github/workflows/ci.yml`: Automated pull request validation (tests, build check).
  - `.github/workflows/release.yml`: Tag-driven automated release packaging signed AAB/APK.
- **Unit Test Suite**:
  - Chunker unit tests (windowing, overlap, boundary handling).
  - FTS query sanitizer tests (special characters, boolean operators).
  - RRF ranking fusion tests (score reconciliation, order preservation).
  - Risk scoring & trust policy tests (denied by default, escalation rules).
  - Audit hash chain integrity tests (valid chain verification, simulated tampering detection).
  - Plan parser tests (JSON schema validation, malformed tool call handling).
  - LLM router tests (local-first fallback, sensitive data cloud cutoff).
