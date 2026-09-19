package com.typesafe.jevplayer.core.bridge;

import com.typesafe.jevplayer.core.hud.HudState;

public interface HudRendererBridge {
    void updateHudState(HudState hudState);
}
