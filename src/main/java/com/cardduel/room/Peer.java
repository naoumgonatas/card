package com.cardduel.room;

/**
 * The link between a {@link Seat} and whatever shows the table to that player (in the plugin: the Hytale UI page).
 *
 * <p>Keeping this an interface is what lets {@code room}, {@code engine} and {@code ui} stay free of Hytale classes
 * and be tested on their own. Implemented by {@code com.cardduel.hytale.CardTablePage}.
 */
public interface Peer {

    /** Is the player still online? A seated player who goes offline forfeits. */
    boolean isConnected();

    /** Redraw the table. {@code full} = everything, otherwise only the clock. Must tolerate a closed UI. */
    void refresh(boolean full);

    /** Send a short chat message to the player. */
    void tell(String message);

    /** Hide the table screen (used when a player leaves). */
    void closeUi();
}
