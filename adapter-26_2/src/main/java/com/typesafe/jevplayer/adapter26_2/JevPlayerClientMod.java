package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.JevPlayerCore;
import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import com.typesafe.jevplayer.core.engine.JevPlayerEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class JevPlayerClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(JevPlayerCore.MOD_ID);

    private static JevPlayerClientMod instance;
    private JevPlayerConfig config;
    private Path configPath;
    private Path logDir;

    private JevPlayerEngine engine;
    private FabricWorldSensor worldSensor;
    private BaritoneActionExecutor baritoneExecutor;
    private FabricActionExecutor fabricExecutor;
    private CompositeActionExecutor compositeExecutor;
    private FabricInputBridge inputBridge;
    private FabricHudRenderer hudRenderer;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[{}] Initializing JevPlayer client mod v{} for Minecraft 26.2...",
                JevPlayerCore.MOD_NAME, JevPlayerCore.VERSION);

        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve("jevplayer.json");
        this.logDir = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("jevplayer");
        this.logDir.toFile().mkdirs();

        loadConfig();

        Minecraft client = Minecraft.getInstance();
        this.inputBridge = new FabricInputBridge(client);
        this.worldSensor = new FabricWorldSensor(client, config.fairMode);
        this.baritoneExecutor = new BaritoneActionExecutor(client, config.fairMode);
        this.fabricExecutor = new FabricActionExecutor(client);
        this.compositeExecutor = new CompositeActionExecutor(baritoneExecutor, fabricExecutor);
        this.hudRenderer = new FabricHudRenderer(client);

        this.engine = new JevPlayerEngine(config, worldSensor, compositeExecutor, inputBridge, hudRenderer, logDir);

        registerCommands();
        registerTickHandler();

        LOGGER.info("[{}] Initialized successfully for 26.2! Provider: {}, Fair perception: {}",
                JevPlayerCore.MOD_NAME, config.provider, config.fairMode);
    }

    public static JevPlayerClientMod getInstance() {
        return instance;
    }

    public void loadConfig() {
        File file = configPath.toFile();
        if (file.exists()) {
            try (FileReader fr = new FileReader(file)) {
                this.config = JevPlayerConfig.fromJson(fr);
                LOGGER.info("[{}] Loaded config from {}", JevPlayerCore.MOD_NAME, file.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.error("[{}] Error reading config, using defaults: {}", JevPlayerCore.MOD_NAME, e.getMessage());
                this.config = new JevPlayerConfig();
            }
        } else {
            this.config = new JevPlayerConfig();
            saveConfig();
        }
    }

    public void saveConfig() {
        try (FileWriter fw = new FileWriter(configPath.toFile())) {
            config.toJson(fw);
        } catch (Exception e) {
            LOGGER.error("[{}] Error writing default config: {}", JevPlayerCore.MOD_NAME, e.getMessage());
        }
    }

    private void registerTickHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }

            // Check F8 kill switch
            if (inputBridge.wasKillSwitchPressed()) {
                if (engine.isEnabled()) {
                    engine.triggerKillSwitch();
                    inputBridge.sendChatMessage("§c[JevPlayer] Kill switch triggered! Autopilot stopped.");
                } else {
                    engine.setEnabled(true);
                    inputBridge.sendChatMessage("§a[JevPlayer] Autopilot resumed via hotkey.");
                }
            }

            // Check F9 HUD toggle
            if (inputBridge.wasHudTogglePressed()) {
                config.hudEnabled = !config.hudEnabled;
                inputBridge.sendChatMessage("§b[JevPlayer] HUD overlay " + (config.hudEnabled ? "§aEnabled" : "§cDisabled"));
            }

            // Advance ticks
            compositeExecutor.onTick();
            engine.tick();
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("jevplayer")
                    .then(ClientCommands.literal("status").executes(ctx -> {
                        boolean enabled = engine.isEnabled();
                        String statusMsg = String.format("§b[JevPlayer] Status: %s §7| Provider: §f%s §7| Milestone: §f%s §7| Spend: §e$%.4f",
                                enabled ? "§aRUNNING" : "§cSTOPPED",
                                engine.getDecisionProvider().getName(),
                                engine.getEpisodeDirector().getCurrentMilestone().name(),
                                engine.getDecisionProvider().getTotalSpendUsd()
                        );
                        ctx.getSource().sendFeedback(Component.literal(statusMsg));
                        return 1;
                    }))
                    .then(ClientCommands.literal("start").executes(ctx -> {
                        engine.setEnabled(true);
                        ctx.getSource().sendFeedback(Component.literal("§a[JevPlayer] Autonomous play activated!"));
                        return 1;
                    }))
                    .then(ClientCommands.literal("stop").executes(ctx -> {
                        engine.setEnabled(false);
                        ctx.getSource().sendFeedback(Component.literal("§c[JevPlayer] Autonomous play stopped."));
                        return 1;
                    }))
                    .then(ClientCommands.literal("reload").executes(ctx -> {
                        loadConfig();
                        ctx.getSource().sendFeedback(Component.literal("§b[JevPlayer] Configuration reloaded."));
                        return 1;
                    }))
                    .then(ClientCommands.literal("test_gather_wood").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal("§e[JevPlayer] Testing Baritone wood gathering..."));
                        baritoneExecutor.startAction("gather_wood", worldSensor.captureSnapshot(System.currentTimeMillis()));
                        return 1;
                    }))
            );
        });
    }

    public JevPlayerEngine getEngine() {
        return engine;
    }
}
