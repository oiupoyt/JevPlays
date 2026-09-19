# JevPlayer

> **Autonomous Minecraft Survival driven by TypeSafe AI's Jev model through Baritone**

[![Build and Verify](https://github.com/oiupoyt/JevPlays/actions/workflows/build.yml/badge.svg)](https://github.com/oiupoyt/JevPlays/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft: 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1%20%7C%2026.2-brightgreen.svg)](https://fabricmc.net/)

**JevPlayer** is a client-side Fabric mod that enables an autonomous player agent to play Minecraft survival mode from scratch for a full 1-hour episode without human intervention.

- **The Brain:** **Jev** (TypeSafe AI's "System One" decision model). It does not generate text or code; it returns fast (70–500 ms), calibrated typed decisions, confidence ratings, and urgency scores.
- **The Actuator:** **Baritone** (public API). Handles pathfinding, mining, obstacle clearing, and movement physics smoothly.
- **The Engine:** An allocation-light, multi-module core featuring emergency reflexes, watchdog monitoring, a 9-stage milestone ladder, in-game HUD telemetry, and deterministic headless simulation.

---

## Key Features

- **Multi-Version Architecture:** Modular design separates pure game logic (`core`) from versioned Fabric adapters (`adapter-1_21_11`, `adapter-26_1`, `adapter-26_2`).
- **Hybrid System 0 / System 1 Decision Loop:**
  - **Reflex Engine (System 0):** Zero-latency ($< 0.05 \text{ ms}$) synchronous emergency reactions for lava, drowning, point-blank combat, and starvation.
  - **Jev Model (System 1):** Fast asynchronous strategic choices across 14 high-level survival actions.
  - **Mock Mode:** Built-in calibrated heuristic decision engine requiring no API keys or internet connection.
  - **Decision Replay:** Replay recorded JSONL decision logs to reproduce runs deterministically.
- **Robust Watchdogs:**
  - **Stuck Watchdog:** Detects stationary states ($< 1.5$ blocks moved over 45s without inventory change) and automatically clears pathing and sidesteps.
  - **Loop Breaker:** Detects repeated action failures and temporarily blacklists problematic actions.
  - **Instant Kill Switch (`F9`):** Immediately relinquishes all control and stops all Baritone processes.
- **In-Game HUD Overlay (`F8`):**
  - Displays current action, decision confidence, rolling latency (p50/p95), active watchdog status, and cumulative spend.
- **Enterprise Safety & Privacy:**
  - Strict key redaction: API keys are never logged, committed, or rendered in plain text (`sk-live_***abcd`).
  - Singleplayer protection: `allowMultiplayer=false` by default prevents accidental activation on public servers.
  - Hard spend cap: Gracefully falls back to MockProvider when spend reaches the configured limit ($1.00 default).

---

## Directory Structure

```
JevPlays/
├── core/                  # Pure Java (no Minecraft/Fabric imports)
│   ├── src/main/java/     # Config, state, actions, reflexes, watchdogs, director, Jev HTTP client
│   └── src/test/java/     # 100% deterministic unit tests & headless simulator
├── adapter-1_21_11/       # Fabric Loom adapter for Minecraft 1.21.11
├── adapter-26_1/          # Adapter for Minecraft 26.1 (unobfuscated)
├── adapter-26_2/          # Adapter for Minecraft 26.2 (unobfuscated)
├── libs/                  # Official Baritone API jars
├── docs/
│   └── PERFORMANCE.md     # Latency budgets, zero-GC rules, Spark & JFR profiling
├── ARCHITECTURE.md        # Sense-Decide-Act lifecycle, threading & Baritone binding
├── ASSUMPTIONS.md         # Documented parameters, thresholds, and fallback rules
└── jevplayer.sample.json  # Sample configuration template
```

---

## Getting Started

### Prerequisites
- **Java Development Kit:** JDK 21+ (JDK 25 recommended)
- **Minecraft Launcher:** Fabric Loader 0.16+ installed for Minecraft 1.21.11
- **Baritone:** Ensure the Baritone Fabric jar is present in your `.minecraft/mods` folder.

### 1. Build the Mod
Clone the repository and build all modules using the Gradle wrapper:

```bash
git clone https://github.com/oiupoyt/JevPlays.git
cd JevPlays
./gradlew build
```

The compiled mod JAR will be located at:
```
adapter-1_21_11/build/libs/adapter-1_21_11-1.0.0.jar
```

### 2. Configure Your API Key
JevPlayer supports two methods for setting the TypeSafe API key:

#### Option A: Environment Variable (Recommended)
```bash
export TYPESAFE_AI_API_KEY="your-actual-api-key-here"
```

#### Option B: Configuration File
Copy `jevplayer.sample.json` into `.minecraft/config/jevplayer.json` and insert your credentials:
```json
{
  "apiKey": "your-actual-api-key-here",
  "endpoint": "https://api.typesafe.ai/v1/systemone",
  "mockMode": false,
  "confidenceThreshold": 0.65,
  "fairMode": true,
  "spendCapUsd": 1.0,
  "debugOverlay": true,
  "cinematicCamera": true
}
```
*Note: If no API key is found, JevPlayer automatically starts in **Mock Mode**.*

### 3. Launch Development Client
To launch the client directly from Gradle:
```bash
./gradlew :adapter-1_21_11:runClient
```

---

## Controls & Keybindings

| Key | Action | Description |
| :---: | :--- | :--- |
| **`F8`** | **Toggle HUD** | Shows/hides the JevPlayer telemetry card (active milestone, confidence, latency, spend). |
| **`F9`** | **Kill Switch** | Emergency abort. Instantly disengages autonomous ticking and cancels all Baritone goals. |

---

## Configuration Reference

Configuration is loaded from `config/jevplayer.json`:

| Key | Type | Default | Description |
| :--- | :---: | :---: | :--- |
| `apiKey` | `string` | `""` | TypeSafe API key (or set via `TYPESAFE_AI_API_KEY`). |
| `endpoint` | `string` | `https://api.typesafe.ai/v1/systemone` | TypeSafe Jev inference endpoint. |
| `mockMode` | `boolean` | `false` | When `true`, uses the internal heuristic model instead of the cloud API. |
| `confidenceThreshold` | `float` | `0.65` | Minimum confidence score required to accept a Jev decision. |
| `fairMode` | `boolean` | `true` | Enforces `legitMine=true` in Baritone (no X-Ray / cheating perception). |
| `allowMultiplayer` | `boolean` | `false` | Guard against running automation on external servers. |
| `spendCapUsd` | `double` | `1.00` | Maximum spend limit per run before falling back to MockProvider ($0.042/1M tokens). |
| `debugOverlay` | `boolean` | `true` | Enables the in-game HUD overlay. |
| `cinematicCamera` | `boolean` | `true` | Enables `smoothLook=true` in Baritone for video recordings. |
| `hudRefreshRateMs` | `long` | `200` | Telemetry refresh interval to eliminate per-frame GC allocations. |
| `logReplay` | `boolean` | `false` | Replays decisions from a recorded log file. |
| `replayLogPath` | `string` | `decision_log.jsonl` | Path to the recorded JSONL log for replay mode. |

---

## Testing & Simulation

The `core` module includes a deterministic headless simulation engine that runs complete survival loops without launching Minecraft:

```bash
# Run all unit tests, legal action masks, watchdogs, and simulated loops:
./gradlew :core:test
```

### Tested Capabilities
- Legal action masking across health, tool, and environmental states.
- Emergency reflex preemption and watchdog transitions.
- Spend cap tracking and automatic fail-soft fallback.
- Token rate calculations ($0.042 / 1M tokens) and JSON schema validation.
- Recorded decision log parsing and replay fidelity.

---

## Additional Documentation

- [Architecture Specification](file:///home/oiupoyt/projects/JevPlays/ARCHITECTURE.md): In-depth diagrams of the Sense-Decide-Act loop and thread synchronization.
- [Assumptions & Design Choices](file:///home/oiupoyt/projects/JevPlays/ASSUMPTIONS.md): Recorded decisions on confidence thresholds, spend caps, and reflex priorities.
- [Performance Guide](file:///home/oiupoyt/projects/JevPlays/docs/PERFORMANCE.md): Profiling protocols with Spark and JFR, allocation budgets, and soak testing.

---

## License & Compliance

- **Mod Code:** Licensed under the [MIT License](LICENSE).
- **Baritone Actuator:** Interfaced strictly via public API interfaces (`baritone.api.BaritoneAPI`) in compliance with the **LGPL-3.0** license. No Baritone source code is forked, bundled, or modified.
