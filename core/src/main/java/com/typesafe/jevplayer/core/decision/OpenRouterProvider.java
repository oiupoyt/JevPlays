package com.typesafe.jevplayer.core.decision;

import com.google.gson.*;
import com.typesafe.jevplayer.core.config.JevPlayerConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class OpenRouterProvider implements DecisionProvider {
    private static final Gson GSON = new Gson();

    private final JevPlayerConfig config;
    private final String apiKey;
    private final HttpClient httpClient;
    private final MockProvider fallbackProvider = new MockProvider();
    private final LatencyTracker latencyTracker = new LatencyTracker();

    private double totalSpendUsd = 0.0;
    private boolean spendCapReached = false;
    private volatile boolean warmedUp = false;

    public OpenRouterProvider(JevPlayerConfig config) {
        this.config = config;
        this.apiKey = config.resolveApiKey();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(Math.max(config.requestTimeoutMs, 3000)))
                .build();
    }

    @Override
    public String getName() {
        return "openrouter";
    }

    public String getEffectiveEndpoint() {
        String base = config.baseUrl != null && !config.baseUrl.isBlank()
                ? config.baseUrl.trim()
                : "https://openrouter.ai/api/v1";
        base = base.replaceAll("/+$", "");
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (!base.endsWith("/v1")) {
            base += "/v1";
        }
        return base + "/chat/completions";
    }

    public String getEffectiveModel() {
        if (config.model != null && !config.model.isBlank() && !config.model.equalsIgnoreCase("jev-latest")) {
            return config.model.trim();
        }
        return "google/gemini-2.5-flash";
    }

    @Override
    public void warmUp() {
        if (warmedUp || apiKey.isEmpty()) return;
        warmedUp = true;
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(getEffectiveEndpoint()))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("HTTP-Referer", "https://github.com/oiupoyt/JevPlays")
                        .header("X-Title", "JevPlayer")
                        .header("User-Agent", "JevPlayer/1.0.0")
                        .timeout(Duration.ofMillis(Math.max(config.requestTimeoutMs, 3000)))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"model\":\"" + getEffectiveModel() + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":1}",
                                StandardCharsets.UTF_8))
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Warm up is best-effort
            }
        });
    }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        if (apiKey.isEmpty() || isSpendCapReached()) {
            DecisionResult mockResult = fallbackProvider.decide(request);
            return new DecisionResult(
                    mockResult.requestId(),
                    mockResult.requestTimestampMs(),
                    mockResult.stateHash(),
                    mockResult.actionChoice(),
                    mockResult.inDanger(),
                    mockResult.progressOk(),
                    mockResult.urgency(),
                    mockResult.latencyMs(),
                    mockResult.gated(),
                    mockResult.originalActionBeforeGating(),
                    mockResult.providerName() + (isSpendCapReached() ? "(spend_cap)" : "(no_key)"),
                    0.0,
                    mockResult.inputTokens(),
                    true
            );
        }

        long startTime = System.currentTimeMillis();
        String payload = buildChatCompletionPayload(request.state().toPromptText(), request.legalActionCriteria());
        int estimatedInputTokens = Math.max(1, payload.length() / 4);

        int maxRetries = 1;
        int attempt = 0;

        while (attempt <= maxRetries) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(getEffectiveEndpoint()))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("HTTP-Referer", "https://github.com/oiupoyt/JevPlays")
                        .header("X-Title", "JevPlayer")
                        .header("User-Agent", "JevPlayer/1.0.0")
                        .timeout(Duration.ofMillis(Math.max(config.requestTimeoutMs, 3000)))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status == 200) {
                    long latency = System.currentTimeMillis() - startTime;
                    latencyTracker.recordLatency(latency);
                    DecisionResult parsed = parseResponse(request, response.body(), latency, estimatedInputTokens);
                    recordSpend(parsed.estimatedCostUsd());
                    return parsed;
                } else if (status == 429 || status >= 500) {
                    if (attempt < maxRetries) {
                        Thread.sleep(200);
                        attempt++;
                        continue;
                    }
                    latencyTracker.recordError();
                    break;
                } else {
                    latencyTracker.recordError();
                    break;
                }
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    attempt++;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    latencyTracker.recordError();
                    break;
                }
            }
        }

        // Fallback on network/API failure
        DecisionResult fallbackResult = fallbackProvider.decide(request);
        return new DecisionResult(
                fallbackResult.requestId(),
                fallbackResult.requestTimestampMs(),
                fallbackResult.stateHash(),
                fallbackResult.actionChoice(),
                fallbackResult.inDanger(),
                fallbackResult.progressOk(),
                fallbackResult.urgency(),
                System.currentTimeMillis() - startTime,
                fallbackResult.gated(),
                fallbackResult.originalActionBeforeGating(),
                "openrouter(fallback)",
                0.0,
                estimatedInputTokens,
                true
        );
    }

    public String buildChatCompletionPayload(String stateText, Map<String, String> criteria) {
        JsonObject root = new JsonObject();
        root.addProperty("model", getEffectiveModel());
        root.addProperty("temperature", 0.2);
        root.addProperty("max_tokens", 150);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        root.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();

        // System message
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content",
                "You are JevPlayer, an autonomous AI player controlling Minecraft. " +
                "Analyze the player's vitals, inventory, surroundings, and active goal, then select the best action from the provided LEGAL ACTIONS list. " +
                "You must respond with valid JSON matching this schema:\n" +
                "{\n" +
                "  \"action\": \"<one of the legal action IDs>\",\n" +
                "  \"confidence\": <float 0.0 to 1.0>,\n" +
                "  \"in_danger\": <float 0.0 to 1.0 (1.0 = immediate death risk, 0.0 = safe)>,\n" +
                "  \"progress_ok\": <float 0.0 to 1.0 (1.0 = progressing towards goal, 0.0 = stuck/idling)>,\n" +
                "  \"urgency\": <integer 0 (idle fine), 1 (soon), or 2 (immediate crisis)>,\n" +
                "  \"reasoning\": \"<short explanation>\"\n" +
                "}"
        );
        messages.add(sysMsg);

        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        StringBuilder userContent = new StringBuilder();
        userContent.append("GAME STATE:\n").append(stateText).append("\n\n");
        userContent.append("LEGAL ACTIONS (choose exactly one):\n");
        for (Map.Entry<String, String> entry : criteria.entrySet()) {
            userContent.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        userContent.append("\nReturn JSON only.");
        userMsg.addProperty("content", userContent.toString());
        messages.add(userMsg);

        root.add("messages", messages);
        return GSON.toJson(root);
    }

    public DecisionResult parseResponse(DecisionRequest request, String responseBody, long latencyMs, int fallbackInputTokens) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

        String content = "";
        if (json.has("choices") && json.get("choices").isJsonArray()) {
            JsonArray choices = json.getAsJsonArray("choices");
            if (!choices.isEmpty() && choices.get(0).isJsonObject()) {
                JsonObject choice0 = choices.get(0).getAsJsonObject();
                if (choice0.has("message") && choice0.get("message").isJsonObject()) {
                    JsonObject msg = choice0.getAsJsonObject("message");
                    if (msg.has("content") && !msg.get("content").isJsonNull()) {
                        content = msg.get("content").getAsString();
                    }
                }
            }
        }

        JsonObject parsed = new JsonObject();
        try {
            parsed = JsonParser.parseString(cleanJsonText(content)).getAsJsonObject();
        } catch (Exception ignored) {
            // Handle malformed JSON
        }

        // 1. Action
        String rawAction = parsed.has("action") && !parsed.get("action").isJsonNull()
                ? parsed.get("action").getAsString().trim().toLowerCase()
                : "hold";

        Map<String, String> legal = request.legalActionCriteria();
        String selectedAction = "hold";
        if (legal.containsKey(rawAction)) {
            selectedAction = rawAction;
        } else {
            for (String key : legal.keySet()) {
                if (key.equalsIgnoreCase(rawAction) || rawAction.contains(key) || key.contains(rawAction)) {
                    selectedAction = key;
                    break;
                }
            }
            if (!legal.containsKey(selectedAction)) {
                selectedAction = legal.containsKey("explore") ? "explore"
                        : (legal.isEmpty() ? "hold" : legal.keySet().iterator().next());
            }
        }

        // Confidence
        double confidence = 0.85;
        if (parsed.has("confidence") && !parsed.get("confidence").isJsonNull()) {
            try {
                confidence = parsed.get("confidence").getAsDouble();
            } catch (Exception ignored) {}
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));

        Map<String, Double> probs = new HashMap<>();
        probs.put(selectedAction, confidence);
        ChoiceAnswer choiceAnswer = new ChoiceAnswer(selectedAction, probs, confidence);

        // 2. In danger
        double dangerVal = 0.0;
        if (parsed.has("in_danger") && !parsed.get("in_danger").isJsonNull()) {
            JsonElement dElem = parsed.get("in_danger");
            if (dElem.isJsonPrimitive() && dElem.getAsJsonPrimitive().isBoolean()) {
                dangerVal = dElem.getAsBoolean() ? 0.9 : 0.1;
            } else {
                try {
                    dangerVal = dElem.getAsDouble();
                } catch (Exception ignored) {}
            }
        }
        dangerVal = Math.max(0.0, Math.min(1.0, dangerVal));
        NoulAnswer inDanger = new NoulAnswer(dangerVal, 0.85);

        // 3. Progress ok
        double progressVal = 0.8;
        if (parsed.has("progress_ok") && !parsed.get("progress_ok").isJsonNull()) {
            JsonElement pElem = parsed.get("progress_ok");
            if (pElem.isJsonPrimitive() && pElem.getAsJsonPrimitive().isBoolean()) {
                progressVal = pElem.getAsBoolean() ? 0.9 : 0.2;
            } else {
                try {
                    progressVal = pElem.getAsDouble();
                } catch (Exception ignored) {}
            }
        }
        progressVal = Math.max(0.0, Math.min(1.0, progressVal));
        NoulAnswer progressOk = new NoulAnswer(progressVal, 0.85);

        // 4. Urgency
        int urgencyVal = 0;
        if (parsed.has("urgency") && !parsed.get("urgency").isJsonNull()) {
            try {
                urgencyVal = parsed.get("urgency").getAsInt();
            } catch (Exception ignored) {}
        }
        urgencyVal = Math.max(0, Math.min(2, urgencyVal));
        ScoreAnswer urgency = ScoreAnswer.of(urgencyVal);

        // 5. Usage & cost
        int inputTokens = fallbackInputTokens;
        double costUsd = 0.0;
        if (json.has("usage") && json.get("usage").isJsonObject()) {
            JsonObject usage = json.getAsJsonObject("usage");
            if (usage.has("prompt_tokens") && !usage.get("prompt_tokens").isJsonNull()) {
                inputTokens = usage.get("prompt_tokens").getAsInt();
            }
            if (usage.has("cost") && !usage.get("cost").isJsonNull()) {
                costUsd = usage.get("cost").getAsDouble();
            } else if (usage.has("cost_details") && usage.get("cost_details").isJsonObject()) {
                JsonObject cd = usage.getAsJsonObject("cost_details");
                if (cd.has("upstream_inference_cost") && !cd.get("upstream_inference_cost").isJsonNull()) {
                    costUsd = cd.get("upstream_inference_cost").getAsDouble();
                }
            }
        }
        if (costUsd == 0.0) {
            costUsd = (inputTokens / 1_000_000.0) * 0.075;
        }

        return new DecisionResult(
                request.requestId(),
                request.timestampMs(),
                request.stateHash(),
                choiceAnswer,
                inDanger,
                progressOk,
                urgency,
                latencyMs,
                false,
                selectedAction,
                "openrouter",
                costUsd,
                inputTokens,
                false
        );
    }

    public static String cleanJsonText(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    private synchronized void recordSpend(double costUsd) {
        totalSpendUsd += costUsd;
        if (totalSpendUsd >= config.maxSpendUsd) {
            spendCapReached = true;
        }
    }

    @Override
    public synchronized double getTotalSpendUsd() {
        return totalSpendUsd;
    }

    @Override
    public synchronized boolean isSpendCapReached() {
        return spendCapReached;
    }

    @Override
    public long getRollingP50LatencyMs() {
        return latencyTracker.getP50();
    }

    @Override
    public long getRollingP95LatencyMs() {
        return latencyTracker.getP95();
    }

    @Override
    public double getErrorRate() {
        return latencyTracker.getErrorRate();
    }
}
