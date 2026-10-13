package com.cardduel.room;

import com.cardduel.engine.CardDef;
import com.cardduel.engine.Game;
import com.cardduel.engine.Minion;
import com.cardduel.engine.PlayerState;
import com.cardduel.engine.Result;
import com.cardduel.engine.Target;
import com.cardduel.engine.TargetKind;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns clicks into game actions for one player: the "what did the player just click on?" state machine.
 *
 * <p>Click flow (no drag and drop, everything is click, click):
 * <ul>
 *   <li>Hand card without a target: click it and it is played.</li>
 *   <li>Hand card with a target: click the card, then click the target.</li>
 *   <li>Attack: click your minion, then click an enemy minion or the enemy hero.</li>
 *   <li>Hero power: click it (and then a target if it needs one).</li>
 *   <li>Mulligan: click the cards to throw back, then confirm.</li>
 * </ul>
 * Pure Java. All methods take the game and the player's index; a failed action leaves a message for the player.
 */
public final class SeatController {

    public enum Mode { IDLE, CARD_TARGET, POWER_TARGET, ATTACK }

    private Mode mode = Mode.IDLE;
    private int pendingHand = -1;
    private int attacker = -1;
    private final Set<Integer> replace = new TreeSet<>();
    private String message = "";

    public void reset() {
        mode = Mode.IDLE;
        pendingHand = -1;
        attacker = -1;
        replace.clear();
        message = "";
    }

    // ------------------------------------------------------------------------------------------------
    // What the view needs
    // ------------------------------------------------------------------------------------------------

    public Mode mode() {
        return mode;
    }

    public int attackerSlot() {
        return mode == Mode.ATTACK ? attacker : -1;
    }

    public int pendingHandIndex() {
        return mode == Mode.CARD_TARGET ? pendingHand : -1;
    }

    public boolean isMarkedForReplace(int handIdx) {
        return replace.contains(handIdx);
    }

    /** Last feedback for the player ("Not enough mana." etc.). Empty when there is nothing to say. */
    public String message() {
        return message;
    }

    public void say(String text) {
        message = text == null ? "" : text;
    }

    /** One line telling the player what to do next. */
    public String hint(Game g, int me) {
        if (g.phase == Game.Phase.MULLIGAN) {
            return g.me(me).mulliganDone ? "Waiting for your opponent to choose..."
                    : "Click the cards you want to replace, then press Confirm.";
        }
        if (g.isOver()) return "";
        if (!g.isMyTurn(me)) return "Opponent's turn. Plan your next move!";
        return switch (mode) {
            case CARD_TARGET -> "Choose a target for " + g.me(me).hand.get(Math.max(0, Math.min(pendingHand, g.me(me).hand.size() - 1))).name + ". (! = valid)";
            case POWER_TARGET -> "Choose a target for your hero power. (! = valid)";
            case ATTACK -> "Choose what to attack. (! = valid)";
            case IDLE -> "Click a card to play it, or a minion marked * to attack.";
        };
    }

    /** Targets to mark with "!" in the current mode. */
    public Set<Target> highlighted(Game g, int me) {
        Set<Target> out = new HashSet<>();
        if (!g.isMyTurn(me)) return out;
        switch (mode) {
            case CARD_TARGET -> {
                TargetKind tk = g.handTargetKind(me, pendingHand);
                if (tk != null) out.addAll(g.validTargets(me, tk));
            }
            case POWER_TARGET -> {
                TargetKind tk = g.me(me).hero.chosenTarget();
                if (tk != null) out.addAll(g.validTargets(me, tk));
            }
            case ATTACK -> out.addAll(g.validAttackTargets(me));
            default -> {
            }
        }
        return out;
    }

    /** Drops selections that stopped making sense (turn changed, selected minion died, card left the hand...). */
    public void sanitize(Game g, int me) {
        if (g.phase != Game.Phase.PLAYING || !g.isMyTurn(me)) {
            if (mode != Mode.IDLE) mode = Mode.IDLE;
            pendingHand = -1;
            attacker = -1;
        } else if (mode == Mode.ATTACK && !g.canMinionAttack(me, attacker)) {
            mode = Mode.IDLE;
            attacker = -1;
        } else if (mode == Mode.CARD_TARGET && g.handTargetKind(me, pendingHand) == null) {
            mode = Mode.IDLE;
            pendingHand = -1;
        } else if (mode == Mode.POWER_TARGET && !g.canHeroPower(me)) {
            mode = Mode.IDLE;
        }
        if (g.phase != Game.Phase.MULLIGAN) replace.clear();
    }

    // ------------------------------------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------------------------------------

