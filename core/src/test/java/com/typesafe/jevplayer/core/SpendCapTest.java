package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import com.typesafe.jevplayer.core.decision.DecisionRequest;
import com.typesafe.jevplayer.core.decision.DecisionResult;
import com.typesafe.jevplayer.core.decision.TypeSafeProvider;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpendCapTest {

    @Test
    public void testSpendCapFallbackWhenExceeded() {
        JevPlayerConfig config = new JevPlayerConfig();
        // Set an extremely low spend cap so it triggers immediately
        config.maxSpendUsd = 0.0000001;
        config.apiKey = "dummy_key_for_test";

        TypeSafeProvider provider = new TypeSafeProvider(config);
        DecisionRequest req = new DecisionRequest(1, 1000, 42, StateSnapshot.initial(1000), Map.of("explore", "Wander"));

        DecisionResult result = provider.decide(req);

        // When spend cap is exceeded, provider must fallback to mock heuristics
        assertTrue(provider.isSpendCapReached(), "Spend cap should be reached");
        assertNotNull(result.getChosenActionId());
    }
}
