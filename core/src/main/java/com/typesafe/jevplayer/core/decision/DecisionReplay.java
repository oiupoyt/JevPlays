package com.typesafe.jevplayer.core.decision;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public final class DecisionReplay implements DecisionProvider {
    public record LogEntry(
            long timestampMs,
            long stateHash,
            List<String> optionsOffered,
            Map<String, Double> probabilities,
            String chosenAction,
            double confidence,
            long latencyMs,
            boolean gated
    ) {}

    private final List<LogEntry> entries;
    private int currentIndex = 0;

    public DecisionReplay(List<LogEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public static DecisionReplay fromJsonLines(Reader reader) throws IOException {
        List<LogEntry> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                long ts = obj.has("timestampMs") ? obj.get("timestampMs").getAsLong() : 0;
                long hash = obj.has("stateHash") ? obj.get("stateHash").getAsLong() : 0;
                String chosen = obj.has("chosenAction") ? obj.get("chosenAction").getAsString() : "hold";
                double conf = obj.has("confidence") ? obj.get("confidence").getAsDouble() : 0.8;
                long lat = obj.has("latencyMs") ? obj.get("latencyMs").getAsLong() : 10;
                boolean gated = obj.has("gated") && obj.get("gated").getAsBoolean();

                List<String> options = new ArrayList<>();
                if (obj.has("optionsOffered")) {
                    obj.getAsJsonArray("optionsOffered").forEach(e -> options.add(e.getAsString()));
                }

                Map<String, Double> probs = new HashMap<>();
                if (obj.has("probabilities")) {
                    JsonObject pObj = obj.getAsJsonObject("probabilities");
                    for (String k : pObj.keySet()) {
                        probs.put(k, pObj.get(k).getAsDouble());
                    }
                } else {
                    probs.put(chosen, 1.0);
                }

                list.add(new LogEntry(ts, hash, options, probs, chosen, conf, lat, gated));
            }
        }
        return new DecisionReplay(list);
    }

    @Override
    public String getName() {
        return "replay";
    }

    @Override
    public void warmUp() {}

    @Override
    public synchronized DecisionResult decide(DecisionRequest request) {
        if (entries.isEmpty()) {
            return new MockProvider().decide(request);
        }

        LogEntry entry = entries.get(Math.min(currentIndex, entries.size() - 1));
        if (currentIndex < entries.size() - 1) {
            currentIndex++;
        }

        String action = entry.chosenAction();
        // If not legal in current request, fallback to first legal option
        if (!request.legalActionCriteria().containsKey(action)) {
            action = request.legalActionCriteria().keySet().iterator().next();
        }

        ChoiceAnswer choice = new ChoiceAnswer(action, entry.probabilities(), entry.confidence());
        return new DecisionResult(
                request.requestId(),
                request.timestampMs(),
                request.stateHash(),
                choice,
                new NoulAnswer(0.0, 0.9),
                new NoulAnswer(0.9, 0.9),
                ScoreAnswer.of(0),
                entry.latencyMs(),
                entry.gated(),
                entry.chosenAction(),
                "replay",
                0.0,
                100,
                false
        );
    }

    @Override
    public double getTotalSpendUsd() {
        return 0.0;
    }

    @Override
    public boolean isSpendCapReached() {
        return false;
    }

    @Override
    public long getRollingP50LatencyMs() {
        return 0;
    }

    @Override
    public long getRollingP95LatencyMs() {
        return 0;
    }

    @Override
    public double getErrorRate() {
        return 0;
    }

    public int getRemainingCount() {
        return Math.max(0, entries.size() - currentIndex);
    }
}
