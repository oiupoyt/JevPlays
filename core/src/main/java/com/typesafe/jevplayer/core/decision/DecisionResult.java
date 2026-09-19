package com.typesafe.jevplayer.core.decision;

import java.util.Map;

public record DecisionResult(
        long requestId,
        long requestTimestampMs,
        long stateHash,
        ChoiceAnswer actionChoice,
        NoulAnswer inDanger,
        NoulAnswer progressOk,
        ScoreAnswer urgency,
        long latencyMs,
        boolean gated,
        String originalActionBeforeGating,
        String providerName,
        double estimatedCostUsd,
        int inputTokens,
        boolean fallback
) {
    public String getChosenActionId() {
        return actionChoice != null ? actionChoice.choice() : "hold";
    }

    public double getConfidence() {
        return actionChoice != null ? actionChoice.confidence() : 0.0;
    }

    public Map<String, Double> getProbabilities() {
        return actionChoice != null ? actionChoice.probabilities() : Map.of();
    }
}
