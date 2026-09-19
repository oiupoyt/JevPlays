package com.typesafe.jevplayer.core.state;

public record EntitySummary(
        String type,
        double distance,
        String direction, // "N", "NE", "E", "SE", "S", "SW", "W", "NW", "UP", "DOWN"
        boolean hostile,
        boolean inLineOfSight,
        boolean inFov
) {
    public String toCompactString() {
        return String.format("%s:%.1fm(%s)", type, distance, direction);
    }
}
