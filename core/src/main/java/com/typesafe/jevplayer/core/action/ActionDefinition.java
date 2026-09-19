package com.typesafe.jevplayer.core.action;

import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.function.Predicate;

public final class ActionDefinition {
    private final ActionId actionId;
    private final Predicate<StateSnapshot> precondition;
    private final Predicate<StateSnapshot> terminationCondition;
    private final long defaultTimeoutMs;

    public ActionDefinition(
            ActionId actionId,
            Predicate<StateSnapshot> precondition,
            Predicate<StateSnapshot> terminationCondition,
            long defaultTimeoutMs
    ) {
        this.actionId = actionId;
        this.precondition = precondition;
        this.terminationCondition = terminationCondition;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public ActionId getActionId() {
        return actionId;
    }

    public String getId() {
        return actionId.getId();
    }

    public String getDescription() {
        return actionId.getDescription();
    }

    public boolean isLegal(StateSnapshot snapshot) {
        return precondition.test(snapshot);
    }

    public boolean isFinished(StateSnapshot snapshot) {
        return terminationCondition.test(snapshot);
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }
}
