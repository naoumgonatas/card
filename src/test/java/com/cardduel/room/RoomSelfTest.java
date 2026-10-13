package com.cardduel.room;

import com.cardduel.engine.Game;
import com.cardduel.ui.TableView;
import com.cardduel.ui.UiOp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for tables, lobby, click handling and the screen model. No framework needed: {@code gradlew selfTest}.
 */
public final class RoomSelfTest {

    private static int checks;
    private static int failures;

    static final class FakePeer implements Peer {
        boolean connected = true;
        boolean closed;
        int fullRefreshes;
        int timerRefreshes;
        final List<String> messages = new ArrayList<>();

        @Override public boolean isConnected() { return connected; }
        @Override public void refresh(boolean full) { if (full) fullRefreshes++; else timerRefreshes++; }
        @Override public void tell(String message) { messages.add(message); }
        @Override public void closeUi() { closed = true; }
    }

    public static void main(String[] args) throws IOException {
        seatingAndLobby();
        leaveAndReconnect();
        duelFlowThroughClicks();
        offlineAndIdle();
        uiIdsExistInMarkup();
        fuzz(args.length > 0 ? Integer.parseInt(args[0]) : 300);

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    // ================================================================================================

    static void seatingAndLobby() {
        section("seating + lobby");
        RoomManager rm = new RoomManager();
        FakePeer pa = new FakePeer(), pb = new FakePeer(), pc = new FakePeer();

        RoomManager.Join a = rm.join("w@1,2,3", Runnable::run, "A", "Alice", pa, 0);
        check(a.error() == null && a.room() != null, "first player sits");
        check(a.room().state == Room.State.LOBBY, "table starts in the lobby");
        check(rm.join("w@1,2,3", Runnable::run, "A", "Alice", pa, 0).seat() == a.seat(), "sitting again returns the same seat");

        RoomManager.Join other = rm.join("w@9,9,9", Runnable::run, "A", "Alice", pa, 0);
        check(other.error() != null, "can't sit at two tables at once");

        RoomManager.Join b = rm.join("w@1,2,3", Runnable::run, "B", "Bob", pb, 0);
        check(b.error() == null && b.room() == a.room(), "second player joins the same room");
        check(rm.join("w@1,2,3", Runnable::run, "C", "Cara", pc, 0).error() != null, "third player is turned away");
        check(rm.roomCount() == 1, "still one room");

        Room room = a.room();
        room.handle("A", "DECKNEXT", 1);
        check(room.seats[0].deck != 0, "deck choice cycles");
        room.handle("A", "READY", 2);
        check(room.state == Room.State.LOBBY, "one ready player doesn't start the duel");
        room.handle("A", "DECKNEXT", 2);
        int deckAfter = room.seats[0].deck;
        room.handle("A", "DECKNEXT", 2);
        check(room.seats[0].deck == deckAfter, "can't change deck while ready");
        room.handle("B", "READY", 3);
        check(room.state == Room.State.DUEL && room.game != null, "both ready starts the duel");
        check(pa.fullRefreshes > 0 && pb.fullRefreshes > 0, "both players were redrawn");

        // lobby view
        RoomManager rm2 = new RoomManager();
        Room solo = rm2.join("w@5,5,5", Runnable::run, "S", "Solo", new FakePeer(), 0).room();
        List<UiOp> ops = TableView.full(solo, 0, 0);
        check(flag(ops, "#Lobby.Visible") && !flag(ops, "#Duel.Visible"), "lobby screen shown");
        check(flag(ops, "#BotBtn.Visible"), "bot button offered when alone");
        solo.handle("S", "BOT", 1);
        check(solo.seats[1] != null && solo.seats[1].bot, "bot sat down");
        check(!flag(TableView.full(solo, 0, 0), "#BotBtn.Visible"), "bot button hidden once someone sits");
        solo.handle("S", "READY", 2);
        check(solo.state == Room.State.DUEL, "ready against the bot starts the duel at once");
    }

    static void leaveAndReconnect() {
        section("leaving");
        RoomManager rm = new RoomManager();
        FakePeer pa = new FakePeer(), pb = new FakePeer();
        Room room = rm.join("t", Runnable::run, "A", "Alice", pa, 0).room();
        rm.join("t", Runnable::run, "B", "Bob", pb, 0);
        room.handle("A", "READY", 1);
        room.handle("B", "READY", 1);
        check(room.state == Room.State.DUEL, "duel running");

        room.handle("A", "LEAVE", 2);
        check(room.state == Room.State.DUEL && room.seatIndex("A") >= 0, "first click on Leave only arms it");
        room.handle("A", "LEAVE", 3);
        check(room.seatIndex("A") < 0 && pa.closed, "second click leaves and closes the screen");
        check(room.state == Room.State.FINISHED && room.game.winner == 1, "leaving mid-duel forfeits");
        check(rm.roomOf("A") == null, "manager forgot Alice");

        List<UiOp> bobView = TableView.full(room, 1, 4);
        check(text(bobView, "#Status.Text").contains("YOU WIN"), "Bob sees he won");
        check(text(bobView, "#EndBtn.Text").equals("Back to lobby"), "no opponent left, so the button returns to the lobby");
        room.handle("B", "END", 5);
        check(room.state == Room.State.LOBBY, "Bob is back in the lobby");

        RoomManager.Join c = rm.join("t", Runnable::run, "C", "Cara", new FakePeer(), 6);
        check(c.error() == null && c.room() == room, "a new player can take the empty chair");
        room.handle("B", "LEAVE", 7);
        room.handle("C", "LEAVE", 7);
        check(rm.roomCount() == 0, "empty room is removed");

        // Rematch with bot
        RoomManager rm2 = new RoomManager();
        Room r2 = rm2.join("bot", Runnable::run, "H", "Hero", new FakePeer(), 0).room();
        r2.handle("H", "BOT", 1);
        r2.handle("H", "READY", 1);
        r2.game.concede(0);
        r2.handle("H", "LEAVE", 2); // first click arms (the room hasn't noticed the game ended yet)
        r2.handle("H", "LEAVE", 3);
        check(rm2.roomCount() == 0, "leaving a finished duel against a bot removes the table");
    }

    static void duelFlowThroughClicks() {
        section("mulligan + turn via clicks");
        RoomManager rm = new RoomManager();
        Room room = rm.join("d", Runnable::run, "A", "Alice", new FakePeer(), 0).room();
        rm.join("d", Runnable::run, "B", "Bob", new FakePeer(), 0);
        room.handle("A", "READY", 1);
        room.handle("B", "READY", 1);
        Game g = room.game;
        List<UiOp> ops = TableView.full(room, 0, 1);
        check(text(ops, "#Status.Text").equals("MULLIGAN"), "mulligan screen");
        room.handle("A", "H0", 1); // mark first card
        check(room.controllers[0].isMarkedForReplace(0) || g.me(0).hand.get(0).id.equals("coin"), "card marked for replacement");
        room.handle("A", "END", 2);
        check(g.me(0).mulliganDone, "confirm sends the mulligan");
        room.handle("B", "END", 2);
        check(g.phase == Game.Phase.PLAYING, "duel starts after both confirmed");

        int first = g.turn;
        String firstId = first == 0 ? "A" : "B";
        String secondId = first == 0 ? "B" : "A";
        room.handle(secondId, "END", 3);
        check(g.turn == first, "waiting player's End Turn click is refused");
        check(!room.controllers[1 - first].message().isEmpty(), "and they are told why");
        room.handle(firstId, "END", 3);
        check(g.turn == 1 - first, "End Turn passes the turn");
        check(text(TableView.full(room, 1 - first, 4), "#Status.Text").startsWith("YOUR TURN"), "status line follows the turn");
        room.handle("A", "bogus", 4);
        room.handle("A", "H99", 4);
        room.handle("A", "Hx", 4);
        check(true, "garbage actions are ignored without throwing");
    }

    static void offlineAndIdle() {
        section("offline + idle");
        RoomManager rm = new RoomManager();
        FakePeer pa = new FakePeer(), pb = new FakePeer();
        Room room = rm.join("o", Runnable::run, "A", "Alice", pa, 0).room();
        rm.join("o", Runnable::run, "B", "Bob", pb, 0);
        room.handle("A", "READY", 1);
        room.handle("B", "READY", 1);
        pa.connected = false;
        room.tick(1000);
        check(room.seatIndex("A") < 0 && room.state == Room.State.FINISHED, "a player who goes offline forfeits");
        check(room.game.winner == 1, "the other player wins");

        RoomManager rm2 = new RoomManager();
        FakePeer p = new FakePeer();
        Room lobby = rm2.join("i", Runnable::run, "A", "Alice", p, 0).room();
        lobby.tick(Room.IDLE_KICK_MILLIS - 1);
        check(lobby.seatIndex("A") == 0, "not kicked yet");
        lobby.tick(Room.IDLE_KICK_MILLIS + 1);
        check(lobby.seatIndex("A") < 0 && rm2.roomCount() == 0, "idle lobby player is seated out and the room freed");
        check(!p.messages.isEmpty(), "and told about it");

        // a closed UI (peer detached) keeps the seat
        RoomManager rm3 = new RoomManager();
        Room r3 = rm3.join("c", Runnable::run, "A", "Alice", new FakePeer(), 0).room();
        r3.seats[0].peer = null;
        r3.tick(5);
        check(r3.seatIndex("A") == 0, "closing the screen doesn't give up the seat");
    }

    // ================================================================================================
    // Screen ids: every element the code talks to must exist in Table.ui
    // ================================================================================================

    static void uiIdsExistInMarkup() throws IOException {
        section("screen ids vs Table.ui");
        String ui;
        try (InputStream in = RoomSelfTest.class.getResourceAsStream("/Common/UI/Custom/CardDuel/Table.ui")) {
            if (in == null) {
                check(false, "Table.ui not found on the classpath");
                return;
            }
            ui = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> inMarkup = new HashSet<>();
        Matcher m = Pattern.compile("#([A-Za-z][A-Za-z0-9]*)").matcher(ui.replaceAll("#[0-9a-fA-F]{6}", ""));
        while (m.find()) inMarkup.add(m.group(1));

        // 1. every selector TableView can emit, in lobby and duel, both viewpoints
        Set<String> used = new HashSet<>();
        RoomManager rm = new RoomManager();
        Room room = rm.join("u", Runnable::run, "A", "Alice", new FakePeer(), 0).room();
        collect(used, TableView.full(room, 0, 0));
        rm.join("u", Runnable::run, "B", "Bob", new FakePeer(), 0);
        room.handle("A", "READY", 1);
        room.handle("B", "READY", 1);
        room.handle("A", "END", 2);
        room.handle("B", "END", 2);
        for (int i = 0; i < 2; i++) collect(used, TableView.full(room, i, 3));
        collect(used, TableView.timer(room, 0, 3));
        // fill every slot so all hand and board ids show up
        for (var p : room.game.players) {
            while (p.board.size() < Game.MAX_BOARD) p.board.add(newMinion(room.game, "pebble"));
            while (p.hand.size() < Game.MAX_HAND) p.hand.add(com.cardduel.engine.CardLibrary.get("sapling"));
        }
        for (int i = 0; i < 2; i++) collect(used, TableView.full(room, i, 3));
        for (String id : used) check(inMarkup.contains(id), "#" + id + " exists in Table.ui");

        // 2. every element the page listens to
        String page = Files.readString(Path.of("src/main/java/com/cardduel/hytale/CardTablePage.java"));
        Matcher b = Pattern.compile("\"#([A-Za-z][A-Za-z0-9]*)\"").matcher(page);
        int bound = 0;
        while (b.find()) {
            if (b.group(1).length() == 1) continue; // "#M" + i style prefixes, checked per slot below
            bound++;
            check(inMarkup.contains(b.group(1)), "bound element #" + b.group(1) + " exists in Table.ui");
        }
        check(bound >= 10, "page binds its buttons (" + bound + " literal ids found)");
        for (int i = 0; i < TableView.BOARD_SLOTS; i++) check(inMarkup.contains("M" + i) && inMarkup.contains("E" + i), "board slots " + i);
        for (int i = 0; i < TableView.HAND_SLOTS; i++) check(inMarkup.contains("H" + i), "hand slot " + i);
    }

    private static com.cardduel.engine.Minion newMinion(Game g, String id) {
        try {
            var ctor = com.cardduel.engine.Minion.class.getDeclaredConstructor(int.class, com.cardduel.engine.CardDef.class);
            ctor.setAccessible(true);
            return ctor.newInstance(7000 + checks, com.cardduel.engine.CardLibrary.get(id));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void collect(Set<String> used, List<UiOp> ops) {
        Matcher m = Pattern.compile("#([A-Za-z][A-Za-z0-9]*)").matcher("");
        for (UiOp op : ops) {
            m.reset(op.selector());
            while (m.find()) used.add(m.group(1));
        }
    }

    // ================================================================================================
    // Fuzz: a human clicking at random against the bot, many full duels, must never throw or wedge
    // ================================================================================================

    static void fuzz(int duels) {
        section("random clicking fuzz (" + duels + " duels vs the bot)");
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < 10; i++) actions.add("H" + i);
        for (int i = 0; i < 7; i++) {
            actions.add("M" + i);
            actions.add("E" + i);
        }
        actions.addAll(List.of("MH", "EH", "END", "POWER", "CANCEL", "END", "END"));
        int finished = 0;
        int viewChecks = 0;
        for (int seed = 0; seed < duels; seed++) {
            Random rng = new Random(seed);
            RoomManager rm = new RoomManager();
            Room room = rm.join("f" + seed, Runnable::run, "H", "Human", new FakePeer(), 0).room();
            room.handle("H", "BOT", 0);
            room.handle("H", "READY", 0);
            long now = 0;
            try {
                for (int step = 0; step < 4000 && room.state == Room.State.DUEL; step++) {
                    now += 400;
                    room.handle("H", actions.get(rng.nextInt(actions.size())), now);
                    if (step % 5 == 0) room.tick(now);
                    if (step % 50 == 0) {
                        TableView.full(room, 0, now);
                        viewChecks++;
                    }
                }
                // let the clock run out if the random clicker never ended its turns
                for (int i = 0; i < 2000 && room.state == Room.State.DUEL; i++) {
                    now += 5000;
                    room.tick(now);
                }
            } catch (RuntimeException e) {
                check(false, "seed " + seed + " threw " + e);
                e.printStackTrace(System.out);
                break;
            }
            if (room.state == Room.State.FINISHED) finished++;
            else check(false, "seed " + seed + " never finished (" + room.state + ")");
        }
        check(finished == duels, "all " + duels + " duels finished (" + finished + ")");
        System.out.println("  " + viewChecks + " screens rendered along the way");
    }

    // ================================================================================================

    private static boolean flag(List<UiOp> ops, String selector) {
        for (UiOp op : ops) if (op.selector().equals(selector)) return (Boolean) op.value();
        throw new IllegalStateException("no op for " + selector);
    }

    private static String text(List<UiOp> ops, String selector) {
        for (UiOp op : ops) if (op.selector().equals(selector)) return (String) op.value();
        throw new IllegalStateException("no op for " + selector);
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
