package com.typesafe.jevplayer.core.hud;

import java.util.List;

public record HudState(
        boolean active,
        boolean debugPage,
        String chosenAction,
        double confidence,
        boolean gated,
        String milestoneName,
        String milestoneRequirements,
        long elapsedSeconds,
        long decisionsCount,
        long latencyP50Ms,
        double totalSpendUsd,
        boolean spendCapReached,
        String providerName,
        List<OptionBar> topOptions,
        // Debug metrics
        double lastTickMs,
        double avgTickMs,
        double maxTickMs,
        double errorRate,
        long inFlightAgeMs
) {
    public record OptionBar(
            String actionId,
            double probability,
            boolean isChosen
    ) {}

    public static HudState empty() {
        return new HudState(
                false, false, "idle", 0.0, false, "None", "None",
                0, 0, 0, 0.0, false, "mock", List.of(),
                0.0, 0.0, 0.0, 0.0, 0
        );
    }
}
