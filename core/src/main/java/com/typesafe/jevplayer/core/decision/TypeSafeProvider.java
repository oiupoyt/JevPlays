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
import java.util.concurrent.ThreadLocalRandom;

public final class TypeSafeProvider implements DecisionProvider {
    private static final Gson GSON = new Gson();
    private static final double COST_PER_MILLION_INPUT_TOKENS = 0.042;

    private final JevPlayerConfig config;
    private final String apiKey;
    private final HttpClient httpClient;
    private final MockProvider fallbackProvider = new MockProvider();
    private final LatencyTracker latencyTracker = new LatencyTracker();

    private double totalSpendUsd = 0.0;
    private boolean spendCapReached = false;
    private volatile boolean warmedUp = false;

    public TypeSafeProvider(JevPlayerConfig config) {
        this.config = config;
        this.apiKey = config.resolveApiKey();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(config.requestTimeoutMs))
                .build();
    }

    @Override
    public String getName() {
        return "typesafe";
    }

    @Override
    public void warmUp() {
        if (warmedUp || apiKey.isEmpty()) return;
        warmedUp = true;
        Thread.ofVirtual().start(() -> {
            try {
                // Send a cheap warm-up request to complete TLS and HTTP/2 handshake
                Map<String, String> dummyCriteria = Map.of("hold", "Wait");
                String payload = buildJsonPayload("warmup", dummyCriteria);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.baseUrl + "/systemone"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "JevPlayer/1.0.0")
                        .timeout(Duration.ofMillis(config.requestTimeoutMs))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
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
        String payload = buildJsonPayload(request.state().toPromptText(), request.legalActionCriteria());
        int estimatedInputTokens = Math.max(1, payload.length() / 4);

        // Check spend cap before sending
        double estimatedCost = (estimatedInputTokens / 1_000_000.0) * COST_PER_MILLION_INPUT_TOKENS;
        if (totalSpendUsd + estimatedCost > config.maxSpendUsd) {
            spendCapReached = true;
            return fallbackProvider.decide(request);
        }

        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(config.baseUrl + "/systemone"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "JevPlayer/1.0.0")
                        .timeout(Duration.ofMillis(config.requestTimeoutMs))
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
                } else if (status == 429 || status == 529 || status >= 500) {
                    // Retry with backoff
                    long backoffMs = 100 * (1L << attempt) + ThreadLocalRandom.current().nextInt(50);
                    Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                    if (retryAfter.isPresent()) {
                        try {
                            backoffMs = Math.max(backoffMs, Long.parseLong(retryAfter.get()) * 1000L);
                        } catch (NumberFormatException ignored) {}
                    }
                    if (attempt < maxRetries) {
                        Thread.sleep(Math.min(backoffMs, 500));
                        attempt++;
                        continue;
                    }
                    latencyTracker.recordError();
                    break;
                } else {
                    // Client error (4xx)
                    latencyTracker.recordError();
                    break;
                }
            } catch (Exception e) {
                lastException = e;
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
                "typesafe(fallback)",
                0.0,
                estimatedInputTokens,
                true
        );
    }

    public String buildJsonPayload(String stateText, Map<String, String> criteria) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.model);
        root.addProperty("state", stateText);

        JsonObject questions = new JsonObject();

        // 1. Action question
        JsonObject actionQ = new JsonObject();
        actionQ.addProperty("type", "choice");
        actionQ.addProperty("instructions", "Choose the single best immediate action for the player to take right now given current vitals, inventory, surroundings, and active goal.");
        JsonObject actionCriteria = new JsonObject();
        for (Map.Entry<String, String> entry : criteria.entrySet()) {
            actionCriteria.addProperty(entry.getKey(), entry.getValue());
        }
        actionQ.add("criteria", actionCriteria);
        questions.add("action", actionQ);

        // 2. In danger question (noul)
        JsonObject dangerQ = new JsonObject();
        dangerQ.addProperty("type", "noul");
        dangerQ.addProperty("instructions", "Is the player in immediate danger of dying in the next few seconds?");
        JsonObject dangerCriteria = new JsonObject();
        dangerCriteria.addProperty("true", "Player faces lethal threat, lava, drowning, or imminent mob swarm.");
        dangerCriteria.addProperty("false", "Player is safe or threat is minor/manageable.");
        dangerQ.add("criteria", dangerCriteria);
        questions.add("in_danger", dangerQ);

        // 3. Progress ok question (noul)
        JsonObject progressQ = new JsonObject();
        progressQ.addProperty("type", "noul");
        progressQ.addProperty("instructions", "Is the player making useful progress toward the current goal?");
        JsonObject progressCriteria = new JsonObject();
        progressCriteria.addProperty("true", "Player is steadily progressing toward the active milestone.");
        progressCriteria.addProperty("false", "Player is stuck, idling aimlessly, or failing repeatedly.");
        progressQ.add("criteria", progressCriteria);
        questions.add("progress_ok", progressQ);

        // 4. Urgency question (score)
        JsonObject urgencyQ = new JsonObject();
        urgencyQ.addProperty("type", "score");
        urgencyQ.addProperty("instructions", "Assess the tactical urgency of taking immediate action.");
        JsonArray urgencyCriteria = new JsonArray();
        urgencyCriteria.add("Idle is fine");
        urgencyCriteria.add("Soon");
        urgencyCriteria.add("Now");
        urgencyQ.add("criteria", urgencyCriteria);
        questions.add("urgency", urgencyQ);

        root.add("questions", questions);
        return GSON.toJson(root);
    }

    public DecisionResult parseResponse(DecisionRequest request, String responseBody, long latencyMs, int fallbackInputTokens) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonObject answers = json.has("answers") ? json.getAsJsonObject("answers") : new JsonObject();

        // 1. Parse action
        ChoiceAnswer choiceAnswer = null;
        if (answers.has("action")) {
            JsonObject aObj = answers.getAsJsonObject("action");
            String choice = aObj.has("choice") ? aObj.get("choice").getAsString() : "hold";
            double confidence = aObj.has("confidence") && !aObj.get("confidence").isJsonNull()
                    ? aObj.get("confidence").getAsDouble() : 0.85;
            Map<String, Double> probs = new HashMap<>();
            if (aObj.has("probabilities") && aObj.get("probabilities").isJsonObject()) {
                JsonObject pObj = aObj.getAsJsonObject("probabilities");
                for (String k : pObj.keySet()) {
                    probs.put(k, pObj.get(k).getAsDouble());
                }
            } else {
                probs.put(choice, 1.0);
            }
            choiceAnswer = new ChoiceAnswer(choice, probs, confidence);
        } else {
            choiceAnswer = ChoiceAnswer.of("hold", 0.50);
        }

        // 2. Parse in_danger
        NoulAnswer inDanger = null;
        if (answers.has("in_danger")) {
            JsonObject dObj = answers.getAsJsonObject("in_danger");
            double noul = dObj.has("noul") ? dObj.get("noul").getAsDouble() : 0.0;
            double conf = dObj.has("confidence") && !dObj.get("confidence").isJsonNull()
                    ? dObj.get("confidence").getAsDouble() : 0.85;
            inDanger = new NoulAnswer(noul, conf);
        } else {
            inDanger = new NoulAnswer(0.0, 0.5);
        }

        // 3. Parse progress_ok
        NoulAnswer progressOk = null;
        if (answers.has("progress_ok")) {
            JsonObject pObj = answers.getAsJsonObject("progress_ok");
            double noul = pObj.has("noul") ? pObj.get("noul").getAsDouble() : 0.8;
            double conf = pObj.has("confidence") && !pObj.get("confidence").isJsonNull()
                    ? pObj.get("confidence").getAsDouble() : 0.85;
            progressOk = new NoulAnswer(noul, conf);
        } else {
            progressOk = new NoulAnswer(0.8, 0.5);
        }

        // 4. Parse urgency
        ScoreAnswer urgency = null;
        if (answers.has("urgency")) {
            JsonObject uObj = answers.getAsJsonObject("urgency");
            int score = uObj.has("score") ? uObj.get("score").getAsInt() : 0;
            double conf = uObj.has("confidence") && !uObj.get("confidence").isJsonNull()
                    ? uObj.get("confidence").getAsDouble() : 0.85;
            urgency = new ScoreAnswer(score, null, conf);
        } else {
            urgency = ScoreAnswer.of(0);
        }

        // 5. Usage & cost
        int inputTokens = fallbackInputTokens;
        if (json.has("usage") && json.get("usage").isJsonObject()) {
            JsonObject usage = json.getAsJsonObject("usage");
            if (usage.has("input_tokens") && !usage.get("input_tokens").isJsonNull()) {
                inputTokens = usage.get("input_tokens").getAsInt();
            }
        }
        double costUsd = (inputTokens / 1_000_000.0) * COST_PER_MILLION_INPUT_TOKENS;

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
                choiceAnswer.choice(),
                "typesafe",
                costUsd,
                inputTokens,
                false
        );
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
