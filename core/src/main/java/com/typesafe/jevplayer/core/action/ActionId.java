package com.typesafe.jevplayer.core.action;

public enum ActionId {
    GATHER_WOOD("gather_wood", "Mine nearest tree logs until target inventory count"),
    MINE_STONE("mine_stone", "Mine cobblestone / stone blocks with pickaxe"),
    MINE_ORE_COAL("mine_ore_coal", "Seek and mine coal ore"),
    MINE_ORE_IRON("mine_ore_iron", "Seek and mine iron ore with stone+ pickaxe"),
    EXPLORE("explore", "Wander to discover new terrain, trees, animals, and resources"),
    CRAFT_ESSENTIALS("craft_essentials", "Craft planks, sticks, crafting table, or tools"),
    SMELT("smelt", "Set up furnace and smelt iron ore or cook food"),
    EAT("eat", "Select available food in hotbar and consume it to replenish hunger"),
    FIGHT("fight", "Engage nearest hostile mob with cooldown-timed melee strikes"),
    FLEE("flee", "Retreat from immediate threats toward safe, well-lit ground"),
    PLACE_TORCH("place_torch", "Place a torch on nearby ground or wall to light the area"),
    BUILD_SHELTER("build_shelter", "Quickly construct a dirt/wood shelter or dig-in hole"),
    SLEEP("sleep", "Place bed and sleep through the night"),
    HOLD("hold", "Wait safely and observe without moving");

    private final String id;
    private final String description;

    ActionId(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public static ActionId fromId(String id) {
        if (id == null) return HOLD;
        for (ActionId a : values()) {
            if (a.id.equalsIgnoreCase(id)) return a;
        }
        return HOLD;
    }
}
