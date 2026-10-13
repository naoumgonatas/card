# Adding content

All content is in `src/main/java/com/cardduel/engine/CardLibrary.java`. Run `gradlew selfTest` after every change:
it validates all decks and plays 3000 bot games, so broken cards show up immediately.

## Add a minion
Copy an existing minion line and change it. A minion is cost, attack, health, plus optional keywords and effects:
`Keyword.TAUNT`, `CHARGE`, `DIVINE_SHIELD`, `WINDFURY`; battlecry and deathrattle are `Effect`s
(for example `Effect.damage(TargetKind.ENEMY_CHARACTER, 2)`). Look at the existing cards for each effect type.

## Add a spell
Same, with an `Effect` and a `TargetKind` (what the player must click, or `NONE`).

## Add a deck / hero
Add a `HeroDef` (name, hero power cost/effect) and a `DeckPreset` with exactly 30 cards, max 2 copies each.
Startup validation fails loudly if the deck is invalid. New presets appear in the lobby's deck picker automatically.

## Add a new kind of effect
1. Add a value to `EffectKind`.
2. Handle it in `Game` (search for where another `EffectKind` is applied).
3. Add a test in `EngineSelfTest`.

## Regenerate docs/CARDS.md
Compile and run a small program that prints `CardLibrary.all()` as a markdown table (the previous one lived outside
the project; any 20-line loop over `CardLibrary.all()` does it).

## Change the screen
Edit `src/main/resources/Common/UI/Custom/CardDuel/Table.ui` and `ui/TableView.java`. Every id used by `TableView`
must exist in `Table.ui`; `RoomSelfTest` checks that.

## Replace the deck model
The deck is a cube with `Common/BlockTextures/CardDuel_Deck.png`. To get a flat stack of cards, model it in Blockbench,
export to `Common/Blocks/`, and point the `CustomModel` fields in `CardDuel_Deck.json` at it.

## Tunables
`Room.java`: `BOT_THINK_MILLIS`, `IDLE_KICK_MILLIS`. `Game.java`: turn length (75 s) and idle-turn forfeit (3).
