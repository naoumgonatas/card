# Hytale API notes

The Hytale glue (`com.cardduel.hytale`) was written without access to the real server jar, using patterns from working
open-source plugins. It compiles against stand-in classes only. **If the build or the first run fails, start here.**
Server version targeted: 0.7.x (`manifest.json` → `ServerVersion`). Math types are `org.joml` since Update 7.

## Calls we make, and what to check

| Where | API | Risk | If it breaks |
|---|---|---|---|
| CardDuelPlugin | `JavaPlugin.setup/start/shutdown`, `getCodecRegistry(Interaction.CODEC).register(name, class, codec)`, `getCommandRegistry().registerCommand` | low (standard plugin pattern) | compare with the plugin template's example class |
| CardDuelPlugin | `HytaleServer.SCHEDULED_EXECUTOR` | low | use your own `ScheduledExecutorService` |
| UseTableInteraction | `SimpleInstantInteraction.firstRun(type, context, cooldownHandler)` | medium | check the signature in an existing interaction in the server jar |
| UseTableInteraction | **`context.getTargetBlock()` with `.x/.y/.z`** | **highest**. Never verified | if it is null or has other fields, find how the server exposes the used block (look at built-in "Use" interactions such as doors/benches) and rebuild the key `world@x,y,z` from it |
| UseTableInteraction | `PlayerRef.getComponentType()`, `Player.getPageManager().openCustomPage(ref, store, page)` | medium | same call pattern as AdminUI-style plugins |
| CardTablePage | `InteractiveCustomUIPage<Data>`, `build(...)`, `handleDataEvent(...)`, `sendUpdate(cmd, evt, false)` | medium | `sendUpdate` must always be called or the client shows "Loading..." |
| CardTablePage | `UICommandBuilder.set(selector, boolean)` for `.Visible` | **high** | if no boolean overload exists, use `set(sel, "true"/"false")` or the matching typed overload |
| CardTablePage | `UIEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Id", EventData.of("Button", action))` | medium | |
| CardTablePage | `protected void close()` hook to detach the seat when the window closes | medium | if it is not called, closing the window leaves the page attached; the 5-minute idle rule still frees the seat |
| CardTablePage | `Page.None` via `setPage` to close the UI from code | medium | |
| Commands | `AbstractAsyncCommand`, `context.sender()`, `sender instanceof PlayerRef` | medium | in some versions the sender is a `Player`; adapt the cast |
| Assets | block JSON `Interactions.Use`, `Furniture.Signs` category, 32x32 cube texture | **high** | compare `CardDuel_Deck.json` with a vanilla placeable block (e.g. a bench or crate) and copy its structure |
| Assets | `Table.ui` syntax (templates `@MinionSlot`, groups, `.TooltipText`) | **high**, never rendered | enable Diagnostic Mode; the client prints the exact line of a bad UI file |

## Symptoms -> cause

* **Pressing F does nothing**: the block JSON `Interactions.Use` or the RootInteraction file name does not match, or the
  codec was not registered. Check the server log for "Card Duel: N cards ... loaded" (proves `setup` ran).
* **"Could not tell which deck you used"**: `getTargetBlock()` problem (see above).
* **Screen stuck on "Loading..."**: an exception in `build()` or a `.ui` parse error; read the log and Diagnostic Mode.
* **Screen opens but is blank/ugly**: layout only, edit `Table.ui`. Game logic is unaffected.
* **Build error on a Hytale class**: the API moved; fix only inside `com.cardduel.hytale`. Nothing else imports Hytale.

## Where to look things up

Official docs/template: https://hytale.com (modding docs), plugin template with `com.azuredoom.hytale-tools`.
Decompile or browse the server jar in IntelliJ (`HytaleServer.jar`) and search for the class names above.
