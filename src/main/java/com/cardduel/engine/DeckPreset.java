package com.cardduel.engine;

import java.util.List;

/**
 * A ready-made 30-card deck with its hero. Players pick one in the lobby.
 *
 * @param cards card ids; a deck must have exactly {@link Game#DECK_SIZE} entries (checked at startup)
 */
public record DeckPreset(String id, String name, String description, HeroDef hero, List<String> cards) {
}
