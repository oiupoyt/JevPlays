package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.director.EpisodeDirector;
import com.typesafe.jevplayer.core.director.Milestone;
import com.typesafe.jevplayer.core.director.MilestoneLadder;
import com.typesafe.jevplayer.core.state.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MilestoneDirectorTest {

    @Test
    public void testMilestoneProgression() {
        MilestoneLadder ladder = new MilestoneLadder();
        EpisodeDirector director = new EpisodeDirector(ladder, null, 60);

        // Starts on wood milestone
        assertEquals("wood", director.getCurrentMilestone().id());

        // Initial state: 0 logs -> not complete
        StateSnapshot s1 = StateSnapshot.initial(1000);
        director.tick(s1, 1000);
        assertEquals("wood", director.getCurrentMilestone().id());

        // Player obtains 4 logs -> progresses to crafting_table!
        InventorySummary invLogs = new InventorySummary(
                4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                20, "none", "none", "none", List.of()
        );
        StateSnapshot s2 = new StateSnapshot(2000, s1.vitals(), s1.time(), invLogs, s1.surroundings(), s1.goal(), s1.recentHistory());
        director.tick(s2, 2000);
        assertEquals("crafting_table", director.getCurrentMilestone().id());

        // Player crafts planks -> progresses to wooden_tools!
        InventorySummary invPlanks = new InventorySummary(
                0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                20, "none", "none", "none", List.of()
        );
        StateSnapshot s3 = new StateSnapshot(3000, s1.vitals(), s1.time(), invPlanks, s1.surroundings(), s1.goal(), s1.recentHistory());
        director.tick(s3, 3000);
        assertEquals("wooden_tools", director.getCurrentMilestone().id());

        // Player crafts wooden pickaxe -> progresses to stone_tools!
        InventorySummary invPick = new InventorySummary(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                20, "wood", "none", "none", List.of()
        );
        StateSnapshot s4 = new StateSnapshot(4000, s1.vitals(), s1.time(), invPick, s1.surroundings(), s1.goal(), s1.recentHistory());
        director.tick(s4, 4000);
        assertEquals("stone_tools", director.getCurrentMilestone().id());
    }

    @Test
    public void testEpisodeClockAndWrapUp() {
        MilestoneLadder ladder = new MilestoneLadder();
        EpisodeDirector director = new EpisodeDirector(ladder, null, 60); // 60 min = 3600 sec

        long start = System.currentTimeMillis();
        assertFalse(director.isWrapUpPhase(start));
        assertFalse(director.isEpisodeComplete(start));

        // Advance 58 minutes (3480 seconds) -> wrap up phase!
        long at58m = start + (3480 * 1000L);
        assertTrue(director.isWrapUpPhase(at58m));
        assertFalse(director.isEpisodeComplete(at58m));

        // Advance 60 minutes (3600 seconds) -> episode complete!
        long at60m = start + (3601 * 1000L);
        assertTrue(director.isEpisodeComplete(at60m));
    }
}
