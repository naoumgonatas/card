package com.cardduel.hytale;

import com.cardduel.room.Peer;
import com.cardduel.room.Room;
import com.cardduel.room.Seat;
import com.cardduel.ui.TableView;
import com.cardduel.ui.UiOp;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The table screen one player sees. It does no game logic: clicks go to {@link Room#handle}, and whenever the room
 * changes it calls {@link #refresh} so the page redraws from {@link TableView}.
 *
 * <p>Everything Hytale-specific about the table lives here and in {@code Common/UI/Custom/CardDuel/Table.ui}.
 */
public final class CardTablePage extends InteractiveCustomUIPage<CardTablePage.Data> implements Peer {

    /** Path of the markup file, relative to {@code Common/UI/Custom}. */
    public static final String UI_FILE = "CardDuel/Table.ui";

    private final PlayerRef playerRef;
    private final String playerId;
    private Room room;
    private volatile boolean open = true;
    /** True once this page already answered the event being handled (Hytale shows "Loading..." until it does). */
    private boolean answered;

    public CardTablePage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.playerRef = playerRef;
        this.playerId = playerRef.getUuid().toString();
    }

    /** Called right after the player sat down, before the page is opened. */
    public void bind(Room room) {
        this.room = room;
    }

    // ================================================================================================
    // Hytale page callbacks
    // ================================================================================================

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(UI_FILE);
        bindButtons(events);
        int me = room.seatIndex(playerId);
        if (me >= 0) apply(cmd, TableView.full(room, me, System.currentTimeMillis()));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        super.handleDataEvent(ref, store, data);
        answered = false;
        if (data != null && data.button != null && room != null) {
            room.handle(playerId, data.button, System.currentTimeMillis());
        }
        // Always answer the click, even if nothing changed, or the client keeps showing "Loading...".
        if (!answered && open) sendUpdate();
    }

    @Override
    protected void close() {
        open = false;
        if (room != null) {
            int me = room.seatIndex(playerId);
            if (me >= 0) {
                Seat seat = room.seats[me];
                if (seat != null && seat.peer == this) seat.peer = null; // stays seated, F on the deck reopens it
            }
        }
        super.close();
    }

    // ================================================================================================
    // Peer: how the room talks to this player
    // ================================================================================================

    @Override
    public boolean isConnected() {
        Ref<EntityStore> ref = playerRef.getReference();
        return ref != null && ref.isValid();
    }

    @Override
    public void refresh(boolean full) {
        if (!open || room == null) return;
        int me = room.seatIndex(playerId);
        if (me < 0) return;
        long now = System.currentTimeMillis();
        UICommandBuilder cmd = new UICommandBuilder();
        apply(cmd, full ? TableView.full(room, me, now) : TableView.timer(room, me, now));
        sendUpdate(cmd, new UIEventBuilder(), false);
        answered = true;
    }

    @Override
    public void tell(String message) {
        playerRef.sendMessage(Message.raw(message));
    }

    @Override
    public void closeUi() {
        // Peer method (not the page hook above): hide the table for this player.
        open = false;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) player.getPageManager().setPage(ref, store, Page.None);
    }

    // ================================================================================================

    private static void apply(UICommandBuilder cmd, List<UiOp> ops) {
        for (UiOp op : ops) {
            if (op.value() instanceof Boolean b) {
                cmd.set(op.selector(), b.booleanValue());
            } else {
                cmd.set(op.selector(), (String) op.value());
            }
        }
    }

    /** Every clickable element and the action name {@link Room#handle} receives for it. */
    private static void bindButtons(UIEventBuilder events) {
        for (int i = 0; i < TableView.BOARD_SLOTS; i++) {
            bind(events, "#M" + i, "M" + i);
            bind(events, "#E" + i, "E" + i);
        }
        for (int i = 0; i < TableView.HAND_SLOTS; i++) {
            bind(events, "#H" + i, "H" + i);
        }
        bind(events, "#MeHero", "MH");
        bind(events, "#OppHero", "EH");
        bind(events, "#EndBtn", "END");
        bind(events, "#PowerBtn", "POWER");
        bind(events, "#CancelBtn", "CANCEL");
        bind(events, "#LeaveBtn", "LEAVE");
        bind(events, "#ReadyBtn", "READY");
        bind(events, "#BotBtn", "BOT");
        bind(events, "#DeckPrev", "DECKPREV");
        bind(events, "#DeckNext", "DECKNEXT");
        bind(events, "#LbLeaveBtn", "LEAVE");
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Button", action), false);
    }

    /** What the client sends back with each click. */
    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .addField(new KeyedCodec<>("Button", Codec.STRING), (d, v) -> d.button = v, d -> d.button)
                .build();

        private String button;
    }
}
