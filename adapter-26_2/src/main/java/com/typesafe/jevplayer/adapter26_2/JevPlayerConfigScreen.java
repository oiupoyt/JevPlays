package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class JevPlayerConfigScreen extends Screen {
    private final Screen parentScreen;
    private final JevPlayerConfig config;

    private boolean isTypeSafe;
    private boolean fairMode;

    private EditBox apiKeyEdit;
    private EditBox modelEdit;
    private EditBox baseUrlEdit;
    private EditBox intervalEdit;
    private EditBox maxSpendEdit;

    private Button providerButton;
    private Button fairModeButton;

    private String statusMessage = null;
    private int statusColor = 0xFF55FF55;

    public JevPlayerConfigScreen(Screen parentScreen) {
        super(Component.literal("JevPlayer Configuration"));
        this.parentScreen = parentScreen;
        this.config = JevPlayerClientMod.getInstance().getConfig();
        this.isTypeSafe = "typesafe".equalsIgnoreCase(config.provider);
        this.fairMode = config.fairMode;
    }

    private Component getProviderComponent() {
        return Component.literal("Provider: " + (isTypeSafe ? "§bTypeSafe AI (Live)" : "§eMock (Simulated)"));
    }

    private Component getFairModeComponent() {
        return Component.literal("Fair Perception: " + (fairMode ? "§aON (Player View Only)" : "§cOFF (All-Seeing)"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldW = 240;
        int leftX = centerX - fieldW / 2;
        int startY = 32;

        // Provider Button
        this.providerButton = addRenderableWidget(
                Button.builder(getProviderComponent(), btn -> {
                    isTypeSafe = !isTypeSafe;
                    btn.setMessage(getProviderComponent());
                }).bounds(leftX, startY, fieldW, 20).build()
        );

        // API Key Field
        this.apiKeyEdit = new EditBox(this.font, leftX, startY + 36, fieldW, 20, Component.literal("API Key"));
        this.apiKeyEdit.setMaxLength(256);
        this.apiKeyEdit.setValue(config.apiKey != null ? config.apiKey : "");
        addRenderableWidget(this.apiKeyEdit);

        // Model Field
        this.modelEdit = new EditBox(this.font, leftX, startY + 72, fieldW, 20, Component.literal("Model"));
        this.modelEdit.setMaxLength(64);
        this.modelEdit.setValue(config.model != null ? config.model : "jev-latest");
        addRenderableWidget(this.modelEdit);

        // Base URL Field
        this.baseUrlEdit = new EditBox(this.font, leftX, startY + 108, fieldW, 20, Component.literal("Base URL"));
        this.baseUrlEdit.setMaxLength(256);
        this.baseUrlEdit.setValue(config.baseUrl != null ? config.baseUrl : "https://api.typesafe.ai/v1");
        addRenderableWidget(this.baseUrlEdit);

        // Fair Mode Button
        this.fairModeButton = addRenderableWidget(
                Button.builder(getFairModeComponent(), btn -> {
                    fairMode = !fairMode;
                    btn.setMessage(getFairModeComponent());
                }).bounds(leftX, startY + 144, fieldW, 20).build()
        );

        // Interval and Max Spend (Two columns)
        int halfW = (fieldW - 10) / 2;
        this.intervalEdit = new EditBox(this.font, leftX, startY + 180, halfW, 20, Component.literal("Interval"));
        this.intervalEdit.setMaxLength(10);
        this.intervalEdit.setValue(String.valueOf(config.minDecisionIntervalMs));
        addRenderableWidget(this.intervalEdit);

        this.maxSpendEdit = new EditBox(this.font, leftX + halfW + 10, startY + 180, halfW, 20, Component.literal("Max Spend"));
        this.maxSpendEdit.setMaxLength(10);
        this.maxSpendEdit.setValue(String.valueOf(config.maxSpendUsd));
        addRenderableWidget(this.maxSpendEdit);

        // Save & Cancel Buttons
        int btnY = Math.max(startY + 214, this.height - 28);
        addRenderableWidget(
                Button.builder(Component.literal("§aSave & Apply"), btn -> saveAndClose())
                        .bounds(leftX, btnY, halfW, 20).build()
        );

        addRenderableWidget(
                Button.builder(Component.literal("§cCancel"), btn -> onClose())
                        .bounds(leftX + halfW + 10, btnY, halfW, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int fieldW = 240;
        int leftX = centerX - fieldW / 2;
        int startY = 32;

        extractor.centeredText(this.font, Component.literal("§6§lJevPlayer Settings"), centerX, 12, 0xFFFFFFFF);

        extractor.text(this.font, Component.literal("§7API Key (or env TYPESAFE_AI_API_KEY):"), leftX, startY + 25, 0xFFAAAAAA);
        extractor.text(this.font, Component.literal("§7Model:"), leftX, startY + 61, 0xFFAAAAAA);
        extractor.text(this.font, Component.literal("§7Base URL:"), leftX, startY + 97, 0xFFAAAAAA);
        extractor.text(this.font, Component.literal("§7Min Interval (ms):"), leftX, startY + 169, 0xFFAAAAAA);
        extractor.text(this.font, Component.literal("§7Max Spend ($):"), leftX + (fieldW - 10) / 2 + 10, startY + 169, 0xFFAAAAAA);

        if (statusMessage != null) {
            extractor.centeredText(this.font, Component.literal(statusMessage), centerX, this.height - 46, statusColor);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parentScreen);
        }
    }

    private void saveAndClose() {
        try {
            config.provider = isTypeSafe ? "typesafe" : "mock";
            config.apiKey = apiKeyEdit.getValue().trim();
            config.model = modelEdit.getValue().trim();
            config.baseUrl = baseUrlEdit.getValue().trim();
            config.fairMode = fairMode;

            try {
                config.minDecisionIntervalMs = Long.parseLong(intervalEdit.getValue().trim());
            } catch (NumberFormatException ignored) {}

            try {
                config.maxSpendUsd = Double.parseDouble(maxSpendEdit.getValue().trim());
            } catch (NumberFormatException ignored) {}

            config.validate();
            JevPlayerClientMod.getInstance().saveConfig();
            JevPlayerClientMod.getInstance().reloadConfig();

            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                        Component.literal("§a[JevPlayer] Settings saved! Active Provider: §f" + config.provider +
                                (isTypeSafe ? " §7(Key: §e" + config.getRedactedApiKey() + "§7)" : ""))
                );
            }
            onClose();
        } catch (Exception e) {
            this.statusMessage = "§cFailed to save settings: " + e.getMessage();
            this.statusColor = 0xFFFF5555;
        }
    }
}
