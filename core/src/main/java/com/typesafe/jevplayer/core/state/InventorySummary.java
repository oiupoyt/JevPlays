package com.typesafe.jevplayer.core.state;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record InventorySummary(
        int logs,
        int planks,
        int sticks,
        int cobblestone,
        int coal,
        int rawIron,
        int ironIngots,
        int foodCount,
        int torches,
        int beds,
        int dirt,
        int freeSlots,
        String bestPickaxeTier, // "none", "wood", "stone", "iron", "diamond"
        String bestAxeTier,
        String bestSwordTier,
        List<String> durabilityWarnings
) {
    public static InventorySummary empty() {
        return new InventorySummary(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                36, "none", "none", "none", Collections.emptyList()
        );
    }
}
