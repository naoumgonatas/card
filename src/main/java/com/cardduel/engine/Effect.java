package com.cardduel.engine;

/** One thing a card does when played (spell / battlecry) or when it dies (deathrattle). */
public record Effect(EffectKind kind, TargetKind target, int a, int b, String cardId) {

    public static Effect damage(TargetKind target, int amount) {
        return new Effect(EffectKind.DAMAGE, target, amount, 0, null);
    }

    public static Effect heal(TargetKind target, int amount) {
        return new Effect(EffectKind.HEAL, target, amount, 0, null);
    }

    public static Effect draw(int cards) {
        return new Effect(EffectKind.DRAW, TargetKind.OWN_HERO, cards, 0, null);
    }

    public static Effect buff(TargetKind target, int attack, int health) {
        return new Effect(EffectKind.BUFF, target, attack, health, null);
    }

    public static Effect summon(String tokenId, int count) {
        return new Effect(EffectKind.SUMMON, TargetKind.OWN_HERO, count, 0, tokenId);
    }

    public static Effect gainMana(int amount) {
        return new Effect(EffectKind.GAIN_MANA, TargetKind.OWN_HERO, amount, 0, null);
    }

    public static Effect destroy(TargetKind target) {
        return new Effect(EffectKind.DESTROY, target, 0, 0, null);
    }
}
