package com.cardduel.room;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Knows every table on the server and which table each player sits at. Thread-safe. */
public final class RoomManager implements Room.Listener {

    /** Result of trying to sit down: either a room + seat, or an error message for the player. */
    public record Join(Room room, Seat seat, String error) {
    }

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> seated = new ConcurrentHashMap<>();
    private final Random rng = new Random();

    /**
     * @param key      unique table key (world + block position)
     * @param executor runs code on that world's thread
     */
    public Join join(String key, Executor executor, String playerId, String playerName, Peer peer, long now) {
        String current = seated.get(playerId);
        if (current != null && !current.equals(key)) {
            Room other = rooms.get(current);
            if (other != null && other.seatIndex(playerId) >= 0) {
                return new Join(null, null, "You are already sitting at another card table. Use /cardleave first.");
            }
            seated.remove(playerId);
        }
        Room room = rooms.computeIfAbsent(key, k -> new Room(k, executor, this, rng));
        Seat seat = room.join(playerId, playerName, peer, now);
        if (seat == null) {
            if (room.isEmpty()) rooms.remove(key, room);
            return new Join(null, null, "This table is full. Two players are already dueling.");
        }
        seated.put(playerId, key);
        return new Join(room, seat, null);
    }

    public Room roomOf(String playerId) {
        String key = seated.get(playerId);
        return key == null ? null : rooms.get(key);
    }

    /** Called once a second from the plugin's scheduler (any thread). Each room ticks on its own world thread. */
    public void tickAll() {
        long now = System.currentTimeMillis();
        for (Room room : rooms.values()) {
            room.executor.execute(() -> {
                try {
                    room.tick(now);
                } catch (RuntimeException e) {
                    System.err.println("[CardDuel] room " + room.key + " failed to tick: " + e);
                    e.printStackTrace();
                }
            });
        }
    }

    public int roomCount() {
        return rooms.size();
    }

    public void clear() {
        rooms.clear();
        seated.clear();
    }

    @Override
    public void seatLeft(Room room, String playerId) {
        seated.remove(playerId, room.key);
    }

    @Override
    public void roomEmpty(Room room) {
        rooms.remove(room.key, room);
    }
}
