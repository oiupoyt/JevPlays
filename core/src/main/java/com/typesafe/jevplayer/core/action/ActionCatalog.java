package com.typesafe.jevplayer.core.action;

import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.*;

public final class ActionCatalog {
    private final Map<ActionId, ActionDefinition> definitions = new EnumMap<>(ActionId.class);

    public ActionCatalog() {
        registerDefaultActions();
    }

    private void registerDefaultActions() {
        // gather_wood: legal if we have inventory space
        register(new ActionDefinition(
                ActionId.GATHER_WOOD,
                state -> state.inventory().freeSlots() > 0,
                state -> state.inventory().logs() >= 16,
                25000
        ));

        // mine_stone: legal if we have pickaxe
        register(new ActionDefinition(
                ActionId.MINE_STONE,
                state -> !state.inventory().bestPickaxeTier().equals("none") && state.inventory().freeSlots() > 0,
                state -> state.inventory().cobblestone() >= 20,
                25000
        ));

        // mine_ore_coal: legal if pickaxe
        register(new ActionDefinition(
                ActionId.MINE_ORE_COAL,
                state -> !state.inventory().bestPickaxeTier().equals("none") && state.inventory().freeSlots() > 0,
                state -> state.inventory().coal() >= 16,
                30000
        ));

        // mine_ore_iron: legal if stone+ pickaxe
        register(new ActionDefinition(
                ActionId.MINE_ORE_IRON,
                state -> hasStoneOrBetterPickaxe(state) && state.inventory().freeSlots() > 0,
                state -> (state.inventory().rawIron() + state.inventory().ironIngots()) >= 10,
                30000
        ));

        // explore: always legal
        register(new ActionDefinition(
                ActionId.EXPLORE,
                state -> true,
                state -> false,
                20000
        ));

        // craft_essentials: legal if we have logs, planks, sticks, or cobblestone
        register(new ActionDefinition(
                ActionId.CRAFT_ESSENTIALS,
                state -> state.inventory().logs() > 0 || state.inventory().planks() > 0
                        || state.inventory().sticks() > 0 || state.inventory().cobblestone() >= 3
                        || state.inventory().ironIngots() >= 3,
                state -> false,
                8000
        ));

        // smelt: legal if furnace or cobblestone >= 8 and raw iron/food to smelt
        register(new ActionDefinition(
                ActionId.SMELT,
                state -> (state.inventory().cobblestone() >= 8 || state.inventory().freeSlots() < 36)
                        && (state.inventory().rawIron() > 0 || state.inventory().foodCount() > 0)
                        && (state.inventory().coal() > 0 || state.inventory().planks() > 0 || state.inventory().logs() > 0),
                state -> state.inventory().rawIron() == 0,
                25000
        ));

        // eat: legal if hunger < 20 and food available
        register(new ActionDefinition(
                ActionId.EAT,
                state -> state.vitals().hunger() < 20 && state.inventory().foodCount() > 0,
                state -> state.vitals().hunger() >= 20 || state.inventory().foodCount() == 0,
                6000
        ));

        // fight: legal if hostiles nearby
        register(new ActionDefinition(
                ActionId.FIGHT,
                state -> !state.surroundings().nearestHostiles().isEmpty()
                        && state.surroundings().nearestHostiles().get(0).distance() <= 12.0,
                state -> state.surroundings().nearestHostiles().isEmpty()
                        || state.surroundings().nearestHostiles().get(0).distance() > 16.0,
                15000
        ));

        // flee: legal if health low or hostile close
        register(new ActionDefinition(
                ActionId.FLEE,
                state -> (!state.surroundings().nearestHostiles().isEmpty() && state.surroundings().nearestHostiles().get(0).distance() <= 16.0)
                        || state.vitals().health() <= 10.0f,
                state -> state.surroundings().nearestHostiles().isEmpty()
                        || state.surroundings().nearestHostiles().get(0).distance() > 20.0,
                10000
        ));

        // place_torch: legal if low light and torches present
        register(new ActionDefinition(
                ActionId.PLACE_TORCH,
                state -> state.surroundings().lightLevel() <= 7 && state.inventory().torches() > 0,
                state -> state.surroundings().lightLevel() > 7,
                4000
        ));

        // build_shelter: legal if blocks present and night or sunset near
        register(new ActionDefinition(
                ActionId.BUILD_SHELTER,
                state -> (state.inventory().dirt() >= 8 || state.inventory().cobblestone() >= 8 || state.inventory().planks() >= 8),
                state -> false,
                20000
        ));

        // sleep: legal if night and bed present
        register(new ActionDefinition(
                ActionId.SLEEP,
                state -> !state.time().isDay() && state.inventory().beds() > 0,
                state -> state.time().isDay(),
                12000
        ));

        // hold: always legal
        register(new ActionDefinition(
                ActionId.HOLD,
                state -> true,
                state -> false,
                5000
        ));
    }

    private static boolean hasStoneOrBetterPickaxe(StateSnapshot state) {
        String tier = state.inventory().bestPickaxeTier();
        return tier.equals("stone") || tier.equals("iron") || tier.equals("diamond") || tier.equals("netherite");
    }

    public void register(ActionDefinition definition) {
        definitions.put(definition.getActionId(), definition);
    }

    public ActionDefinition get(ActionId actionId) {
        return definitions.get(actionId);
    }

    public ActionDefinition get(String actionIdStr) {
        return definitions.get(ActionId.fromId(actionIdStr));
    }

    /**
     * Generates the legal-action mask for this tick.
     * Guaranteed never to return an empty list (always includes at least EXPLORE or HOLD).
     */
    public List<ActionDefinition> getLegalActions(StateSnapshot snapshot) {
        List<ActionDefinition> legal = new ArrayList<>(definitions.size());
        for (ActionDefinition def : definitions.values()) {
            if (def.isLegal(snapshot)) {
                legal.add(def);
            }
        }
        if (legal.isEmpty()) {
            legal.add(definitions.get(ActionId.HOLD));
        }
        return Collections.unmodifiableList(legal);
    }

    /**
     * Creates a map of actionId -> description for only the legal actions,
     * directly usable as Jev Choice criteria!
     */
    public Map<String, String> getLegalCriteria(StateSnapshot snapshot) {
        List<ActionDefinition> legal = getLegalActions(snapshot);
        Map<String, String> criteria = new LinkedHashMap<>();
        for (ActionDefinition def : legal) {
            criteria.put(def.getId(), def.getDescription());
        }
        return criteria;
    }
}
