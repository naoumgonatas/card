package com.cardduel.hytale;

import com.cardduel.room.RoomManager;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Runs when a player presses F (Use) on a placed Card Deck block.
 *
 * <p>Wiring: {@code CardDuel_Deck.json} (block) -> {@code BlockType.Interactions.Use = "CardDuel_UseTable"}
 * -> {@code Server/Item/RootInteractions/CardDuel_UseTable.json} -> {@code Type: "CardDuel_UseTable"} -> this class
 * (registered under that name in {@link CardDuelPlugin#setup}).
 *
 * <p>The table (room) is identified by the position of the block, so every placed deck is its own room.
 */
public final class UseTableInteraction extends SimpleInstantInteraction {

    public static final String TYPE_NAME = "CardDuel_UseTable";

    public static final BuilderCodec<UseTableInteraction> CODEC = BuilderCodec.builder(
            UseTableInteraction.class, UseTableInteraction::new, SimpleInstantInteraction.CODEC).build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        try {
            Ref<EntityStore> entity = context.getEntity();
            PlayerRef playerRef = commandBuffer.getComponent(entity, PlayerRef.getComponentType());
            Player player = commandBuffer.getComponent(entity, Player.getComponentType());
            if (playerRef == null || player == null) {
                context.getState().state = InteractionState.Finished; // not a player (a mob?), ignore
                return;
            }

            // Block that was used. `var` on purpose: the position type (x/y/z fields) has changed between server versions.
            var block = context.getTargetBlock();
            if (block == null) {
                playerRef.sendMessage(Message.raw("Card Duel: could not tell which deck you used. Try again."));
                context.getState().state = InteractionState.Finished;
                return;
            }

            World world = commandBuffer.getExternalData().getWorld();
            Store<EntityStore> store = commandBuffer.getExternalData().getStore();
            String key = world.getName() + "@" + block.x + "," + block.y + "," + block.z;

            CardTablePage page = new CardTablePage(playerRef);
            RoomManager.Join join = CardDuelPlugin.rooms().join(key, world, playerRef.getUuid().toString(),
                    playerRef.getUsername(), page, System.currentTimeMillis());
            if (join.error() != null) {
                playerRef.sendMessage(Message.raw(join.error()));
                context.getState().state = InteractionState.Finished;
                return;
            }
            page.bind(join.room());
            player.getPageManager().openCustomPage(entity, store, page);
            context.getState().state = InteractionState.Finished;
        } catch (Throwable t) {
            CardDuelPlugin.LOGGER.atSevere().withCause(t).log("Card Duel: failed to open the table");
            context.getState().state = InteractionState.Failed;
        }
    }
}
