package com.typesafe.jevplayer.core.decision;

import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.Map;

public record DecisionRequest(
        long requestId,
        long timestampMs,
        long stateHash,
        StateSnapshot state,
        Map<String, String> legalActionCriteria
) {
}
