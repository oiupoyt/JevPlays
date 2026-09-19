package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.bridge.ActionExecutorBridge;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class FabricActionExecutor implements ActionExecutorBridge {
    private final Minecraft client;
    private String currentAction = null;
    private long actionTicks = 0;

    public FabricActionExecutor(Minecraft client) {
        this.client = client;
    }

    @Override
    public boolean startAction(String actionId, StateSnapshot state) {
        stopAllActions();
        this.currentAction = actionId;
        this.actionTicks = 0;

        var player = client.player;
        if (player == null) return false;

        switch (actionId) {
            case "eat" -> {
                client.options.keyUse.setDown(true);
                return true;
            }
            case "fight" -> {
                Entity nearest = findNearestMonster(player);
                if (nearest != null) {
                    lookAt(player, nearest.getEyePosition());
                    if (player.getAttackStrengthScale(0.5f) >= 0.95f) {
                        if (client.gameMode != null) {
                            client.gameMode.attack(player, nearest);
                            player.swing(InteractionHand.MAIN_HAND);
                        }
                    }
                    return true;
                }
            }
            case "sleep" -> {
                client.options.keyUse.setDown(true);
                return true;
            }
            case "craft_essentials", "smelt", "place_torch", "build_shelter" -> {
                return true;
            }
            case "hold" -> {
                return true;
            }
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
        this.actionTicks = 0;
        if (client.options != null) {
            client.options.keyUse.setDown(false);
            client.options.keyAttack.setDown(false);
        }
    }

    @Override
    public boolean isActionActive(String actionId) {
        return Objects.equals(this.currentAction, actionId);
    }

    @Override
    public boolean isActionFinished(String actionId, StateSnapshot state) {
        if (!Objects.equals(this.currentAction, actionId)) return true;
        return switch (actionId) {
            case "eat" -> state.vitals().hunger() >= 20 || actionTicks >= 40;
            case "sleep" -> state.time().isDay() || actionTicks >= 100;
            case "fight" -> state.surroundings().nearestHostiles().isEmpty() || actionTicks >= 60;
            case "craft_essentials", "smelt", "place_torch", "build_shelter" -> actionTicks >= 20;
            default -> actionTicks >= 20;
        };
    }

    public void onTick() {
        if (currentAction != null) {
            actionTicks++;
            if ("eat".equals(currentAction)) {
                if (client.options != null) {
                    client.options.keyUse.setDown(true);
                }
            }
        }
    }

    private Entity findNearestMonster(Entity player) {
        if (client.level == null) return null;
        Entity nearest = null;
        double minDistSq = 16.0;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof Monster && e.isAlive()) {
                double distSq = player.distanceToSqr(e);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = e;
                }
            }
        }
        return nearest;
    }

    private void lookAt(Entity player, Vec3 target) {
        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();
        double r = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (Math.toDegrees(-Math.atan2(dy, r)));
        player.setYRot(yaw);
        player.setXRot(pitch);
    }
}
