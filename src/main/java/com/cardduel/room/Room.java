package com.cardduel.room;

import com.cardduel.engine.Bot;
import com.cardduel.engine.CardLibrary;
import com.cardduel.engine.Game;

import java.util.Random;
import java.util.concurrent.Executor;

/**
 * One table = one room = at most two players. Pure Java; every method must be called on the world thread
 * (the scheduler hops there with {@link #executor}).
 *
 * <p>Life cycle: LOBBY (pick a deck, ready up, or add the training bot) -> DUEL -> FINISHED (result, rematch) -> LOBBY.
 */
public final class Room {

    public enum State { LOBBY, DUEL, FINISHED }

    /** Notified when seats empty so the {@link RoomManager} can forget players and rooms. */
    public interface Listener {
        void seatLeft(Room room, String playerId);

        void roomEmpty(Room room);
    }

    public static final long IDLE_KICK_MILLIS = 5 * 60_000L;
    public static final long BOT_THINK_MILLIS = 1_200L;

    public final String key;
    public final Executor executor;
    public final Seat[] seats = new Seat[2];
    public final SeatController[] controllers = {new SeatController(), new SeatController()};
    public final boolean[] leaveArmed = new boolean[2];
    public State state = State.LOBBY;
    public Game game;
    /** Short note shown in the lobby ("Table is full" ...). */
    public String banner = "";

    private final Listener listener;
    private final Random rng;
    private long botActAt;
    private int lastTurnSeen = -1;

    public Room(String key, Executor executor, Listener listener, Random rng) {
        this.key = key;
        this.executor = executor;
        this.listener = listener;
        this.rng = rng;
    }

    // ================================================================================================
    // Seating
    // ================================================================================================

    public int seatIndex(String playerId) {
        for (int i = 0; i < 2; i++) {
            if (seats[i] != null && !seats[i].bot && seats[i].id.equals(playerId)) return i;
        }
        return -1;
    }

    public boolean isEmpty() {
        return seats[0] == null && seats[1] == null;
    }

    /** Sits a player down (or reconnects them to their own seat). Returns null if the table is full. */
    public Seat join(String id, String name, Peer peer, long now) {
        int mine = seatIndex(id);
        if (mine >= 0) {
            seats[mine].peer = peer;
            seats[mine].lastActive = now;
            changed();
            return seats[mine];
        }
        int free = seats[0] == null ? 0 : seats[1] == null ? 1 : -1;
        if (free < 0) return null;
        Seat s = new Seat(id, name, false, free % CardLibrary.presets().size());
        s.peer = peer;
        s.lastActive = now;
        seats[free] = s;
        if (state == State.FINISHED) toLobby(); // the previous opponent left, start fresh
        banner = "";
        changed();
        return s;
    }

    public void leave(String id, long now) {
        int i = seatIndex(id);
        if (i < 0) return;
        Seat gone = seats[i];
        if (state == State.DUEL && game != null && !game.isOver()) {
            game.concede(i);
            state = State.FINISHED;
            other(i, seat -> seat.ready = false);
        }
        seats[i] = null;
        controllers[i].reset();
        leaveArmed[i] = false;
        listener.seatLeft(this, id);
        Peer p = gone.peer;
        gone.peer = null;
        if (p != null) {
            try {
                p.closeUi();
            } catch (RuntimeException ignored) {
                // the player may already be gone
            }
        }
        // A bot alone at a table is pointless.
        if (seats[1 - i] != null && seats[1 - i].bot) {
            seats[1 - i] = null;
        }
        if (isEmpty()) {
            listener.roomEmpty(this);
            return;
        }
        if (state == State.LOBBY) {
            for (Seat s : seats) if (s != null) s.ready = false;
            banner = gone.name + " left the table.";
        }
        changed();
    }

    private void other(int i, java.util.function.Consumer<Seat> action) {
        if (seats[1 - i] != null) action.accept(seats[1 - i]);
    }

    // ================================================================================================
    // Player input (called from UI button clicks)
    // ================================================================================================

    public void handle(String playerId, String action, long now) {
        int me = seatIndex(playerId);
        if (me < 0 || action == null) return;
        seats[me].lastActive = now;
        banner = "";
        if (state == State.LOBBY) {
            lobbyAction(me, action, now);
        } else {
            duelAction(me, action, now);
        }
        if (state == State.DUEL && game.isOver()) finishDuel();
        if (seats[0] != null || seats[1] != null) changed();
    }

    private void lobbyAction(int me, String action, long now) {
        Seat s = seats[me];
        switch (action) {
            case "READY" -> {
                s.ready = !s.ready;
                startIfReady(now);
            }
            case "BOT" -> {
                if (seats[1 - me] == null) {
                    Seat bot = new Seat("bot", "Training Bot", true, rng.nextInt(CardLibrary.presets().size()));
                    bot.ready = true;
                    seats[1 - me] = bot;
                    banner = "A training bot sat down. Press Ready when you like.";
                    startIfReady(now);
                }
            }
            case "DECKNEXT" -> cycleDeck(s, +1);
            case "DECKPREV" -> cycleDeck(s, -1);
            case "LEAVE" -> leave(s.id, now);
            default -> {
            }
        }
    }

    private void cycleDeck(Seat s, int delta) {
        if (s.ready) return;
        s.deck = Math.floorMod(s.deck + delta, CardLibrary.presets().size());
    }

