package com.typesafe.jevplayer.core.decision;

public record NoulAnswer(
        double noul,
        double confidence
) {
    public static NoulAnswer of(double probability) {
        return new NoulAnswer(probability, 0.85);
    }

    public boolean isAffirmative() {
        return noul >= 0.50;
    }
}
