# PocketOps Session Interface: Streaming Token Engine & Dynamic Tool Blocks

Orchestrates real-time character-level streaming token ingestion, dynamic `<tool_call>` extraction, live thinking phase reveals, and high-fidelity monospace tool execution trays in accordance with the confirmed user preferences.

## User Review & Critical Decisions

> [!IMPORTANT]
> The architectural direction and visual interface choices below incorporate your confirmed preferences:
> - **Streaming Animation & Thinking**: Smooth real-time token streaming with live thinking expansion that auto-scrolls the conversation feed without freezing the UI thread.
> - **Tool Card Layout**: Outlined `#302c2c` monospace trays with end-aligned latency pills (`ok 42 ms` / `approved`), divider rule, and a vertical 1.dp guideline (`│`) preceding parameters and outputs.
> - **Helper Shortcuts**: Interactive slash command chips (`[/search]`, `[/index]`, `[/scopes]`, `[/alarm]`) positioned above the persistent input field.

---

## 1. Overview & Core Concept

- **What It Does**: Pipelined natural language operations client running the on-device Hermes Agent loop. Processes user instructions, streams model tokens live, parses embedded `<tool_call>` blocks, executes local capabilities via `LocalMcpRouter` (`set_alarm`, `search_memory`, `read_sms`, `process_media`, `check_accessibility`), and presents cryptographic audit-chain evidence.
- **Target Audience / Persona**: Security-conscious and power users requiring local, zero-cloud-egress system operations and explainable tool execution on Android.
- **Key Value**: 100% on-device latency (<50ms execution), deterministic auditability, and immediate visual clarity through typewriter token streaming and structured tool execution cards.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Prompt Entry**: The user taps a slash shortcut (e.g. `[/alarm]`) or types in the persistent `› tell pocketops what to do` field and taps the solid white `Send` button.
2. **Live Thinking Stream**: The model begins streaming character-by-character tokens. The UI displays an active thinking block with real-time text expansion and an animated status dot.
3. **Tool Boundary Interception**: Once the model closes a `<tool_call>` block, the engine cleanly excises raw syntax from the chat text and renders a dedicated monospace tool tray:
   - Header with tool signature `▸ set_alarm` and status pill (`approved` or `ok 42 ms`).
   - Parameter section with 1.dp horizontal divider and vertical 1.dp accent guideline `│`.
4. **Action Approval**: If the tool has medium or high risk (e.g. `alarm.write`), an insets-aware approval sheet is presented with full-width split `[Deny]` vs `[Allow once]` buttons.
5. **Session Persistence**: Completed turns update the session metadata, compute the SHA-256 block hash for the audit ledger, and refresh the bottom status bar (`● local qwen2.5-3b • 0 B sent`).

### Visual Identity & Theme
- **Theme**: Strict Monospace Utilitarian Dark Palette.
- **Color Palette Tokens**:
  - Background (`--bg`): `#201d1d`
  - Surface (`--surface`): `#302c2c`
  - Border (`--border`): `#464343`
  - Text (`--text`): `#fdfcfc`
  - Muted (`--muted`): `#9a9898`
  - Accent (`--accent`): `#007aff`
  - Success (`--success`): `#30d158`
  - Warning (`--warning`): `#ff9f0a`
  - Danger (`--danger`): `#ff5f57`
- **Typography**: IBM Plex Mono across headers, body, tags, and code blocks.
- **Structural Constraints**: Zero gradients, zero drop-shadows, 1.dp solid borders, `statusBarsPadding()` and `navigationBarsPadding()` safe area compliance, and minimum 44.dp touch targets.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Token Streaming on Background Coroutines (`Dispatchers.Default`)**
  - *Chosen Approach*: `EdgeTokenParser` evaluates the token string and character buffer on `Dispatchers.Default`, emitting `StateFlow<AgentTurnState>` updates.
  - *Why*: Prevents high-frequency text emissions from blocking the Jetpack Compose layout or frame rendering.
- **Decision 2: Monospace Tray with Vertical Accent Guideline**
  - *Chosen Approach*: Encapsulated card structure with a 1.dp border, 6.dp corner radius, and an internal box with a 1.dp left border acting as the code/output guideline.
  - *Why*: Matches terminal and developer tools aesthetic from the design mockups with maximum scanability.
- **Decision 3: Insets Buffer Zone**
  - *Chosen Approach*: Outermost container applies `statusBarsPadding()` and `navigationBarsPadding()`, while the system status bar and navigation bar maintain the `#201d1d` color.
  - *Why*: Permanently eliminates hardware punch-hole, clock, and gesture bar collisions.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity                             │
│       Surface(color = #201D1D) + enableEdgeToEdge()         │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                  PocketAiHubScreen                          │
│     .statusBarsPadding() + .navigationBarsPadding()         │
└──────────────────────────────┬──────────────────────────────┘
                               │
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
┌──────────────┐       ┌──────────────┐       ┌─────────────────┐
│ MemoryScreen │       │GuardianScreen│       │ PocketOpsScreen │
│(Search/Srcs) │       │(Overview/App)│       │ (Chat/Sessions) │
└──────────────┘       └──────────────┘       └────────┬────────┘
                                                       │
                               ┌───────────────────────┴───────────────────────┐
                               ▼                                               ▼
                     ┌──────────────────┐                            ┌───────────────────┐
                     │ EdgeTokenParser  │                            │  LocalMcpRouter   │
                     │  (Dispatchers.   │──<tool_call> JSON Schema──>│ (Alarm, SMS, File │
                     │    Default)      │                            │  Storage, A11Y)   │
                     └────────┬─────────┘                            └─────────┬─────────┘
                              │                                                │
                              │ StateFlow<AgentTurnState>                      │ TelemetryLog
                              ▼                                                ▼
                     ┌───────────────────────────────────────────────────────────────────┐
                     │            PocketOps UI & Fixed Status Bar                        │
                     │      Monospace Trays • Latency Pills • Audit Ledger               │
                     └───────────────────────────────────────────────────────────────────┘
```

### Component & State Mapping
- `PocketOpsViewModel`: Exposes `uiState: StateFlow<PocketOpsUiState>` containing current session messages, tool execution trays, audit logs, and approval prompts.
- `EdgeTokenParser`: Emits `AgentTurnState.Thinking` during inference and `AgentTurnState.ExecutingTool` when `<tool_call>` tags close.
- `LocalMcpRouter`: Executes system intents and sandbox operations on `Dispatchers.IO`, measuring execution time (`measureTimeMillis`) to supply the `ok 42 ms` telemetry pills.
- `ConfirmActionDialog`: Renders the high-contrast approval sheet for actions requiring confirmation.
