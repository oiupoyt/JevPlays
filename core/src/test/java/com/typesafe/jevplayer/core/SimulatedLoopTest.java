package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.config.JevPlayerConfig;
import com.typesafe.jevplayer.core.decision.MockProvider;
import com.typesafe.jevplayer.core.engine.JevPlayerEngine;
import com.typesafe.jevplayer.core.sim.SimulatedWorldAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SimulatedLoopTest {

    @Test
    public void testFullHeadlessSimulation(@TempDir Path tempDir) throws Exception {
        JevPlayerConfig config = new JevPlayerConfig();
        config.minDecisionIntervalMs = 50; // fast cadence for headless test
        config.provider = "mock";

        SimulatedWorldAdapter sim = new SimulatedWorldAdapter();

        try (JevPlayerEngine engine = new JevPlayerEngine(config, sim, sim, sim, sim, tempDir)) {
            engine.setDecisionProvider(new MockProvider());
            engine.setEnabled(true);
            assertTrue(engine.isEnabled());

            // Run simulation for 300 game ticks (~15 seconds in-game)
            for (int i = 0; i < 300; i++) {
                sim.advanceTick();
                engine.tick();
                Thread.sleep(2); // Small yield to allow async worker thread to respond
            }

            // Verify progress was made:
            assertTrue(sim.getLogs() > 0, "Bot should have gathered logs");
            assertTrue(engine.getEpisodeDirector().getElapsedSeconds(System.currentTimeMillis()) >= 0);

            // Verify HUD was populated
            var hud = sim.getLastHudState();
            assertTrue(hud.active(), "HUD should be active");
            assertNotNull(hud.chosenAction());

            // Verify kill switch
            engine.triggerKillSwitch();
            assertFalse(engine.isEnabled(), "Kill switch should disable engine immediately");
        }
    }
}
