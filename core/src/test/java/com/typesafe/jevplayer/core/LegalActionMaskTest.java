package com.typesafe.jevplayer.core;

import com.typesafe.jevplayer.core.action.ActionCatalog;
import com.typesafe.jevplayer.core.action.ActionDefinition;
import com.typesafe.jevplayer.core.action.ActionId;
import com.typesafe.jevplayer.core.state.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LegalActionMaskTest {

    @Test
    public void testEmptyInventoryLegalActions() {
        ActionCatalog catalog = new ActionCatalog();
        StateSnapshot initial = StateSnapshot.initial(System.currentTimeMillis());

        List<ActionDefinition> legal = catalog.getLegalActions(initial);
        Map<String, String> criteria = catalog.getLegalCriteria(initial);

        // With empty inventory, gather_wood and explore and hold should be legal
        assertTrue(criteria.containsKey("gather_wood"), "gather_wood must be legal with free slots");
        assertTrue(criteria.containsKey("explore"), "explore is always legal");
        assertTrue(criteria.containsKey("hold"), "hold is always legal");

        // mine_stone should NOT be legal without pickaxe
        assertFalse(criteria.containsKey("mine_stone"), "mine_stone illegal without pickaxe");
        assertFalse(criteria.containsKey("mine_ore_coal"), "mine_ore_coal illegal without pickaxe");
        assertFalse(criteria.containsKey("mine_ore_iron"), "mine_ore_iron illegal without stone+ pickaxe");
        assertFalse(criteria.containsKey("eat"), "eat illegal without food");
        assertFalse(criteria.containsKey("fight"), "fight illegal with no hostiles nearby");
    }

    @Test
    public void testPickaxeUnlocksMining() {
        ActionCatalog catalog = new ActionCatalog();
        InventorySummary invWithPick = new InventorySummary(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                20, "stone", "none", "none", List.of()
        );
        StateSnapshot state = new StateSnapshot(
                System.currentTimeMillis(),
                Vitals.defaultVitals(),
                TimeInfo.defaultTime(),
                invWithPick,
                Surroundings.defaultSurroundings(),
                GoalInfo.defaultGoal(),
                List.of()
        );

        Map<String, String> criteria = catalog.getLegalCriteria(state);
        assertTrue(criteria.containsKey("mine_stone"), "mine_stone should be legal with stone pickaxe");
        assertTrue(criteria.containsKey("mine_ore_coal"), "mine_ore_coal should be legal with stone pickaxe");
        assertTrue(criteria.containsKey("mine_ore_iron"), "mine_ore_iron should be legal with stone pickaxe");
    }

    @Test
    public void testHungerUnlocksEat() {
        ActionCatalog catalog = new ActionCatalog();
        Vitals hungry = new Vitals(20.0f, 20.0f, 10, false, 300, false, false, false, false);
        InventorySummary invWithFood = new InventorySummary(
                0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0,
                20, "none", "none", "none", List.of()
        );
        StateSnapshot state = new StateSnapshot(
                System.currentTimeMillis(),
                hungry,
                TimeInfo.defaultTime(),
                invWithFood,
                Surroundings.defaultSurroundings(),
                GoalInfo.defaultGoal(),
                List.of()
        );

        Map<String, String> criteria = catalog.getLegalCriteria(state);
        assertTrue(criteria.containsKey("eat"), "eat should be legal when hungry and holding food");
    }

    @Test
    public void testHostileThreatUnlocksCombatAndFlee() {
        ActionCatalog catalog = new ActionCatalog();
        EntitySummary zombie = new EntitySummary("zombie", 4.5, "N", true, true, true);
        Surroundings threatened = new Surroundings(
                "plains", 64, false, 15,
                List.of(zombie), null, -1.0, -1.0, Map.of()
        );
        StateSnapshot state = new StateSnapshot(
                System.currentTimeMillis(),
                Vitals.defaultVitals(),
                TimeInfo.defaultTime(),
                InventorySummary.empty(),
                threatened,
                GoalInfo.defaultGoal(),
                List.of()
        );

        Map<String, String> criteria = catalog.getLegalCriteria(state);
        assertTrue(criteria.containsKey("fight"), "fight should be legal when hostile mob is near");
        assertTrue(criteria.containsKey("flee"), "flee should be legal when hostile mob is near");
    }
}
