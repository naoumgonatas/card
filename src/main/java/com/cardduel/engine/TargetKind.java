package com.cardduel.engine;

/**
 * Who an {@link Effect} hits.
 *
 * "Chosen" kinds need the player to click a target. All other kinds resolve automatically.
 */
public enum TargetKind {
    // ---- chosen by the player ----
    ANY_CHARACTER(true),
    ANY_MINION(true),
    ENEMY_CHARACTER(true),
    ENEMY_MINION(true),
    FRIENDLY_MINION(true),

    // ---- resolved automatically ----
    ENEMY_HERO(false),
    OWN_HERO(false),
    ALL_ENEMY_MINIONS(false),
    ALL_FRIENDLY_MINIONS(false),
    ALL_MINIONS(false),
    ALL_CHARACTERS(false),
    RANDOM_ENEMY_CHARACTER(false),
    RANDOM_ENEMY_MINION(false),
    /** The minion that owns the effect (battlecry / deathrattle source). */
    SELF(false);

    private final boolean chosen;

    TargetKind(boolean chosen) {
        this.chosen = chosen;
    }

    public boolean isChosen() {
        return chosen;
    }
}
