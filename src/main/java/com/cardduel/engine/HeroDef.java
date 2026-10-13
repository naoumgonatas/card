package com.cardduel.engine;

import java.util.List;

/** A hero with its once-per-turn hero power. */
public record HeroDef(String id, String name, String powerName, String powerText, int powerCost, List<Effect> powerEffects) {

    /** The target the player has to choose for the hero power, or null if it needs none. */
    public TargetKind chosenTarget() {
        for (Effect e : powerEffects) {
            if (e.target().isChosen()) return e.target();
        }
        return null;
    }
}
