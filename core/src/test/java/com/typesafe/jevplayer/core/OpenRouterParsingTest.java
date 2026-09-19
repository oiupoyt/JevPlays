package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import com.typesafe.jevplayer.core.decision.DecisionRequest;
import com.typesafe.jevplayer.core.decision.DecisionResult;
import com.typesafe.jevplayer.core.decision.OpenRouterProvider;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class OpenRouterParsingTest {

    @Test
    public void testOpenRouterPayloadBuilding() {
        JevPlayerConfig config = new JevPlayerConfig();
        config.provider = "openrouter";
        config.apiKey = "sk-or-v1-testkey";
        config.model = "google/gemini-2.5-flash";

        OpenRouterProvider provider = new OpenRouterProvider(config);
        Map<String, String> criteria = Map.of(
                "gather_wood", "Chop trees for logs",
                "explore", "Look around"
        );

        String payload = provider.buildChatCompletionPayload("health=20\nhunger=20", criteria);

        assertTrue(payload.contains("\"model\":\"google/gemini-2.5-flash\""));
        assertTrue(payload.contains("\"type\":\"json_object\""));
        assertTrue(payload.contains("gather_wood: Chop trees for logs"));
        assertTrue(payload.contains("LEGAL ACTIONS"));
    }

    @Test
    public void testOpenRouterResponseParsingClean() {
        JevPlayerConfig config = new JevPlayerConfig();
        config.provider = "openrouter";
        config.apiKey = "sk-or-v1-testkey";

        OpenRouterProvider provider = new OpenRouterProvider(config);

        String openRouterResponse = """
        {
          "id": "gen-12345",
          "model": "google/gemini-2.5-flash",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "{\\"action\\": \\"gather_wood\\", \\"confidence\\": 0.92, \\"in_danger\\": 0.0, \\"progress_ok\\": 0.85, \\"urgency\\": 0, \\"reasoning\\": \\"Need wood for tools\\"}"
              }
            }
          ],
          "usage": {
            "prompt_tokens": 120,
            "completion_tokens": 35,
            "total_tokens": 155,
            "cost": 0.000015
          }
        }
        """;

        DecisionRequest req = new DecisionRequest(1, 1000, 42, StateSnapshot.initial(1000), Map.of("gather_wood", "Chop trees"));
        DecisionResult result = provider.parseResponse(req, openRouterResponse, 250, 120);

        assertNotNull(result);
        assertEquals("gather_wood", result.getChosenActionId());
        assertEquals(0.92, result.getConfidence(), 0.001);
        assertNotNull(result.inDanger());
        assertEquals(0.0, result.inDanger().noul(), 0.001);
        assertFalse(result.inDanger().isAffirmative());
        assertNotNull(result.progressOk());
        assertEquals(0.85, result.progressOk().noul(), 0.001);
        assertTrue(result.progressOk().isAffirmative());
        assertNotNull(result.urgency());
        assertEquals(0, result.urgency().score());
        assertEquals("openrouter", result.providerName());
        assertEquals(120, result.inputTokens());
        assertEquals(0.000015, result.estimatedCostUsd(), 0.000001);
        assertFalse(result.fallback());
    }

    @Test
    public void testOpenRouterResponseParsingWithMarkdownFences() {
        JevPlayerConfig config = new JevPlayerConfig();
        OpenRouterProvider provider = new OpenRouterProvider(config);

        String markdownResponse = """
        {
          "id": "gen-67890",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "```json\\n{\\"action\\": \\"explore\\", \\"confidence\\": 0.88, \\"in_danger\\": false, \\"progress_ok\\": true, \\"urgency\\": 1}\\n```"
              }
            }
          ]
        }
        """;

        DecisionRequest req = new DecisionRequest(2, 2000, 84, StateSnapshot.initial(2000), Map.of("explore", "Wander"));
        DecisionResult result = provider.parseResponse(req, markdownResponse, 180, 100);

        assertNotNull(result);
        assertEquals("explore", result.getChosenActionId());
        assertEquals(0.88, result.getConfidence(), 0.001);
        assertEquals(0.1, result.inDanger().noul(), 0.001);
        assertEquals(0.9, result.progressOk().noul(), 0.001);
        assertEquals(1, result.urgency().score());
    }

    @Test
    public void testEffectiveEndpoint() {
        JevPlayerConfig config = new JevPlayerConfig();
        config.baseUrl = "https://openrouter.ai/api/v1";
        OpenRouterProvider p1 = new OpenRouterProvider(config);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", p1.getEffectiveEndpoint());

        config.baseUrl = "https://openrouter.ai/api/";
        OpenRouterProvider p2 = new OpenRouterProvider(config);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", p2.getEffectiveEndpoint());

        config.baseUrl = "https://openrouter.ai/api/v1/chat/completions";
        OpenRouterProvider p3 = new OpenRouterProvider(config);
        assertEquals("https://openrouter.ai/api/v1/chat/completions", p3.getEffectiveEndpoint());
    }
}
