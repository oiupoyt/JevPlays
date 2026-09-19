# JevPlayer Architecture Specification

**JevPlayer** is a client-side Fabric mod designed for autonomous Minecraft survival gameplay. It integrates **TypeSafe AI's Jev** (a fast, calibrated System-One decision engine) with **Baritone** (the industry-standard pathing and mining actuator) via a zero-allocation, modular architecture.

---

## 1. System Overview & Sense-Decide-Act Cycle

The core runtime operates on an asynchronous **Sense $\to$ Decide $\to$ Act** cycle designed to maintain client-thread responsiveness without dropping frames.

```mermaid
flowchart TD
    subgraph Minecraft_Client_Thread ["Minecraft Client Thread (~20 TPS)"]
        A["Client Tick Event"] --> B["Sense: World Sensor"]
        B --> C["State Snapshot Created"]
        C --> D{"Reflex Engine Emergency?"}
        D -- Yes --> E["Immediate Reflex Actuation (Lava/Drown/Flee)"]
        D -- No --> F{"Watchdog Alert?"}
        F -- Yes --> G["Watchdog Recovery (Stuck/Loop Reset)"]
        F -- No --> H["Check Pending Asynchronous Decision"]
        H --> I["Act: Baritone Action Executor"]
    end

    subgraph Async_Decision_Worker ["Async Decision Worker (Daemon Thread)"]
        C -. Post State .-> J["Legal Actions Filter"]
        J --> K["Format Concise JSON Prompt"]
        K --> L{"Provider Mode"}
        L -- Real API --> M["HTTP/2 POST to api.typesafe.ai (70-500ms)"]
        L -- Mock Mode --> N["Calibrated Heuristic Decision (<1ms)"]
        L -- Replay --> O["Recorded JSONL Decision Log"]
        M --> P["Parse Typed Jev Decision"]
        N --> P
        O --> P
        P -. Deliver Decision .-> H
    end

    subgraph Logging_Worker ["Background Logger Thread"]
        C -. Log Snapshot .-> Q["decision_log.jsonl"]
        P -. Log Decision .-> Q
        I -. Log Milestone/Event .-> R["episode_events.json"]
    end
```

---

## 2. Multi-Module Project Structure

To decouple Minecraft dependencies and allow cross-version support, the repository is partitioned into a zero-dependency core module and thin versioned adapters:

```
JevPlays/
├── core/                           # Pure Java 21/25, ZERO Minecraft dependencies
│   ├── src/main/java/com/typesafe/jevplayer/core/
│   │   ├── action/                 # Action catalog (14 actions) & legal action generator
│   │   ├── bridge/                 # Abstract sensor & actuator bridge interfaces
│   │   ├── config/                 # Configuration model & secure key redaction
│   │   ├── decision/               # TypeSafe HTTP/2 client, MockProvider, DecisionReplay
│   │   ├── director/               # 9-stage milestone ladder & episode pacing
│   │   ├── reflex/                 # Zero-latency emergency reflexes (lava, air, combat)
│   │   ├── sim/                    # Headless simulated world adapter for unit tests
│   │   ├── state/                  # Zero-allocation state snapshots & hash generators
│   │   └── watchdog/               # Stuck detection, loop breaker, death watchdogs
│   └── src/test/java/              # 100% deterministic unit tests (JUnit 5)
├── adapter-1_21_11/                # Fabric Loom module for Minecraft 1.21.11
│   ├── src/main/java/.../          # Concrete WorldSensor, ActionExecutor, InputBridge, HUD
│   └── src/main/resources/         # fabric.mod.json for 1.21.11
├── adapter-26_1/                   # Thin adapter for Minecraft 26.1 (unobfuscated)
│   └── src/main/java/.../          # ClientModInitializer & Baritone 1.18.0 bridge
├── adapter-26_2/                   # Thin adapter for Minecraft 26.2 (unobfuscated)
│   └── src/main/java/.../          # ClientModInitializer & Baritone 1.19.0 bridge
└── libs/                           # Official Baritone public API jars
```

