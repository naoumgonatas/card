package com.cardduel.engine;

/** Static keywords a minion can carry. Add new ones here, then handle them in {@link Game}. */
public enum Keyword {
    /** Enemies must attack this minion before anything else. */
    TAUNT("Taunt"),
    /** Can attack the turn it is played. */
    CHARGE("Charge"),
    /** The first instance of damage is ignored, then the shield pops. */
    DIVINE_SHIELD("Divine Shield"),
    /** Can attack twice per turn. */
    WINDFURY("Windfury");

    public final String label;

    Keyword(String label) {
        this.label = label;
    }
}
