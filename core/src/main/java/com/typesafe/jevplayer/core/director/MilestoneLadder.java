package com.typesafe.jevplayer.core.director;

import java.util.*;

public final class MilestoneLadder {
    private final List<Milestone> milestones = new ArrayList<>();

    public MilestoneLadder() {
        registerDefaultLadder();
    }

    private void registerDefaultLadder() {
        // 1. Wood
        milestones.add(new Milestone(
                "wood",
                "Gather Wood",
                "Need logs >= 4",
                state -> state.inventory().logs() >= 4,
                Set.of("gather_wood", "explore", "eat", "flee", "fight", "hold"),
                180
        ));

        // 2. Crafting Table
        milestones.add(new Milestone(
                "crafting_table",
                "Craft Table & Planks",
                "Need planks >= 4",
                state -> state.inventory().planks() >= 4,
                Set.of("craft_essentials", "gather_wood", "explore", "eat", "flee", "fight", "hold"),
                120
        ));

        // 3. Wooden Tools
        milestones.add(new Milestone(
                "wooden_tools",
                "Craft Wooden Pickaxe",
                "Need wooden pickaxe",
                state -> !state.inventory().bestPickaxeTier().equals("none"),
                Set.of("craft_essentials", "gather_wood", "explore", "eat", "flee", "fight", "hold"),
                180
        ));

        // 4. Stone Tools
        milestones.add(new Milestone(
                "stone_tools",
                "Mine Stone & Craft Stone Pickaxe",
                "Need cobblestone >= 3 and stone pickaxe",
                state -> {
                    String tier = state.inventory().bestPickaxeTier();
                    return (tier.equals("stone") || tier.equals("iron") || tier.equals("diamond"))
                            && state.inventory().cobblestone() >= 3;
                },
                Set.of("mine_stone", "craft_essentials", "explore", "eat", "flee", "fight", "hold"),
                240
        ));

        // 5. Furnace & Food
        milestones.add(new Milestone(
                "furnace_food",
                "Craft Furnace & Procure Food",
                "Need cobblestone >= 8 and food >= 3",
                state -> state.inventory().cobblestone() >= 8 && state.inventory().foodCount() >= 3,
                Set.of("mine_stone", "smelt", "craft_essentials", "eat", "explore", "flee", "fight", "hold"),
                300
        ));

        // 6. Coal & Torches
        milestones.add(new Milestone(
                "coal_torches",
                "Mine Coal & Place Torches",
                "Need coal >= 4 or torches >= 4",
                state -> state.inventory().coal() >= 4 || state.inventory().torches() >= 4,
                Set.of("mine_ore_coal", "craft_essentials", "place_torch", "explore", "eat", "flee", "fight", "hold"),
                300
        ));

        // 7. Shelter & Bed
        milestones.add(new Milestone(
                "shelter_bed",
                "Night Shelter & Bed",
                "Need bed or blocks >= 20",
                state -> state.inventory().beds() >= 1 || (state.inventory().cobblestone() + state.inventory().dirt()) >= 20,
                Set.of("build_shelter", "sleep", "place_torch", "craft_essentials", "explore", "eat", "flee", "fight", "hold"),
                300
        ));

        // 8. Iron
        milestones.add(new Milestone(
                "iron",
                "Find Iron Ore",
                "Need raw iron >= 3 or iron ingots >= 3",
                state -> (state.inventory().rawIron() + state.inventory().ironIngots()) >= 3,
                Set.of("mine_ore_iron", "mine_stone", "smelt", "explore", "eat", "flee", "fight", "hold"),
                480
        ));

        // 9. Iron Tools
        milestones.add(new Milestone(
                "iron_tools",
                "Smelt Iron & Craft Iron Pickaxe",
                "Need iron pickaxe",
                state -> {
                    String tier = state.inventory().bestPickaxeTier();
                    return tier.equals("iron") || tier.equals("diamond");
                },
                Set.of("smelt", "craft_essentials", "mine_ore_iron", "explore", "eat", "flee", "fight", "hold"),
                480
        ));
    }

    public List<Milestone> getMilestones() {
        return Collections.unmodifiableList(milestones);
    }

    public Milestone getByIndex(int index) {
        if (index < 0) return milestones.get(0);
        if (index >= milestones.size()) return milestones.get(milestones.size() - 1);
        return milestones.get(index);
    }

    public int size() {
        return milestones.size();
    }
}