    private void duelAction(int me, String action, long now) {
        SeatController c = controllers[me];
        if (action.equals("LEAVE")) {
            if (state == State.FINISHED || leaveArmed[me]) {
                leave(seats[me].id, now);
            } else {
                leaveArmed[me] = true;
                c.say("Leaving now means you forfeit. Press Leave again to confirm.");
            }
            return;
        }
        leaveArmed[me] = false;
        if (state == State.FINISHED) {
            if (action.equals("END")) rematch(me, now);
            return;
        }
        Game g = game;
        switch (action) {
            case "END" -> c.clickEnd(g, me, now);
            case "POWER" -> c.clickHeroPower(g, me, now);
            case "CANCEL" -> c.clickCancel();
            case "MH" -> c.clickOwnHero(g, me, now);
            case "EH" -> c.clickEnemyHero(g, me, now);
            default -> {
                if (action.length() >= 2) {
                    int idx;
                    try {
                        idx = Integer.parseInt(action.substring(1));
                    } catch (NumberFormatException e) {
                        return;
                    }
                    switch (action.charAt(0)) {
                        case 'H' -> c.clickHand(g, me, idx, now);
                        case 'M' -> c.clickOwnMinion(g, me, idx, now);
                        case 'E' -> c.clickEnemyMinion(g, me, idx, now);
                        default -> {
                        }
                    }
                }
            }
        }
    }

    // ================================================================================================
    // Game flow
    // ================================================================================================

    private void startIfReady(long now) {
        if (seats[0] != null && seats[1] != null && seats[0].ready && seats[1].ready) startDuel(now);
    }

    private void rematch(int me, long now) {
        Seat s = seats[me];
        if (seats[1 - me] == null) {
            toLobby();
            return;
        }
        s.ready = true;
        banner = "";
        startIfReady(now);
    }

    private void toLobby() {
        state = State.LOBBY;
        game = null;
        for (Seat s : seats) {
            if (s != null) s.ready = s.bot;
        }
        for (SeatController c : controllers) c.reset();
        leaveArmed[0] = leaveArmed[1] = false;
    }

    private void startDuel(long now) {
        Seat a = seats[0];
        Seat b = seats[1];
        game = new Game(CardLibrary.preset(a.deck), a.name, CardLibrary.preset(b.deck), b.name, rng.nextInt(2), rng, now);
        state = State.DUEL;
        for (SeatController c : controllers) c.reset();
        a.ready = false;
        b.ready = b.bot;
        leaveArmed[0] = leaveArmed[1] = false;
        lastTurnSeen = -1;
        botActAt = now + BOT_THINK_MILLIS;
        for (int i = 0; i < 2; i++) {
            if (seats[i].bot) Bot.mulligan(game, i, now);
        }
        if (seats[0].peer != null) seats[0].peer.tell("The duel begins! Choose cards to replace, then confirm.");
        if (seats[1].peer != null) seats[1].peer.tell("The duel begins! Choose cards to replace, then confirm.");
    }

    private void finishDuel() {
        state = State.FINISHED;
        for (SeatController c : controllers) c.reset();
        for (Seat s : seats) {
            if (s != null) s.ready = s.bot;
        }
    }

    // ================================================================================================
    // Clock: called about once a second on the world thread
    // ================================================================================================

    public void tick(long now) {
        // Offline players forfeit; abandoned tables are freed.
        for (int i = 0; i < 2; i++) {
            Seat s = seats[i];
            if (s == null || s.bot) continue;
            boolean offline = s.peer != null && !s.peer.isConnected();
            boolean idle = state != State.DUEL && now - s.lastActive > IDLE_KICK_MILLIS;
            if (offline || idle) {
                if (idle && s.peer != null) s.peer.tell("You were away from the card table for too long, so you were seated out.");
                leave(s.id, now);
            }
        }
        if (isEmpty()) return;

        if (state == State.DUEL) {
            boolean changed = game.tick(now);
            changed |= runBots(now);
            if (game.isOver()) {
                finishDuel();
                changed = true;
            }
            if (changed) {
                changed();
                return;
            }
            refreshAll(false);
        }
    }

    private boolean runBots(long now) {
        boolean acted = false;
        for (int i = 0; i < 2; i++) {
            if (seats[i] == null || !seats[i].bot) continue;
            if (game.phase == Game.Phase.MULLIGAN && !game.me(i).mulliganDone) {
                Bot.mulligan(game, i, now);
                acted = true;
            }
        }
        if (game.phase == Game.Phase.PLAYING) {
            if (game.turn != lastTurnSeen) {
                lastTurnSeen = game.turn;
                botActAt = now + BOT_THINK_MILLIS;
            }
            Seat s = seats[game.turn];
            if (s != null && s.bot && now >= botActAt) {
                Bot.playTurn(game, game.turn, now);
                acted = true;
            }
        }
        return acted;
    }

    // ================================================================================================
    // Telling the players
    // ================================================================================================

    public void changed() {
        refreshAll(true);
    }

    private void refreshAll(boolean full) {
        for (Seat s : seats) {
            if (s == null || s.peer == null) continue;
            try {
                s.peer.refresh(full);
            } catch (RuntimeException e) {
                s.peer = null; // the UI is gone; the player stays seated and can reopen it
            }
        }
    }
}
