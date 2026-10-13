package com.cardduel.room;

/**
 * One of the two chairs at a table. {@code id} is the player's UUID string (or "bot" for the training bot).
 *
 * <p>Seat index (0 or 1) is also the player index inside the {@link com.cardduel.engine.Game}.
 */
public final class Seat {
    public final String id;
    public final String name;
    public final boolean bot;
    /** Index into {@link com.cardduel.engine.CardLibrary#presets()}. */
    public int deck;
    /** Lobby: "I'm ready". Game over: "I want a rematch". */
    public boolean ready;
    /** Epoch millis of the last thing this player did. Used to free tables that were walked away from. */
    public long lastActive;
    /** How this player sees the table right now; null when they closed the UI (they stay seated). */
    public Peer peer;

    Seat(String id, String name, boolean bot, int deck) {
        this.id = id;
        this.name = name;
        this.bot = bot;
        this.deck = deck;
    }
}
