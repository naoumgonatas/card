package com.cardduel.ui;

import com.cardduel.engine.CardDef;
import com.cardduel.engine.CardLibrary;
import com.cardduel.engine.DeckPreset;
import com.cardduel.engine.Game;
import com.cardduel.engine.HeroDef;
import com.cardduel.engine.Keyword;
import com.cardduel.engine.Minion;
import com.cardduel.engine.PlayerState;
import com.cardduel.engine.Target;
import com.cardduel.room.Room;
import com.cardduel.room.Seat;
import com.cardduel.room.SeatController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns the state of a {@link Room} into a flat list of {@link UiOp}s for one player.
 *
 * <p>This is the single place where game state meets screen element ids. The ids must match
 * {@code Common/UI/Custom/CardDuel/Table.ui}. Pure Java, so it is covered by the self test.
 *
 * <p>Legend shown to players: {@code *} ready to attack / playable, {@code !} valid target, {@code >} selected,
 * {@code X} marked for mulligan.
 */
public final class TableView {

    public static final int HAND_SLOTS = 10;
    public static final int BOARD_SLOTS = Game.MAX_BOARD;
    public static final int LOG_LINES = 8;

    private TableView() {
    }

    /** Everything on screen. */
    public static List<UiOp> full(Room room, int me, long now) {
        List<UiOp> ops = new ArrayList<>(220);
        if (room.state == Room.State.LOBBY || room.game == null) {
            lobby(room, me, ops);
        } else {
            duel(room, me, now, ops);
        }
        return ops;
    }

    /** Just the clock, sent every second. */
    public static List<UiOp> timer(Room room, int me, long now) {
        List<UiOp> ops = new ArrayList<>(1);
        ops.add(UiOp.text("#Timer.Text", timerText(room, now)));
        return ops;
    }

    // ================================================================================================
    // Lobby
    // ================================================================================================

    private static void lobby(Room room, int me, List<UiOp> ops) {
        Seat mine = room.seats[me];
        Seat opp = room.seats[1 - me];
        ops.add(UiOp.flag("#Lobby.Visible", true));
        ops.add(UiOp.flag("#Duel.Visible", false));

        DeckPreset deck = CardLibrary.preset(mine.deck);
        HeroDef hero = deck.hero();
        ops.add(UiOp.text("#LbMeName.Text", "You: " + mine.name));
        ops.add(UiOp.text("#LbMeDeck.Text", "Deck: " + deck.name() + "  (" + (mine.deck + 1) + "/" + CardLibrary.presets().size() + ")"));
        ops.add(UiOp.text("#LbMeInfo.Text", deck.description()));
        ops.add(UiOp.text("#LbMeHero.Text", "Hero: " + hero.name()));
        ops.add(UiOp.text("#LbMePower.Text", "Hero power - " + hero.powerName() + " (" + hero.powerCost() + " mana): " + hero.powerText()));
        ops.add(UiOp.text("#LbMeReady.Text", mine.ready ? "READY" : "Not ready"));
        ops.add(UiOp.text("#ReadyBtn.Text", mine.ready ? "Cancel ready" : "Ready!"));
        ops.add(UiOp.flag("#DeckPrev.Visible", !mine.ready));
        ops.add(UiOp.flag("#DeckNext.Visible", !mine.ready));
        ops.add(UiOp.flag("#BotBtn.Visible", opp == null));

        if (opp == null) {
            ops.add(UiOp.text("#LbOppName.Text", "Empty chair"));
            ops.add(UiOp.text("#LbOppReady.Text", "Waiting for a second player to press F on the deck..."));
        } else {
            ops.add(UiOp.text("#LbOppName.Text", "Opponent: " + opp.name));
            ops.add(UiOp.text("#LbOppReady.Text", opp.ready ? "READY" : "Choosing a deck..."));
        }
        String status;
        if (!room.banner.isEmpty()) {
            status = room.banner;
        } else if (opp == null) {
            status = "Waiting for an opponent. You can also play the training bot.";
        } else if (mine.ready) {
            status = "Waiting for " + opp.name + " to be ready...";
        } else {
            status = "Pick your deck and press Ready!";
        }
        ops.add(UiOp.text("#LbStatus.Text", status));
    }

