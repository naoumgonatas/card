package com.cardduel.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Immutable definition of a card. Create them with {@link #minion} / {@link #spell} in {@link CardLibrary}. */
public final class CardDef {
    public final String id;
    public final String name;
    public final CardType type;
    public final int cost;
    public final int attack;
    public final int health;
    public final Set<Keyword> keywords;
    /** Spell effects, or the battlecry of a minion. */
    public final List<Effect> onPlay;
    public final List<Effect> deathrattle;
    public final String text;
    /** Tokens never appear in decks; they are only summoned. */
    public final boolean token;

    private CardDef(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.type = b.type;
        this.cost = b.cost;
        this.attack = b.attack;
        this.health = b.health;
        this.keywords = Collections.unmodifiableSet(b.keywords.isEmpty() ? EnumSet.noneOf(Keyword.class) : EnumSet.copyOf(b.keywords));
        this.onPlay = List.copyOf(b.onPlay);
        this.deathrattle = List.copyOf(b.deathrattle);
        this.text = b.text != null ? b.text : autoText(b);
        this.token = b.token;
    }

    /** The target kind the player must pick when playing this card, or null if no target is needed. */
    public TargetKind chosenTarget() {
        for (Effect e : onPlay) {
            if (e.target().isChosen()) return e.target();
        }
        return null;
    }

    public boolean isMinion() {
        return type == CardType.MINION;
    }

    public static Builder minion(String id, String name, int cost, int attack, int health) {
        Builder b = new Builder(id, name, CardType.MINION, cost);
        b.attack = attack;
        b.health = health;
        return b;
    }

    public static Builder spell(String id, String name, int cost) {
        return new Builder(id, name, CardType.SPELL, cost);
    }

    // ------------------------------------------------------------------------------------------------

    private static String autoText(Builder b) {
        List<String> parts = new ArrayList<>();
        for (Keyword k : b.keywords) parts.add(k.label);
        String kw = String.join(", ", parts);
        StringBuilder sb = new StringBuilder(kw);
        String play = describe(b.onPlay);
        if (!play.isEmpty()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append(b.type == CardType.MINION ? "Battlecry: " : "").append(play);
        }
        String dr = describe(b.deathrattle);
        if (!dr.isEmpty()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append("Deathrattle: ").append(dr);
        }
        return sb.toString();
    }

    private static String describe(List<Effect> effects) {
        List<String> out = new ArrayList<>();
        for (Effect e : effects) out.add(describe(e));
        return String.join(" ", out);
    }

    private static String describe(Effect e) {
        String who = switch (e.target()) {
            case ANY_CHARACTER -> "a character";
            case ANY_MINION -> "a minion";
            case ENEMY_CHARACTER -> "an enemy";
            case ENEMY_MINION -> "an enemy minion";
            case FRIENDLY_MINION -> "a friendly minion";
            case ENEMY_HERO -> "the enemy hero";
            case OWN_HERO -> "your hero";
            case ALL_ENEMY_MINIONS -> "all enemy minions";
            case ALL_FRIENDLY_MINIONS -> "your minions";
            case ALL_MINIONS -> "all minions";
            case ALL_CHARACTERS -> "ALL characters";
            case RANDOM_ENEMY_CHARACTER -> "a random enemy";
            case RANDOM_ENEMY_MINION -> "a random enemy minion";
            case SELF -> "this minion";
        };
        return switch (e.kind()) {
            case DAMAGE -> "Deal " + e.a() + " damage to " + who + ".";
            case HEAL -> "Restore " + e.a() + " health to " + who + ".";
            case DRAW -> "Draw " + e.a() + (e.a() == 1 ? " card." : " cards.");
            case BUFF -> "Give " + who + " +" + e.a() + "/+" + e.b() + ".";
            case SUMMON -> "Summon " + e.a() + " " + CardLibrary.nameOf(e.cardId()) + (e.a() == 1 ? "." : "s.");
            case GAIN_MANA -> "Gain " + e.a() + " Mana Crystal this turn only.";
            case DESTROY -> "Destroy " + who + ".";
        };
    }

    // ------------------------------------------------------------------------------------------------

    public static final class Builder {
        private final String id;
        private final String name;
        private final CardType type;
        private final int cost;
        private int attack;
        private int health;
        private final Set<Keyword> keywords = EnumSet.noneOf(Keyword.class);
        private final List<Effect> onPlay = new ArrayList<>();
        private final List<Effect> deathrattle = new ArrayList<>();
        private String text;
        private boolean token;

        private Builder(String id, String name, CardType type, int cost) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.cost = cost;
        }

        public Builder keywords(Keyword... ks) {
            Collections.addAll(keywords, ks);
            return this;
        }

        /** Battlecry (minion) or spell effects, in order. */
        public Builder onPlay(Effect... es) {
            Collections.addAll(onPlay, es);
            return this;
        }

        public Builder deathrattle(Effect... es) {
            Collections.addAll(deathrattle, es);
            return this;
        }

        /** Overrides the auto-generated card text. */
        public Builder text(String t) {
            this.text = t;
            return this;
        }

        public Builder token() {
            this.token = true;
            return this;
        }

        public CardDef build() {
            return new CardDef(this);
        }
    }
}
