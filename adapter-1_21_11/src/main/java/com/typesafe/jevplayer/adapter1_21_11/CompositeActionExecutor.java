package com.typesafe.jevplayer.adapter1_21_11;

import com.typesafe.jevplayer.core.bridge.ActionExecutorBridge;
import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.Set;

public final class CompositeActionExecutor implements ActionExecutorBridge {
    private final BaritoneActionExecutor baritoneExecutor;
    private final FabricActionExecutor fabricExecutor;

    private static final Set<String> BARITONE_ACTIONS = Set.of(
            "gather_wood", "mine_stone", "mine_ore_coal", "mine_ore_iron", "explore", "flee", "hold"
    );

    public CompositeActionExecutor(BaritoneActionExecutor baritoneExecutor, FabricActionExecutor fabricExecutor) {
        this.baritoneExecutor = baritoneExecutor;
        this.fabricExecutor = fabricExecutor;
    }

    @Override
    public boolean startAction(String actionId, StateSnapshot state) {
        if (BARITONE_ACTIONS.contains(actionId)) {
            fabricExecutor.stopAllActions();
            return baritoneExecutor.startAction(actionId, state);
        } else {
            baritoneExecutor.stopAllActions();
            return fabricExecutor.startAction(actionId, state);
        }
    }

    public void onTick() {
        fabricExecutor.onTick();
    }

    @Override
    public void stopAction(String actionId) {
        if (BARITONE_ACTIONS.contains(actionId)) {
            baritoneExecutor.stopAction(actionId);
        } else {
            fabricExecutor.stopAction(actionId);
        }
    }

    @Override
    public void stopAllActions() {
        baritoneExecutor.stopAllActions();
        fabricExecutor.stopAllActions();
    }

    @Override
    public boolean isActionActive(String actionId) {
        if (BARITONE_ACTIONS.contains(actionId)) {
            return baritoneExecutor.isActionActive(actionId);
        } else {
            return fabricExecutor.isActionActive(actionId);
        }
    }

    @Override
    public boolean isActionFinished(String actionId, StateSnapshot state) {
        if (BARITONE_ACTIONS.contains(actionId)) {
            return baritoneExecutor.isActionFinished(actionId, state);
        } else {
            return fabricExecutor.isActionFinished(actionId, state);
        }
    }
}