    // ================================================================================================
    // Duel
    // ================================================================================================

    private static void duel(Room room, int me, long now, List<UiOp> ops) {
        Game g = room.game;
        SeatController ctl = room.controllers[me];
        ctl.sanitize(g, me);
        PlayerState mine = g.me(me);
        PlayerState opp = g.opp(me);
        int enemy = 1 - me;
        boolean playing = g.phase == Game.Phase.PLAYING;
        boolean myTurn = g.isMyTurn(me);
        Set<Target> hot = ctl.highlighted(g, me);

        ops.add(UiOp.flag("#Lobby.Visible", false));
        ops.add(UiOp.flag("#Duel.Visible", true));

        // ---- heroes
        hero(ops, "#OppHero", opp, hot.contains(Target.hero(enemy)), g.phase != Game.Phase.OVER && g.turn == enemy && playing);
        hero(ops, "#MeHero", mine, hot.contains(Target.hero(me)), myTurn);

        // ---- boards
        for (int i = 0; i < BOARD_SLOTS; i++) {
            minion(ops, "#E" + i, i < opp.board.size() ? opp.board.get(i) : null, hot.contains(new Target(enemy, i)), false, false);
            Minion m = i < mine.board.size() ? mine.board.get(i) : null;
            minion(ops, "#M" + i, m, hot.contains(new Target(me, i)), myTurn && g.canMinionAttack(me, i),
                    ctl.attackerSlot() == i);
        }

        // ---- hand
        for (int i = 0; i < HAND_SLOTS; i++) {
            String id = "#H" + i;
            if (i >= mine.hand.size()) {
                ops.add(UiOp.flag(id + ".Visible", false));
                continue;
            }
            CardDef c = mine.hand.get(i);
            boolean playable = myTurn && g.canPlay(me, i);
            boolean marked = g.phase == Game.Phase.MULLIGAN && ctl.isMarkedForReplace(i);
            boolean selected = ctl.pendingHandIndex() == i;
            ops.add(UiOp.flag(id + ".Visible", true));
            ops.add(UiOp.text(id + " #Cost.Text", String.valueOf(c.cost) + (playable ? " *" : "")));
            ops.add(UiOp.text(id + " #Name.Text", (marked ? "X " : selected ? "> " : "") + c.name));
            ops.add(UiOp.text(id + " #Info.Text", c.isMinion() ? c.attack + "/" + c.health + keywordShort(c) : "Spell"));
            ops.add(UiOp.text(id + ".TooltipText", c.name + " (" + c.cost + ")\n" + c.text));
        }

        // ---- status line, hint, timer
        ops.add(UiOp.text("#Status.Text", statusLine(room, g, me)));
        String msg = ctl.message().isEmpty() ? ctl.hint(g, me) : ctl.message();
        ops.add(UiOp.text("#Hint.Text", msg));
        ops.add(UiOp.text("#Timer.Text", timerText(room, now)));

        // ---- buttons
        String end;
        if (g.isOver()) {
            end = room.seats[enemy] == null ? "Back to lobby" : "Play again";
            if (room.seats[me] != null && room.seats[me].ready) end = "Waiting for opponent...";
        } else if (g.phase == Game.Phase.MULLIGAN) {
            end = mine.mulliganDone ? "Waiting..." : "Confirm";
        } else {
            end = myTurn ? "End Turn" : "Opponent's turn";
        }
        ops.add(UiOp.text("#EndBtn.Text", end));
        ops.add(UiOp.flag("#PowerBtn.Visible", playing));
        ops.add(UiOp.text("#PowerBtn.Text", mine.hero.powerName() + " (" + mine.hero.powerCost() + ")"
                + (mine.heroPowerUsed ? " - used" : myTurn && g.canHeroPower(me) ? " *" : "")));
        ops.add(UiOp.text("#PowerBtn.TooltipText", mine.hero.powerName() + ": " + mine.hero.powerText()));
        ops.add(UiOp.flag("#CancelBtn.Visible", ctl.mode() != SeatController.Mode.IDLE));
        ops.add(UiOp.text("#LeaveBtn.Text", room.leaveArmed[me] ? "Really leave? (forfeit)" : g.isOver() ? "Leave table" : "Leave / forfeit"));

        // ---- log, oldest at the top
        List<String> log = g.recentLog(LOG_LINES);
        for (int i = 0; i < LOG_LINES; i++) {
            int src = i - (LOG_LINES - log.size());
            ops.add(UiOp.text("#Log" + i + ".Text", src >= 0 ? log.get(src) : ""));
        }
    }

