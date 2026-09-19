package com.typesafe.jevplayer.core.state;

public record TimeInfo(
        boolean isDay,
        long ticksToSunset,
        boolean isRaining,
        boolean isThundering,
        long dayCount
) {
    public static TimeInfo defaultTime() {
        return new TimeInfo(true, 12000, false, false, 1);
    }
}
