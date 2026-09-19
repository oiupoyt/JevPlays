package com.typesafe.jevplayer.adapter1_21_11;

import com.typesafe.jevplayer.core.bridge.InputBridge;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class FabricInputBridge implements InputBridge {
    private final MinecraftClient client;
    private final KeyBinding killSwitchKey;
    private final KeyBinding hudToggleKey;

    public FabricInputBridge(MinecraftClient client) {
        this.client = client;

        this.killSwitchKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jevplayer.kill_switch",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.MISC
        ));

        this.hudToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.jevplayer.hud_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                KeyBinding.Category.MISC
        ));
    }

    public boolean wasKillSwitchPressed() {
        return killSwitchKey.wasPressed();
    }

    public boolean wasHudTogglePressed() {
        return hudToggleKey.wasPressed();
    }

    @Override
    public void releaseAllInputs() {
        if (client.options == null) return;
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.attackKey.setPressed(false);
        client.options.useKey.setPressed(false);
    }

    @Override
    public void sendChatMessage(String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    @Override
    public boolean isRemoteServer() {
        return client.getCurrentServerEntry() != null || !client.isIntegratedServerRunning();
    }
}
