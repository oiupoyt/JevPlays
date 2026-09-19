# Design Decisions & Recorded Assumptions

This document records the architectural choices, default parameters, and operational assumptions made during the design and implementation of **JevPlayer**.

---

## 1. TypeSafe Jev API & Spend Economics

- **API Endpoint & Protocol:**
  - Default URL: `https://api.typesafe.ai/v1/systemone`
  - Protocol: HTTP/2 with persistent connection pooling and TLS session resumption.
  - Authentication: Bearer token via `Authorization: Bearer <key>`.
  - Environment Fallback: `TYPESAFE_AI_API_KEY` is checked if the config field `apiKey` is empty.
- **Model Cost & Spend Cap:**
  - Standard pricing model: **$0.042 per 1M input tokens**.
  - Token estimation rule: 1 token $\approx$ 4 characters of JSON snapshot prompt.
  - Spend Cap Default: `$1.00` per episode run.
  - Hard Cap Behavior: When cumulative estimated spend reaches `spendCapUsd`, the system logs a `SpendCapExceededEvent` and transitions seamlessly to `MockProvider` (heuristic fallback) without throwing runtime exceptions or crashing Minecraft.
- **Confidence Calibration:**
  - Default Confidence Threshold: `0.65`.
  - Any decision returned with confidence $< 0.65$ triggers the fallback selector (selecting the highest confidence option that satisfies current immediate needs such as low health or urgent shelter).

---

## 2. Multi-Threading & Concurrency Model

- **Client Thread Protection:**
  - Minecraft's main client render thread must never block on network I/O.
  - All calls to `DecisionProvider.query()` run asynchronously in a dedicated cached executor thread pool.
  - Target per-tick client overhead: $< 0.3 \text{ ms}$ average, with a hard budget of $< 2.0 \text{ ms}$ for worst-case spikes.
- **State & Decision Synchronization:**
  - Synchronization between the main tick thread and the decision background thread is managed via non-blocking `AtomicReference<StateSnapshot>` and `AtomicReference<JevDecision>`.
  - While a network query is in-flight (typically 70–500 ms), the bot continues executing its active Baritone goal or holds state.
- **Preemptive Reflex Engine:**
  - Synchronous reflex evaluations execute every tick prior to accepting any Jev decision.
  - If an emergency is detected (lava submersion, drowning/air $< 4$, burning without water, point-blank hostile damage, starvation $\le 2$ food), reflexes immediately seize control, execute protective actions (e.g. step back, swim up, eat food, sprint dodge), and cancel any obsolete asynchronous goal.

---

## 3. Baritone Integration & Actuation

- **API Boundary & Licensing:**
  - Strictly binds to `baritone.api.BaritoneAPI` via public interfaces.
  - Zero forking and zero bundling of Baritone source code, fully respecting the LGPL-3.0 license.
  - Baritone is loaded as a runtime mod (`modCompileOnly` during build).
- **Legitimacy & Fair Mode:**
  - `fairMode=true` by default: sets `BaritoneAPI.getSettings().legitMine.value = true`. The bot only pathfinds to and mines blocks that have been exposed to player sight or are logically discoverable without X-Ray cheats.
  - `smoothLook=true` by default: activates cinematic camera interpolation so video recordings appear organic rather than jerky or robotic.
  - `allowSprint=true`: enables tactical sprinting during pathfinding and combat flees.
- **Nether Pathfinding:**
  - Includes `dev.babbaj:nether-pathfinder:1.6` runtime capability for dimension transitions.

---

## 4. Safety, Watchdogs & Controls

- **Singleplayer Restriction:**
  - Automation bots on public servers violate multiplayer fair-play rules.
  - `allowMultiplayer=false` is enforced by default. If the player connects to an external multiplayer server, the mod logs a warning and disables autonomous ticking unless the user explicitly overrides `allowMultiplayer: true` in `jevplayer.json`.
- **Instant Kill Switch:**
  - Keybinding: `F9` (default MISC category).
  - Pressing `F9` immediately sets the internal state to `DISABLED`, cancels all active Baritone processes (`getCustomGoalProcess().isActive()`, `getPathingBehavior().cancelEverything()`), and halts background polling.
- **Watchdog Escalation:**
  - **Stuck Watchdog:** Triggers if player position has moved $< 1.5$ blocks over a 45-second sliding window while no inventory changes have occurred. Clears pathing cache, jumps/steps aside, and resets goal.
  - **Loop Breaker:** Triggers if the same high-level action fails 3 consecutive times. Blacklists that specific action for 60 seconds to prevent getting trapped in local minima.
  - **Death Watchdog:** Detects player death, records the milestone failure, awaits respawn, and resets the goal planner to Phase 1 (gather wood).

---

## 5. Security & Privacy

- **Zero API Key Leakage:**
  - The API key is never serialized into plain text in `episode_events.json`, `decision_log.jsonl`, standard Minecraft logs, or rendered on the HUD overlay.
  - The method `JevPlayerConfig.getRedactedApiKey()` masks all but the first 7 and last 4 characters (`sk-live_***abcd`).
  - No secret keys are stored in version control; `.gitignore` guards all environment and secret files.

---

## 6. HUD & Video Overlay

- **In-Game Overlay:**
  - Keybinding: `F8` toggles HUD display on/off.
  - Visuals: Clean semi-transparent card rendering current milestone, active Jev action, decision confidence percentage, rolling latency (p50/p95), watchdog state, and cumulative token spend.
  - Rendering Throttling: State text formatting is throttled to update at most once every 200 ms to prevent per-frame garbage generation.
