# Architecture

Four layers. Only the last one knows Hytale exists, so the first three are fully testable on any machine.

```
 engine  ->  room  ->  ui  ->  hytale
 rules       tables    what the   plugin, F-key interaction,
             & seats   screen     UI page, commands
                       shows
```

## engine (`com.cardduel.engine`)

* `Game` is one duel between two players. Every action is a method that returns a `Result` (`ok` or a message for the
  player): `mulligan`, `playCard`, `attack`, `heroPower`, `endTurn`, `concede`, plus `tick(now)` for timers.
  Time is passed in as millis so tests control the clock.
* `CardDef` (immutable card definition) + `Effect` (what a card does) + `TargetKind` (who it hits) + `Keyword`.
  Effects are data (`Effect.damage(ENEMY_CHARACTER, 3)`), not code, so cards are one-liners in `CardLibrary`.
* `CardLibrary` holds every card, hero and deck preset and validates them on startup (30 cards, max 2 copies, ids exist).
* `PlayerState` / `Minion` hold runtime state. They are public for reading; change them only through `Game`.
* `Bot` is the training opponent. It only uses the public `Game` API, like a human would.
* A `Target(owner, slot)` points at a hero (`slot = -1`) or a minion slot on a board. The same type is used by clicks,
  effects and the bot.

## room (`com.cardduel.room`)

* `RoomManager`: all tables on the server and which table each player sits at (a player can sit at one table).
* `Room`: one table. Two `Seat`s, a state (`LOBBY` → `DUEL` → `FINISHED` → `LOBBY`), the `Game`, and the one-second
  `tick`, which runs turn timers, paces the bot, forfeits players who went offline and frees abandoned tables.
  `Room.handle(playerId, action, now)` is the single entry point for every button click.
* `SeatController`: the click state machine for one player ("I clicked a card that needs a target, now I'm choosing").
* `Peer`: what a seat uses to reach the player (redraw, chat message, close the screen). The Hytale implementation is
  `CardTablePage`. Tests use a fake.

Thread rule: **everything in `room` runs on the world thread.** Clicks already arrive there. The scheduler and the
`/cardleave` command hop onto it with `room.executor` (the `World`, which is an `Executor`).

## ui (`com.cardduel.ui`)

`TableView.full(room, seat, now)` returns a flat list of `UiOp(selector, value)`, for example
`#M0 #Name.Text = "Cave Bear"` or `#H3.Visible = false`. It contains all the "what do we show" logic (markers, status
line, hints, timer, log). The ids are the contract with `Table.ui`; `RoomSelfTest` verifies they all exist.

## hytale (`com.cardduel.hytale`)

* `CardDuelPlugin`: registers the interaction type `CardDuel_UseTable`, the commands, and a 1 s ticker.
* `UseTableInteraction`: runs when someone presses F on a placed deck. Builds the table key from the world name and
  block position, sits the player down through `RoomManager.join`, opens the page.
* `CardTablePage`: the UI page. `build()` loads `Table.ui`, binds every button, applies `TableView`. A click arrives in
  `handleDataEvent` as `Button = "H3"` (hand card 3), is passed to `Room.handle`, and the room tells every seated
  player's page to `refresh` (a `sendUpdate` with all the `set` commands).
* `CardDuelCommand` (`/cardduel`), `CardLeaveCommand` (`/cardleave`).

## From F key to card on the table

```
press F on deck block
 -> block JSON  BlockType.Interactions.Use = "CardDuel_UseTable"        (Server/Item/Items/CardDuel_Deck.json)
 -> root interaction file { Type: "CardDuel_UseTable" }                 (Server/Item/RootInteractions/)
 -> UseTableInteraction.firstRun   (registered in CardDuelPlugin.setup)
 -> RoomManager.join(key = world@x,y,z, ...)   -> Room (new or existing) -> Seat
 -> player.getPageManager().openCustomPage(... CardTablePage)
click a card
 -> CardTablePage.handleDataEvent("H3") -> Room.handle -> SeatController.clickHand -> Game.playCard
 -> Room.changed() -> CardTablePage.refresh() for both players -> TableView.full -> UICommandBuilder.set(...)
```

## Design decisions worth knowing

* **Click, click instead of drag and drop.** Hytale's custom UI has no drag and drop. Everything is select-then-target.
* **One static screen, many `set` commands.** Slots exist once in `Table.ui` and are shown/hidden and re-texted. The
  page never clears and rebuilds the UI, so button bindings are made once in `build()`.
* **The table is the block position.** No block entity or saved data: rooms live in memory, so a server restart ends
  duels. That is deliberate for v0.1.
* **Leaving = forfeit,** and offline players forfeit on the next tick. Closing the window only detaches the screen;
  the seat is kept for 5 minutes outside duels (`Room.IDLE_KICK_MILLIS`).
