package com.typesafe.jevplayer.adapter26_2;

import baritone.api.BaritoneAPI;
import com.typesafe.jevplayer.core.JevPlayerCore;
import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[{}] Initialized client adapter for Minecraft 26.2 (Core v{})",
                JevPlayerCore.MOD_NAME, JevPlayerCore.VERSION);

        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve("jevplayer.json");
        loadConfig();

        try {
            var settings = BaritoneAPI.getSettings();
            settings.allowSprint.value = true;
            settings.legitMine.value = config.fairMode;
            settings.smoothLook.value = true;
            LOGGER.info("[{}] Baritone v1.19.0 configured successfully for 26.2!", JevPlayerCore.MOD_NAME);
        } catch (Throwable t) {
            LOGGER.warn("[{}] Baritone API not detected: {}", JevPlayerCore.MOD_NAME, t.getMessage());
        }
    }

    public void loadConfig() {
        File file = configPath.toFile();
        if (file.exists()) {
            try (FileReader fr = new FileReader(file)) {
                this.config = JevPlayerConfig.fromJson(fr);
            } catch (Exception e) {
                this.config = new JevPlayerConfig();
            }
        } else {
            this.config = new JevPlayerConfig();
            try (FileWriter fw = new FileWriter(file)) {
                config.toJson(fw);
            } catch (Exception ignored) {}
        }
    }

    public static JevPlayerClientMod getInstance() {
        return instance;
    }
}
