package com.typesafe.jevplayer.core.decision;

import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.*;

public final class MockProvider implements DecisionProvider {
    private final LatencyTracker latencyTracker = new LatencyTracker();

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public void warmUp() {
        latencyTracker.recordLatency(12);
    }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        long startTime = System.currentTimeMillis();
        StateSnapshot state = request.state();
        Map<String, String> legal = request.legalActionCriteria();

        // 1. Evaluate danger (noul)
        boolean hasThreat = !state.surroundings().nearestHostiles().isEmpty()
                && state.surroundings().nearestHostiles().get(0).distance() <= 6.0;
        double dangerProb = hasThreat ? 0.85 : (state.vitals().health() <= 8.0f ? 0.65 : 0.05);
        NoulAnswer inDanger = new NoulAnswer(dangerProb, 0.90);

        // 2. Evaluate progress_ok (noul)
        double progressProb = state.recentHistory().stream().noneMatch(h -> "failed".equals(h.outcome())) ? 0.85 : 0.40;
        NoulAnswer progressOk = new NoulAnswer(progressProb, 0.80);

        // 3. Evaluate urgency (score: 0 = Idle, 1 = Soon, 2 = Now)
        int urgencyScore = hasThreat || state.vitals().inLava() ? 2 : (!state.time().isDay() ? 1 : 0);
        ScoreAnswer urgency = ScoreAnswer.of(urgencyScore);

        // 4. Select best legal action via heuristic priority
        String candidate = pickHeuristicAction(state, legal);

        // Build realistic probabilities for legal choices
        Map<String, Double> probabilities = new LinkedHashMap<>();
        double remainingProb = 1.0;
        double chosenProb = 0.75;
        probabilities.put(candidate, chosenProb);
        remainingProb -= chosenProb;

        List<String> otherKeys = new ArrayList<>(legal.keySet());
        otherKeys.remove(candidate);
        if (!otherKeys.isEmpty()) {
            double split = remainingProb / otherKeys.size();
            for (String key : otherKeys) {
                probabilities.put(key, Math.round(split * 100.0) / 100.0);
            }
        }

        double confidence = hasThreat ? 0.92 : 0.84;
        ChoiceAnswer choice = new ChoiceAnswer(candidate, probabilities, confidence);

        long latency = System.currentTimeMillis() - startTime + 8; // simulate realistic fast ~8-15ms local latency
        latencyTracker.recordLatency(latency);

        return new DecisionResult(
                request.requestId(),
                request.timestampMs(),
                request.stateHash(),
                choice,
                inDanger,
                progressOk,
                urgency,
                latency,
                false,
                candidate,
                "mock",
                0.0,
                request.state().estimateTokenCount(),
                false
        );
    }

    private String pickHeuristicAction(StateSnapshot state, Map<String, String> legal) {
        // High priority: immediate survival reflexes handled in reflex layer, but if asked:
        if (state.vitals().health() <= 8.0f && legal.containsKey("flee")) {
            return "flee";
        }
        if (!state.surroundings().nearestHostiles().isEmpty()
                && state.surroundings().nearestHostiles().get(0).distance() <= 5.0
                && legal.containsKey("fight")) {
            return "fight";
        }
        if (state.vitals().hunger() <= 14 && legal.containsKey("eat")) {
            return "eat";
        }
        if (!state.time().isDay()) {
            if (legal.containsKey("sleep")) return "sleep";
            if (legal.containsKey("build_shelter")) return "build_shelter";
        }

        // Milestone progression heuristics
        String milestone = state.goal().milestoneId();
        switch (milestone) {
            case "wood" -> {
                if (state.inventory().logs() < 4 && legal.containsKey("gather_wood")) return "gather_wood";
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
            }
            case "crafting_table" -> {
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
                if (state.inventory().logs() < 4 && legal.containsKey("gather_wood")) return "gather_wood";
            }
            case "wooden_tools" -> {
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
                if (state.inventory().logs() < 3 && legal.containsKey("gather_wood")) return "gather_wood";
            }
            case "stone_tools" -> {
                if (state.inventory().cobblestone() < 8 && legal.containsKey("mine_stone")) return "mine_stone";
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
            }
            case "furnace_food" -> {
                if (state.inventory().cobblestone() < 8 && legal.containsKey("mine_stone")) return "mine_stone";
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
                if (legal.containsKey("smelt")) return "smelt";
            }
            case "coal_torches" -> {
                if (state.inventory().coal() < 4 && legal.containsKey("mine_ore_coal")) return "mine_ore_coal";
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
                if (legal.containsKey("place_torch")) return "place_torch";
            }
            case "shelter_bed" -> {
                if (legal.containsKey("sleep")) return "sleep";
                if (legal.containsKey("build_shelter")) return "build_shelter";
            }
            case "iron" -> {
                if (state.inventory().rawIron() < 3 && legal.containsKey("mine_ore_iron")) return "mine_ore_iron";
                if (legal.containsKey("smelt")) return "smelt";
            }
            case "iron_tools" -> {
                if (state.inventory().ironIngots() < 3 && legal.containsKey("smelt")) return "smelt";
                if (legal.containsKey("craft_essentials")) return "craft_essentials";
            }
        }

        // General opportunistic gathering
        if (state.inventory().logs() < 3 && legal.containsKey("gather_wood")) {
            return "gather_wood";
        }
        if (state.inventory().cobblestone() < 5 && legal.containsKey("mine_stone")) {
            return "mine_stone";
        }
        if (state.surroundings().lightLevel() <= 6 && legal.containsKey("place_torch")) {
            return "place_torch";
        }

        if (legal.containsKey("explore")) return "explore";
        if (legal.containsKey("hold")) return "hold";
        return legal.keySet().iterator().next();
    }

    @Override
    public double getTotalSpendUsd() {
        return 0.0;
    }

    @Override
    public boolean isSpendCapReached() {
        return false;
    }

    @Override
    public long getRollingP50LatencyMs() {
        return latencyTracker.getP50();
    }

    @Override
    public long getRollingP95LatencyMs() {
        return latencyTracker.getP95();
    }

    @Override
    public double getErrorRate() {
        return latencyTracker.getErrorRate();
    }
}
