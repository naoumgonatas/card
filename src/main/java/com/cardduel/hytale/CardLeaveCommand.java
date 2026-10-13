package com.cardduel.hytale;

import com.cardduel.room.Room;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/** {@code /cardleave}: stand up from your card table (forfeits a duel in progress). */
public final class CardLeaveCommand extends AbstractAsyncCommand {

    public CardLeaveCommand() {
        super("cardleave", "Leave your Card Duel table");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        CommandSender sender = context.sender();
        if (!(sender instanceof PlayerRef playerRef)) {
            context.sendMessage(Message.raw("Only players can sit at a card table."));
            return CompletableFuture.completedFuture(null);
        }
        String id = playerRef.getUuid().toString();
        Room room = CardDuelPlugin.rooms().roomOf(id);
        if (room == null) {
            context.sendMessage(Message.raw("You are not sitting at a card table."));
            return CompletableFuture.completedFuture(null);
        }
        // Commands run off the world thread; game state may only be touched on it.
        room.executor.execute(() -> room.leave(id, System.currentTimeMillis()));
        context.sendMessage(Message.raw("You stood up from the card table."));
        return CompletableFuture.completedFuture(null);
    }
}
