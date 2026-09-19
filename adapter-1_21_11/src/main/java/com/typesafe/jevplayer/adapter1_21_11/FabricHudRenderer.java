package com.typesafe.jevplayer.adapter1_21_11;

import com.typesafe.jevplayer.core.bridge.HudRendererBridge;
import com.typesafe.jevplayer.core.hud.HudState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

public final class FabricHudRenderer implements HudRendererBridge {
    private final MinecraftClient client;
    private volatile HudState currentState = HudState.empty();

    public FabricHudRenderer(MinecraftClient client) {
        this.client = client;
        HudRenderCallback.EVENT.register(this::render);
    }

    @Override
    public void updateHudState(HudState hudState) {
        this.currentState = hudState;
    }

    private void render(DrawContext context, RenderTickCounter tickCounter) {
        HudState state = this.currentState;
        if (!state.active() || client.options.hudHidden) {
            return;
        }

        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        int x = 12;
        int y = 12;
        int width = 230;
        int height = state.debugPage() ? 165 : 125;

        // Semi-transparent dark background (0xAA111118)
        context.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0xD00E0E14);
        // Clean accent border
        context.fill(x - 4, y - 4, x + width + 4, y - 3, 0xFF4A88E8);

        // Header
        context.drawTextWithShadow(tr, "§b§lJevPlayer §7| §f" + state.providerName(), x, y, 0xFFFFFFFF);
        y += 12;

        // Action badge
        String actionText = "Action: §e§l" + state.chosenAction() + (state.gated() ? " §c[GATED]" : "");
        context.drawTextWithShadow(tr, actionText, x, y, 0xFFFFFFFF);
        String confText = String.format("Conf: §a%.0f%%", state.confidence() * 100.0);
        context.drawTextWithShadow(tr, confText, x + 155, y, 0xFFAAAAAA);
        y += 13;

        // Top-3 Option Probability Bars
        List<HudState.OptionBar> options = state.topOptions();
        for (HudState.OptionBar opt : options) {
            int barWidth = 140;
            int filledWidth = (int) (barWidth * Math.max(0.0, Math.min(1.0, opt.probability())));
            int barY = y + 1;

            // Bar background
            context.fill(x + 70, barY, x + 70 + barWidth, barY + 6, 0x55444444);
            // Filled bar (green if chosen, light blue if alternative)
            int barColor = opt.isChosen() ? 0xFF2ECC71 : 0xFF3498DB;
            if (filledWidth > 0) {
                context.fill(x + 70, barY, x + 70 + filledWidth, barY + 6, barColor);
            }

            String optLabel = opt.actionId();
            if (optLabel.length() > 9) optLabel = optLabel.substring(0, 9);
            context.drawTextWithShadow(tr, optLabel, x, y, opt.isChosen() ? 0xFFFFFFFF : 0xFFAAAAAA);
            String probLabel = String.format("%d%%", (int) (opt.probability() * 100));
            context.drawTextWithShadow(tr, probLabel, x + 70 + barWidth + 4, y, 0xFFDDDDDD);
            y += 10;
        }

        y += 2;
        // Milestone & Progress
        context.drawTextWithShadow(tr, "Milestone: §f" + state.milestoneName(), x, y, 0xFFCCCCCC);
        y += 11;
        context.drawTextWithShadow(tr, "Target: §7" + state.milestoneRequirements(), x, y, 0xFF888888);
        y += 12;

        // Time, Decisions, Latency, Spend
        long mins = state.elapsedSeconds() / 60;
        long secs = state.elapsedSeconds() % 60;
        String statsLine1 = String.format("Time: §f%02d:%02d §7| Dec: §f%d §7| p50: §f%dms",
                mins, secs, state.decisionsCount(), state.latencyP50Ms());
        context.drawTextWithShadow(tr, statsLine1, x, y, 0xFFBBBBBB);
        y += 11;

        String spendLine = String.format("Spend: §e$%.4f%s", state.totalSpendUsd(),
                state.spendCapReached() ? " §c[CAP REACHED]" : "");
        context.drawTextWithShadow(tr, spendLine, x, y, 0xFFBBBBBB);
        y += 12;

        // Debug Page Metrics
        if (state.debugPage()) {
            context.fill(x - 2, y - 2, x + width + 2, y - 1, 0x44FFFFFF);
            y += 2;
            String debug1 = String.format("Tick: §f%.2fms §7(avg §f%.2f§7, max §f%.2f§7)",
                    state.lastTickMs(), state.avgTickMs(), state.maxTickMs());
            context.drawTextWithShadow(tr, debug1, x, y, 0xFFAAAAAA);
            y += 10;
            String debug2 = String.format("Error rate: §f%.1f%% §7| In-flight: §f%dms",
                    state.errorRate() * 100.0, state.inFlightAgeMs());
            context.drawTextWithShadow(tr, debug2, x, y, 0xFFAAAAAA);
        }
    }
}
