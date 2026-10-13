package com.cardduel.hytale;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/** {@code /cardduel}: short how-to in chat. */
public final class CardDuelCommand extends AbstractAsyncCommand {

    public CardDuelCommand() {
        super("cardduel", "How to play Card Duel");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        context.sendMessage(Message.raw("=== Card Duel ==="));
        context.sendMessage(Message.raw("1. Get the Card Deck item (creative menu, or /give <you> CardDuel_Deck)."));
        context.sendMessage(Message.raw("2. Place it on a table. Every placed deck is its own table for 2 players."));
        context.sendMessage(Message.raw("3. Press F on the deck to sit down. A second player presses F to join."));
        context.sendMessage(Message.raw("4. Pick a deck, press Ready, and duel! You can also play a training bot alone."));
        context.sendMessage(Message.raw("Closed the window by accident? Press F on the deck again. /cardleave stands you up."));
        return CompletableFuture.completedFuture(null);
    }
}
