package com.cardduel.engine;

public enum EffectKind {
    /** a = damage. */
    DAMAGE,
    /** a = amount restored (never above max health). */
    HEAL,
    /** a = cards drawn by the effect's owner. */
    DRAW,
    /** a = attack bonus, b = health bonus (minions only). */
    BUFF,
    /** cardId = token to summon, a = how many. */
    SUMMON,
    /** a = mana crystals gained this turn only (The Coin). */
    GAIN_MANA,
    /** Destroys the targeted minion. */
    DESTROY
}
