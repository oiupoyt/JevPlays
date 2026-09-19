package com.typesafe.jevplayer.core.watchdog;

import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.HashMap;
import java.util.Map;

public final class WatchdogEngine {
    private static final long STUCK_THRESHOLD_MS = 45000; // 45 seconds
    private static final long ACTION_COOLDOWN_MS = 15000; // 15 seconds

    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;
    private long lastMovementTimeMs = 0;

    private int lastInventoryHash = 0;
    private long lastInventoryChangeTimeMs = 0;

    private final Map<String, Integer> consecutiveFailures = new HashMap<>();
    private final Map<String, Long> blockedActions = new HashMap<>();

    private int deathCount = 0;
    private boolean wasDead = false;
    private boolean killSwitchActive = false;

    public void updatePosition(double x, double y, double z, long nowMs) {
        if (Double.isNaN(lastX)) {
            lastX = x;
            lastY = y;
            lastZ = z;
            lastMovementTimeMs = nowMs;
            return;
        }

        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // Position moved more than 1.5 blocks
        if (distSq > 2.25) {
            lastX = x;
            lastY = y;
            lastZ = z;
            lastMovementTimeMs = nowMs;
        }
    }

    public void updateInventory(StateSnapshot snapshot, long nowMs) {
        int invHash = computeInventoryHash(snapshot);
        if (invHash != lastInventoryHash) {
            lastInventoryHash = invHash;
            lastInventoryChangeTimeMs = nowMs;
        }
    }

    public WatchdogEvent tick(StateSnapshot snapshot, String currentActionId, long nowMs) {
        if (killSwitchActive) {
            return WatchdogEvent.killSwitch();
        }

        // 1. Death check
        if (snapshot.vitals().health() <= 0.0f) {
            if (!wasDead) {
                wasDead = true;
                deathCount++;
                return WatchdogEvent.death(deathCount);
            }
        } else {
            wasDead = false;
        }

        // Clean up expired action blocks
        blockedActions.entrySet().removeIf(entry -> entry.getValue() <= nowMs);

        // 2. Stuck check
        if (currentActionId != null && !"hold".equalsIgnoreCase(currentActionId)) {
            if (lastMovementTimeMs > 0 && lastInventoryChangeTimeMs > 0) {
                long stationaryTime = nowMs - lastMovementTimeMs;
                long noInvTime = nowMs - lastInventoryChangeTimeMs;

                if (stationaryTime >= STUCK_THRESHOLD_MS && noInvTime >= STUCK_THRESHOLD_MS) {
                    // Player hasn't moved and hasn't collected anything for 45s
                    blockAction(currentActionId, nowMs);
                    lastMovementTimeMs = nowMs;
                    lastInventoryChangeTimeMs = nowMs;
                    return WatchdogEvent.stuck("No movement or inventory change for " + (stationaryTime / 1000) + "s", "explore");
                }
            }
        }

        return WatchdogEvent.NONE_EVENT;
    }

    public void recordActionOutcome(String actionId, boolean success, long nowMs) {
        if (actionId == null) return;
        if (success) {
            consecutiveFailures.remove(actionId);
        } else {
            int failures = consecutiveFailures.getOrDefault(actionId, 0) + 1;
            consecutiveFailures.put(actionId, failures);
            if (failures >= 3) {
                blockAction(actionId, nowMs);
                consecutiveFailures.remove(actionId);
            }
        }
    }

    public boolean isActionBlocked(String actionId, long nowMs) {
        Long expiry = blockedActions.get(actionId);
        return expiry != null && expiry > nowMs;
    }

    public void blockAction(String actionId, long nowMs) {
        blockedActions.put(actionId, nowMs + ACTION_COOLDOWN_MS);
    }

    public void setKillSwitch(boolean active) {
        this.killSwitchActive = active;
    }

    public boolean isKillSwitchActive() {
        return killSwitchActive;
    }

    public int getDeathCount() {
        return deathCount;
    }

    public void reset() {
        lastX = Double.NaN;
        lastY = Double.NaN;
        lastZ = Double.NaN;
        lastMovementTimeMs = 0;
        lastInventoryHash = 0;
        lastInventoryChangeTimeMs = 0;
        consecutiveFailures.clear();
        blockedActions.clear();
        killSwitchActive = false;
    }

    private static int computeInventoryHash(StateSnapshot snapshot) {
        var inv = snapshot.inventory();
        int hash = inv.logs();
        hash = 31 * hash + inv.planks();
        hash = 31 * hash + inv.cobblestone();
        hash = 31 * hash + inv.coal();
        hash = 31 * hash + inv.rawIron();
        hash = 31 * hash + inv.ironIngots();
        hash = 31 * hash + inv.freeSlots();
        return hash;
    }
}
