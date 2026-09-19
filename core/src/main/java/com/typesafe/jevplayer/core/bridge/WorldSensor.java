package com.typesafe.jevplayer.core.bridge;

import com.typesafe.jevplayer.core.state.StateSnapshot;

public interface WorldSensor {
    StateSnapshot captureSnapshot(long nowMs);

    double getPlayerX();

    double getPlayerY();

    double getPlayerZ();
}
