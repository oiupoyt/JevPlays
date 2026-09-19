package com.typesafe.jevplayer.core.state;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StateSnapshot(
        long timestampMs,
        Vitals vitals,
        TimeInfo time,
        InventorySummary inventory,
        Surroundings surroundings,
        GoalInfo goal,
        List<ActionHistoryEntry> recentHistory
) {
    public StateSnapshot {
        if (vitals == null) vitals = Vitals.defaultVitals();
        if (time == null) time = TimeInfo.defaultTime();
        if (inventory == null) inventory = InventorySummary.empty();
        if (surroundings == null) surroundings = Surroundings.defaultSurroundings();
        if (goal == null) goal = GoalInfo.defaultGoal();
        if (recentHistory == null) recentHistory = Collections.emptyList();
    }

    public static StateSnapshot initial(long nowMs) {
        return new StateSnapshot(
                nowMs,
                Vitals.defaultVitals(),
                TimeInfo.defaultTime(),
                InventorySummary.empty(),
                Surroundings.defaultSurroundings(),
                GoalInfo.defaultGoal(),
                Collections.emptyList()
        );
    }

    /**
     * Formats state into compact, stable key=value lines.
     * Reuses the provided StringBuilder to eliminate per-tick allocations.
     */
    public void appendPromptText(StringBuilder sb) {
        sb.append("[vitals]\n");
        sb.append("health=").append(String.format("%.1f/%.1f", vitals.health(), vitals.maxHealth())).append('\n');
        sb.append("hunger=").append(vitals.hunger()).append('\n');
        sb.append("has_saturation=").append(vitals.hasSaturation()).append('\n');
        sb.append("air=").append(vitals.air()).append('\n');
        sb.append("on_fire=").append(vitals.onFire()).append('\n');
        sb.append("falling=").append(vitals.falling()).append('\n');
        sb.append("in_lava=").append(vitals.inLava()).append('\n');
        sb.append("in_water=").append(vitals.inWater()).append('\n');

        sb.append("\n[time]\n");
        sb.append("is_day=").append(time.isDay()).append('\n');
        sb.append("ticks_to_sunset=").append(time.ticksToSunset()).append('\n');
        sb.append("weather=").append(time.isThundering() ? "thunder" : (time.isRaining() ? "rain" : "clear")).append('\n');

        sb.append("\n[inventory]\n");
        sb.append("logs=").append(inventory.logs()).append('\n');
        sb.append("planks=").append(inventory.planks()).append('\n');
        sb.append("sticks=").append(inventory.sticks()).append('\n');
        sb.append("cobblestone=").append(inventory.cobblestone()).append('\n');
        sb.append("coal=").append(inventory.coal()).append('\n');
        sb.append("raw_iron=").append(inventory.rawIron()).append('\n');
        sb.append("iron_ingots=").append(inventory.ironIngots()).append('\n');
        sb.append("food=").append(inventory.foodCount()).append('\n');
        sb.append("torches=").append(inventory.torches()).append('\n');
        sb.append("beds=").append(inventory.beds()).append('\n');
        sb.append("dirt=").append(inventory.dirt()).append('\n');
        sb.append("free_slots=").append(inventory.freeSlots()).append('\n');
        sb.append("pickaxe=").append(inventory.bestPickaxeTier()).append('\n');
        sb.append("axe=").append(inventory.bestAxeTier()).append('\n');
        sb.append("sword=").append(inventory.bestSwordTier()).append('\n');
        if (!inventory.durabilityWarnings().isEmpty()) {
            sb.append("durability_warnings=").append(String.join(",", inventory.durabilityWarnings())).append('\n');
        }

        sb.append("\n[surroundings]\n");
        sb.append("biome=").append(surroundings.biome()).append('\n');
        sb.append("y_level=").append(surroundings.yLevel()).append('\n');
        sb.append("is_underground=").append(surroundings.isUnderground()).append('\n');
        sb.append("light_level=").append(surroundings.lightLevel()).append('\n');
        if (!surroundings.nearestHostiles().isEmpty()) {
            sb.append("nearest_hostiles=");
            for (int i = 0; i < surroundings.nearestHostiles().size(); i++) {
                if (i > 0) sb.append(';');
                sb.append(surroundings.nearestHostiles().get(i).toCompactString());
            }
            sb.append('\n');
        } else {
            sb.append("nearest_hostiles=none\n");
        }
        if (surroundings.nearestPassive() != null) {
            sb.append("nearest_passive=").append(surroundings.nearestPassive().toCompactString()).append('\n');
        } else {
            sb.append("nearest_passive=none\n");
        }
        if (surroundings.nearestWaterDistance() >= 0) {
            sb.append("water_dist=").append(String.format("%.1f", surroundings.nearestWaterDistance())).append('\n');
        }
        if (surroundings.nearestLavaDistance() >= 0) {
            sb.append("lava_dist=").append(String.format("%.1f", surroundings.nearestLavaDistance())).append('\n');
        }
        if (!surroundings.visibleResourceBlocks().isEmpty()) {
            sb.append("visible_resources=");
            int count = 0;
            for (Map.Entry<String, Integer> e : surroundings.visibleResourceBlocks().entrySet()) {
                if (count++ > 0) sb.append(';');
                sb.append(e.getKey()).append(':').append(e.getValue());
            }
            sb.append('\n');
        }

        sb.append("\n[goal]\n");
        sb.append("milestone=").append(goal.milestoneId()).append('\n');
        sb.append("unmet=").append(goal.unmetRequirements()).append('\n');

        sb.append("\n[recent_history]\n");
        if (recentHistory.isEmpty()) {
            sb.append("none\n");
        } else {
            for (int i = 0; i < recentHistory.size(); i++) {
                if (i > 0) sb.append(';');
                sb.append(recentHistory.get(i).toCompactString());
            }
            sb.append('\n');
        }
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder(512);
        appendPromptText(sb);
        return sb.toString();
    }

    public int estimateTokenCount() {
        // Conservative heuristic: ~4 characters per token
        return Math.max(1, toPromptText().length() / 4);
    }

    /**
     * Stable fast hash for detecting material state changes.
     */
    public long computeStateHash() {
        long hash = 17;
        hash = 31 * hash + Math.round(vitals.health());
        hash = 31 * hash + vitals.hunger();
        hash = 31 * hash + (vitals.onFire() ? 1 : 0);
        hash = 31 * hash + (vitals.inLava() ? 1 : 0);
        hash = 31 * hash + (time.isDay() ? 1 : 0);
        hash = 31 * hash + inventory.logs();
        hash = 31 * hash + inventory.planks();
        hash = 31 * hash + inventory.cobblestone();
        hash = 31 * hash + inventory.coal();
        hash = 31 * hash + inventory.rawIron();
        hash = 31 * hash + inventory.ironIngots();
        hash = 31 * hash + inventory.foodCount();
        hash = 31 * hash + surroundings.yLevel();
        hash = 31 * hash + (surroundings.isUnderground() ? 1 : 0);
        hash = 31 * hash + (surroundings.nearestHostiles().isEmpty() ? 0 : 1);
        if (!surroundings.nearestHostiles().isEmpty()) {
            hash = 31 * hash + (long) (surroundings.nearestHostiles().get(0).distance() * 10);
        }
        hash = 31 * hash + goal.milestoneId().hashCode();
        return hash;
    }
}