    private static void hero(List<UiOp> ops, String id, PlayerState p, boolean targetable, boolean active) {
        ops.add(UiOp.text(id + " #Name.Text", (targetable ? "! " : "") + p.name + (active ? "  (turn)" : "")));
        ops.add(UiOp.text(id + " #Hp.Text", "HP " + Math.max(0, p.heroHealth) + "/" + p.heroMaxHealth + "   " + p.hero.name()));
        ops.add(UiOp.text(id + " #Info.Text", "Mana " + p.mana + "/" + p.maxMana + "   Hand " + p.hand.size() + "   Deck " + p.deck.size()
                + (p.fatigue > 0 ? "   Fatigue " + p.fatigue : "")));
    }

    private static void minion(List<UiOp> ops, String id, Minion m, boolean targetable, boolean canAttack, boolean selected) {
        if (m == null) {
            ops.add(UiOp.flag(id + ".Visible", false));
            return;
        }
        ops.add(UiOp.flag(id + ".Visible", true));
        ops.add(UiOp.text(id + " #Name.Text", (selected ? "> " : targetable ? "! " : "") + m.card.name));
        ops.add(UiOp.text(id + " #Stats.Text", m.attack + "/" + m.health + (canAttack ? " *" : "") + (m.isDamaged() ? "  (dmg)" : "")));
        ops.add(UiOp.text(id + " #Tags.Text", tags(m)));
        ops.add(UiOp.text(id + ".TooltipText", m.card.name + "\n" + (m.card.text.isEmpty() ? "No text." : m.card.text)));
    }

    // ================================================================================================
    // Text helpers
    // ================================================================================================

    static String statusLine(Room room, Game g, int me) {
        if (g.phase == Game.Phase.MULLIGAN) return "MULLIGAN";
        if (g.isOver()) {
            String who = g.winner == 2 ? "It's a draw!" : g.winner == me ? "YOU WIN!" : "You lost.";
            return who + "  " + g.endReason;
        }
        return g.isMyTurn(me) ? "YOUR TURN  (turn " + g.turnCount + ")" : "OPPONENT'S TURN  (turn " + g.turnCount + ")";
    }

    static String timerText(Room room, long now) {
        if (room.game == null || room.game.isOver()) return "";
        long left = Math.max(0, (room.game.deadline - now + 999) / 1000);
        return (left / 60) + ":" + String.format("%02d", left % 60);
    }

    private static String tags(Minion m) {
        List<String> out = new ArrayList<>();
        for (Keyword k : m.keywords) {
            if (k == Keyword.DIVINE_SHIELD && !m.divineShield) continue;
            out.add(k.label);
        }
        if (m.divineShield && !m.keywords.contains(Keyword.DIVINE_SHIELD)) out.add(Keyword.DIVINE_SHIELD.label);
        if (!m.card.deathrattle.isEmpty()) out.add("Deathrattle");
        return String.join(", ", out);
    }

    private static String keywordShort(CardDef c) {
        if (c.keywords.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (Keyword k : c.keywords) out.add(k.label);
        return "  " + String.join(",", out);
    }
}
