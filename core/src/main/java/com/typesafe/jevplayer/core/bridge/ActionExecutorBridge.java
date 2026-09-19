package com.typesafe.jevplayer.core.bridge;

import com.typesafe.jevplayer.core.state.StateSnapshot;

public interface ActionExecutorBridge {
    boolean startAction(String actionId, StateSnapshot state);

    void stopAction(String actionId);

    void stopAllActions();

    boolean isActionActive(String actionId);

    boolean isActionFinished(String actionId, StateSnapshot state);
}
