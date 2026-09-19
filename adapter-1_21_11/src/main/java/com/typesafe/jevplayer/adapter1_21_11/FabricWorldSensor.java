package com.typesafe.jevplayer.adapter1_21_11;

import com.typesafe.jevplayer.core.bridge.WorldSensor;
import com.typesafe.jevplayer.core.state.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

public final class FabricWorldSensor implements WorldSensor {
    private final MinecraftClient client;
    private final boolean fairMode;

    public FabricWorldSensor(MinecraftClient client, boolean fairMode) {
        this.client = client;
        this.fairMode = fairMode;
    }

    @Override
    public StateSnapshot captureSnapshot(long nowMs) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            return StateSnapshot.initial(nowMs);
        }

        // 1. Vitals
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int hunger = player.getHungerManager().getFoodLevel();
        boolean saturation = player.getHungerManager().getSaturationLevel() > 0.0f;
        int air = player.getAir();
        boolean onFire = player.isOnFire();
        boolean falling = player.fallDistance > 2.0f;
        boolean inLava = player.isInLava();
        boolean inWater = player.isSubmergedInWater();

        Vitals vitals = new Vitals(health, maxHealth, hunger, saturation, air, onFire, falling, inLava, inWater);

        // 2. Time
        long timeOfDay = world.getTimeOfDay() % 24000;
        boolean isDay = timeOfDay < 12000;
        long ticksToSunset = isDay ? (12000 - timeOfDay) : 0;
        boolean isRaining = world.isRaining();
        boolean isThundering = world.isThundering();
        long dayCount = world.getTimeOfDay() / 24000 + 1;

        TimeInfo time = new TimeInfo(isDay, ticksToSunset, isRaining, isThundering, dayCount);

        // 3. Inventory
        InventorySummary inventory = scanInventory(player);

        // 4. Surroundings
        Surroundings surroundings = scanSurroundings(player, world);

        return new StateSnapshot(
                nowMs,
                vitals,
                time,
                inventory,
                surroundings,
                GoalInfo.defaultGoal(),
                List.of()
        );
    }

    private InventorySummary scanInventory(ClientPlayerEntity player) {
        int logs = 0;
        int planks = 0;
        int sticks = 0;
        int cobblestone = 0;
        int coal = 0;
        int rawIron = 0;
        int ironIngots = 0;
        int foodCount = 0;
        int torches = 0;
        int beds = 0;
        int dirt = 0;
        int freeSlots = 0;

        String bestPickaxeTier = "none";
        String bestAxeTier = "none";
        String bestSwordTier = "none";
        List<String> durabilityWarnings = new ArrayList<>();

        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) {
                if (i < 36) freeSlots++;
                continue;
            }

            Item item = stack.getItem();
            int count = stack.getCount();

            // Logs
            if (item instanceof BlockItem blockItem) {
                var block = blockItem.getBlock();
                if (block == Blocks.OAK_LOG || block == Blocks.BIRCH_LOG || block == Blocks.SPRUCE_LOG
                        || block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG
                        || block == Blocks.MANGROVE_LOG || block == Blocks.CHERRY_LOG) {
                    logs += count;
                } else if (block == Blocks.OAK_PLANKS || block == Blocks.BIRCH_PLANKS || block == Blocks.SPRUCE_PLANKS
                        || block == Blocks.JUNGLE_PLANKS || block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS
                        || block == Blocks.MANGROVE_PLANKS || block == Blocks.CHERRY_PLANKS) {
                    planks += count;
                } else if (block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.STONE) {
                    cobblestone += count;
                } else if (block == Blocks.TORCH) {
                    torches += count;
                } else if (item instanceof BedItem) {
                    beds += count;
                } else if (block == Blocks.DIRT) {
                    dirt += count;
                }
            }

            if (item == Items.STICK) sticks += count;
            if (item == Items.COAL || item == Items.CHARCOAL) coal += count;
            if (item == Items.RAW_IRON) rawIron += count;
            if (item == Items.IRON_INGOT) ironIngots += count;

            // Food
            if (stack.contains(net.minecraft.component.DataComponentTypes.FOOD)) {
                foodCount += count;
            }

            // Tools & weapons
            if (stack.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)) {
                String tier = getToolTierName(stack);
                if (compareToolTiers(tier, bestPickaxeTier) > 0) bestPickaxeTier = tier;
                checkDurability(stack, "pickaxe", durabilityWarnings);
            } else if (stack.isIn(net.minecraft.registry.tag.ItemTags.AXES)) {
                String tier = getToolTierName(stack);
                if (compareToolTiers(tier, bestAxeTier) > 0) bestAxeTier = tier;
                checkDurability(stack, "axe", durabilityWarnings);
            } else if (stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) {
                String tier = getToolTierName(stack);
                if (compareToolTiers(tier, bestSwordTier) > 0) bestSwordTier = tier;
                checkDurability(stack, "sword", durabilityWarnings);
            }
        }

        return new InventorySummary(
                logs, planks, sticks, cobblestone, coal, rawIron, ironIngots,
                foodCount, torches, beds, dirt, freeSlots,
                bestPickaxeTier, bestAxeTier, bestSwordTier,
                durabilityWarnings
        );
    }

    private void checkDurability(ItemStack stack, String name, List<String> warnings) {
        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int current = max - stack.getDamage();
            if (current <= 10 || (double) current / max <= 0.15) {
                warnings.add(name + "_low_durability(" + current + "/" + max + ")");
            }
        }
    }

    private static String getToolTierName(ItemStack stack) {
        Item item = stack.getItem();
        String name = item.toString().toLowerCase(Locale.ROOT);
        if (name.contains("diamond")) return "diamond";
        if (name.contains("iron")) return "iron";
        if (name.contains("golden")) return "gold";
        if (name.contains("stone")) return "stone";
        if (name.contains("wooden")) return "wood";
        return "wood";
    }

    private static int compareToolTiers(String a, String b) {
        List<String> order = List.of("none", "wood", "gold", "stone", "iron", "diamond", "netherite");
        return Integer.compare(order.indexOf(a), order.indexOf(b));
    }

    private Surroundings scanSurroundings(ClientPlayerEntity player, ClientWorld world) {
        BlockPos playerPos = player.getBlockPos();
        String biome = world.getBiome(playerPos).getKey().map(k -> k.getValue().getPath()).orElse("unknown");
        int yLevel = playerPos.getY();
        int lightLevel = world.getLightLevel(playerPos);
        boolean isUnderground = yLevel < 55 || !world.isSkyVisible(playerPos);

        List<EntitySummary> nearestHostiles = new ArrayList<>();
        EntitySummary nearestPassive = null;
        double minPassiveDist = Double.MAX_VALUE;

        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);

        // Scan nearby entities within 24 block radius
        for (Entity entity : world.getEntities()) {
            if (entity == player || !(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            double dist = player.distanceTo(entity);
            if (dist > 24.0) continue;

            Vec3d toEntity = entity.getEntityPos().subtract(player.getEntityPos()).normalize();
            double dot = lookVec.dotProduct(toEntity);

            // Fair perception check: in FOV (within 110 deg cone: dot > -0.2) and line of sight
            boolean inFov = dot > -0.2;
            boolean hasLos = hasLineOfSight(world, eyePos, living.getEyePos());

            if (fairMode && (!inFov || !hasLos)) {
                // If hostile is extremely close (< 3m), player hears it even if not looking
                if (dist > 3.0) continue;
            }

            String dir = computeDirection(player, entity);
            String typeName = entity.getType().getName().getString();

            if (entity instanceof HostileEntity) {
                nearestHostiles.add(new EntitySummary(typeName, Math.round(dist * 10.0) / 10.0, dir, true, hasLos, inFov));
            } else if (entity instanceof AnimalEntity) {
                if (dist < minPassiveDist) {
                    minPassiveDist = dist;
                    nearestPassive = new EntitySummary(typeName, Math.round(dist * 10.0) / 10.0, dir, false, hasLos, inFov);
                }
            }
        }

        // Sort nearest hostiles by distance and keep top 5
        nearestHostiles.sort(Comparator.comparingDouble(EntitySummary::distance));
        if (nearestHostiles.size() > 5) {
            nearestHostiles = nearestHostiles.subList(0, 5);
        }

        // Fast local block scan for visible resources (air-exposed only if fairMode)
        Map<String, Integer> visibleResources = new HashMap<>();
        double nearestWater = -1.0;
        double nearestLava = -1.0;

        int radius = 8;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    var block = state.getBlock();
                    if (block == Blocks.WATER) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (nearestWater < 0 || d < nearestWater) nearestWater = d;
                    } else if (block == Blocks.LAVA) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (nearestLava < 0 || d < nearestLava) nearestLava = d;
                    }

                    if (fairMode && !isExposedToAir(world, pos)) {
                        continue;
                    }

                    if (block == Blocks.OAK_LOG || block == Blocks.BIRCH_LOG || block == Blocks.SPRUCE_LOG) {
                        visibleResources.merge("log", 1, Integer::sum);
                    } else if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
                        visibleResources.merge("coal_ore", 1, Integer::sum);
                    } else if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
                        visibleResources.merge("iron_ore", 1, Integer::sum);
                    }
                }
            }
        }

        return new Surroundings(
                biome, yLevel, isUnderground, lightLevel,
                nearestHostiles, nearestPassive,
                Math.round(nearestWater * 10.0) / 10.0,
                Math.round(nearestLava * 10.0) / 10.0,
                visibleResources
        );
    }

    private boolean isExposedToAir(ClientWorld world, BlockPos pos) {
        return world.getBlockState(pos.up()).isAir()
                || world.getBlockState(pos.down()).isAir()
                || world.getBlockState(pos.north()).isAir()
                || world.getBlockState(pos.south()).isAir()
                || world.getBlockState(pos.east()).isAir()
                || world.getBlockState(pos.west()).isAir();
    }

    private boolean hasLineOfSight(ClientWorld world, Vec3d start, Vec3d end) {
        BlockHitResult hit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private String computeDirection(ClientPlayerEntity player, Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - player.getYaw() - 90.0;
        angle = MathHelper.wrapDegrees(angle);

        if (angle >= -22.5 && angle < 22.5) return "ahead";
        if (angle >= 22.5 && angle < 67.5) return "ahead-right";
        if (angle >= 67.5 && angle < 112.5) return "right";
        if (angle >= 112.5 && angle < 157.5) return "behind-right";
        if (angle >= 157.5 || angle < -157.5) return "behind";
        if (angle >= -157.5 && angle < -112.5) return "behind-left";
        if (angle >= -112.5 && angle < -67.5) return "left";
        return "ahead-left";
    }

    @Override
    public double getPlayerX() {
        return client.player != null ? client.player.getX() : 0.0;
    }

    @Override
    public double getPlayerY() {
        return client.player != null ? client.player.getY() : 64.0;
    }

    @Override
    public double getPlayerZ() {
        return client.player != null ? client.player.getZ() : 0.0;
    }
}
