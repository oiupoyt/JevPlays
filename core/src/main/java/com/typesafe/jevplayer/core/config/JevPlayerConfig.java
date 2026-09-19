package com.typesafe.jevplayer.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class JevPlayerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String apiKeyEnvVar = "TYPESAFE_AI_API_KEY";
    public String apiKey = ""; // Discouraged; env var preferred. Never logged or committed.
    public String provider = "mock"; // "mock" or "typesafe"
    public String model = "jev-latest";
    public String baseUrl = "https://api.typesafe.ai/v1";

    public long minDecisionIntervalMs = 500;
    public long maxActionAgeMs = 20000;
    public long requestTimeoutMs = 1500;
    public double maxSpendUsd = 0.50;

    public Map<String, Double> confidenceThresholds = new HashMap<>();
    public String safeDefaultAction = "explore";

    public boolean fairMode = true;
    public boolean allowMultiplayer = false;
    public int episodeMinutes = 60;

    public String hotkeyKillSwitch = "F8";
    public String hotkeyToggleHud = "F9";
    public boolean hudEnabled = true;
    public boolean hudDebugPage = false;
    public String logLevel = "INFO";

    public JevPlayerConfig() {
        initDefaultThresholds();
    }

    private void initDefaultThresholds() {
        confidenceThresholds.put("eat", 0.40);
        confidenceThresholds.put("gather_wood", 0.50);
        confidenceThresholds.put("mine_stone", 0.50);
        confidenceThresholds.put("mine_ore_coal", 0.55);
        confidenceThresholds.put("mine_ore_iron", 0.55);
        confidenceThresholds.put("craft_essentials", 0.50);
        confidenceThresholds.put("smelt", 0.50);
        confidenceThresholds.put("fight", 0.60);
        confidenceThresholds.put("flee", 0.45);
        confidenceThresholds.put("build_shelter", 0.50);
        confidenceThresholds.put("sleep", 0.40);
        confidenceThresholds.put("place_torch", 0.45);
        confidenceThresholds.put("explore", 0.50);
        confidenceThresholds.put("hold", 0.30);
    }

    public void validate() {
        if (minDecisionIntervalMs < 50) minDecisionIntervalMs = 50;
        if (maxActionAgeMs < 1000) maxActionAgeMs = 1000;
        if (requestTimeoutMs < 200) requestTimeoutMs = 200;
        if (maxSpendUsd < 0) maxSpendUsd = 0.50;
        if (episodeMinutes <= 0) episodeMinutes = 60;
        if (safeDefaultAction == null || safeDefaultAction.isBlank()) safeDefaultAction = "explore";
        if (provider == null || (!provider.equalsIgnoreCase("typesafe") && !provider.equalsIgnoreCase("mock"))) {
            provider = "mock";
        }
        if (confidenceThresholds == null) {
            confidenceThresholds = new HashMap<>();
            initDefaultThresholds();
        } else {
            for (Map.Entry<String, Double> entry : confidenceThresholds.entrySet()) {
                if (entry.getValue() == null || entry.getValue() < 0.0) entry.setValue(0.0);
                else if (entry.getValue() > 1.0) entry.setValue(1.0);
            }
        }
    }

    public double getConfidenceThreshold(String actionId) {
        return confidenceThresholds.getOrDefault(actionId, 0.50);
    }

    /**
     * Resolves the effective API key from environment variable or explicit config.
     * Guaranteed not to be logged.
     */
    public String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String envKey = System.getenv(apiKeyEnvVar != null ? apiKeyEnvVar : "TYPESAFE_AI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return "";
    }

    /**
     * Safe string for display/logging. Always masks the secret.
     */
    public String getRedactedApiKey() {
        String key = resolveApiKey();
        if (key.isEmpty()) {
            return "[NONE]";
        }
        if (key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    public static JevPlayerConfig fromJson(Reader reader) {
        JevPlayerConfig config = GSON.fromJson(reader, JevPlayerConfig.class);
        if (config == null) {
            config = new JevPlayerConfig();
        }
        config.validate();
        return config;
    }

    public static JevPlayerConfig fromJson(String json) {
        JevPlayerConfig config = GSON.fromJson(json, JevPlayerConfig.class);
        if (config == null) {
            config = new JevPlayerConfig();
        }
        config.validate();
        return config;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public void toJson(Writer writer) {
        GSON.toJson(this, writer);
    }
}
