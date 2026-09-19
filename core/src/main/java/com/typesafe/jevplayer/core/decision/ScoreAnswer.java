package com.typesafe.jevplayer.core.decision;

import java.util.Map;

public record ScoreAnswer(
        int score,
        Map<String, Double> probabilities,
        double confidence
) {
    public static ScoreAnswer of(int score) {
        return new ScoreAnswer(score, null, 0.85);
    }
}
