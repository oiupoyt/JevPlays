package com.typesafe.jevplayer.core.reflex;

import com.typesafe.jevplayer.core.state.EntitySummary;
import com.typesafe.jevplayer.core.state.StateSnapshot;

import java.util.List;

public final class ReflexEngine {

    /**
     * Evaluates reflexes on the client main thread.
     * Guaranteed to be zero-allocation on the happy path (returning ReflexResult.NONE).
     * Budget target: < 0.05 ms.
     */
    public ReflexResult evaluate(StateSnapshot state) {
        // 1. Environmental emergency: lava, severe fire, drowning
        if (state.vitals().inLava()) {
            return ReflexResult.fire("flee", "Emergency: Player is inside lava pool!");
        }
        if (state.vitals().air() < 60) {
            return ReflexResult.fire("flee", "Emergency: Drowning / low air reserves!");
        }
        if (state.vitals().onFire() && state.vitals().health() < 12.0f) {
            return ReflexResult.fire("flee", "Emergency: On fire with critical health!");
        }

        // 2. Immediate Combat Hazard
        List<EntitySummary> hostiles = state.surroundings().nearestHostiles();
        if (!hostiles.isEmpty()) {
            EntitySummary nearest = hostiles.get(0);
            double dist = nearest.distance();

            // Critical health retreat
            if (state.vitals().health() <= 7.0f && dist <= 12.0) {
                return ReflexResult.fire("flee", "Reflex: Low health (" + state.vitals().health() + ") near threat " + nearest.type());
            }

            // Point-blank self defense
            if (dist <= 3.2 && state.vitals().health() > 7.0f) {
                return ReflexResult.fire("fight", "Reflex: Point-blank hostile threat: " + nearest.type() + " at " + dist + "m");
            }
        }

        // 3. Starvation Reflex
        if (state.vitals().hunger() <= 6 && state.inventory().foodCount() > 0) {
            return ReflexResult.fire("eat", "Reflex: Severe hunger (" + state.vitals().hunger() + ")");
        }

        // 4. Nightfall Threat without shelter
        if (!state.time().isDay() && !state.surroundings().isUnderground()) {
            if (state.inventory().beds() > 0) {
                return ReflexResult.fire("sleep", "Reflex: Nightfall - Bed available to skip night");
            }
            if (!hostiles.isEmpty() && hostiles.get(0).distance() <= 16.0) {
                return ReflexResult.fire("build_shelter", "Reflex: Nightfall hostiles approaching without shelter");
            }
        }

        // 5. Tool durability emergency
        if (!state.inventory().durabilityWarnings().isEmpty()) {
            // If primary tool is about to break and we have materials, trigger crafting
            if (state.inventory().planks() >= 3 || state.inventory().cobblestone() >= 3 || state.inventory().ironIngots() >= 3) {
                return ReflexResult.fire("craft_essentials", "Reflex: Tool broken or near-broken (" + state.inventory().durabilityWarnings().get(0) + ")");
            }
        }

        return ReflexResult.NONE;
    }
}
