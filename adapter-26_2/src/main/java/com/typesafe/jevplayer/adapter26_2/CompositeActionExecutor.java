package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.bridge.ActionExecutorBridge;
import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.Objects;
import java.util.Set;

public final class CompositeActionExecutor implements ActionExecutorBridge {
    private final BaritoneActionExecutor baritoneExecutor;
    private final FabricActionExecutor fabricExecutor;

    private static final Set<String> BARITONE_ACTIONS = Set.of(
            "gather_wood", "mine_stone", "mine_ore_coal", "mine_ore_iron",
            "explore", "flee"
    );

    private String currentAction = null;
    private ActionExecutorBridge activeExecutor = null;

    public CompositeActionExecutor(BaritoneActionExecutor baritoneExecutor, FabricActionExecutor fabricExecutor) {
        this.baritoneExecutor = baritoneExecutor;
        this.fabricExecutor = fabricExecutor;
    }

    @Override
    public boolean startAction(String actionId, StateSnapshot state) {
        stopAllActions();
        this.currentAction = actionId;

        if (BARITONE_ACTIONS.contains(actionId)) {
            if (baritoneExecutor.startAction(actionId, state)) {
                this.activeExecutor = baritoneExecutor;
                return true;
            }
        }

        if (fabricExecutor.startAction(actionId, state)) {
            this.activeExecutor = fabricExecutor;
            return true;
        }

        return false;
    }

    @Override
    public void stopAction(String actionId) {
        if (Objects.equals(this.currentAction, actionId)) {
            stopAllActions();
        }
    }

    @Override
    public void stopAllActions() {
        this.currentAction = null;
        this.activeExecutor = null;
        baritoneExecutor.stopAllActions();
        fabricExecutor.stopAllActions();
    }

    @Override
    public boolean isActionActive(String actionId) {
        if (!Objects.equals(this.currentAction, actionId)) return false;
        if (activeExecutor != null) {
            return activeExecutor.isActionActive(actionId);
        }
        return false;
    }

    @Override
    public boolean isActionFinished(String actionId, StateSnapshot state) {
        if (!Objects.equals(this.currentAction, actionId)) return true;
        if (activeExecutor != null) {
            return activeExecutor.isActionFinished(actionId, state);
        }
        return true;
    }

    public void onTick() {
        fabricExecutor.onTick();
    }
}
