package com.typesafe.jevplayer.core.director;

import com.typesafe.jevplayer.core.state.GoalInfo;
import com.typesafe.jevplayer.core.state.StateSnapshot;

public final class EpisodeDirector {
    private final MilestoneLadder ladder;
    private final EpisodeEventLogger eventLogger;
    private final long episodeDurationSeconds;
    private final long startTimeMs;

    private int currentMilestoneIndex = 0;
    private long milestoneStartTimeMs;
    private boolean wasNight = false;

    public EpisodeDirector(MilestoneLadder ladder, EpisodeEventLogger eventLogger, int episodeMinutes) {
        this.ladder = ladder;
        this.eventLogger = eventLogger;
        this.episodeDurationSeconds = (long) episodeMinutes * 60;
        this.startTimeMs = System.currentTimeMillis();
        this.milestoneStartTimeMs = startTimeMs;
    }

    public void tick(StateSnapshot state, long nowMs) {
        // 1. Night survived check
        boolean isDay = state.time().isDay();
        if (wasNight && isDay) {
            long elapsedSec = getElapsedSeconds(nowMs);
            if (eventLogger != null) {
                eventLogger.logEvent("night_survived", "Survived night at day " + state.time().dayCount(), elapsedSec);
            }
        }
        wasNight = !isDay;

        // 2. Check milestone completion
        if (currentMilestoneIndex < ladder.size()) {
            Milestone current = ladder.getByIndex(currentMilestoneIndex);
            if (current.isComplete(state)) {
                long elapsedSec = getElapsedSeconds(nowMs);
                if (eventLogger != null) {
                    eventLogger.logEvent("milestone_reached", "Completed milestone: " + current.name(), elapsedSec);
                }
                currentMilestoneIndex++;
                milestoneStartTimeMs = nowMs;
            }
        }
    }

    public Milestone getCurrentMilestone() {
        return ladder.getByIndex(currentMilestoneIndex);
    }

    public GoalInfo createGoalInfo(long nowMs) {
        Milestone current = getCurrentMilestone();
        long elapsedSec = Math.max(0, (nowMs - milestoneStartTimeMs) / 1000);
        return new GoalInfo(current.id(), current.requirementsDescription(), current.softBudgetSeconds(), elapsedSec);
    }

    public long getElapsedSeconds(long nowMs) {
        return Math.max(0, (nowMs - startTimeMs) / 1000);
    }

    public long getRemainingSeconds(long nowMs) {
        return Math.max(0, episodeDurationSeconds - getElapsedSeconds(nowMs));
    }

    public boolean isWrapUpPhase(long nowMs) {
        // Last 3 minutes of episode
        return getRemainingSeconds(nowMs) <= 180;
    }

    public boolean isEpisodeComplete(long nowMs) {
        return getElapsedSeconds(nowMs) >= episodeDurationSeconds;
    }

    public int getCurrentMilestoneIndex() {
        return currentMilestoneIndex;
    }

    public void resetMilestoneProgress(long nowMs) {
        milestoneStartTimeMs = nowMs;
    }
}
