package com.cardduel.engine;

/**
 * Points at a character on the table.
 *
 * @param owner player index (0 or 1)
 * @param slot  index on that player's board, or {@link #HERO} for the hero
 */
public record Target(int owner, int slot) {
    public static final int HERO = -1;

    public static Target hero(int owner) {
        return new Target(owner, HERO);
    }

    public boolean isHero() {
        return slot == HERO;
    }
}
