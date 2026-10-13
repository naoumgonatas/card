package com.cardduel.hytale;

import com.cardduel.engine.CardLibrary;
import com.cardduel.room.RoomManager;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Card Duel entry point. Registers the "use table" interaction, two chat commands and a one-second ticker.
 *
 * <p>Everything else (rules, rooms, screens) is plain Java in the other packages; this class and its neighbours in
 * {@code com.cardduel.hytale} are the only code that touches the Hytale API.
 */
public final class CardDuelPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static CardDuelPlugin instance;

    private final RoomManager rooms = new RoomManager();
    private ScheduledFuture<?> ticker;

    public CardDuelPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /** All tables on the server. */
    public static RoomManager rooms() {
        return instance.rooms;
    }

    @Override
    protected void setup() {
        // Touching CardLibrary validates every card and deck now, so a typo fails at startup instead of mid-duel.
        LOGGER.atInfo().log("Card Duel: " + CardLibrary.all().size() + " cards, " + CardLibrary.presets().size() + " decks loaded");

        // Makes the type name "CardDuel_UseTable" usable in asset JSON (see Server/Item/RootInteractions).
        getCodecRegistry(Interaction.CODEC).register(UseTableInteraction.TYPE_NAME, UseTableInteraction.class, UseTableInteraction.CODEC);

        getCommandRegistry().registerCommand(new CardDuelCommand());
        getCommandRegistry().registerCommand(new CardLeaveCommand());
    }

    @Override
    protected void start() {
        // Turn timers, the training bot and offline detection all run off this one-second tick.
        ticker = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                rooms.tickAll();
            } catch (Throwable t) { // an exception would silently cancel a repeating task
                LOGGER.atSevere().withCause(t).log("Card Duel: ticker error");
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        if (ticker != null) ticker.cancel(false);
        rooms.clear();
    }
}
