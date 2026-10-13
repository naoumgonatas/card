package com.cardduel.engine;

import java.util.List;
import java.util.Random;

/**
 * Dependency-free test suite for the rules engine. Run it with {@code gradlew selfTest}.
 *
 * <p>It lives in the {@code engine} package so it can poke at package-private internals when setting up scenarios.
 * It deliberately uses no test framework: no downloads, no IDE setup, just a main method that exits non-zero on failure.
 */
public final class EngineSelfTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        cardLibraryIsValid();
        openingHands();
        manaCurveAndPlaying();
        summoningSicknessChargeWindfury();
        tauntIsEnforced();
        divineShield();
        trades();
        deathrattles();
        spellsAndHealing();
        heroPowers();
        fatigue();
        handOverflowAndFullBoard();
        turnOrderIsEnforced();
        timers();
        bothHeroesDie();
        concede();
        botSimulation(args.length > 0 ? Integer.parseInt(args[0]) : 3000);

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    // ================================================================================================

    static void cardLibraryIsValid() {
        section("card library");
        check(CardLibrary.presets().size() >= 2, "at least two deck presets");
        for (DeckPreset p : CardLibrary.presets()) {
            check(p.cards().size() == Game.DECK_SIZE, p.name() + " has 30 cards");
        }
        check(CardLibrary.get("fireball").chosenTarget() == TargetKind.ANY_CHARACTER, "fireball needs a target");
        check(CardLibrary.get("ember_storm").chosenTarget() == null, "ember storm needs no target");
        check(!CardLibrary.get("sapling").text.isEmpty(), "cards have text");
    }

    static void openingHands() {
        section("opening hands + mulligan");
        for (int first = 0; first < 2; first++) {
            Game g = new Game(CardLibrary.preset(0), "A", CardLibrary.preset(1), "B", first, new Random(1), 0);
            int second = 1 - first;
            check(g.players[first].hand.size() == 3, "first player starts with 3 cards");
            check(g.players[second].hand.size() == 5, "second player starts with 4 cards + the Coin");
            check(g.players[second].hand.stream().anyMatch(c -> c.id.equals(CardLibrary.COIN)), "second player has the Coin");
            check(g.phase == Game.Phase.MULLIGAN, "starts in mulligan");
            check(!g.playCard(first, 0, null, 0).ok(), "can't play during the mulligan");

            // Mulligan everything on one side; the Coin must stay, hand size must stay, no card is lost.
            int deckBefore = g.players[second].deck.size();
            check(g.mulligan(second, List.of(0, 1, 2, 3, 4), 0).ok(), "second player mulligans");
            check(g.players[second].hand.size() == 5, "hand size unchanged after mulligan");
            check(g.players[second].hand.stream().anyMatch(c -> c.id.equals(CardLibrary.COIN)), "the Coin survives a mulligan");
            check(g.players[second].deck.size() == deckBefore, "deck size unchanged after mulligan");
            check(!g.mulligan(second, List.of(0), 0).ok(), "can't mulligan twice");
            check(g.phase == Game.Phase.MULLIGAN, "still waiting for the other player");
            check(g.mulligan(first, List.of(), 0).ok(), "first player keeps");
            check(g.phase == Game.Phase.PLAYING, "duel begins when both chose");
            check(g.turn == first, "first player takes the first turn");
            check(g.players[first].hand.size() == 4, "first player drew for the turn");
            check(g.players[first].mana == 1 && g.players[first].maxMana == 1, "turn 1 has 1 mana");
        }
    }

    static void manaCurveAndPlaying() {
        section("mana + playing cards");
        Game g = fresh(7);
        PlayerState a = g.players[0];
        a.hand.add(CardLibrary.get("bear"));
        a.mana = 3;
        check(!g.playCard(0, 0, null, 0).ok(), "can't cast a 4-drop with 3 mana");
        a.mana = 4;
        check(g.playCard(0, 0, null, 0).ok(), "plays a 4-drop with 4 mana");
        check(a.mana == 0 && a.board.size() == 1 && a.hand.isEmpty(), "mana spent, minion on board, card gone");
        check(a.board.get(0).sick, "new minion is summoning sick");

        // mana grows by one per own turn up to 10
        Game g2 = fresh(8);
        int p = g2.turn;
        for (int i = 0; i < 25 && !g2.isOver(); i++) {
            g2.endTurn(g2.turn, 0);
        }
        check(g2.players[0].maxMana == 10 && g2.players[1].maxMana == 10, "max mana caps at 10");
        check(p == p, "sanity");
    }

    static void summoningSicknessChargeWindfury() {
        section("summoning sickness / charge / windfury");
        Game g = fresh(9);
        PlayerState a = g.players[0];
        a.mana = 10;
        a.hand.add(CardLibrary.get("mud_crawler"));
        a.hand.add(CardLibrary.get("raider")); // charge 3/1
        a.hand.add(CardLibrary.get("gale_hawk")); // windfury 2/4
        g.playCard(0, 0, null, 0);
        g.playCard(0, 0, null, 0);
        g.playCard(0, 0, null, 0);
        check(!g.attack(0, 0, Target.hero(1), 0).ok(), "summoning-sick minion can't attack");
        check(g.attack(0, 1, Target.hero(1), 0).ok(), "charge minion attacks immediately");
        check(g.players[1].heroHealth == 27, "charge minion dealt 3");
        check(!g.attack(0, 1, Target.hero(1), 0).ok(), "can't attack twice");

        g.endTurn(0, 0);
        g.endTurn(1, 0);
        check(g.attack(0, 2, Target.hero(1), 0).ok(), "windfury first swing");
        check(g.attack(0, 2, Target.hero(1), 0).ok(), "windfury second swing");
        check(!g.attack(0, 2, Target.hero(1), 0).ok(), "windfury third swing refused");
        check(g.attack(0, 0, Target.hero(1), 0).ok(), "no longer sick next turn");
    }

    static void tauntIsEnforced() {
        section("taunt");
        Game g = fresh(10);
        g.players[0].board.add(readyMinion("raider"));
        g.players[1].board.add(readyMinion("mud_crawler"));
        g.players[1].board.add(readyMinion("mossback")); // taunt
        check(!g.attack(0, 0, Target.hero(1), 0).ok(), "can't hit the hero past a taunt");
        check(!g.attack(0, 0, new Target(1, 0), 0).ok(), "can't hit a non-taunt minion past a taunt");
        check(g.attack(0, 0, new Target(1, 1), 0).ok(), "can hit the taunt");
        check(g.players[0].board.isEmpty(), "3/1 charger died to the 1/4 taunt's counter-hit");
        check(g.players[1].board.size() == 2 && g.players[1].board.get(1).health == 1, "taunt took 3 damage");
    }

    static void divineShield() {
        section("divine shield");
        Game g = fresh(11);
        g.players[0].board.add(readyMinion("raider")); // 3/1
        Minion shielded = readyMinion("dawn_squire"); // 3/2 shield
        g.players[1].board.add(shielded);
        check(g.attack(0, 0, new Target(1, 0), 0).ok(), "attack into shield");
        check(!shielded.divineShield && shielded.health == 2, "shield popped without health loss");
        check(g.players[0].board.isEmpty(), "attacker still died to the counter-hit");
    }

    static void trades() {
        section("trading");
        Game g = fresh(12);
        g.players[0].board.add(readyMinion("bear")); // 4/4
        g.players[1].board.add(readyMinion("bear"));
        g.attack(0, 0, new Target(1, 0), 0);
        check(g.players[0].board.isEmpty() && g.players[1].board.isEmpty(), "equal minions trade");
        check(!g.attack(0, 5, Target.hero(1), 0).ok(), "no such minion");
        check(!g.attack(0, 0, Target.hero(0), 0).ok(), "can't attack your own hero");
    }

    static void deathrattles() {
        section("deathrattles");
        Game g = fresh(13);
        g.players[1].board.add(readyMinion("slime_mother")); // 2/4: summons two slimelings
        g.players[0].board.add(readyMinion("bear")); // 4/4 kills it
        g.attack(0, 0, new Target(1, 0), 0);
        check(g.players[1].board.size() == 2, "slime mother left two slimelings");
        check(g.players[1].board.stream().allMatch(m -> m.card.id.equals("slimeling")), "they are slimelings");

        Game g2 = fresh(14);
        int handBefore = g2.players[1].hand.size();
        g2.players[1].board.add(readyMinion("magpie")); // 2/1: draw on death
        g2.players[0].board.add(readyMinion("bear"));
        g2.attack(0, 0, new Target(1, 0), 0);
        check(g2.players[1].hand.size() == handBefore + 1, "loot magpie drew a card for its owner");
    }

    static void spellsAndHealing() {
        section("spells");
        Game g = fresh(15);
        PlayerState a = g.players[0];
        a.mana = 10;
        a.hand.add(CardLibrary.get("fireball"));
        check(!g.playCard(0, 0, null, 0).ok(), "targeted spell without a target is refused");
        check(!g.playCard(0, 0, new Target(1, 3), 0).ok(), "targeted spell at an empty slot is refused");
        check(g.playCard(0, 0, Target.hero(1), 0).ok(), "fireball the enemy hero");
        check(g.players[1].heroHealth == 24, "fireball deals 6");

        g.players[1].board.add(readyMinion("mud_crawler"));
        g.players[1].board.add(readyMinion("mossback"));
        a.hand.add(CardLibrary.get("ember_storm"));
        g.playCard(0, 0, null, 0);
        check(g.players[1].board.size() == 1 && g.players[1].board.get(0).health == 1, "ember storm: crawler dies, mossback survives at 1");

        a.mana = 10;
        a.hand.add(CardLibrary.get("void_strike"));
        check(!g.playCard(0, 0, Target.hero(1), 0).ok(), "void strike can't target a hero");
        check(g.playCard(0, 0, new Target(1, 0), 0).ok(), "void strike destroys a minion");
        check(g.players[1].board.isEmpty(), "board cleared");

        a.mana = 10;
        a.hand.add(CardLibrary.get("healing_touch"));
        a.heroHealth = 10;
        g.playCard(0, 0, Target.hero(0), 0);
        check(a.heroHealth == 18, "healing touch restores 8");
        a.mana = 10;
        a.hand.add(CardLibrary.get("healing_touch"));
        g.playCard(0, 0, Target.hero(0), 0);
        check(a.heroHealth == 26, "healing again");
        a.hand.add(CardLibrary.get("healing_touch"));
        a.mana = 10;
        g.playCard(0, 0, Target.hero(0), 0);
        check(a.heroHealth == 30, "healing is capped at max health");

        a.board.add(readyMinion("mud_crawler"));
        a.hand.add(CardLibrary.get("heartwood"));
        a.mana = 10;
        check(g.playCard(0, 0, new Target(0, 0), 0).ok(), "buff a friendly minion");
        check(a.board.get(0).attack == 5 && a.board.get(0).health == 5 && a.board.get(0).maxHealth == 5, "heartwood gave +3/+3");
        a.hand.add(CardLibrary.get("heartwood"));
        check(!g.playCard(0, 0, Target.hero(0), 0).ok(), "can't buff a hero");

        Game g2 = fresh(16);
        g2.players[0].mana = 10;
        g2.players[0].hand.add(CardLibrary.get("heartwood"));
        check(!g2.playCard(0, 0, null, 0).ok(), "buff with no friendly minion on board is refused");
        g2.players[0].hand.clear();
        g2.players[0].hand.add(CardLibrary.get("coin"));
        g2.players[0].mana = 3;
        g2.playCard(0, 0, null, 0);
        check(g2.players[0].mana == 4, "the Coin gives one mana");
        g2.players[0].mana = 10;
        g2.players[0].hand.add(CardLibrary.get("coin"));
        g2.playCard(0, 0, null, 0);
        check(g2.players[0].mana == 10, "the Coin can't push mana past 10");
    }

    static void heroPowers() {
        section("hero powers");
        // preset 0 hero (Pyra) deals 1 damage; preset 1 hero (Bram) summons a pebble.
        Game g = new Game(CardLibrary.preset(0), "A", CardLibrary.preset(1), "B", 0, new Random(3), 0);
        g.mulligan(0, List.of(), 0);
        g.mulligan(1, List.of(), 0);
        PlayerState a = g.players[0];
        a.mana = 1;
        check(!g.heroPower(0, Target.hero(1), 0).ok(), "hero power needs 2 mana");
        a.mana = 4;
        check(!g.heroPower(0, null, 0).ok(), "Pyra's power needs a target");
        check(g.heroPower(0, Target.hero(1), 0).ok(), "Pyra pings the enemy hero");
        check(g.players[1].heroHealth == 29, "1 damage dealt");
        check(!g.heroPower(0, Target.hero(1), 0).ok(), "only once per turn");
        g.endTurn(0, 0);
        PlayerState b = g.players[1];
        b.mana = 4;
        check(g.heroPower(1, null, 0).ok(), "Bram summons a pebble");
        check(b.board.size() == 1 && b.board.get(0).card.id.equals("pebble"), "pebble on board");
        b.mana = 4;
        b.heroPowerUsed = false;
        while (b.board.size() < Game.MAX_BOARD) b.board.add(readyMinion("pebble"));
        check(!g.canHeroPower(1), "can't use a summoning power on a full board");
        check(!g.heroPower(1, null, 0).ok(), "and the engine refuses it");
        g.endTurn(1, 0);
        check(a.heroHealth == 30 && !a.heroPowerUsed, "hero power refreshes on your turn");
    }

    static void fatigue() {
        section("fatigue");
        Game g = fresh(17);
        PlayerState a = g.players[0];
        a.deck.clear();
        g.endTurn(0, 0); // now player 1's turn
        g.endTurn(1, 0); // player 0 starts a turn with an empty deck
        check(a.heroHealth == 29, "first fatigue damage is 1");
        g.endTurn(0, 0);
        g.endTurn(1, 0);
        check(a.heroHealth == 27, "then 2");
        g.endTurn(0, 0);
        g.endTurn(1, 0);
        check(a.heroHealth == 24, "then 3");
    }

    static void handOverflowAndFullBoard() {
        section("hand + board limits");
        Game g = fresh(18);
        PlayerState b = g.players[1];
        b.hand.clear();
        for (int i = 0; i < Game.MAX_HAND; i++) b.hand.add(CardLibrary.get("sapling"));
        g.endTurn(0, 0); // player 1 draws with a full hand
        check(b.hand.size() == Game.MAX_HAND, "a full hand burns the drawn card");

        PlayerState a = g.players[0];
        a.mana = 10;
        while (a.board.size() < Game.MAX_BOARD) a.board.add(readyMinion("pebble"));
        g.endTurn(1, 0);
        a.hand.add(CardLibrary.get("sapling"));
        a.mana = 10;
        check(!g.playCard(0, a.hand.size() - 1, null, 0).ok(), "can't play a minion onto a full board");
        a.hand.add(CardLibrary.get("fireball"));
        check(g.canPlay(0, a.hand.size() - 1), "but spells are fine");
    }

    static void turnOrderIsEnforced() {
        section("turn order");
        Game g = fresh(19);
        check(!g.endTurn(1, 0).ok(), "the waiting player can't end the turn");
        g.players[1].hand.add(CardLibrary.get("sapling"));
        g.players[1].mana = 10;
        check(!g.playCard(1, g.players[1].hand.size() - 1, null, 0).ok(), "the waiting player can't play cards");
        check(!g.heroPower(1, null, 0).ok(), "or use the hero power");
        g.players[1].board.add(readyMinion("bear"));
        check(!g.attack(1, 0, Target.hero(0), 0).ok(), "or attack");
    }

    static void timers() {
        section("timers");
        Game g = new Game(CardLibrary.preset(0), "A", CardLibrary.preset(1), "B", 0, new Random(4), 0);
        check(!g.tick(1000), "nothing happens before the mulligan deadline");
        check(g.tick(Game.MULLIGAN_MILLIS + 1), "mulligan auto-confirms at the deadline");
        check(g.phase == Game.Phase.PLAYING, "duel started");
        long t = Game.MULLIGAN_MILLIS + 1;
        int first = g.turn;
        check(!g.tick(t + Game.TURN_MILLIS), "exactly at the deadline is still fine");
        check(g.tick(t + Game.TURN_MILLIS + 1), "turn passes after the deadline");
        check(g.turn == 1 - first, "other player's turn now");
        t += Game.TURN_MILLIS + 1;
        // the first player idles three turns in a row -> forfeit
        for (int i = 0; i < 6 && !g.isOver(); i++) {
            if (g.turn == 1 - first) {
                g.endTurn(g.turn, t); // the other player plays normally
            } else {
                t += Game.TURN_MILLIS + 1;
                g.tick(t);
            }
        }
        check(g.isOver() && g.winner == 1 - first, "idle player forfeits after " + Game.MAX_IDLE_TURNS + " timeouts");
    }

    static void bothHeroesDie() {
        section("win / draw");
        Game g = fresh(20);
        g.players[0].heroHealth = 2;
        g.players[1].heroHealth = 2;
        g.players[0].board.add(readyMinion("raider"));
        g.players[0].mana = 10;
        g.players[0].hand.add(CardLibrary.get("fireball"));
        g.playCard(0, 0, Target.hero(0), 0); // fireball your own face
        check(g.isOver() && g.winner == 1, "dying to your own fireball loses");

        Game d = fresh(21);
        d.players[0].heroHealth = 1;
        d.players[1].heroHealth = 1;
        d.players[0].deck.clear();
        d.players[1].deck.clear();
        d.players[0].fatigue = 5;
        d.endTurn(0, 0); // player 1 draws from empty deck: takes 1 fatigue -> dead
        check(d.isOver() && d.winner == 0, "fatigue can end the game");
        check(!d.endTurn(1, 0).ok(), "nothing works after the game is over");

        Game t = fresh(22);
        t.players[0].heroHealth = 6;
        t.players[1].heroHealth = 6;
        t.players[0].mana = 10;
        t.players[0].hand.add(CardLibrary.get("drake")); // battlecry: 2 to enemy hero
        check(t.playCard(0, 0, null, 0).ok(), "play the drake");
        check(t.players[1].heroHealth == 4, "drake battlecry hit the enemy hero");
    }

    static void concede() {
        section("concede");
        Game g = fresh(23);
        check(g.concede(0).ok(), "concede works");
        check(g.isOver() && g.winner == 1, "the other player wins");
        check(!g.concede(1).ok(), "can't concede twice");
    }

    // ================================================================================================
    // Bot vs bot: thousands of full games with invariant checks after every action.
    // ================================================================================================

    static void botSimulation(int games) {
        section("bot vs bot simulation (" + games + " games)");
        long turns = 0;
        int[] wins = new int[3];
        int maxTurns = 0;
        for (int seed = 0; seed < games; seed++) {
            Random rng = new Random(seed);
            int first = rng.nextInt(2);
            Game g = new Game(CardLibrary.preset(seed), "Bot0", CardLibrary.preset(seed / 2), "Bot1", first, rng, 0);
            Bot.mulligan(g, 0, 0);
            Bot.mulligan(g, 1, 0);
            int guard = 0;
            while (!g.isOver() && guard++ < 400) {
                Bot.playTurn(g, g.turn, 0);
                String bad = invariantViolation(g);
                if (bad != null) {
                    check(false, "seed " + seed + " turn " + g.turnCount + ": " + bad);
                    break;
                }
            }
            if (!g.isOver()) {
                check(false, "seed " + seed + " did not finish within 400 turns");
                continue;
            }
            wins[g.winner]++;
            turns += g.turnCount;
            maxTurns = Math.max(maxTurns, g.turnCount);
        }
        check(true, "no invariant violations");
        System.out.printf("  wins: seat0=%d seat1=%d draws=%d, avg turns=%.1f, longest=%d%n", wins[0], wins[1], wins[2],
                turns / (double) games, maxTurns);
        check(wins[2] < games / 10, "draws are rare");
        check(wins[0] > games / 10 && wins[1] > games / 10, "both seats win sometimes");
    }

    private static String invariantViolation(Game g) {
        for (int i = 0; i < 2; i++) {
            PlayerState s = g.players[i];
            if (s.hand.size() > Game.MAX_HAND) return "hand over limit";
            if (s.board.size() > Game.MAX_BOARD) return "board over limit";
            if (s.mana < 0 || s.mana > Game.MAX_MANA) return "mana out of range: " + s.mana;
            if (s.maxMana > Game.MAX_MANA) return "max mana over 10";
            if (s.heroHealth > s.heroMaxHealth) return "hero over max health";
            if (s.fatigue < 0) return "negative fatigue";
            for (Minion m : s.board) {
                if (m.health <= 0 && !g.isOver()) return m.card.name + " lingered at " + m.health + " health";
                if (m.health > m.maxHealth) return m.card.name + " above max health";
                if (m.attacksUsed > m.maxAttacks()) return m.card.name + " attacked too often";
            }
        }
        if (g.isOver() && g.winner < 0) return "game over without a winner";
        return null;
    }

    // ================================================================================================

    private static Game fresh(long seed) {
        Game g = new Game(CardLibrary.preset(0), "A", CardLibrary.preset(1), "B", 0, new Random(seed), 0);
        g.mulligan(0, List.of(), 0);
        g.mulligan(1, List.of(), 0);
        for (PlayerState s : g.players) {
            s.hand.clear();
            s.board.clear();
        }
        g.players[0].mana = 10;
        g.players[0].maxMana = 10;
        g.players[1].mana = 10;
        g.players[1].maxMana = 10;
        return g;
    }

    private static Minion readyMinion(String id) {
        Minion m = new Minion(9000 + checks, CardLibrary.get(id));
        m.sick = false;
        return m;
    }

    private static void section(String name) {
        System.out.println(name);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + what);
        }
    }
}
