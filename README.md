# Card Duel — a Hearthstone-style card game for Hytale

Place a **Card Deck** on a table, press **F** on it, and sit down. A second player presses **F** on the same deck to
join. Each placed deck is its own table (a room) for **two players maximum**. Pick a deck, press Ready, and duel.
Playing alone? Press **Play vs Training Bot**.

> **Status: v0.1.0, first delivery.** The rules, rooms and screen logic are tested (see *Tests*). The Hytale-specific
> glue was written against the current server API (0.7) using patterns from working open-source plugins, but it has
> **not been run inside a real Hytale server yet** (see *First run / what to check*).

## Quick start

1. Install **Java 25** (JetBrains Runtime recommended) and open this folder in IntelliJ IDEA (or any editor).
2. Run the tests (no Hytale needed): `gradlew selfTest` (Windows: `gradlew.bat selfTest`)
3. Build the mod: `gradlew build` → `build/libs/CardDuel-0.1.0.jar`
4. Drop the jar into your Hytale mods folder (`%appdata%/Hytale/UserData/Mods/`, or the `mods` folder of your server)
   and enable it for your world. For development you can also use `gradlew setupHytaleDev` then `gradlew runServer`
   (provided by the template's Gradle plugin).
5. In game: `/give <you> CardDuel_Deck` (or find **Card Deck** in the creative menu), place it on a table,
   press **F** on it. `/cardduel` prints these instructions in chat.

Tip: turn on **Diagnostic Mode** (Hytale settings → General) while developing, it gives detailed UI error messages.

## How to play

| Action | How |
|---|---|
| Play a card | Click it in your hand. Cards with a target: click the card, then the target. |
| Attack | Click one of your minions marked `*`, then click an enemy minion or the enemy hero. |
| Hero power | Click the hero power button (2 mana, once per turn). |
| End turn | `End Turn` button. A turn lasts 75 seconds. |
| Mulligan | At the start, click the cards you want to replace, then `Confirm`. |
| Cancel a selection | `Cancel` button, or click the same card again. |
| Leave | `Leave` (twice if the duel is running, because leaving forfeits). `/cardleave` works anywhere. |
| Closed the window by accident | Press **F** on the deck again, you keep your seat. |

Markers on screen: `*` ready to attack / playable, `!` valid target, `>` selected, `X` marked for mulligan.
Hover any card for its text.

### Hearthstone rules that are implemented

30 hero health, 30-card decks (max 2 copies), 10 max hand size (extra cards burn), 7 max board size, mana crystals
(+1 per turn up to 10), first player draws 3 and the second player 4 plus **The Coin**, **mulligan**, fatigue damage
(1, 2, 3...), summoning sickness, **Taunt, Charge, Divine Shield, Windfury**, **Battlecry** (including targeted ones),
**Deathrattle**, spells (damage, heal, draw, buff, summon, destroy, area damage), simultaneous combat damage,
chain death resolution, once-per-turn **hero powers** (each deck has its own hero), 75 s turn timer, three missed turns
in a row forfeits, draw when both heroes die, rematch.

Not implemented yet (see `docs/ROADMAP.md`): weapons/hero attacks, Stealth, Poisonous, Lifesteal, Freeze, Silence,
Secrets, Discover, Overload, deck building and card collection, custom card art.

## Where things are

```
src/main/java/com/cardduel/
  engine/   the rules. Pure Java. CardLibrary.java is where you add cards, heroes and decks.
  room/     tables: seats, lobby, rematch, bot pacing, click handling. Pure Java.
  ui/       TableView: turns game state into "set this label to that text" instructions. Pure Java.
  hytale/   the only code that touches the Hytale API (plugin, F-key interaction, UI page, commands).
src/main/resources/
  Server/Item/Items/CardDuel_Deck.json                 the placeable deck block
  Server/Item/RootInteractions/CardDuel_UseTable.json  connects "press F" to our code
  Server/Languages/en-US/cardduel.lang                 item names
  Common/UI/Custom/CardDuel/Table.ui (+ .png)          the table screen and its art
  Common/BlockTextures, Common/Icons                   deck block texture and inventory icon (placeholders)
src/test/java/                                         the self tests
docs/                                                  ARCHITECTURE, ADDING_CONTENT, HYTALE_API_NOTES, ROADMAP, CARDS
```

## Tests

`gradlew selfTest` runs two dependency-free suites (no framework, no server needed):

* **EngineSelfTest**: 121 checks of the rules plus 3000 full bot-vs-bot games with invariant checks after every turn.
* **RoomSelfTest**: 135 checks of seating, lobby, leaving, forfeits, idle handling, clicks, and that every element id
  the code uses exists in `Table.ui`, plus 300 full duels played by a random clicker against the bot.

## First run / what to check

I could not start Hytale or download the server API in my workspace, so the Hytale glue was compiled only against
stand-in classes modelled on working plugins. If something does not work on your first run, `docs/HYTALE_API_NOTES.md`
lists every Hytale call this mod makes, where each came from, and what to change if the API differs. The most likely
spots, in order: the block interaction JSON, `context.getTargetBlock()`, the `.ui` layout, `UICommandBuilder.set(.., boolean)`.

## Placeholder art

All art is generated placeholder art: a purple card-back pattern for the deck block, its icon and the lobby, and a green
felt for the table screen. The deck is a plain cube; replace it with a flat card-stack model made in Blockbench
(`ADDING_CONTENT.md` → *Replace the deck model*).