### Why Multi-Module?
1. **Deterministic Testing:** The `core` module compiles with pure Java and runs entire 10,000-tick simulations in headless unit tests without launching Minecraft or loading native libraries.
2. **Version Independence:** Changes to Minecraft versions (such as the un-obfuscated architecture in 26.x) do not require altering core decision logic, reflex trees, or watchdog rules.
3. **No Code Duplication:** Adapters remain thin shims (typically $< 300$ LOC) that map Minecraft entities, blocks, and keys to core POJOs.

---

## 3. Concurrency & Thread Synchronization

Minecraft runs rendering and client entity updates synchronously on the main thread. Network I/O or blocking waits on that thread cause immediate frame stutter.

### Lock-Free Reference Exchange
```mermaid
sequenceDiagram
    participant Main as Client Tick Thread
    participant StateRef as AtomicReference<StateSnapshot>
    participant DecRef as AtomicReference<JevDecision>
    participant Worker as Decision Daemon Worker
    participant Jev as TypeSafe AI (System One)

    Main->>Main: Sense environment & calculate legal actions
    Main->>StateRef: set(latestSnapshot)
    StateRef-->>Worker: Poll state
    Worker->>Jev: HTTP/2 POST /v1/systemone (70-500ms)
    Note over Main: Client renders smoothly at 60+ FPS<br/>Reflexes actively guard player
    Jev-->>Worker: HTTP 200 {choice, confidence, urgency}
    Worker->>DecRef: set(decision)
    DecRef-->>Main: getAndSet(null)
    Main->>Main: Actuate Baritone goal
```

- **Thread Confinement:** State reading occurs only on the client thread.
- **Reference Passing:** The decision worker takes an immutable `StateSnapshot`.
- **Preemption:** If an emergency reflex fires while a network query is pending, the reflex takes precedence immediately. When the delayed HTTP response returns, the main thread discards it if the game state has transitioned.

---

## 4. Reflex Engine vs. System-One Jev

| Feature | Reflex Engine (System 0) | TypeSafe Jev (System 1) |
| :--- | :--- | :--- |
| **Execution Thread** | Client tick thread (synchronous) | Background daemon thread (asynchronous) |
| **Latency** | $< 0.05 \text{ ms}$ | $70 \text{ -- } 500 \text{ ms}$ |
| **Trigger Criteria** | Lava, drowning, fire, point-blank combat, starving | Strategic choices (mining, crafting, exploring, sleeping) |
| **Preemption Power** | Can cancel any active Baritone goal instantly | Subordinate to active reflexes and watchdogs |
| **Failure Mode** | Fail-safe: immediate protective action | Fail-soft: fallback to heuristic rule if confidence $< 0.65$ |

---

## 5. Actuation via Baritone (LGPL-3.0 Compliance)

JevPlayer never modifies, forks, or bundles Baritone source code. All actuation interfaces through `baritone.api.BaritoneAPI`:

- **Pathing:** `BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos))`
- **Mining:** `BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mineByName(blockName)`
- **Exploration:** `BaritoneAPI.getProvider().getPrimaryBaritone().getExploreProcess().explore(originX, originZ)`
- **Inventory/Crafting:** Executed via standard Minecraft `ClientPlayerInteractionManager` window click packets or Baritone builder process.

---

## 6. Watchdog & Failure Recovery Matrix

```mermaid
stateDiagram-v2
    [*] --> Healthy
    Healthy --> ReflexActive: Lava / Low Air / Under Attack
    ReflexActive --> Healthy: Danger Cleared

    Healthy --> StuckDetected: Stationary > 45s without inventory change
    StuckDetected --> ClearingPath: Cancel Goal + Step Aside + Blacklist
    ClearingPath --> Healthy: Movement Resumed

    Healthy --> ActionLoopDetected: Same Action Failed 3 Times
    ActionLoopDetected --> BlacklistingAction: Suppress Action for 60s
    BlacklistingAction --> Healthy: Shift to Alternative Action

    Healthy --> Dead: Player Health <= 0
    Dead --> Respawning: Auto-Respawn Click
    Respawning --> Healthy: Reset Ladder to Milestone 1 (Wood)

    Healthy --> Disabled: Kill Switch (F9) Pressed
    Disabled --> [*]
```
