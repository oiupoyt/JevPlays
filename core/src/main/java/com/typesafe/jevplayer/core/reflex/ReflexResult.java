package com.typesafe.jevplayer.core.reflex;

public record ReflexResult(
        boolean triggered,
        String actionId,
        String reason
) {
    public static final ReflexResult NONE = new ReflexResult(false, null, null);

    public static ReflexResult fire(String actionId, String reason) {
        return new ReflexResult(true, actionId, reason);
    }
}
