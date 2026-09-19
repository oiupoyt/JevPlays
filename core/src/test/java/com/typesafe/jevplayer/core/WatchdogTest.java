package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.state.StateSnapshot;
import com.typesafe.jevplayer.core.watchdog.WatchdogEngine;
import com.typesafe.jevplayer.core.watchdog.WatchdogEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WatchdogTest {

    @Test
    public void testStuckDetection() {
        WatchdogEngine watchdog = new WatchdogEngine();
        StateSnapshot state = StateSnapshot.initial(1000);

        // Initial position at 1000ms
        watchdog.updatePosition(10.0, 64.0, 10.0, 1000);
        watchdog.updateInventory(state, 1000);

        // Advance 10 seconds: not stuck yet
        WatchdogEvent event1 = watchdog.tick(state, "mine_stone", 11000);
        assertEquals(WatchdogEvent.Type.NONE, event1.type());

        // Advance past 45s threshold with no position or inventory change: STUCK!
        WatchdogEvent event2 = watchdog.tick(state, "mine_stone", 50000);
        assertEquals(WatchdogEvent.Type.STUCK, event2.type());
        assertEquals("explore", event2.fallbackAction());

        // Verify action 'mine_stone' is now temporarily blocked
        assertTrue(watchdog.isActionBlocked("mine_stone", 50000));
    }

    @Test
    public void testLoopBreakerAfterThreeFailures() {
        WatchdogEngine watchdog = new WatchdogEngine();
        long now = 1000;

        assertFalse(watchdog.isActionBlocked("gather_wood", now));

        // 1st failure
        watchdog.recordActionOutcome("gather_wood", false, now);
        assertFalse(watchdog.isActionBlocked("gather_wood", now));

        // 2nd failure
        watchdog.recordActionOutcome("gather_wood", false, now + 1000);
        assertFalse(watchdog.isActionBlocked("gather_wood", now + 1000));

        // 3rd consecutive failure: blocked!
        watchdog.recordActionOutcome("gather_wood", false, now + 2000);
        assertTrue(watchdog.isActionBlocked("gather_wood", now + 2000), "Should block after 3 consecutive failures");
    }

    @Test
    public void testDeathWatchdog() {
        WatchdogEngine watchdog = new WatchdogEngine();
        StateSnapshot alive = StateSnapshot.initial(1000);
        watchdog.tick(alive, "explore", 1000);
        assertEquals(0, watchdog.getDeathCount());

        // Player dies
        var deadVitals = new com.typesafe.jevplayer.core.state.Vitals(0.0f, 20.0f, 0, false, 0, false, false, false, false);
        StateSnapshot dead = new StateSnapshot(2000, deadVitals, alive.time(), alive.inventory(), alive.surroundings(), alive.goal(), alive.recentHistory());

        WatchdogEvent deathEvent = watchdog.tick(dead, "explore", 2000);
        assertEquals(WatchdogEvent.Type.DEATH, deathEvent.type());
        assertEquals(1, watchdog.getDeathCount());
    }

    @Test
    public void testKillSwitch() {
        WatchdogEngine watchdog = new WatchdogEngine();
        StateSnapshot state = StateSnapshot.initial(1000);

        watchdog.setKillSwitch(true);
        assertTrue(watchdog.isKillSwitchActive());

        WatchdogEvent event = watchdog.tick(state, "gather_wood", 1000);
        assertEquals(WatchdogEvent.Type.KILL_SWITCH, event.type());
    }
}
