package com.cardduel.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A small rules-aware opponent so one player can test or practise alone. Not a strong player on purpose. */
public final class Bot {

    private Bot() {
    }

    /** Keep cheap cards, throw back expensive ones. */
    public static void mulligan(Game g, int pl, long now) {
        PlayerState s = g.me(pl);
        List<Integer> replace = new ArrayList<>();
        for (int i = 0; i < s.hand.size(); i++) {
            CardDef c = s.hand.get(i);
            if (c.cost >= 4 && !c.id.equals(CardLibrary.COIN)) replace.add(i);
        }
        g.mulligan(pl, replace, now);
    }

    /** Plays a whole turn: cards, hero power, attacks, then ends the turn. */
    public static void playTurn(Game g, int pl, long now) {
        if (!g.isMyTurn(pl)) return;
        playCards(g, pl, now);
        if (g.isOver()) return;
        useHeroPower(g, pl, now);
        if (g.isOver()) return;
        attackWithEverything(g, pl, now);
        if (g.isOver()) return;
        g.endTurn(pl, now);
    }

    // ---------------------------------------------------------------------------------------------

    private static void playCards(Game g, int pl, long now) {
        PlayerState s = g.me(pl);
        for (int guard = 0; guard < 30; guard++) {
            int bestIdx = -1;
            Target bestTarget = null;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < s.hand.size(); i++) {
                CardDef c = s.hand.get(i);
                if (!g.canPlay(pl, i)) continue;
                if (c.id.equals(CardLibrary.COIN)) {
                    // Only spend the Coin if it unlocks a card we otherwise couldn't afford.
                    boolean unlocks = false;
                    for (CardDef other : s.hand) {
                        if (other != c && other.cost == s.mana + 1) unlocks = true;
                    }
                    if (!unlocks) continue;
                }
                Target t = null;
                TargetKind tk = c.chosenTarget();
                if (tk != null) {
                    Effect first = firstChosen(c.onPlay);
                    t = pickTarget(g, pl, first, tk);
                    if (t == null && !g.validTargets(pl, tk).isEmpty() && c.isMinion()) {
                        t = g.validTargets(pl, tk).get(0);
                    }
                    if (t == null && !c.isMinion()) continue; // no worthwhile target for this spell
                }
                int score = c.cost * 10 + (c.isMinion() ? 3 : 0) - (c.id.equals(CardLibrary.COIN) ? 100 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx = i;
                    bestTarget = t;
                }
            }
            if (bestIdx < 0) return;
            Result r = g.playCard(pl, bestIdx, bestTarget, now);
            if (!r.ok() || g.isOver()) return;
        }
    }

    private static void useHeroPower(Game g, int pl, long now) {
        if (!g.canHeroPower(pl)) return;
        HeroDef h = g.me(pl).hero;
        TargetKind tk = h.chosenTarget();
        Target t = null;
        if (tk != null) {
            t = pickTarget(g, pl, firstChosen(h.powerEffects()), tk);
            if (t == null) return;
        }
        g.heroPower(pl, t, now);
    }

    private static void attackWithEverything(Game g, int pl, long now) {
        Set<Integer> done = new HashSet<>();
        for (int guard = 0; guard < 40 && !g.isOver(); guard++) {
            PlayerState s = g.me(pl);
            int slot = -1;
            for (int i = 0; i < s.board.size(); i++) {
                Minion m = s.board.get(i);
                if (m.canAttack() && !done.contains(m.uid)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return;
            Minion a = s.board.get(slot);
            Target t = chooseAttackTarget(g, pl, a);
            if (t == null) {
                done.add(a.uid);
                continue;
            }
            Result r = g.attack(pl, slot, t, now);
            if (!r.ok()) done.add(a.uid);
            // Windfury minions may come back for a second swing; everything else is marked done by canAttack().
        }
    }

    private static Target chooseAttackTarget(Game g, int pl, Minion a) {
        List<Target> valid = g.validAttackTargets(pl);
        PlayerState enemy = g.opp(pl);
        Target hero = Target.hero(1 - pl);

        // Going face for lethal?
        if (valid.contains(hero)) {
            int potential = 0;
            for (Minion m : g.me(pl).board) {
                if (m.canAttack()) potential += m.attack * (m.maxAttacks() - m.attacksUsed);
            }
            if (potential >= enemy.heroHealth) return hero;
        }

        Target best = null;
        int bestScore = 0;
        for (Target t : valid) {
            if (t.isHero()) continue;
            Minion d = g.minionAt(t);
            boolean kills = d.divineShield ? false : a.attack >= d.health;
            boolean survives = a.divineShield || d.attack < a.health;
            int score = 0;
            if (kills) score += d.attack + d.health + 2;
            if (!survives) score -= a.attack + a.health;
            if (kills && survives) score += 5;
            if (d.hasTaunt()) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best != null) return best;
        if (valid.contains(hero)) return hero;
        return null; // only taunts left and no good trade: hold back
    }

    private static Effect firstChosen(List<Effect> effects) {
        for (Effect e : effects) {
            if (e.target().isChosen()) return e;
        }
        return null;
    }

    /** Chooses a sensible target for a chosen-target effect, or null if nothing worthwhile exists. */
    static Target pickTarget(Game g, int pl, Effect e, TargetKind tk) {
        List<Target> valid = g.validTargets(pl, tk);
        if (valid.isEmpty() || e == null) return null;
        Target hero = Target.hero(1 - pl);
        switch (e.kind()) {
            case DAMAGE -> {
                Target bestKill = null;
                int bestValue = -1;
                for (Target t : valid) {
                    if (t.owner() == pl) continue;
                    if (t.isHero()) continue;
                    Minion m = g.minionAt(t);
                    boolean kills = m.divineShield ? false : m.health <= e.a();
                    int value = m.attack * 2 + m.health;
                    if (kills && value > bestValue) {
                        bestValue = value;
                        bestKill = t;
                    }
                }
                if (g.opp(pl).heroHealth <= e.a() && valid.contains(hero)) return hero;
                if (bestKill != null) return bestKill;
                if (valid.contains(hero)) return hero;
                // No hero allowed: hit the biggest enemy minion.
                Target biggest = null;
                int biggestAtk = -1;
                for (Target t : valid) {
                    if (t.owner() == pl || t.isHero()) continue;
                    Minion m = g.minionAt(t);
                    if (m.attack > biggestAtk) {
                        biggestAtk = m.attack;
                        biggest = t;
                    }
                }
                return biggest;
            }
            case HEAL -> {
                Target worst = null;
                int missing = 0;
                for (Target t : valid) {
                    if (t.owner() != pl) continue;
                    int miss;
                    if (t.isHero()) {
                        miss = g.me(pl).heroMaxHealth - g.me(pl).heroHealth;
                    } else {
                        Minion m = g.minionAt(t);
                        miss = m.maxHealth - m.health;
                    }
                    if (miss > missing) {
                        missing = miss;
                        worst = t;
                    }
                }
                return missing >= 2 ? worst : null;
            }
            case BUFF -> {
                Target best = null;
                int bestAtk = -1;
                for (Target t : valid) {
                    if (t.owner() != pl || t.isHero()) continue;
                    Minion m = g.minionAt(t);
                    if (m.attack > bestAtk) {
                        bestAtk = m.attack;
                        best = t;
                    }
                }
                return best;
            }
            case DESTROY -> {
                Target best = null;
                int bestValue = -1;
                for (Target t : valid) {
                    if (t.owner() == pl || t.isHero()) continue;
                    Minion m = g.minionAt(t);
                    int value = m.attack + m.health;
                    if (value > bestValue) {
                        bestValue = value;
                        best = t;
                    }
                }
                return best;
            }
            default -> {
                return valid.get(0);
            }
        }
    }
}
