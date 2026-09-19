package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.bridge.HudRendererBridge;
import com.typesafe.jevplayer.core.hud.HudState;
import net.minecraft.client.Minecraft;

public final class FabricHudRenderer implements HudRendererBridge {
    private final Minecraft client;
    private HudState currentState;

    public FabricHudRenderer(Minecraft client) {
        this.client = client;
    }

    @Override
    public void updateHudState(HudState state) {
        this.currentState = state;
    }

    public HudState getCurrentState() {
        return currentState;
    }
}
