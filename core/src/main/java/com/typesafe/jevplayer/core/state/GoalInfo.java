package com.typesafe.jevplayer.core.state;

public record GoalInfo(
        String milestoneId,
        String unmetRequirements,
        long softTimeBudgetSeconds,
        long elapsedMilestoneSeconds
) {
    public static GoalInfo defaultGoal() {
        return new GoalInfo("wood", "needs logs >= 4", 300, 0);
    }
}
