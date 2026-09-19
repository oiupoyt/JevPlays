package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.decision.DecisionReplay;
import com.typesafe.jevplayer.core.decision.DecisionRequest;
import com.typesafe.jevplayer.core.decision.DecisionResult;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DecisionReplayTest {

    @Test
    public void testReplayLogFeeding() throws Exception {
        String jsonLines = """
        {"timestampMs":1000,"stateHash":42,"optionsOffered":["gather_wood","explore"],"probabilities":{"gather_wood":0.9,"explore":0.1},"chosenAction":"gather_wood","confidence":0.9,"latencyMs":15,"gated":false}
        {"timestampMs":2000,"stateHash":43,"optionsOffered":["craft_essentials","explore"],"probabilities":{"craft_essentials":0.85,"explore":0.15},"chosenAction":"craft_essentials","confidence":0.85,"latencyMs":18,"gated":false}
        """;

        DecisionReplay replay = DecisionReplay.fromJsonLines(new StringReader(jsonLines));
        assertEquals("replay", replay.getName());
        assertEquals(2, replay.getRemainingCount());

        DecisionRequest req1 = new DecisionRequest(1, 1000, 42, StateSnapshot.initial(1000), Map.of("gather_wood", "Mine", "explore", "Wander"));
        DecisionResult res1 = replay.decide(req1);
        assertEquals("gather_wood", res1.getChosenActionId());
        assertEquals(0.9, res1.getConfidence(), 0.001);

        DecisionRequest req2 = new DecisionRequest(2, 2000, 43, StateSnapshot.initial(2000), Map.of("craft_essentials", "Craft", "explore", "Wander"));
        DecisionResult res2 = replay.decide(req2);
        assertEquals("craft_essentials", res2.getChosenActionId());
        assertEquals(0.85, res2.getConfidence(), 0.001);
    }
}
