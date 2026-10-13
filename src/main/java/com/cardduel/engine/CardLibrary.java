package com.cardduel.engine;

import static com.cardduel.engine.Keyword.CHARGE;
import static com.cardduel.engine.Keyword.DIVINE_SHIELD;
import static com.cardduel.engine.Keyword.TAUNT;
import static com.cardduel.engine.Keyword.WINDFURY;
import static com.cardduel.engine.TargetKind.ALL_ENEMY_MINIONS;
import static com.cardduel.engine.TargetKind.ALL_FRIENDLY_MINIONS;
import static com.cardduel.engine.TargetKind.ANY_CHARACTER;
import static com.cardduel.engine.TargetKind.ENEMY_CHARACTER;
import static com.cardduel.engine.TargetKind.ENEMY_HERO;
import static com.cardduel.engine.TargetKind.ENEMY_MINION;
import static com.cardduel.engine.TargetKind.FRIENDLY_MINION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * THE place to add cards, heroes and decks.
 *
 * <ol>
 *   <li>Add a card with {@link #add}. Tokens (summon-only cards) go first.</li>
 *   <li>Put its id into one of the deck lists at the bottom.</li>
 *   <li>Decks are validated on startup: exactly 30 cards, max 2 copies, every id must exist.</li>
 * </ol>
 */
public final class CardLibrary {

    private static final Map<String, CardDef> ALL = new LinkedHashMap<>();
    private static final List<DeckPreset> PRESETS = new ArrayList<>();

    public static final String COIN = "coin";

    static {
        // ---------------------------------------------------------------- tokens
        add(CardDef.minion("slimeling", "Slimeling", 1, 1, 1).token().text("A wobbly little slime."));
        add(CardDef.minion("pebble", "Pebble", 1, 1, 1).token().text("Small, but it counts."));
        add(CardDef.minion("thorn", "Thorn Wall", 1, 0, 2).keywords(TAUNT).token());
        add(CardDef.spell(COIN, "The Coin", 0).onPlay(Effect.gainMana(1)).token());

        // ---------------------------------------------------------------- minions
        add(CardDef.minion("sapling", "Kweebec Sapling", 1, 1, 2).text("A shy forest sprout."));
        add(CardDef.minion("ember_imp", "Ember Imp", 1, 2, 1).text("Hot-headed and fragile."));
        add(CardDef.minion("glintbug", "Glintbug", 1, 1, 1).keywords(DIVINE_SHIELD));

        add(CardDef.minion("magpie", "Loot Magpie", 2, 2, 1).deathrattle(Effect.draw(1)));
        add(CardDef.minion("mossback", "Mossback", 2, 1, 4).keywords(TAUNT));
        add(CardDef.minion("mud_crawler", "Mud Crawler", 2, 2, 2).text("Crawls out of the swamp."));

        add(CardDef.minion("raider", "Feran Raider", 3, 3, 1).keywords(CHARGE));
        add(CardDef.minion("healer", "Kweebec Healer", 3, 2, 3).onPlay(Effect.heal(ANY_CHARACTER, 3)));
        add(CardDef.minion("archer", "Trork Archer", 3, 2, 2).onPlay(Effect.damage(ENEMY_CHARACTER, 1)));
        add(CardDef.minion("dawn_squire", "Dawn Squire", 3, 3, 2).keywords(DIVINE_SHIELD));
        add(CardDef.minion("drummer", "Warchief's Drummer", 3, 2, 3).onPlay(Effect.buff(ALL_FRIENDLY_MINIONS, 1, 0)));

        add(CardDef.minion("bear", "Cave Bear", 4, 4, 4).text("Do not wake the bear."));
        add(CardDef.minion("gale_hawk", "Gale Hawk", 4, 2, 4).keywords(WINDFURY));
        add(CardDef.minion("scholar", "Kweebec Scholar", 4, 2, 4).onPlay(Effect.draw(1)));
        add(CardDef.minion("slime_mother", "Slime Mother", 4, 2, 4).deathrattle(Effect.summon("slimeling", 2)));

        add(CardDef.minion("golem", "Stone Golem", 5, 4, 6).keywords(TAUNT));
        add(CardDef.minion("brute", "Trork Brute", 5, 4, 4).keywords(CHARGE));

        add(CardDef.minion("drake", "Ember Drake", 6, 6, 5).onPlay(Effect.damage(ENEMY_HERO, 2)));
        add(CardDef.minion("giant", "Frost Giant", 7, 8, 8).text("It just keeps getting bigger."));

        // ---------------------------------------------------------------- spells
        add(CardDef.spell("spark", "Spark", 1).onPlay(Effect.damage(ANY_CHARACTER, 2)));
        add(CardDef.spell("briar_barrier", "Briar Barrier", 2).onPlay(Effect.summon("thorn", 2)));
        add(CardDef.spell("insight", "Insight", 3).onPlay(Effect.draw(2)));
        add(CardDef.spell("healing_touch", "Healing Touch", 3).onPlay(Effect.heal(ANY_CHARACTER, 8)));
        add(CardDef.spell("heartwood", "Heartwood Blessing", 3).onPlay(Effect.buff(FRIENDLY_MINION, 3, 3)));
        add(CardDef.spell("rally", "Rally the Grove", 3).onPlay(Effect.buff(ALL_FRIENDLY_MINIONS, 1, 1)));
        add(CardDef.spell("fireball", "Fireball", 4).onPlay(Effect.damage(ANY_CHARACTER, 6)));
        add(CardDef.spell("ember_storm", "Ember Storm", 5).onPlay(Effect.damage(ALL_ENEMY_MINIONS, 3)));
        add(CardDef.spell("void_strike", "Void Strike", 5).onPlay(Effect.destroy(ENEMY_MINION)));

        // ---------------------------------------------------------------- heroes
        HeroDef pyra = new HeroDef("pyra", "Pyra the Ember Witch", "Flicker",
                "Deal 1 damage to a character.", Game.HERO_POWER_COST, List.of(Effect.damage(ANY_CHARACTER, 1)));
        HeroDef bram = new HeroDef("bram", "Bram the Stone Warden", "Pebble Call",
                "Summon a 1/1 Pebble.", Game.HERO_POWER_COST, List.of(Effect.summon("pebble", 1)));

        // ---------------------------------------------------------------- decks (exactly 30 cards each)
        PRESETS.add(new DeckPreset("ember_rush", "Ember Rush",
                "Fast and fiery. Burn them out before they can set up.", pyra,
                deck("spark", 2, "ember_imp", 2, "magpie", 2, "raider", 2, "archer", 2, "mud_crawler", 2,
                        "gale_hawk", 2, "dawn_squire", 2, "drummer", 2, "bear", 2, "brute", 2, "fireball", 2,
                        "ember_storm", 1, "drake", 2, "rally", 1, "insight", 1, "glintbug", 1)));
        PRESETS.add(new DeckPreset("stone_wall", "Stone Wall",
                "Taunts, healing and big finishers. Outlast them.", bram,
                deck("sapling", 2, "glintbug", 2, "mossback", 2, "mud_crawler", 1, "healer", 2, "briar_barrier", 2,
                        "dawn_squire", 2, "scholar", 2, "slime_mother", 2, "bear", 2, "golem", 2, "healing_touch", 2,
                        "heartwood", 1, "rally", 2, "void_strike", 1, "insight", 1, "giant", 1, "drake", 1)));

        validate();
    }

    private CardLibrary() {
    }

    // ---------------------------------------------------------------------------------------------

    private static void add(CardDef.Builder b) {
        CardDef c = b.build();
        if (ALL.put(c.id, c) != null) throw new IllegalStateException("Duplicate card id: " + c.id);
    }

    /** deck("a", 2, "b", 1, ...) expands to a list of ids. */
    private static List<String> deck(Object... pairs) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            for (int n = 0; n < (Integer) pairs[i + 1]; n++) out.add((String) pairs[i]);
        }
        return Collections.unmodifiableList(out);
    }

    private static void validate() {
        for (CardDef c : ALL.values()) {
            for (Effect e : concat(c.onPlay, c.deathrattle)) {
                if (e.kind() == EffectKind.SUMMON && !ALL.containsKey(e.cardId())) {
                    throw new IllegalStateException(c.id + " summons unknown card " + e.cardId());
                }
            }
        }
        for (DeckPreset p : PRESETS) {
            if (p.cards().size() != Game.DECK_SIZE) {
                throw new IllegalStateException("Deck " + p.id() + " has " + p.cards().size() + " cards, needs " + Game.DECK_SIZE);
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String id : p.cards()) {
                CardDef c = ALL.get(id);
                if (c == null) throw new IllegalStateException("Deck " + p.id() + " has unknown card " + id);
                if (c.token) throw new IllegalStateException("Deck " + p.id() + " contains token " + id);
                if (counts.merge(id, 1, Integer::sum) > 2) throw new IllegalStateException("Deck " + p.id() + " has 3+ copies of " + id);
            }
        }
    }

    private static List<Effect> concat(List<Effect> a, List<Effect> b) {
        List<Effect> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    // ---------------------------------------------------------------------------------------------

    public static CardDef get(String id) {
        CardDef c = ALL.get(id);
        if (c == null) throw new IllegalArgumentException("Unknown card id: " + id);
        return c;
    }

    public static String nameOf(String id) {
        CardDef c = ALL.get(id);
        return c == null ? id : c.name;
    }

    public static List<CardDef> all() {
        return List.copyOf(ALL.values());
    }

    public static List<DeckPreset> presets() {
        return List.copyOf(PRESETS);
    }

    public static DeckPreset preset(int index) {
        return PRESETS.get(Math.floorMod(index, PRESETS.size()));
    }
}
