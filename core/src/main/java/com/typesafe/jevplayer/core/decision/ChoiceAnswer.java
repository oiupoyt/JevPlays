package com.typesafe.jevplayer.core.decision;

import java.util.Collections;
import java.util.Map;

public record ChoiceAnswer(
        String choice,
        Map<String, Double> probabilities,
        double confidence
) {
    public static ChoiceAnswer of(String choice, double confidence) {
        return new ChoiceAnswer(choice, Collections.singletonMap(choice, 1.0), confidence);
    }
}
