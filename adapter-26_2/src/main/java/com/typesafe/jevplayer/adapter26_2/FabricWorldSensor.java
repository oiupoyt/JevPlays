package com.typesafe.jevplayer.adapter26_2;

import com.typesafe.jevplayer.core.bridge.WorldSensor;
import com.typesafe.jevplayer.core.state.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class FabricWorldSensor implements WorldSensor {
    private final Minecraft client;
    private boolean fairMode;

    public FabricWorldSensor(Minecraft client, boolean fairMode) {
        this.client = client;
        this.fairMode = fairMode;
    }

    public void setFairMode(boolean fairMode) {
        this.fairMode = fairMode;
    }

    @Override
    public StateSnapshot captureSnapshot(long nowMs) {
        var player = client.player;
        var level = client.level;

        if (player == null || level == null) {
            return StateSnapshot.initial(nowMs);
        }

        // 1. Vitals
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int hunger = player.getFoodData().getFoodLevel();
        boolean saturation = player.getFoodData().getSaturationLevel() > 0.0f;
        int air = player.getAirSupply();
        boolean onFire = player.isOnFire();
        boolean falling = player.fallDistance > 2.0f;
        boolean inLava = player.isInLava();
        boolean inWater = player.isInWater();

        Vitals vitals = new Vitals(health, maxHealth, hunger, saturation, air, onFire, falling, inLava, inWater);

        // 2. Time
        long timeOfDay = level.getLevelData().getGameTime() % 24000;
        boolean isDay = timeOfDay < 12000;
        long ticksToSunset = isDay ? (12000 - timeOfDay) : 0;
        boolean isRaining = level.isRaining();
        boolean isThundering = level.isThundering();
        long dayCount = level.getLevelData().getGameTime() / 24000 + 1;

        TimeInfo time = new TimeInfo(isDay, ticksToSunset, isRaining, isThundering, dayCount);

        // 3. Inventory
        InventorySummary inventory = scanInventory(player);

        // 4. Surroundings
        Surroundings surroundings = scanSurroundings(player, level);

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

    private InventorySummary scanInventory(net.minecraft.world.entity.player.Player player) {
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
        int size = inv.getContainerSize();

        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                if (i < 36) freeSlots++;
                continue;
            }

            int count = stack.getCount();
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

            if (path.endsWith("_log")) logs += count;
            else if (path.endsWith("_planks")) planks += count;
            else if ("stick".equals(path)) sticks += count;
            else if ("cobblestone".equals(path) || "cobbled_deepslate".equals(path)) cobblestone += count;
            else if ("coal".equals(path) || "charcoal".equals(path)) coal += count;
            else if ("raw_iron".equals(path)) rawIron += count;
            else if ("iron_ingot".equals(path)) ironIngots += count;
            else if ("torch".equals(path)) torches += count;
            else if (path.endsWith("_bed")) beds += count;
            else if ("dirt".equals(path)) dirt += count;

            if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                foodCount += count;
            }

            if (path.endsWith("_pickaxe")) {
                String tier = extractTier(path);
                if (tierRank(tier) > tierRank(bestPickaxeTier)) bestPickaxeTier = tier;
            } else if (path.endsWith("_axe")) {
                String tier = extractTier(path);
                if (tierRank(tier) > tierRank(bestAxeTier)) bestAxeTier = tier;
            } else if (path.endsWith("_sword")) {
                String tier = extractTier(path);
                if (tierRank(tier) > tierRank(bestSwordTier)) bestSwordTier = tier;
            }
        }

        return new InventorySummary(
                logs, planks, sticks, cobblestone, coal, rawIron, ironIngots,
                foodCount, torches, beds, dirt, freeSlots,
                bestPickaxeTier, bestAxeTier, bestSwordTier, durabilityWarnings
        );
    }

    private Surroundings scanSurroundings(net.minecraft.world.entity.player.Player player, net.minecraft.client.multiplayer.ClientLevel level) {
        int px = (int) player.getX();
        int py = (int) player.getY();
        int pz = (int) player.getZ();

        Map<String, Integer> visibleResources = new HashMap<>();
        int lightLevel = level.getMaxLocalRawBrightness(player.blockPosition());
        String biome = level.getBiome(player.blockPosition()).unwrapKey()
                .map(k -> k.identifier().getPath()).orElse("plains");

        int radius = 12;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double nearestWaterDist = -1.0;
        double nearestLavaDist = -1.0;

        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    pos.set(px + dx, py + dy, pz + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (blockId.endsWith("_log") || blockId.contains("ore")) {
                        visibleResources.merge(blockId, 1, Integer::sum);
                    }
                    if (blockId.contains("water") && nearestWaterDist < 0) {
                        nearestWaterDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    }
                    if (blockId.contains("lava") && nearestLavaDist < 0) {
                        nearestLavaDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    }
                }
            }
        }

        boolean isUnderground = py < 50;

        List<EntitySummary> hostiles = new ArrayList<>();
        EntitySummary nearestPassive = null;
        double minPassiveDist = Double.MAX_VALUE;

        for (Entity e : level.entitiesForRendering()) {
            if (e == player || !e.isAlive()) continue;

            double dist = player.distanceTo(e);
            if (dist > 32.0) continue;

            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            boolean isHostile = (e instanceof Monster);
            boolean isAnimal = (e instanceof Animal);

            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            String direction = getDirection(dx, dz);

            EntitySummary summary = new EntitySummary(
                    typeId,
                    dist,
                    direction,
                    isHostile,
                    true,
                    true
            );

            if (isHostile) {
                hostiles.add(summary);
            } else if (isAnimal && dist < minPassiveDist) {
                minPassiveDist = dist;
                nearestPassive = summary;
            }
        }

        hostiles.sort(Comparator.comparingDouble(EntitySummary::distance));
        if (hostiles.size() > 5) {
            hostiles = hostiles.subList(0, 5);
        }

        return new Surroundings(
                biome,
                py,
                isUnderground,
                lightLevel,
                hostiles,
                nearestPassive,
                nearestWaterDist,
                nearestLavaDist,
                visibleResources
        );
    }

    private String getDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle >= 337.5 || angle < 22.5) return "E";
        if (angle < 67.5) return "SE";
        if (angle < 112.5) return "S";
        if (angle < 157.5) return "SW";
        if (angle < 202.5) return "W";
        if (angle < 247.5) return "NW";
        if (angle < 292.5) return "N";
        return "NE";
    }

    private String extractTier(String itemName) {
        if (itemName.startsWith("wooden_")) return "wooden";
        if (itemName.startsWith("stone_")) return "stone";
        if (itemName.startsWith("iron_")) return "iron";
        if (itemName.startsWith("golden_")) return "golden";
        if (itemName.startsWith("diamond_")) return "diamond";
        if (itemName.startsWith("netherite_")) return "netherite";
        return "none";
    }

    private int tierRank(String tier) {
        return switch (tier) {
            case "wooden", "golden" -> 1;
            case "stone" -> 2;
            case "iron" -> 3;
            case "diamond" -> 4;
            case "netherite" -> 5;
            default -> 0;
        };
    }

    @Override
    public double getPlayerX() {
        return client.player != null ? client.player.getX() : 0.0;
    }

    @Override
    public double getPlayerY() {
        return client.player != null ? client.player.getY() : 0.0;
    }

    @Override
    public double getPlayerZ() {
        return client.player != null ? client.player.getZ() : 0.0;
    }
}
