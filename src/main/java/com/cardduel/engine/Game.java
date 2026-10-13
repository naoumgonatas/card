package com.cardduel.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One duel between two players, Hearthstone style. Pure Java, no Hytale classes, fully unit-testable.
 *
 * <p>All mutating methods return a {@link Result}. Time is passed in as epoch millis so tests can control it.
 * Not thread-safe: {@link com.cardduel.room.Room} serialises access.
 */
public final class Game {

    public static final int START_HEALTH = 30;
    public static final int DECK_SIZE = 30;
    public static final int MAX_HAND = 10;
    public static final int MAX_BOARD = 7;
    public static final int MAX_MANA = 10;
    public static final int HERO_POWER_COST = 2;
    public static final long TURN_MILLIS = 75_000L;
    public static final long MULLIGAN_MILLIS = 30_000L;
    /** A player who lets this many turns in a row time out forfeits. */
    public static final int MAX_IDLE_TURNS = 3;

    public enum Phase { MULLIGAN, PLAYING, OVER }

    public final PlayerState[] players = new PlayerState[2];
    public Phase phase = Phase.MULLIGAN;
    /** Index of the player whose turn it is (only meaningful while PLAYING). */
    public int turn;
    public int turnCount;
    /** -1 = nobody yet, 0/1 = that player won, 2 = draw. */
    public int winner = -1;
    public String endReason = "";
    public long deadline;
    public final int firstPlayer;

    private final Random rng;
    private int nextUid = 1;
    private final int[] idleTurns = new int[2];
    private final List<String> log = new ArrayList<>();

    public Game(DeckPreset deckA, String nameA, DeckPreset deckB, String nameB, int firstPlayer, Random rng, long now) {
        this.rng = rng;
        this.firstPlayer = firstPlayer;
        players[0] = new PlayerState(nameA, deckA);
        players[1] = new PlayerState(nameB, deckB);
        for (int i = 0; i < 2; i++) {
            PlayerState s = players[i];
            List<CardDef> cards = new ArrayList<>();
            for (String id : s.deckPreset.cards()) cards.add(CardLibrary.get(id));
            Collections.shuffle(cards, rng);
            s.deck.addAll(cards);
        }
        // First player gets 3 cards, second player gets 4 plus The Coin.
        for (int n = 0; n < 3; n++) draw(firstPlayer);
        int second = 1 - firstPlayer;
        for (int n = 0; n < 4; n++) draw(second);
        players[second].hand.add(CardLibrary.get(CardLibrary.COIN));
        deadline = now + MULLIGAN_MILLIS;
        log("The duel begins! " + players[firstPlayer].name + " goes first.");
    }

    // ================================================================================================
    // Queries (used by the UI and the bot)
    // ================================================================================================

    public boolean isOver() {
        return phase == Phase.OVER;
    }

    public PlayerState me(int pl) {
        return players[pl];
    }

    public PlayerState opp(int pl) {
        return players[1 - pl];
    }

    public Minion minionAt(Target t) {
        if (t == null || t.isHero() || t.owner() < 0 || t.owner() > 1) return null;
        List<Minion> b = players[t.owner()].board;
        return t.slot() >= 0 && t.slot() < b.size() ? b.get(t.slot()) : null;
    }

    public List<String> recentLog(int n) {
        return log.subList(Math.max(0, log.size() - n), log.size());
    }

    public boolean isMyTurn(int pl) {
        return phase == Phase.PLAYING && turn == pl;
    }

    public boolean canPlay(int pl, int handIdx) {
        if (!isMyTurn(pl)) return false;
        PlayerState s = players[pl];
        if (handIdx < 0 || handIdx >= s.hand.size()) return false;
        CardDef c = s.hand.get(handIdx);
        if (c.cost > s.mana) return false;
        if (c.isMinion() && s.board.size() >= MAX_BOARD) return false;
        TargetKind tk = c.chosenTarget();
        // Spells that need a target can't be cast with no valid target.
        return tk == null || c.isMinion() || !validTargets(pl, tk).isEmpty();
    }

