package com.cardduel.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Everything one player owns during a game. Read it freely, but change it only through {@link Game}. */
public final class PlayerState {
    public final String name;
    public final HeroDef hero;
    public final DeckPreset deckPreset;
    public int heroHealth = Game.START_HEALTH;
    public final int heroMaxHealth = Game.START_HEALTH;
    public int mana;
    public int maxMana;
    public int fatigue;
    public boolean heroPowerUsed;
    public boolean mulliganDone;
    /** Top of the deck is the LAST element. */
    public final Deque<CardDef> deck = new ArrayDeque<>();
    public final List<CardDef> hand = new ArrayList<>();
    public final List<Minion> board = new ArrayList<>();

    PlayerState(String name, DeckPreset preset) {
        this.name = name;
        this.deckPreset = preset;
        this.hero = preset.hero();
    }
}
