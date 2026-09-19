package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.bridge.InputBridge;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class FabricInputBridge implements InputBridge {
    private final Minecraft client;
    private final KeyMapping killSwitchKey;
    private final KeyMapping hudToggleKey;
    private final KeyMapping configGuiKey;

    public FabricInputBridge(Minecraft client) {
        this.client = client;
        this.killSwitchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.jevplayer.kill_switch",
                GLFW.GLFW_KEY_F8,
                KeyMapping.Category.MISC
        ));
        this.hudToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.jevplayer.toggle_hud",
                GLFW.GLFW_KEY_F9,
                KeyMapping.Category.MISC
        ));
        this.configGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.jevplayer.config_gui",
                GLFW.GLFW_KEY_F10,
                KeyMapping.Category.MISC
        ));
    }

    public boolean wasKillSwitchPressed() {
        return killSwitchKey.consumeClick();
    }

    public boolean wasHudTogglePressed() {
        return hudToggleKey.consumeClick();
    }

    public boolean wasConfigGuiPressed() {
        return configGuiKey.consumeClick();
    }

    @Override
    public void releaseAllInputs() {
        if (client.options == null) return;
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
    }

    @Override
    public void sendChatMessage(String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public boolean isRemoteServer() {
        return client.getCurrentServer() != null || !client.isLocalServer();
    }
}