    public boolean canMinionAttack(int pl, int slot) {
        if (!isMyTurn(pl)) return false;
        Minion m = minionAt(new Target(pl, slot));
        return m != null && m.canAttack();
    }

    public boolean canHeroPower(int pl) {
        if (!isMyTurn(pl)) return false;
        PlayerState s = players[pl];
        if (s.heroPowerUsed || s.mana < s.hero.powerCost()) return false;
        TargetKind tk = s.hero.chosenTarget();
        if (tk != null && validTargets(pl, tk).isEmpty()) return false;
        boolean summons = s.hero.powerEffects().stream().anyMatch(e -> e.kind() == EffectKind.SUMMON);
        return !(summons && s.board.size() >= MAX_BOARD);
    }

    public TargetKind handTargetKind(int pl, int handIdx) {
        PlayerState s = players[pl];
        if (handIdx < 0 || handIdx >= s.hand.size()) return null;
        return s.hand.get(handIdx).chosenTarget();
    }

    /** Legal targets for a "chosen" target kind from the point of view of player {@code owner}. */
    public List<Target> validTargets(int owner, TargetKind kind) {
        List<Target> out = new ArrayList<>();
        if (kind == null || !kind.isChosen()) return out;
        for (int side = 0; side < 2; side++) {
            boolean friendly = side == owner;
            boolean heroOk = switch (kind) {
                case ANY_CHARACTER -> true;
                case ENEMY_CHARACTER -> !friendly;
                default -> false;
            };
            if (heroOk) out.add(Target.hero(side));
            boolean minionOk = switch (kind) {
                case ANY_CHARACTER, ANY_MINION -> true;
                case ENEMY_CHARACTER, ENEMY_MINION -> !friendly;
                case FRIENDLY_MINION -> friendly;
                default -> false;
            };
            if (minionOk) {
                for (int i = 0; i < players[side].board.size(); i++) out.add(new Target(side, i));
            }
        }
        return out;
    }

    /** Targets an attacker of {@code pl} may legally hit, honouring Taunt. */
    public List<Target> validAttackTargets(int pl) {
        int enemy = 1 - pl;
        List<Target> taunts = new ArrayList<>();
        List<Target> all = new ArrayList<>();
        List<Minion> board = players[enemy].board;
        for (int i = 0; i < board.size(); i++) {
            Target t = new Target(enemy, i);
            all.add(t);
            if (board.get(i).hasTaunt()) taunts.add(t);
        }
        if (!taunts.isEmpty()) return taunts;
        all.add(Target.hero(enemy));
        return all;
    }

    // ================================================================================================
    // Player actions
    // ================================================================================================

