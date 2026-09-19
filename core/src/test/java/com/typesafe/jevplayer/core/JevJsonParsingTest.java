package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import com.typesafe.jevplayer.core.decision.DecisionRequest;
import com.typesafe.jevplayer.core.decision.DecisionResult;
import com.typesafe.jevplayer.core.decision.TypeSafeProvider;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JevJsonParsingTest {

    @Test
    public void testRequestPayloadBuilding() {
        JevPlayerConfig config = new JevPlayerConfig();
        TypeSafeProvider provider = new TypeSafeProvider(config);

        Map<String, String> criteria = Map.of(
                "gather_wood", "Mine trees",
                "explore", "Wander area"
        );

        String json = provider.buildJsonPayload("health=20\nhunger=20", criteria);

        assertTrue(json.contains("\"model\":\"jev-latest\""));
        assertTrue(json.contains("\"type\":\"choice\""));
        assertTrue(json.contains("\"gather_wood\":\"Mine trees\""));
        assertTrue(json.contains("\"type\":\"noul\""));
        assertTrue(json.contains("\"type\":\"score\""));
    }

    @Test
    public void testResponseParsingRecordedFixture() {
        JevPlayerConfig config = new JevPlayerConfig();
        TypeSafeProvider provider = new TypeSafeProvider(config);

        String recordedApiResponse = """
        {
          "model": "jev-latest",
          "answers": {
            "action": {
              "type": "choice",
              "choice": "gather_wood",
              "probabilities": {
                "gather_wood": 0.88,
                "explore": 0.12
              },
              "confidence": 0.89
            },
            "in_danger": {
              "type": "noul",
              "noul": 0.04,
              "confidence": 0.95
            },
            "progress_ok": {
              "type": "noul",
              "noul": 0.91,
              "confidence": 0.90
            },
            "urgency": {
              "type": "score",
              "score": 1,
              "confidence": 0.82
            }
          },
          "usage": {
            "input_tokens": 142,
            "output_tokens": 0
          }
        }
        """;

        DecisionRequest req = new DecisionRequest(1, 1000, 42, StateSnapshot.initial(1000), Map.of("gather_wood", "Mine"));
        DecisionResult result = provider.parseResponse(req, recordedApiResponse, 120, 142);

        assertNotNull(result);
        assertEquals("gather_wood", result.getChosenActionId());
        assertEquals(0.89, result.getConfidence(), 0.001);
        assertEquals(0.88, result.getProbabilities().get("gather_wood"), 0.001);

        assertNotNull(result.inDanger());
        assertEquals(0.04, result.inDanger().noul(), 0.001);
        assertFalse(result.inDanger().isAffirmative());

        assertNotNull(result.progressOk());
        assertEquals(0.91, result.progressOk().noul(), 0.001);
        assertTrue(result.progressOk().isAffirmative());

        assertNotNull(result.urgency());
        assertEquals(1, result.urgency().score());

        assertEquals(142, result.inputTokens());
        assertTrue(result.estimatedCostUsd() > 0.0);
        assertFalse(result.fallback());
    }
}
