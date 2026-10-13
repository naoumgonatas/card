package com.cardduel.engine;

import java.util.EnumSet;

/** A minion on the board. Mutable runtime state; the card it came from is {@link #card}. */
public final class Minion {
    public final int uid;
    public final CardDef card;
    public int attack;
    public int health;
    public int maxHealth;
    public final EnumSet<Keyword> keywords;
    public boolean divineShield;
    /** True until the start of its owner's next turn ("summoning sickness"). */
    public boolean sick = true;
    public int attacksUsed;

    Minion(int uid, CardDef card) {
        this.uid = uid;
        this.card = card;
        this.attack = card.attack;
        this.health = card.health;
        this.maxHealth = card.health;
        this.keywords = card.keywords.isEmpty() ? EnumSet.noneOf(Keyword.class) : EnumSet.copyOf(card.keywords);
        this.divineShield = keywords.contains(Keyword.DIVINE_SHIELD);
    }

    public boolean hasTaunt() {
        return keywords.contains(Keyword.TAUNT);
    }

    public int maxAttacks() {
        return keywords.contains(Keyword.WINDFURY) ? 2 : 1;
    }

    /** Can this minion attack right now (ignoring whose turn it is)? */
    public boolean canAttack() {
        if (attack <= 0) return false;
        if (attacksUsed >= maxAttacks()) return false;
        return !sick || keywords.contains(Keyword.CHARGE);
    }

    public boolean isDamaged() {
        return health < maxHealth;
    }
}