    public void clickHand(Game g, int me, int idx, long now) {
        message = "";
        PlayerState s = g.me(me);
        if (idx < 0 || idx >= s.hand.size()) return;
        if (g.phase == Game.Phase.MULLIGAN) {
            if (s.mulliganDone) {
                message = "You already chose. Waiting for your opponent.";
            } else if (s.hand.get(idx).id.equals(com.cardduel.engine.CardLibrary.COIN)) {
                message = "The Coin can't be replaced.";
            } else if (!replace.remove(idx)) {
                replace.add(idx);
            }
            return;
        }
        if (g.isOver()) return;
        if (!g.isMyTurn(me)) {
            message = "It's not your turn.";
            return;
        }
        if (mode == Mode.CARD_TARGET && pendingHand == idx) { // clicking the selected card again cancels
            cancelSelection();
            return;
        }
        cancelSelection();
        CardDef c = s.hand.get(idx);
        if (c.cost > s.mana) {
            message = "Not enough mana for " + c.name + ".";
            return;
        }
        if (c.isMinion() && s.board.size() >= Game.MAX_BOARD) {
            message = "Your board is full.";
            return;
        }
        TargetKind tk = c.chosenTarget();
        if (tk == null) {
            apply(g.playCard(me, idx, null, now));
            return;
        }
        boolean anyTarget = !g.validTargets(me, tk).isEmpty();
        if (!anyTarget) {
            if (c.isMinion()) { // battlecry with nothing to aim at: just play it
                apply(g.playCard(me, idx, null, now));
            } else {
                message = "There is no valid target for " + c.name + ".";
            }
            return;
        }
        mode = Mode.CARD_TARGET;
        pendingHand = idx;
    }

    public void clickOwnMinion(Game g, int me, int slot, long now) {
        message = "";
        if (g.isOver() || g.phase != Game.Phase.PLAYING) return;
        if (!g.isMyTurn(me)) {
            message = "It's not your turn.";
            return;
        }
        if (mode == Mode.CARD_TARGET || mode == Mode.POWER_TARGET) {
            chooseTarget(g, me, new Target(me, slot), now);
            return;
        }
        if (mode == Mode.ATTACK && attacker == slot) {
            cancelSelection();
            return;
        }
        Minion m = g.minionAt(new Target(me, slot));
        if (m == null) return;
        if (m.attack <= 0) {
            message = m.card.name + " has no attack.";
        } else if (m.attacksUsed >= m.maxAttacks()) {
            message = m.card.name + " already attacked this turn.";
        } else if (!g.canMinionAttack(me, slot)) {
            message = m.card.name + " just arrived. It can attack next turn.";
        } else {
            cancelSelection();
            mode = Mode.ATTACK;
            attacker = slot;
        }
    }

    public void clickEnemyMinion(Game g, int me, int slot, long now) {
        clickEnemy(g, me, new Target(1 - me, slot), now);
    }

    public void clickEnemyHero(Game g, int me, long now) {
        clickEnemy(g, me, Target.hero(1 - me), now);
    }

    public void clickOwnHero(Game g, int me, long now) {
        message = "";
        if (g.isOver() || g.phase != Game.Phase.PLAYING || !g.isMyTurn(me)) return;
        if (mode == Mode.CARD_TARGET || mode == Mode.POWER_TARGET) {
            chooseTarget(g, me, Target.hero(me), now);
        }
    }

    private void clickEnemy(Game g, int me, Target t, long now) {
        message = "";
        if (g.isOver() || g.phase != Game.Phase.PLAYING) return;
        if (!g.isMyTurn(me)) {
            message = "It's not your turn.";
            return;
        }
        switch (mode) {
            case CARD_TARGET, POWER_TARGET -> chooseTarget(g, me, t, now);
            case ATTACK -> {
                Result r = g.attack(me, attacker, t, now);
                if (r.ok()) {
                    cancelSelection();
                } else {
                    message = r.message();
                }
            }
            case IDLE -> message = "Select one of your minions first, then click what it should attack.";
        }
    }

    public void clickHeroPower(Game g, int me, long now) {
        message = "";
        if (g.isOver() || g.phase != Game.Phase.PLAYING) return;
        if (!g.isMyTurn(me)) {
            message = "It's not your turn.";
            return;
        }
        if (mode == Mode.POWER_TARGET) {
            cancelSelection();
            return;
        }
        cancelSelection();
        PlayerState s = g.me(me);
        if (s.heroPowerUsed) {
            message = "You already used your hero power this turn.";
        } else if (s.mana < s.hero.powerCost()) {
            message = "Not enough mana for " + s.hero.powerName() + ".";
        } else if (!g.canHeroPower(me)) {
            message = s.hero.chosenTarget() != null ? "There is no valid target." : "Your board is full.";
        } else if (s.hero.chosenTarget() != null) {
            mode = Mode.POWER_TARGET;
        } else {
            apply(g.heroPower(me, null, now));
        }
    }

    /** The big button under the board: Confirm (mulligan) or End Turn. */
    public void clickEnd(Game g, int me, long now) {
        message = "";
        if (g.isOver()) return;
        if (g.phase == Game.Phase.MULLIGAN) {
            apply(g.mulligan(me, replace, now));
            replace.clear();
            return;
        }
        cancelSelection();
        apply(g.endTurn(me, now));
    }

    public void clickCancel() {
        message = "";
        cancelSelection();
    }

    // ------------------------------------------------------------------------------------------------

    private void chooseTarget(Game g, int me, Target t, long now) {
        Result r;
        if (mode == Mode.CARD_TARGET) {
            r = g.playCard(me, pendingHand, t, now);
        } else {
            r = g.heroPower(me, t, now);
        }
        if (r.ok()) {
            cancelSelection();
        } else {
            message = r.message(); // stay in targeting mode so the player can pick again or cancel
        }
    }

    private void cancelSelection() {
        mode = Mode.IDLE;
        pendingHand = -1;
        attacker = -1;
    }

    private void apply(Result r) {
        if (!r.ok()) message = r.message();
    }
}
