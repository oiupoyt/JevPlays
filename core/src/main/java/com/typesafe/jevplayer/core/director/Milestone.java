package com.typesafe.jevplayer.core.director;

import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.Set;
import java.util.function.Predicate;

public record Milestone(
        String id,
        String name,
        String requirementsDescription,
        Predicate<StateSnapshot> completionCheck,
        Set<String> allowedActionIds,
        long softBudgetSeconds
) {
    public boolean isComplete(StateSnapshot state) {
        return completionCheck.test(state);
    }
}
