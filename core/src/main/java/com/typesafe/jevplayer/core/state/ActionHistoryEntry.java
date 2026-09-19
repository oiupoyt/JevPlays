package com.typesafe.jevplayer.core.state;

public record ActionHistoryEntry(
        String actionId,
        String outcome, // "ok", "failed", "interrupted"
        long durationMs
) {
    public String toCompactString() {
        return actionId + ":" + outcome;
    }
}
