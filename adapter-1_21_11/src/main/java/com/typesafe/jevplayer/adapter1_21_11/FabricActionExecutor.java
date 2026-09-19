package com.typesafe.jevplayer.adapter1_21_11;

import com.typesafe.jevplayer.core.bridge.ActionExecutorBridge;
import com.typesafe.jevplayer.core.state.StateSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

public final class FabricActionExecutor implements ActionExecutorBridge {
    private final MinecraftClient client;
    private String currentAction = null;
    private long actionTicks = 0;

    public FabricActionExecutor(MinecraftClient client) {
        this.client = client;
    }

    @Override
    public boolean startAction(String actionId, StateSnapshot state) {
        stopAllActions();
        this.currentAction = actionId;
        this.actionTicks = 0;

        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        switch (actionId) {
            case "eat" -> {
                int foodSlot = findHotbarFood(player);
                if (foodSlot >= 0) {
                    player.getInventory().setSelectedSlot(foodSlot);
                    client.options.useKey.setPressed(true);
                    return true;
                }
            }
            case "fight" -> {
                // Point toward hostile
                Entity nearest = findNearestHostile(player);
                if (nearest != null) {
                    lookAt(player, nearest.getEyePos());
                    if (player.getAttackCooldownProgress(0.5f) >= 0.95f) {
                        if (client.interactionManager != null) {
                            client.interactionManager.attackEntity(player, nearest);
                            player.swingHand(Hand.MAIN_HAND);
                        }
                    }
                    return true;
                }
            }
            case "place_torch" -> {
                int torchSlot = findHotbarItem(player, Items.TORCH);
                if (torchSlot >= 0) {
                    player.getInventory().setSelectedSlot(torchSlot);
                    BlockPos ground = player.getBlockPos().down();
                    if (client.interactionManager != null) {
                        BlockHitResult hit = new BlockHitResult(
                                Vec3d.ofCenter(ground), Direction.UP, ground, false
                        );
                        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                        player.swingHand(Hand.MAIN_HAND);
                    }
                    return true;
                }
            }
            case "sleep" -> {
                int bedSlot = findHotbarItem(player, Items.WHITE_BED); // or any bed
                if (bedSlot >= 0) {
                    player.getInventory().setSelectedSlot(bedSlot);
                    BlockPos target = player.getBlockPos().north();
                    if (client.interactionManager != null) {
                        BlockHitResult hit = new BlockHitResult(
                                Vec3d.ofCenter(target), Direction.UP, target, false
                        );
                        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                    }
                    return true;
                }
            }
            case "craft_essentials", "build_shelter", "smelt" -> {
                return true;
            }
        }
        return false;
    }

    public void onTick() {
        if (currentAction == null || client.player == null) return;
        actionTicks++;

        ClientPlayerEntity player = client.player;

        if ("eat".equals(currentAction)) {
            if (player.getHungerManager().getFoodLevel() >= 20 || findHotbarFood(player) < 0) {
                stopAction("eat");
            } else {
                client.options.useKey.setPressed(true);
            }
        } else if ("fight".equals(currentAction)) {
            Entity nearest = findNearestHostile(player);
            if (nearest != null) {
                lookAt(player, nearest.getEyePos());
                if (player.getAttackCooldownProgress(0.5f) >= 0.95f && player.distanceTo(nearest) <= 3.8) {
                    if (client.interactionManager != null) {
                        client.interactionManager.attackEntity(player, nearest);
                        player.swingHand(Hand.MAIN_HAND);
                    }
                }
            } else {
                stopAction("fight");
            }
        }
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
        if (client.options != null && client.options.useKey != null) {
            client.options.useKey.setPressed(false);
        }
        if (client.options != null && client.options.attackKey != null) {
            client.options.attackKey.setPressed(false);
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
            case "eat" -> state.vitals().hunger() >= 20 || state.inventory().foodCount() == 0;
            case "fight" -> state.surroundings().nearestHostiles().isEmpty();
            case "place_torch" -> actionTicks > 10;
            case "sleep" -> state.time().isDay();
            case "craft_essentials", "build_shelter", "smelt" -> actionTicks > 30;
            default -> actionTicks > 100;
        };
    }

    private int findHotbarFood(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
                return i;
            }
        }
        return -1;
    }

    private int findHotbarItem(ClientPlayerEntity player, Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private Entity findNearestHostile(ClientPlayerEntity player) {
        if (client.world == null) return null;
        Entity nearest = null;
        double minDistance = 12.0;

        for (Entity e : client.world.getEntities()) {
            if (e instanceof HostileEntity hostile && hostile.isAlive()) {
                double dist = player.distanceTo(hostile);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = hostile;
                }
            }
        }
        return nearest;
    }

    private void lookAt(ClientPlayerEntity player, Vec3d target) {
        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        player.setYaw(MathHelper.lerpAngleDegrees(0.3f, player.getYaw(), yaw));
        player.setPitch(MathHelper.lerp(0.3f, player.getPitch(), pitch));
    }
}
