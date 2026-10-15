# Screens

The game's structure lives in the bot, as Java: the `Screens` class beside `Sdk.java` holds one
`GameScreen` constant per screen. A run walks it to reach a screen; the assistant's Discovery tools and the
🗺 Screens window write it. What changed and why is in `CHANGELOG.md` (the Screens audit, 2026-10-15).

## The model (`api/screens`)

- **`GameScreen`**: a name, a `marker` (a picture only this screen shows), a `from` way (the tree), `alsoFrom`
  ways, a `back` input (null: a close button, then Escape), `inputs`, looks (`ScreenVariant`), `popup`, `notes`,
  `reached` (SURVEYED → BUILT → TRIED → TUNED, or LOCKED), `owes`, and `at(Point)`, the corner its card was
  moved to on 🗺 Screens (none: the layout places it). A screen names the one it is reached from,
  never the ones it leads to, so no constant names one declared after it. A popup is in no tree: a marker and a
  way out only.
- **`Input`**: a picture click, a key or a scroll, with `did` (IN_PLACE, NONE, LEAVES; null while untried),
  `kind` (CHORE, SHORTCUT, CHOICE, INFO), `never`, `sometimes`, `locked`, `when` and button looks (`variants`).
- **`Way`**: a parent and the input that opens the screen. Its input is a bare copy; `ScreenGraph.via` reads the
  parent's current input that `sameAs` it, so a look, a lock or a `when` added later counts (2026-10-15).
- **Conditions** (`Input.when`, `Way.when`, `ScreenVariant.when`) are method references only
  (`Firestone::eventOn`); `ScreenTypes.isMethod` refuses anything else before it reaches the Java.
- **`Navigate.to / isOn / where`** (palette, Interaction) are the bot's calls.

## The graph and the walk (`internal/screens`)

- **`ScreenGraph`** is the one pure model every consumer reads: edges, `pathTo`, `presence` (a screen only
  behind a `sometimes` input shows at times, one behind a `locked` input is locked: read off the edges, never
  flagged), shape, the pictures each screen uses, and what each screen owes (`owed`; an `owes` entry
  "marker — why" stands for the marker).
- **Discovery's order** is one rule for every screen (the user's, 2026-10-14): cut every input, try each, and
  finish a screen an input opens before the next input of the screen it was opened from (`unfinishedBelow`,
  `branchBegun`; popups are skipped). `upTo` and `workLeft` say what is next.
- **`ScreenWalk`** finds where the game is (`whereOn`: a screen opened from another, by any way in, hides it) and
  walks one edge at a time: do the input, wait up to `STEP_MS` (10 s) for the next marker, close a popup that
  shows, plan again from where a stray step landed. A way back is done once the left screen's marker is gone,
  wherever it lands. Ends: ARRIVED, NOT_NOW, LOCKED, MISSING (the map names a picture the game no longer shows:
  Upkeep), STUCK, STOPPED.
- **`Reach`** is what acts: `RunReach` for a run (the bot's capture, confidence and click spread),
  `ToolReach` for the assistant. `perform` is the one place an input is done.
- **Telemetry**: Navigate fires `on screen <CONSTANT>` (`ScreenStep`); the run bar and 🗺 Screens show its name.

## The assistant's tools (`plugin/assist`)

- **`ScreenBook`** reads and writes `Screens.java` through the host, on the FX thread; `plugin/pictures/ScreenMapRead`
  is the one safe read the editors, picture groups and the constant view share.
- **Discovery** (`MapTools`): `crop_pictures` cuts a screen's inputs, marker, back, looks (`variantOf`,
  `screenLook`, `when`), `sometimes` and `popup`; `place_pictures` puts pictures the bot already has on a screen;
  `try_input` clicks each input of its list and records what it did — opened a new screen (owing its marker), led to a mapped one
  (a way in or its way back, found with `whereOn`), changed it in place, or nothing — and stops at a popup;
  `not_a_screen` turns a screen back into an IN_PLACE result on each of its ways in; `go_to_screen` and
  `which_screen` walk the map as a run does; `read_map` prints it (`plugin/map/MapReport`). The tool refuses a
  screen while one below it is unfinished or one owes its marker.
- **`edit_map`** (`MapEdits`) fixes a wrong record by hand, nothing clicked: rename, delete, popup, notes, an
  input's result or kind, reset or remove an input, use one as the way back, remove a way, a way's, input's or
  look's `when`, a new screen under another. Each action is the `ScreenEdits` method the window's menus call.
- `Protocol` and `SdkAssist.GUIDE` say when each runs (Discovery, Upkeep).

## The window (`plugin/map`)

- **`ScreenMap`**: one window per project (and a narrow copy in the overlay editor). It reads the map every
  1.5 s and redraws only when a `ScreenCards` value or a picture's file time changed; an empty read is trusted on
  the second in a row. The card a run is on is lit from telemetry.
- **`ScreenCards`** decides, apart from JavaFX, each card (state, progress, `pills`), each link (`label()` names
  the default way back) and each input's row. **`ScreenCanvas`** draws them, folds the tree, caches at most 400
  images and places input boxes on any frame shape (`boxOn`, letterboxed).
- **`ScreenPanel`** is the picked screen: its shot with boxes, ▶ Try, 🧭 Go here, ✂ Cut, notes, and the hand
  edits of `ScreenHands`, all off while a try or a walk runs. **`ScreenDesk`** runs Try and Go here by the
  assistant's own code, so the window and the assistant cannot record an input two ways.
- **Gestures** are `GraphCanvas`'s (the toolkit): the wheel zooms about the pointer, a right drag pans, a left
  drag moves a card or the selected ones (Ctrl/Shift+click, a box drag). A move is written as each moved card's
  `at(new Point(x, y))` in `Screens.java` (`ScreenEdits.move`), so the map looks the same on the next open and on
  another computer; the first move pins every card shown, so the others stay put. **Tidy** (`ScreenEdits.tidy`)
  drops every `at`.
- **Undo** is Studio's own, through the contract's `StudioServices.history()`: each hand edit, move and Tidy is one
  `History.batch` step beside the user's code edits. ↶/↷ and Ctrl+Z/Ctrl+Y (`plugin/HistoryKeys`, shared with the
  Activity Flow) walk that history, and the assistant's MCP `undo`/`redo` walk it too. Nothing in the window
  keeps an undo of its own.
- **`PictureView`** is one window beside the map: a screen's shot big (its inputs boxed, the wheel zooming), or an
  input's picture at whole-pixel zoom, never blurred, with "← <screen>" back to the shot it was opened from.
- **`MapGuide`** is the toolbar's **?** legend, and holds the tooltips it repeats.
- Around it: the `Navigate` screen picker (`plugin/editors/ScreenEditors`), the `Screens` constant preview
  (`ScreenConstantView`), Manage Pictures' groups by screen (`plugin/pictures/ScreenGroups`).
