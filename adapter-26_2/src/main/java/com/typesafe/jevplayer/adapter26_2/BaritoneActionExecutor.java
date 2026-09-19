package com.typesafe.jevplayer.adapter26_2;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import com.typesafe.jevplayer.core.bridge.ActionExecutorBridge;
import com.typesafe.jevplayer.core.state.EntitySummary;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;

public final class BaritoneActionExecutor implements ActionExecutorBridge {
    private final Minecraft client;
    private final boolean fairMode;
    private String currentAction = null;

    private static final String[] WOOD_LOGS = {
            "oak_log", "birch_log", "spruce_log", "jungle_log",
            "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log"
    };
    private static final String[] STONE_BLOCKS = {
            "stone", "cobblestone", "deepslate", "cobbled_deepslate"
    };
    private static final String[] COAL_BLOCKS = {
            "coal_ore", "deepslate_coal_ore"
    };
    private static final String[] IRON_BLOCKS = {
            "iron_ore", "deepslate_iron_ore"
    };

    public BaritoneActionExecutor(Minecraft client, boolean fairMode) {
        this.client = client;
        this.fairMode = fairMode;
        configureBaritoneSettings();
    }

    public void configureBaritoneSettings() {
        try {
            var settings = BaritoneAPI.getSettings();
            settings.allowSprint.value = true;
            settings.legitMine.value = fairMode;
            settings.smoothLook.value = true;
            settings.freeLook.value = true;
        } catch (Throwable t) {
            System.err.println("[JevPlayer] Warning: Failed to configure Baritone settings: " + t.getMessage());
        }
    }

    private IBaritone getBaritone() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean startAction(String actionId, StateSnapshot state) {
        IBaritone baritone = getBaritone();
        if (baritone == null) {
            return false;
        }

        stopAllActions();
        this.currentAction = actionId;

        try {
            switch (actionId) {
                case "gather_wood" -> {
                    baritone.getMineProcess().mineByName(WOOD_LOGS);
                    return true;
                }
                case "mine_stone" -> {
                    baritone.getMineProcess().mineByName(STONE_BLOCKS);
                    return true;
                }
                case "mine_ore_coal" -> {
                    baritone.getMineProcess().mineByName(COAL_BLOCKS);
                    return true;
                }
                case "mine_ore_iron" -> {
                    baritone.getMineProcess().mineByName(IRON_BLOCKS);
                    return true;
                }
                case "explore" -> {
                    var player = client.player;
                    if (player != null) {
                        baritone.getExploreProcess().explore((int) player.getX(), (int) player.getZ());
                        return true;
                    }
                }
                case "flee" -> {
                    var player = client.player;
                    if (player != null) {
                        List<EntitySummary> hostiles = state.surroundings().nearestHostiles();
                        double targetX = player.getX();
                        double targetZ = player.getZ();

                        if (!hostiles.isEmpty()) {
                            double angle = Math.toRadians(player.getYRot() + 180.0);
                            targetX += Math.sin(angle) * 32.0;
                            targetZ -= Math.cos(angle) * 32.0;
                        } else {
                            targetX += 20.0;
                            targetZ += 20.0;
                        }

                        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) targetX, (int) targetZ));
                        return true;
                    }
                }
                case "hold" -> {
                    baritone.getPathingBehavior().cancelEverything();
                    return true;
                }
            }
        } catch (Throwable t) {
            System.err.println("[JevPlayer] Error executing Baritone action '" + actionId + "': " + t.getMessage());
            return false;
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
        IBaritone baritone = getBaritone();
        if (baritone != null) {
            try {
                baritone.getPathingBehavior().cancelEverything();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean isActionActive(String actionId) {
        if (!Objects.equals(this.currentAction, actionId)) return false;
        IBaritone baritone = getBaritone();
        if (baritone == null) return false;

        try {
            return switch (actionId) {
                case "gather_wood", "mine_stone", "mine_ore_coal", "mine_ore_iron" ->
                        baritone.getMineProcess().isActive();
                case "explore" -> baritone.getExploreProcess().isActive();
                case "flee" -> baritone.getCustomGoalProcess().isActive() || baritone.getPathingBehavior().isPathing();
                default -> false;
            };
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isActionFinished(String actionId, StateSnapshot state) {
        if (!Objects.equals(this.currentAction, actionId)) return true;
        IBaritone baritone = getBaritone();
        if (baritone == null) return true;

        try {
            return switch (actionId) {
                case "gather_wood" -> !baritone.getMineProcess().isActive() || state.inventory().logs() >= 8;
                case "mine_stone" -> !baritone.getMineProcess().isActive() || state.inventory().cobblestone() >= 12;
                case "mine_ore_coal" -> !baritone.getMineProcess().isActive() || state.inventory().coal() >= 8;
                case "mine_ore_iron" -> !baritone.getMineProcess().isActive() || (state.inventory().rawIron() + state.inventory().ironIngots()) >= 6;
                case "explore" -> !baritone.getExploreProcess().isActive();
                case "flee" -> !baritone.getCustomGoalProcess().isActive() && !baritone.getPathingBehavior().isPathing();
                default -> true;
            };
        } catch (Throwable t) {
            return true;
        }
    }
}
