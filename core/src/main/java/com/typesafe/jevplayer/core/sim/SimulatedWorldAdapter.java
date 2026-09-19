package com.typesafe.jevplayer.core.sim;

import com.typesafe.jevplayer.core.bridge.*;
import com.typesafe.jevplayer.core.hud.HudState;
import com.typesafe.jevplayer.core.state.*;

import java.util.*;

public final class SimulatedWorldAdapter implements WorldSensor, ActionExecutorBridge, InputBridge, HudRendererBridge {
    private double x = 0.0;
    private double y = 64.0;
    private double z = 0.0;

    private float health = 20.0f;
    private int hunger = 20;
    private boolean isDay = true;
    private long dayCount = 1;

    private int logs = 0;
    private int planks = 0;
    private int sticks = 0;
    private int cobblestone = 0;
    private int coal = 0;
    private int rawIron = 0;
    private int ironIngots = 0;
    private int foodCount = 5;
    private int torches = 0;
    private int beds = 1;
    private int dirt = 10;
    private String pickaxeTier = "none";

    private String activeAction = null;
    private long actionTicks = 0;

    private final List<String> chatMessages = new ArrayList<>();
    private HudState lastHudState = HudState.empty();

    public void advanceTick() {
        actionTicks++;

        // Simulate natural hunger depletion
        if (actionTicks % 400 == 0 && hunger > 0) {
            hunger--;
        }

        // Simulate action execution effects
        if (activeAction != null) {
            switch (activeAction) {
                case "gather_wood" -> {
                    // Gather 1 log every 30 ticks
                    if (actionTicks % 30 == 0) {
                        logs++;
                        x += 0.5; // Simulate slight movement
                    }
                }
                case "craft_essentials" -> {
                    if (logs > 0 && planks < 4) {
                        logs--;
                        planks += 4;
                    } else if (planks >= 2 && sticks < 4) {
                        planks -= 2;
                        sticks += 4;
                    } else if (planks >= 3 && sticks >= 2 && pickaxeTier.equals("none")) {
                        planks -= 3;
                        sticks -= 2;
                        pickaxeTier = "wood";
                    } else if (cobblestone >= 3 && sticks >= 2 && !pickaxeTier.equals("stone") && !pickaxeTier.equals("iron")) {
                        cobblestone -= 3;
                        sticks -= 2;
                        pickaxeTier = "stone";
                    } else if (ironIngots >= 3 && sticks >= 2 && !pickaxeTier.equals("iron")) {
                        ironIngots -= 3;
                        sticks -= 2;
                        pickaxeTier = "iron";
                    }
                }
                case "mine_stone" -> {
                    if (actionTicks % 25 == 0) {
                        cobblestone++;
                        y = Math.max(12.0, y - 0.2);
                    }
                }
                case "mine_ore_coal" -> {
                    if (actionTicks % 35 == 0) {
                        coal++;
                    }
                }
                case "mine_ore_iron" -> {
                    if (actionTicks % 40 == 0) {
                        rawIron++;
                    }
                }
                case "smelt" -> {
                    if (actionTicks % 30 == 0 && rawIron > 0) {
                        rawIron--;
                        ironIngots++;
                    }
                }
                case "eat" -> {
                    if (foodCount > 0 && hunger < 20) {
                        foodCount--;
                        hunger = Math.min(20, hunger + 6);
                        health = Math.min(20.0f, health + 4.0f);
                    }
                }
                case "sleep" -> {
                    if (!isDay) {
                        isDay = true;
                        dayCount++;
                    }
                }
                case "explore" -> {
                    x += 1.0;
                    z += 1.0;
                }
            }
        }
    }

    @Override
    public StateSnapshot captureSnapshot(long nowMs) {
        Vitals vitals = new Vitals(health, 20.0f, hunger, true, 300, false, false, false, false);
        TimeInfo time = new TimeInfo(isDay, isDay ? 6000 : 0, false, false, dayCount);
        InventorySummary inv = new InventorySummary(
                logs, planks, sticks, cobblestone, coal, rawIron, ironIngots,
                foodCount, torches, beds, dirt, 20, pickaxeTier, "none", "none", List.of()
        );
        Surroundings surroundings = new Surroundings(
                "forest", (int) y, y < 55, 15,
                List.of(), null, 12.0, -1.0,
                Map.of("oak_log", 8, "stone", 64)
        );
        return new StateSnapshot(nowMs, vitals, time, inv, surroundings, GoalInfo.defaultGoal(), List.of());
    }

    @Override
    public double getPlayerX() {
        return x;
    }

    @Override
    public double getPlayerY() {
        return y;
    }

    @Override
    public double getPlayerZ() {
        return z;
    }

    @Override
    public boolean startAction(String actionId, StateSnapshot state) {
        this.activeAction = actionId;
        this.actionTicks = 0;
        return true;
    }

    @Override
    public void stopAction(String actionId) {
        if (Objects.equals(this.activeAction, actionId)) {
            this.activeAction = null;
        }
    }

    @Override
    public void stopAllActions() {
        this.activeAction = null;
    }

    @Override
    public boolean isActionActive(String actionId) {
        return Objects.equals(this.activeAction, actionId);
    }

    @Override
    public boolean isActionFinished(String actionId, StateSnapshot state) {
        if (activeAction == null) return true;
        return switch (actionId) {
            case "gather_wood" -> logs >= 4;
            case "mine_stone" -> cobblestone >= 4;
            case "mine_ore_coal" -> coal >= 4;
            case "mine_ore_iron" -> rawIron >= 3;
            case "smelt" -> rawIron == 0;
            case "eat" -> hunger >= 20 || foodCount == 0;
            case "sleep" -> isDay;
            case "craft_essentials" -> actionTicks > 20;
            default -> actionTicks > 100;
        };
    }

    @Override
    public void releaseAllInputs() {}

    @Override
    public void sendChatMessage(String message) {
        chatMessages.add(message);
    }

    @Override
    public boolean isRemoteServer() {
        return false;
    }

    @Override
    public void updateHudState(HudState hudState) {
        this.lastHudState = hudState;
    }

    public HudState getLastHudState() {
        return lastHudState;
    }

    public List<String> getChatMessages() {
        return Collections.unmodifiableList(chatMessages);
    }

    public String getActiveAction() {
        return activeAction;
    }

    public int getLogs() {
        return logs;
    }

    public int getCobblestone() {
        return cobblestone;
    }

    public String getPickaxeTier() {
        return pickaxeTier;
    }
}
