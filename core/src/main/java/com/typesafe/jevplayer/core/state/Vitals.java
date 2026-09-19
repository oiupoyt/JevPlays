package com.typesafe.jevplayer.core.state;

public record Vitals(
        float health,
        float maxHealth,
        int hunger,
        boolean hasSaturation,
        int air,
        boolean onFire,
        boolean falling,
        boolean inLava,
        boolean inWater
) {
    public static Vitals defaultVitals() {
        return new Vitals(20.0f, 20.0f, 20, true, 300, false, false, false, false);
    }
}