    /** Replace the chosen hand cards (indices into the hand). The Coin can't be replaced. */
    public Result mulligan(int pl, Collection<Integer> replaceIdx, long now) {
        if (phase != Phase.MULLIGAN) return Result.fail("The mulligan is over.");
        PlayerState s = players[pl];
        if (s.mulliganDone) return Result.fail("You already chose.");
        List<Integer> idx = new ArrayList<>(new java.util.TreeSet<>(replaceIdx));
        List<CardDef> removed = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int i : idx) {
            if (i < 0 || i >= s.hand.size()) continue;
            CardDef c = s.hand.get(i);
            if (c.id.equals(CardLibrary.COIN)) continue;
            removed.add(c);
            slots.add(i);
        }
        // Draw the replacements first so you can't redraw what you threw away, then shuffle the old cards back.
        for (int k = 0; k < slots.size(); k++) {
            if (s.deck.isEmpty()) break;
            s.hand.set(slots.get(k), s.deck.removeLast());
        }
        for (CardDef c : removed) s.deck.addLast(c);
        List<CardDef> reshuffled = new ArrayList<>(s.deck);
        Collections.shuffle(reshuffled, rng);
        s.deck.clear();
        s.deck.addAll(reshuffled);
        s.mulliganDone = true;
        log(s.name + " keeps " + (s.hand.size() - removed.size()) + " cards.");
        if (players[0].mulliganDone && players[1].mulliganDone) beginPlaying(now);
        return Result.OK;
    }

    public Result playCard(int pl, int handIdx, Target chosen, long now) {
        Result r = requireTurn(pl);
        if (!r.ok()) return r;
        PlayerState s = players[pl];
        if (handIdx < 0 || handIdx >= s.hand.size()) return Result.fail("No such card.");
        CardDef c = s.hand.get(handIdx);
        if (c.cost > s.mana) return Result.fail("Not enough mana.");
        if (c.isMinion() && s.board.size() >= MAX_BOARD) return Result.fail("Your board is full.");

        TargetKind tk = c.chosenTarget();
        Target effectiveTarget = null;
        if (tk != null) {
            List<Target> valid = validTargets(pl, tk);
            if (valid.isEmpty()) {
                if (!c.isMinion()) return Result.fail("There is no valid target for that.");
            } else {
                if (chosen == null || !valid.contains(chosen)) return Result.fail("Choose a valid target.");
                effectiveTarget = chosen;
            }
        }

        idleTurns[pl] = 0;
        s.mana -= c.cost;
        s.hand.remove(handIdx);
        if (c.isMinion()) {
            Minion m = new Minion(nextUid++, c);
            s.board.add(m);
            log(s.name + " plays " + c.name + ".");
            runEffects(pl, c.onPlay, effectiveTarget, m);
        } else {
            log(s.name + " casts " + c.name + ".");
            runEffects(pl, c.onPlay, effectiveTarget, null);
        }
        resolveDeaths();
        return Result.OK;
    }

    public Result attack(int pl, int slot, Target target, long now) {
        Result r = requireTurn(pl);
        if (!r.ok()) return r;
        Minion a = minionAt(new Target(pl, slot));
        if (a == null) return Result.fail("No such minion.");
        if (a.attack <= 0) return Result.fail("That minion has no attack.");
        if (a.sick && !a.keywords.contains(Keyword.CHARGE)) return Result.fail("That minion just arrived and can't attack yet.");
        if (a.attacksUsed >= a.maxAttacks()) return Result.fail("That minion already attacked.");
        if (target == null || target.owner() != 1 - pl) return Result.fail("Pick an enemy to attack.");
        List<Target> valid = validAttackTargets(pl);
        if (!valid.contains(target)) return Result.fail("You must attack a minion with Taunt first.");

        idleTurns[pl] = 0;
        a.attacksUsed++;
        PlayerState enemy = players[1 - pl];
        if (target.isHero()) {
            log(a.card.name + " hits " + enemy.name + " for " + a.attack + ".");
            damage(target, a.attack);
        } else {
            Minion d = minionAt(target);
            log(a.card.name + " attacks " + d.card.name + ".");
            int dealt = a.attack;
            int taken = d.attack;
            damage(target, dealt);
            damage(new Target(pl, slot), taken);
        }
        resolveDeaths();
        return Result.OK;
    }

    public Result heroPower(int pl, Target chosen, long now) {
        Result r = requireTurn(pl);
        if (!r.ok()) return r;
        PlayerState s = players[pl];
        HeroDef h = s.hero;
        if (s.heroPowerUsed) return Result.fail("You already used your hero power this turn.");
        if (s.mana < h.powerCost()) return Result.fail("Not enough mana.");
        boolean summons = h.powerEffects().stream().anyMatch(e -> e.kind() == EffectKind.SUMMON);
        if (summons && s.board.size() >= MAX_BOARD) return Result.fail("Your board is full.");
        TargetKind tk = h.chosenTarget();
        if (tk != null) {
            List<Target> valid = validTargets(pl, tk);
            if (valid.isEmpty()) return Result.fail("There is no valid target.");
            if (chosen == null || !valid.contains(chosen)) return Result.fail("Choose a valid target.");
        }
        idleTurns[pl] = 0;
        s.mana -= h.powerCost();
        s.heroPowerUsed = true;
        log(s.name + " uses " + h.powerName() + ".");
        runEffects(pl, h.powerEffects(), chosen, null);
        resolveDeaths();
        return Result.OK;
    }

    public Result endTurn(int pl, long now) {
        Result r = requireTurn(pl);
        if (!r.ok()) return r;
        idleTurns[pl] = 0;
        log(players[pl].name + " ends the turn.");
        startTurn(1 - pl, now);
        return Result.OK;
    }

    public Result concede(int pl) {
        if (phase == Phase.OVER) return Result.fail("The duel is already over.");
        finish(1 - pl, players[pl].name + " conceded.");
        return Result.OK;
    }

    /** Call about once a second: handles turn timers. Returns true if anything changed. */
    public boolean tick(long now) {
        if (phase == Phase.OVER || now <= deadline) return false;
        if (phase == Phase.MULLIGAN) {
            for (int i = 0; i < 2; i++) {
                if (!players[i].mulliganDone) mulligan(i, List.of(), now);
            }
            return true;
        }
        int pl = turn;
        idleTurns[pl]++;
        log(players[pl].name + " ran out of time.");
        if (idleTurns[pl] >= MAX_IDLE_TURNS) {
            finish(1 - pl, players[pl].name + " was away too long and forfeits.");
        } else {
            startTurn(1 - pl, now);
        }
        return true;
    }

    // ================================================================================================
    // Internals
    // ================================================================================================

    private Result requireTurn(int pl) {
        if (phase == Phase.OVER) return Result.fail("The duel is over.");
        if (phase == Phase.MULLIGAN) return Result.fail("Finish the mulligan first.");
        if (turn != pl) return Result.fail("It's not your turn.");
        return Result.OK;
    }

    private void beginPlaying(long now) {
        phase = Phase.PLAYING;
        startTurn(firstPlayer, now);
    }

    private void startTurn(int pl, long now) {
        turn = pl;
        turnCount++;
        PlayerState s = players[pl];
        s.maxMana = Math.min(MAX_MANA, s.maxMana + 1);
        s.mana = s.maxMana;
        s.heroPowerUsed = false;
        for (Minion m : s.board) {
            m.sick = false;
            m.attacksUsed = 0;
        }
        deadline = now + TURN_MILLIS;
        draw(pl);
        resolveDeaths();
    }

    private void draw(int pl) {
        PlayerState s = players[pl];
        if (s.deck.isEmpty()) {
            s.fatigue++;
            log(s.name + " has no cards left and takes " + s.fatigue + " fatigue damage.");
            damage(Target.hero(pl), s.fatigue);
            return;
        }
        CardDef c = s.deck.removeLast();
        if (s.hand.size() >= MAX_HAND) {
            log(s.name + "'s hand is full: " + c.name + " is burned.");
            return;
        }
        s.hand.add(c);
    }

    private void damage(Target t, int amount) {
        if (amount <= 0) return;
        if (t.isHero()) {
            players[t.owner()].heroHealth -= amount;
            return;
        }
        Minion m = minionAt(t);
        if (m == null) return;
        if (m.divineShield) {
            m.divineShield = false;
            return;
        }
        m.health -= amount;
    }

    private void heal(Target t, int amount) {
        if (t.isHero()) {
            PlayerState s = players[t.owner()];
            s.heroHealth = Math.min(s.heroMaxHealth, s.heroHealth + amount);
            return;
        }
        Minion m = minionAt(t);
        if (m != null) m.health = Math.min(m.maxHealth, m.health + amount);
    }

    private void runEffects(int owner, List<Effect> effects, Target chosen, Minion source) {
        for (Effect e : effects) {
            switch (e.kind()) {
                case DAMAGE -> {
                    for (Target t : resolve(owner, e.target(), chosen, source)) damage(t, e.a());
                }
                case HEAL -> {
                    for (Target t : resolve(owner, e.target(), chosen, source)) heal(t, e.a());
                }
                case DRAW -> {
                    for (int i = 0; i < e.a(); i++) draw(owner);
                }
                case BUFF -> {
                    for (Target t : resolve(owner, e.target(), chosen, source)) {
                        Minion m = minionAt(t);
                        if (m == null) continue;
                        m.attack += e.a();
                        m.health += e.b();
                        m.maxHealth += e.b();
                    }
                }
                case SUMMON -> {
                    for (int i = 0; i < e.a(); i++) summon(owner, CardLibrary.get(e.cardId()));
                }
                case GAIN_MANA -> players[owner].mana = Math.min(MAX_MANA, players[owner].mana + e.a());
                case DESTROY -> {
                    for (Target t : resolve(owner, e.target(), chosen, source)) {
                        Minion m = minionAt(t);
                        if (m != null) m.health = 0;
                    }
                }
            }
        }
    }

    private void summon(int owner, CardDef card) {
        PlayerState s = players[owner];
        if (s.board.size() >= MAX_BOARD) return;
        s.board.add(new Minion(nextUid++, card));
    }

    private List<Target> resolve(int owner, TargetKind kind, Target chosen, Minion source) {
        List<Target> out = new ArrayList<>();
        int enemy = 1 - owner;
        switch (kind) {
            case ANY_CHARACTER, ANY_MINION, ENEMY_CHARACTER, ENEMY_MINION, FRIENDLY_MINION -> {
                if (chosen != null) out.add(chosen);
            }
            case ENEMY_HERO -> out.add(Target.hero(enemy));
            case OWN_HERO -> out.add(Target.hero(owner));
            case ALL_ENEMY_MINIONS -> addBoard(out, enemy);
            case ALL_FRIENDLY_MINIONS -> addBoard(out, owner);
            case ALL_MINIONS -> {
                addBoard(out, owner);
                addBoard(out, enemy);
            }
            case ALL_CHARACTERS -> {
                out.add(Target.hero(owner));
                out.add(Target.hero(enemy));
                addBoard(out, owner);
                addBoard(out, enemy);
            }
            case RANDOM_ENEMY_CHARACTER -> {
                List<Target> pool = new ArrayList<>();
                pool.add(Target.hero(enemy));
                addBoard(pool, enemy);
                out.add(pool.get(rng.nextInt(pool.size())));
            }
            case RANDOM_ENEMY_MINION -> {
                List<Target> pool = new ArrayList<>();
                addBoard(pool, enemy);
                if (!pool.isEmpty()) out.add(pool.get(rng.nextInt(pool.size())));
            }
            case SELF -> {
                if (source != null) {
                    int idx = players[owner].board.indexOf(source);
                    if (idx >= 0) out.add(new Target(owner, idx));
                }
            }
        }
        return out;
    }

    private void addBoard(List<Target> out, int side) {
        for (int i = 0; i < players[side].board.size(); i++) out.add(new Target(side, i));
    }

    /** Removes dead minions (running deathrattles) until the board is stable, then checks for a winner. */
    private void resolveDeaths() {
        boolean again = true;
        int guard = 0;
        while (again && guard++ < 100) {
            again = false;
            int[] order = {turn, 1 - turn};
            for (int side : order) {
                List<Minion> dead = new ArrayList<>();
                for (Minion m : players[side].board) {
                    if (m.health <= 0) dead.add(m);
                }
                if (dead.isEmpty()) continue;
                again = true;
                players[side].board.removeAll(dead);
                for (Minion m : dead) {
                    log(m.card.name + " dies.");
                    if (!m.card.deathrattle.isEmpty()) runEffects(side, m.card.deathrattle, null, m);
                }
            }
        }
        checkWinner();
    }

    private void checkWinner() {
        if (phase == Phase.OVER) return;
        boolean d0 = players[0].heroHealth <= 0;
        boolean d1 = players[1].heroHealth <= 0;
        if (d0 && d1) finish(2, "Both heroes fall. It's a draw!");
        else if (d0) finish(1, players[0].name + " has been defeated.");
        else if (d1) finish(0, players[1].name + " has been defeated.");
    }

    private void finish(int winnerIdx, String reason) {
        phase = Phase.OVER;
        winner = winnerIdx;
        endReason = reason;
        log(reason);
    }

    private void log(String line) {
        log.add(line);
        if (log.size() > 200) log.subList(0, 100).clear();
    }

    /** For tests and the bot: the number of idle turns recorded for a player. */
    public int idleTurns(int pl) {
        return idleTurns[pl];
    }
}
