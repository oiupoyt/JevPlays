package com.typesafe.jevplayer.core.watchdog;

public record WatchdogEvent(
        Type type,
        String reason,
        String fallbackAction
) {
    public enum Type {
        NONE,
        STUCK,
        LOOP_BROKEN,
        DEATH,
        KILL_SWITCH
    }

    public static final WatchdogEvent NONE_EVENT = new WatchdogEvent(Type.NONE, null, null);

    public static WatchdogEvent stuck(String reason, String fallbackAction) {
        return new WatchdogEvent(Type.STUCK, reason, fallbackAction);
    }

    public static WatchdogEvent loopBroken(String actionId, String fallbackAction) {
        return new WatchdogEvent(Type.LOOP_BROKEN, "Action '" + actionId + "' failed 3 times consecutively", fallbackAction);
    }

    public static WatchdogEvent death(int deathCount) {
        return new WatchdogEvent(Type.DEATH, "Player died (death count: " + deathCount + ")", "explore");
    }

    public static WatchdogEvent killSwitch() {
        return new WatchdogEvent(Type.KILL_SWITCH, "Kill switch activated by user", "hold");
    }
}
