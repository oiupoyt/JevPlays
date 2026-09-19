package com.typesafe.jevplayer.core.engine;

import com.typesafe.jevplayer.core.action.ActionCatalog;
import com.typesafe.jevplayer.core.action.ActionDefinition;
import com.typesafe.jevplayer.core.bridge.*;
import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import com.typesafe.jevplayer.core.decision.*;
import com.typesafe.jevplayer.core.director.EpisodeDirector;
import com.typesafe.jevplayer.core.director.EpisodeEventLogger;
import com.typesafe.jevplayer.core.director.MilestoneLadder;
import com.typesafe.jevplayer.core.hud.HudState;
import com.typesafe.jevplayer.core.reflex.ReflexEngine;
import com.typesafe.jevplayer.core.reflex.ReflexResult;
import com.typesafe.jevplayer.core.state.ActionHistoryEntry;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import com.typesafe.jevplayer.core.watchdog.WatchdogEngine;
import com.typesafe.jevplayer.core.watchdog.WatchdogEvent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class JevPlayerEngine implements AutoCloseable {
    private final JevPlayerConfig config;
    private final WorldSensor worldSensor;
    private final ActionExecutorBridge actionExecutor;
    private final InputBridge inputBridge;
    private final HudRendererBridge hudRenderer;
    private final ActionCatalog actionCatalog;
    private final ReflexEngine reflexEngine;
    private final WatchdogEngine watchdogEngine;
    private final MilestoneLadder milestoneLadder;
    private final EpisodeDirector episodeDirector;
    private final EpisodeEventLogger eventLogger;

    private DecisionProvider decisionProvider;
    private final ExecutorService networkExecutor;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicReference<DecisionResult> pendingResult = new AtomicReference<>(null);
    private final AtomicBoolean requestInFlight = new AtomicBoolean(false);
    private final AtomicLong inFlightStartTimeMs = new AtomicLong(0);

    private String currentActionId = "hold";
    private long currentActionStartTimeMs = 0;
    private long lastDecisionTimeMs = 0;
    private long lastDecisionStateHash = 0;
    private long totalDecisionsCount = 0;

    private final LinkedList<ActionHistoryEntry> recentHistory = new LinkedList<>();

    // Performance profiling metrics
    private double lastTickMs = 0.0;
    private double tickSumMs = 0.0;
    private long tickCount = 0;
    private double maxTickMs = 0.0;

    // HUD low-frequency update timer (~4 Hz)
    private long lastHudUpdateTimeMs = 0;
    private DecisionResult latestAppliedResult = null;

    public JevPlayerEngine(
            JevPlayerConfig config,
            WorldSensor worldSensor,
            ActionExecutorBridge actionExecutor,
            InputBridge inputBridge,
            HudRendererBridge hudRenderer,
            Path logDir
    ) {
        this.config = config;
        this.worldSensor = worldSensor;
        this.actionExecutor = actionExecutor;
        this.inputBridge = inputBridge;
        this.hudRenderer = hudRenderer;

        this.actionCatalog = new ActionCatalog();
        this.reflexEngine = new ReflexEngine();
        this.watchdogEngine = new WatchdogEngine();
        this.milestoneLadder = new MilestoneLadder();
        this.eventLogger = new EpisodeEventLogger(logDir.resolve("episode_events.json"));
        this.episodeDirector = new EpisodeDirector(milestoneLadder, eventLogger, config.episodeMinutes);

        this.networkExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jev-network-worker");
            t.setDaemon(true);
            return t;
        });

        initProvider();
    }

    private void initProvider() {
        if ("typesafe".equalsIgnoreCase(config.provider) && !config.resolveApiKey().isEmpty()) {
            this.decisionProvider = new TypeSafeProvider(config);
        } else {
            this.decisionProvider = new MockProvider();
        }
    }

    public void reloadConfig(JevPlayerConfig newConfig) {
        if (newConfig != null) {
            this.config.apiKey = newConfig.apiKey;
            this.config.apiKeyEnvVar = newConfig.apiKeyEnvVar;
            this.config.provider = newConfig.provider;
            this.config.model = newConfig.model;
            this.config.baseUrl = newConfig.baseUrl;
            this.config.minDecisionIntervalMs = newConfig.minDecisionIntervalMs;
            this.config.maxActionAgeMs = newConfig.maxActionAgeMs;
            this.config.requestTimeoutMs = newConfig.requestTimeoutMs;
            this.config.maxSpendUsd = newConfig.maxSpendUsd;
            this.config.fairMode = newConfig.fairMode;
            this.config.allowMultiplayer = newConfig.allowMultiplayer;
            this.config.hudEnabled = newConfig.hudEnabled;
            this.config.hudDebugPage = newConfig.hudDebugPage;
            this.config.logLevel = newConfig.logLevel;
            if (newConfig.confidenceThresholds != null) {
                this.config.confidenceThresholds = new HashMap<>(newConfig.confidenceThresholds);
            }
            this.config.safeDefaultAction = newConfig.safeDefaultAction;
        }
        initProvider();
    }

    public void setDecisionProvider(DecisionProvider provider) {
        this.decisionProvider = provider;
    }

    public DecisionProvider getDecisionProvider() {
        return decisionProvider;
    }

    public void warmUp() {
        if (decisionProvider != null) {
            decisionProvider.warmUp();
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        if (value) {
            if (!config.allowMultiplayer && inputBridge.isRemoteServer()) {
                inputBridge.sendChatMessage("§c[JevPlayer] Refusing to start on remote server! Set allowMultiplayer=true in config to override.");
                return;
            }
            enabled.set(true);
            watchdogEngine.reset();
            currentActionId = "hold";
            currentActionStartTimeMs = System.currentTimeMillis();
            eventLogger.logEvent("engine_started", "Autonomous play activated", episodeDirector.getElapsedSeconds(currentActionStartTimeMs));
            warmUp();
        } else {
            enabled.set(false);
            actionExecutor.stopAllActions();
            inputBridge.releaseAllInputs();
            eventLogger.logEvent("engine_stopped", "Autonomous play stopped", episodeDirector.getElapsedSeconds(System.currentTimeMillis()));
        }
    }

    public void toggleEnabled() {
        setEnabled(!isEnabled());
    }

    public void triggerKillSwitch() {
        enabled.set(false);
        watchdogEngine.setKillSwitch(true);
        actionExecutor.stopAllActions();
        inputBridge.releaseAllInputs();
        eventLogger.logEvent("kill_switch", "Emergency kill switch triggered by user", episodeDirector.getElapsedSeconds(System.currentTimeMillis()));
    }

    /**
     * Main client tick loop. Executed every game tick (20 Hz) on the Minecraft main thread.
     */
    public void tick() {
        long tickStartNs = System.nanoTime();
        long nowMs = System.currentTimeMillis();

        if (!enabled.get()) {
            updateHudIfDue(nowMs, null);
            recordTickTime(tickStartNs);
            return;
        }

        // Safety: ensure multiplayer check remains enforced
        if (!config.allowMultiplayer && inputBridge.isRemoteServer()) {
            triggerKillSwitch();
            recordTickTime(tickStartNs);
            return;
        }

        // 1. Capture snapshot of world
        StateSnapshot state = worldSensor.captureSnapshot(nowMs);
        watchdogEngine.updatePosition(worldSensor.getPlayerX(), worldSensor.getPlayerY(), worldSensor.getPlayerZ(), nowMs);
        watchdogEngine.updateInventory(state, nowMs);

        // 2. Reflex layer (always evaluated first, synchronous, zero-allocation on happy path)
        ReflexResult reflex = reflexEngine.evaluate(state);
        if (reflex.triggered()) {
            applyAction(reflex.actionId(), "reflex: " + reflex.reason(), nowMs);
            recordTickTime(tickStartNs);
            updateHudIfDue(nowMs, state);
            return;
        }

        // 3. Watchdog check (stuck, death, loops)
        WatchdogEvent watchdog = watchdogEngine.tick(state, currentActionId, nowMs);
        if (watchdog.type() == WatchdogEvent.Type.KILL_SWITCH) {
            triggerKillSwitch();
            recordTickTime(tickStartNs);
            return;
        } else if (watchdog.type() == WatchdogEvent.Type.DEATH) {
            eventLogger.logEvent("death", watchdog.reason(), episodeDirector.getElapsedSeconds(nowMs));
            episodeDirector.resetMilestoneProgress(nowMs);
            applyAction(watchdog.fallbackAction(), "watchdog: death recovery", nowMs);
            recordTickTime(tickStartNs);
            updateHudIfDue(nowMs, state);
            return;
        } else if (watchdog.type() == WatchdogEvent.Type.STUCK) {
            eventLogger.logEvent("stuck", watchdog.reason(), episodeDirector.getElapsedSeconds(nowMs));
            applyAction(watchdog.fallbackAction(), "watchdog: stuck recovery", nowMs);
            recordTickTime(tickStartNs);
            updateHudIfDue(nowMs, state);
            return;
        }

        // 4. Update Milestone Director
        episodeDirector.tick(state, nowMs);

        // 5. Check and apply pending decision result from network thread
        DecisionResult freshResult = pendingResult.getAndSet(null);
        if (freshResult != null) {
            requestInFlight.set(false);
            long ageMs = nowMs - freshResult.requestTimestampMs();
            // Discard stale result if age > 1500 ms or state hash changed materially
            if (ageMs <= 1500 && freshResult.stateHash() == state.computeStateHash()) {
                processFreshDecision(freshResult, state, nowMs);
            }
        }

        // 6. Check sticky action expiration or completion
        boolean actionFinished = actionExecutor.isActionFinished(currentActionId, state);
        boolean actionTimedOut = (nowMs - currentActionStartTimeMs) >= config.maxActionAgeMs;

        if (actionFinished || actionTimedOut || "hold".equals(currentActionId)) {
            // Need a new decision
            maybeRequestDecision(state, nowMs);
        }

        recordTickTime(tickStartNs);
        updateHudIfDue(nowMs, state);
    }

    private void processFreshDecision(DecisionResult result, StateSnapshot state, long nowMs) {
        latestAppliedResult = result;
        totalDecisionsCount++;

        String chosen = result.getChosenActionId();
        double confidence = result.getConfidence();
        double threshold = config.getConfidenceThreshold(chosen);

        boolean gated = false;
        String effectiveAction = chosen;

        // Confidence Gating
        if (confidence < threshold) {
            gated = true;
            // Below threshold: keep current action if legal, else pick safe default
            if (actionCatalog.get(currentActionId) != null && actionCatalog.get(currentActionId).isLegal(state)) {
                effectiveAction = currentActionId;
            } else {
                effectiveAction = config.safeDefaultAction;
            }
        }

        // Verify action is legal and not blocked by watchdog
        if (watchdogEngine.isActionBlocked(effectiveAction, nowMs) || !actionCatalog.get(effectiveAction).isLegal(state)) {
            effectiveAction = config.safeDefaultAction;
        }

        applyAction(effectiveAction, (gated ? "gated: " : "decided: ") + chosen, nowMs);

        eventLogger.logEvent("decision",
                String.format("action=%s, conf=%.2f, gated=%b, lat=%dms", chosen, confidence, gated, result.latencyMs()),
                episodeDirector.getElapsedSeconds(nowMs));
    }

    private void maybeRequestDecision(StateSnapshot state, long nowMs) {
        if (requestInFlight.get()) {
            // Check if current in-flight request has timed out
            if (nowMs - inFlightStartTimeMs.get() > config.requestTimeoutMs) {
                requestInFlight.set(false);
            } else {
                return; // At most one request in flight
            }
        }

        // Floor interval check
        if (nowMs - lastDecisionTimeMs < config.minDecisionIntervalMs) {
            return;
        }

        Map<String, String> criteria = actionCatalog.getLegalCriteria(state);
        // Filter out actions blocked by watchdog
        criteria.entrySet().removeIf(e -> watchdogEngine.isActionBlocked(e.getKey(), nowMs));
        if (criteria.isEmpty()) {
            criteria = Map.of("hold", "Wait");
        }

        // If only 1 legal action, skip API call and execute immediately
        if (criteria.size() == 1) {
            String onlyAction = criteria.keySet().iterator().next();
            applyAction(onlyAction, "sole_legal_action", nowMs);
            lastDecisionTimeMs = nowMs;
            return;
        }

        long reqId = totalDecisionsCount + 1;
        long hash = state.computeStateHash();
        DecisionRequest request = new DecisionRequest(reqId, nowMs, hash, state, criteria);

        requestInFlight.set(true);
        inFlightStartTimeMs.set(nowMs);
        lastDecisionTimeMs = nowMs;
        lastDecisionStateHash = hash;

        // Dispatch asynchronously to network thread
        networkExecutor.submit(() -> {
            try {
                DecisionResult res = decisionProvider.decide(request);
                pendingResult.set(res);
            } catch (Exception e) {
                requestInFlight.set(false);
            }
        });
    }

    private void applyAction(String actionId, String reason, long nowMs) {
        if (!Objects.equals(currentActionId, actionId)) {
            // Record outcome of previous action
            if (currentActionId != null && !"hold".equals(currentActionId)) {
                recentHistory.addFirst(new ActionHistoryEntry(currentActionId, "ok", nowMs - currentActionStartTimeMs));
                if (recentHistory.size() > 5) recentHistory.removeLast();
            }

            actionExecutor.stopAction(currentActionId);
            boolean started = actionExecutor.startAction(actionId, worldSensor.captureSnapshot(nowMs));
            currentActionId = actionId;
            currentActionStartTimeMs = nowMs;
            watchdogEngine.recordActionOutcome(actionId, started, nowMs);
        }
    }

    private void recordTickTime(long tickStartNs) {
        double durationMs = (System.nanoTime() - tickStartNs) / 1_000_000.0;
        lastTickMs = durationMs;
        tickSumMs += durationMs;
        tickCount++;
        if (durationMs > maxTickMs) {
            maxTickMs = durationMs;
        }
    }

    private void updateHudIfDue(long nowMs, StateSnapshot state) {
        if (nowMs - lastHudUpdateTimeMs < 250) { // 4 Hz update rate
            return;
        }
        lastHudUpdateTimeMs = nowMs;

        List<HudState.OptionBar> options = new ArrayList<>();
        double confidence = 0.0;
        boolean gated = false;
        String chosen = currentActionId;

        if (latestAppliedResult != null) {
            confidence = latestAppliedResult.getConfidence();
            gated = latestAppliedResult.gated();
            Map<String, Double> probs = latestAppliedResult.getProbabilities();
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(probs.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                var entry = sorted.get(i);
                options.add(new HudState.OptionBar(entry.getKey(), entry.getValue(), entry.getKey().equals(chosen)));
            }
        }

        var currentMilestone = episodeDirector.getCurrentMilestone();
        double avg = tickCount > 0 ? (tickSumMs / tickCount) : 0.0;

        HudState hud = new HudState(
                enabled.get() && config.hudEnabled,
                config.hudDebugPage,
                chosen,
                confidence,
                gated,
                currentMilestone.name(),
                currentMilestone.requirementsDescription(),
                episodeDirector.getElapsedSeconds(nowMs),
                totalDecisionsCount,
                decisionProvider != null ? decisionProvider.getRollingP50LatencyMs() : 0,
                decisionProvider != null ? decisionProvider.getTotalSpendUsd() : 0.0,
                decisionProvider != null && decisionProvider.isSpendCapReached(),
                decisionProvider != null ? decisionProvider.getName() : "none",
                options,
                lastTickMs,
                avg,
                maxTickMs,
                decisionProvider != null ? decisionProvider.getErrorRate() : 0.0,
                requestInFlight.get() ? (nowMs - inFlightStartTimeMs.get()) : 0
        );

        hudRenderer.updateHudState(hud);
    }

    public List<ActionHistoryEntry> getRecentHistory() {
        return Collections.unmodifiableList(recentHistory);
    }

    public EpisodeDirector getEpisodeDirector() {
        return episodeDirector;
    }

    public JevPlayerConfig getConfig() {
        return config;
    }

    @Override
    public void close() {
        setEnabled(false);
        networkExecutor.shutdownNow();
        if (eventLogger != null) {
            eventLogger.close();
        }
        if (decisionProvider != null) {
            try {
                decisionProvider.close();
            } catch (Exception ignored) {}
        }
    }
}
