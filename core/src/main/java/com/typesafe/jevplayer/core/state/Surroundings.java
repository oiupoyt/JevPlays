package com.typesafe.jevplayer.core.state;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record Surroundings(
        String biome,
        int yLevel,
        boolean isUnderground,
        int lightLevel,
        List<EntitySummary> nearestHostiles, // at most 5
        EntitySummary nearestPassive,
        double nearestWaterDistance,
        double nearestLavaDistance,
        Map<String, Integer> visibleResourceBlocks // e.g. "oak_log": 4, "coal_ore": 2, "iron_ore": 1
) {
    public static Surroundings defaultSurroundings() {
        return new Surroundings(
                "plains", 64, false, 15,
                Collections.emptyList(), null, -1.0, -1.0,
                Collections.emptyMap()
        );
    }
}
