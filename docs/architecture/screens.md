# Screens

The game's structure lives in the bot, as Java: the `Screens` class beside `Sdk.java` holds one
`Screen` subclass and its constant per screen. A run walks it to reach a screen; the assistant's Discovery tools and the
🗺 Screens window write it. What changed and why is in `CHANGELOG.md` (the Screens audit, 2026-10-15).

## The model (`api/screens`)

- **`Screen`**: its inputs are its fields, the rest is its `Setup`: a name, `markers` (pictures that all show while the game shows it) and `absent` (pictures none of
  which shows: what tells it from a screen showing the same markers; `internal/screens/Markers` is the one rule every
  reader tells a screen by, 2026-10-16), a `from` way (the tree), `alsoFrom`
  ways, a `back` input (null: a close button, then Escape), `inputs`, looks (`ScreenVariant`), `popup`, `notes`,
  `reached` (SURVEYED → BUILT → TRIED → TUNED, or LOCKED), `owes`, and `at(Point)`, the corner its card was
  moved to on 🗺 Screens (none: the layout places it). A screen names the one it is reached from,
  never the ones it leads to, so no constant names one declared after it. A popup is in no tree: a marker and a
  way out only.
- **`Input`**: a picture click, a key or a scroll, with `tried()` (opened nothing), `leaves()` (to a screen
  reached otherwise), `info()` (only tells: tried last), `never`, `sometimes`, `locked`, `when` and button looks (`variants`). A
  picture shown more than once is one input per copy, `nth` (from 1, in reading order: `Reach.readingOrder`), since
  copies may open different screens; `each` does every copy in turn (the user's call, 2026-10-16). A button is
  pressed by its `Gesture` (`.gesture(Gesture.DOUBLE)`, right, middle, hover; `.hold(Duration)` a hold, else
  `Button.HOLD_TIME`), a click when it says none; a `Hotkey` is a `Combo`, keys together or held
  (`Hotkey.of(Key)` stays the one-key spelling, its own shape). An emulator takes a tap, a double tap and a long
  press; the rest are a mouse's (2026-10-22). A text field is a button that types once clicked (`Typing`):
  `.types(Texts.GUILD)`, a `Words` constant of the bot's `Texts` class (`@SdkValue(TEXTS)`, the user's call: texts
  kept as pictures are), which a walk types on its way through; or `.typed()`, the text a body gives
  (`Navigate.type(field, text)`, a `@Param` when the runner picks it), which `ScreenWalk` never walks through and
  ends NOT_NOW naming. `.clears()` selects what it holds first (Ctrl+A; an emulator, with no select-all, keeps it),
  `.submits()` presses Enter after (2026-10-22). A drag is a button too (`Drag`, built as a trait like the field so
  pictures, looks, copies and strips stay as they are): `.dragsOnto(Pictures.SLOT)`, let go on a picture found on the
  same frame, or `.dragsInto(Zones.END)`, at a zone's centre placed on the frame as `source.region` places it
  (a slider, a swipe); `.dragTime(Duration)`, else `Button.DRAG_TIME`. It takes the place of the gesture and the
  text; a picture drop not on the frame is no press, ABSENT saying so (2026-10-22).
- **Words** (`Caption.of(Texts.SETTINGS, Zones.TITLE)`, a `Texts` and a `Zones` constant): what a screen shows, read
  by OCR inside the zone only. `Setup.texts(List.of(…))` tells a screen by them too: `Markers.onEach` reads them last,
  and only for a set its pictures leave in, each caption once per frame, so a screen of one frame and another title, or
  of no picture of its own, is told apart at the cost of OCR only where pictures cannot decide. `TextButton.of(caption)`
  is a fifth `Input`, clicked at the centre of the line that is its words, by a gesture as a button is. A line matches
  when it is the whole words (`LineMatch.same`): case-blind, spaces and marks at its ends dropped, edits forgiven by
  length (none up to 4 letters, one up to 8, two past), so `Claim` never matches `Claimed` nor `OK` `O` (the user's
  call, 2026-10-22; `Text.findFuzzy`'s two edits anywhere in a line match almost any line for a short label).
- **Places** (the user's calls, 2026-10-22): `Place.above/below/leftOf/rightOf/nearest(anchor)` and
  `between(a, b)`, the anchor a picture or a `Caption`, found on the same frame. `Button.place(place)` keeps a button
  only where its place says (the Claim on Daily's row); `Pick.at(place)` picks a copy from a body. Column and row are
  the anchor's centre grown by its width or height, past its edge; the copy nearest the anchor wins
  (`internal/screens/Placed`); no copy, or no anchor, presses nothing (ABSENT). `Button.beside(anchor, across, down)`
  is a button with no picture of its own: its `picture` is the anchor, its `Beside` the spot's offset in the anchor's
  size (a spot the anchor's size), pressed only when the anchor is found; there is no fixed spot without an anchor.
  `.grid(Grid.of(rows, columns, across, down))` makes the spots a grid's cells, repeated, their steps in the anchor's
  size, `Pick.cell(row, column)` one of them. `Reach.copies` maps each copy of the picture to its spots, then filters
  by the place; `Reach.seen` takes the nearest for a place, the cell for a cell. 🗺's board still boxes a spot on its
  anchor (`ScreenCanvas` was another session's at the time).
- **Readouts and results** (2026-10-22): a `Readout` is a field of its screen beside its inputs, never pressed,
  `public final Readout gold = Readout.digits(Zones.GOLD, Digits.GOLD);` or `Readout.text(Zones.HERO_NAME)` by OCR.
  Both kinds are a `ScreenPart`, the type the contract's `SCREEN_MAP` fields are of; `MapScreen` keeps them apart
  (`inputs`, `readouts`). `Navigate.read(readout)` and `Navigate.number(readout)` read one off a single look, walking
  nothing: empty when that look does not show its screen. A screen of one of several (hero N) names which by a readout
  marked `.subject()`, read by `Navigate.subject(screen)`: the mark is on the readout, not a `Setup.subject(…)` link,
  because the host reads `HERO.name` anywhere as the input reference `BotConstants.input` makes, never as a readout.
  A result is a screen that follows another on its own: Victory and Defeat are each `.after(BATTLE)`, and
  `Navigate.next(BATTLE)` waits up to Battle's `loads` for whichever shows (`ScreenGraph.results`, one step on;
  `followers` goes on past them), popups closed meanwhile. A body that waited on pictures by hand,

  ```java
  if (!Navigate.press(Screens.HOME.battle).done()) return Activities.Battle.LOST;
  return Navigate.next(Screens.BATTLE).filter(Screens.VICTORY::equals).isPresent()
          ? Activities.Battle.WON : Activities.Battle.LOST;
  ```

  names the map's screens only. 🗺's cut form saves a zone or digits *read on this screen* as a readout of the cut's
  name (a number's box becomes a zone of that name too); read_map lists them.
- **`Strip`** (a field of the screen): some of a screen's buttons in one list that scrolls or column whose slots
  shift, named by their pictures and its `Scroll` — never by a rectangle (the user's call, 2026-10-16, after Home's
  left column over a moving fight). A member is looked for anywhere in the band its members were cut in
  (`Reach.band`: their places, grown by the largest).
- **`Way`**: a parent and the input that opens the screen. Its input is a bare copy; `ScreenGraph.via` reads the
  parent's current input that `sameAs` it, so a look, a lock or a `when` added later counts (2026-10-15).
- **Conditions** (`Input.when`, `Way.when`, `ScreenVariant.when`) are method references only
  (`Firestone::eventOn`); `ScreenTypes.isMethod` refuses anything else before it reaches the Java.
- **`Navigate.to / isOn / where / press / state`** (palette, Interaction) are the bot's calls. `press(Screens.X.field[,
  Pick])` walks to the input's screen, scrolls its strip, clicks the copy, closes popups and waits where the map says
  it leads (`ScreenWalk.press`); its `Pressed.result()` is DONE, DISABLED, ABSENT, NOT_NOW or LEFT, its `landed()` the
  screen after. A press that leaves the game on its screen though the map says it leads away is pressed once more
  (lag); a second stay is LEFT. `Pick.untilNone()` presses until the button is gone or disabled (at most 50). `state(button)` is how
  it looks now, nothing walked or scrolled.
- **The start screen and ways on their own** (the user's calls of 2026-10-18): `Setup.start()` marks the screen the
  game opens on, its loading screen, with no way in. `Setup.after(X)` (`Way.onItsOwn`) is a way with no input: the
  screen shows by itself once X is done — Home after Loading, Battle after Entering battle; transitions are screens
  chained so, not only the start screen. A walk presses nothing on it and waits for the next marker up to X's
  `loads(Duration)` (60 s by default), then ends STUCK, "stuck loading on X".
- **A panel** is a screen shown inside another: `Setup.panelOf(HOME)` on a top-level class (`HomeUpgrade`,
  `Screens.HOME_UPGRADE`), its way in an input of that screen. It walks as any child screen and is found over Home
  while Home's markers show. Not a class nested in Home's: Studio's open set of classes reads only classes nested
  directly in `Screens` (the user's pick, 2026-10-18).
- **A tab** (2026-10-22) is a screen shown inside the one its tab button is on, told by that button in its
  `State.SELECTED` look: `Setup.named("Gear").from(HERO.gearTab).tab()`, the button carrying `Look.of(GEAR_TAB_ON,
  State.SELECTED)`. It needs no marker of its own (`ScreenGraph.markers` adds the look, through the reference Studio
  reads the way in as). The screen it is in, and its other tabs, are left from it as from that screen: its edges are
  copied onto each tab (`ScreenGraph.edges`), so a walk presses a sibling tab directly, and its way back is that
  screen's. A walk to the screen it is in arrives once any of its tabs shows (`ScreenGraph.tabOf`). An input of the
  screen is found on that screen's class when pressed from a tab (`ScreenGraph.holder`: its strip, its tree). The
  ⋯ menu's *A tab of …* and edit_map's `set_kind tab` mark it (`ScreenEdits.setTab`).
- **Edge costs** (2026-10-22): `Setup.takes(Duration)` is how long a screen takes to show once its way in is done,
  written by try_input and ▶ Try from the time the screen took to settle (`ActTools.Entered.took`, rounded to
  100 ms) on a screen opened or reached with no time yet. `ScreenGraph.pathTo` is a Dijkstra: an edge costs the
  time its screen takes (`TAKES_MS`, 1 s, when not measured) plus `STEP_COST_MS` (300 ms), a way on its own the
  time the screen it leaves loads in; ties keep the path a search by fewest edges finds first, so a map with no time
  walks as before.
- **A look's shot** (2026-10-22): a frame showing one of a screen's looks (`ScreenWalk.lookOn`) is that look's shot,
  and leaves the screen's own; ❄ Freeze's retake, a cut marker and a walk's retake all save so. The panel's
  *Look* picker shows the screen in one look: its shot (`ScreenShots.shown`, the screen's own while the look has
  none) on the panel, the board and its card, and only the rows every look shows plus that look's own
  (`ScreenCards.rows(graph, constant, look)`), so a strip draws only them too.
- **The shot is named in Java** (the user's call, 2026-10-22): `Setup.shot("screens/HOME.png")` and
  `ScreenVariant.shot(…)`, a path in the bot's resources that a run never reads. A shot found by the screen's
  constant was lost by any rename that did not move the file too. The first save (`ScreenShots.save`) picks a path
  no screen names (`ScreenShots.fresh`: `screens/<CONSTANT>.png`, `screens/<CONSTANT>@<look>.png`, then `_2`…),
  writes the file and then the path into the setup, one history step; a later save writes over the file the Java
  names. A screen whose Java names no shot has none, whatever files the folder holds: a map made before shows its
  markers until a shot is taken again. A delete removes the files its Java names.
- **A rename renames the constant** (2026-10-22): `edit_map rename` and the card's ⋯ *Rename* give the screen its
  name and the constant and class that name makes (`ScreenBook.constantFor(graph, name, own)`), every use renamed
  by binding (`PluginValues.renameGroup`), one history step; a name that makes the same constant keeps it. Studio's
  own rename of a member of any plugin's open set, from the canvas or the assistant's `rename`, is planned the same
  way past the file's lock (Studio's `ManagedRenames`), so `Screens.MARKET` renamed anywhere renames its class too.
  The shot stays where its Java names it.

## Trees (`api/screens/Tree`, the user's calls of 2026-10-17)

- **`Tree`** is an input: linked nodes, each a `Button` field of the one screen it is on, that scroll in two
  dimensions (a research tree). It is generic: another game's tree may have no levels, locks or scroll. It declares
  only how it scrolls (`dragged()`, `wheel(n)`, or neither); nothing measured is kept on it. Zoom is out of scope.
- **`Node`**: its picture, `after` (the nodes whose line leads to it, as the game draws it; never moves a walk) and
  `beside` offsets. **No screen coordinates**: `Beside(other, across, down)` puts the node's centre at `other`'s
  centre plus `across` × other's width and `down` × other's height. `Tree.path` is the fewest offsets from a node in
  view to another, each read either way; `unplaced()` the nodes no chain joins to the first.
- **A look's `State`** gains LOCKED, DONE and BUSY (`State.blocks()`); a press on such a look is `Pressed.blockedBy`.
- **The walk** (`TreeWalk`, from `Reach.perform`): anchors are the nodes seen once on the frame; it moves the view
  toward the target's predicted place (drag or wheel), carries the anchors across by how far the view moved
  (`Reach.shift`, `FrameShift`; else the drag asked for), and clicks the target once it shows.
- **Discovery** cuts the nodes, `edit_map group kind tree` (`by` drag, wheel or none) and `link`, then `try_input`
  with `scroll` over a node (`TreeMeasure`): every linked pair in view is measured (rounded to 0.05), the view moved
  once, and pairs the move split measured across it; offsets merge into the tree as it is when written. `read_map`
  lists the nodes indented by their lines and names the unplaced ones as a problem.
- **🗺**: a tree's row says how it scrolls, its nodes and how many are not placed; its 🌳 draws it (`TreeView`) in the
  picture window, each node at the sum of offsets from the first, scaled to fit, its lines drawn, the unplaced ones in
  a row below. A node clicked is picked as a boxed input of the shot is. The tree's inspector draws it too, its nodes
  dragged there to place them (see *By hand*).

## The graph and the walk (`internal/screens`)

- **`ScreenGraph`** is the one pure model every consumer reads: edges, `pathTo`, `presence` (a screen only
  behind a `sometimes` input shows at times, one behind a `locked` input is locked: read off the edges, never
  flagged), shape, the pictures each screen uses, and what each screen owes (`owed`; an `owes` entry
  "marker — why" stands for the marker).
- **Discovery's order** is breadth first (the user's call, 2026-10-16, in place of 2026-10-14's finish-each-opened-
  screen-first): cut every input of a screen an input opens at once, then try on the screen it came from.
  `nextToExplore` is the shallowest unfinished screen, the ones behind an information input (`isInfo`: kind INFO,
  or help, rules, a leaderboard by name) or a way in that comes and goes last; `untried` lists information inputs
  last. Popups are never gone to.
- **`ScreenWalk`** finds where the game is (`whereOn`: a screen opened from another, by any way in, hides it; of two
  shown that neither hides, the one told by more markers wins; the screens one step from where the game was first,
  every screen only when none of them shows; markers looked for near their cut place, `Reach.findEachNear`, the
  place and `MARKER_MARGIN` both scaled from the cut frame; when no screen shows near, the screens one step away
  over the whole frame, a window of another shape having moved the UI pinned to its edges — never every screen,
  which took 11 s a poll) and
  walks one edge at a time: do the input, wait for the next marker up to twice the time the screen
  `Setup.takes` (at least `LEAST_STEP_MS`, 2 s; `STEP_MS`, 10 s, for a screen not measured), close a popup that
  shows, plan again from where a stray step landed. A way back is done once the left screen's marker is gone,
  wherever it lands. Ends: ARRIVED, NOT_NOW, LOCKED, MISSING (the map names a picture the game no longer shows:
  Upkeep), STUCK, STOPPED.
- **Popups are screens of the map** (2026-10-16; no run method). `closePopups` closes each one a frame shows by its
  `back` (or Escape), waits for its markers to go, presses Escape once more if they stay, and throws `Stayed`
  after that. `PopupGuard.check` calls it before every vision step; with no popup in the map it takes no frame.
  `uncover` clears an overlay the map lacks when a frame shows no screen of it: the map's close buttons (each
  way back that clicks a picture), then Escape when asked. A `Navigate` walk (`uncovering()`) does that once,
  after a second look, and says so; the assistant's walks never do. A timed-out wait clicks only, through
  `PopupGuard.uncover`, which warns "an unmapped popup covered …: map it in Upkeep".
- **Activities run on the map** (2026-10-16): a body opens with `Navigate.to(Screens.X)`, which on arrival sets
  `ScreenMaps.reached`. `FlowWalker` clears it as each activity starts; a `BotStuckException` from the body walks back
  there (`Navigate.to`) and runs the body once more, before the run's home. `InstalledRun.home()` is the run's home
  method, else a walk to `ScreenGraph.home()`: the `root()` (the start screen, else the first screen reached from
  none, popups aside), or past a start screen the one its loading ends on. Nothing leads back to a start screen.
- **An activity names its screen** (2026-10-22): `Flow.activity(Tavern.class, Tavern::body).on(Screens.TAVERN)`
  (`Flow.Step.screen`, written by `FlowTypes` as a wither). A stuck body walks back there rather than to the screen
  it last reached (`FlowWalker.backTo`). `plugin/flow/ActivityScreens` reads which activity works where off the
  flows: 🗺 shows a `⚙ Tavern` pill on that card, and the input picker in `Tavern::body` (the slot's
  `enclosingMethodSource`) offers TAVERN first.
- **Record into <activity>** (2026-10-22): Live's ⏺ menu picks an activity of the flows; then a click or drag on a
  known input appends `Navigate.press(Screens.X.field);` (a hotkey's key too, a field the body types into
  `Navigate.type(field, "<asked>")`), and 🧭 Go here, once arrived, `Navigate.to(Screens.X);`. `BodyRecorder`
  names each call as a typed `Ref` and each argument as a `PluginValues.Argument.member` of `SCREEN_MAP`; the host
  writes the line before the body's closing `return` (`PluginValues.appendCall`, contract 0.11.0). *Record: A ·
  input → B* stays the map's: it writes a way in, not the body.
- **`Reach`** is what acts: `RunReach` for a run (the bot's capture, confidence and click spread),
  `ToolReach` for the assistant. `perform` is the one place an input is done, each through `press(Point, Gesture,
  Duration)`, `keys(Combo)`, a field's `type(text, clears, submits)` and a drag's `drag(from, dropOn(…), time)`
  (`press(Button, Rect, frame)`, false when the drop is missing); words are found by `findText(caption, frame)`,
  the zone placed as `source.region` places it and cut out, then `read` (OCR, a test's fake). A list member not in view is scrolled
  to over a member that shows (past them all, where the last one showed; none yet, the band's middle),
  its `Scroll.most` times each way. After each scroll the list is looked at until two looks agree (`Reach.settled`, up
  to `SCROLL_SETTLE_MAX_MS`; members still where they were are watched to the limit, as a game may glide late); a
  scroll after which every member that shows sits where it was (`Reach.still`, 4 px) met the end and turns back once.
  A walk plans with a list member that comes and goes, out of view, only when no other way is open.
- **Telemetry**: Navigate fires `on screen <CONSTANT>` (`ScreenStep`); the run bar and 🗺 Screens show its name.

## The assistant's tools (`plugin/assist`)

- **`ScreenBook`** reads and writes `Screens.java` through the host, on the FX thread; `plugin/pictures/ScreenMapRead`
  is the one safe read the editors, picture groups and the constant view share.
- **Discovery** (`MapTools`): `crop_pictures` cuts a screen's inputs, marker, back, looks (`variantOf`,
  `screenLook`, `when`), `sometimes` and `popup`; `place_pictures` puts pictures the bot already has on a screen;
  `try_input` clicks each input of its list and records what it did — opened a new screen (owing its marker), led to a mapped one
  (a way in or its way back, found with `whereOn`), changed it in place, or nothing — and stops at a popup; its
  `match` tries one copy of a repeated picture (the picture's one cut split into an untried input per copy first), and
  `which_screen` with `everywhere` numbers the copies;
  `not_a_screen` turns a screen back into a tried input on each of its ways in; `go_to_screen` and
  `which_screen` walk the map as a run does; `read_map` prints it (`plugin/map/MapReport`). The tool refuses while a
  screen owes its marker; a screen finished walks to `nextToExplore`.
- **Cuts** are a box, a round or a free polygon (`plugin/screen/CutShape`, `PictureCuts.cut/ellipse/polygon`): outside
  the shape is transparent and the matcher masks by alpha, so a small round icon over a moving background is cut
  whole without it. `crop_pictures` and its replace take `shape`/`points`; the Capture window's ✎ toggles the shape.
- **`edit_map`** (`MapEdits`) fixes a wrong record by hand, nothing clicked: rename, delete, `set_kind` (screen,
  popup, start, panel), notes, an input's result, kind or copies (`repeated`), reset or remove an input, use one as
  the way back, remove a way, a way's, input's or look's `when`, a new screen under another. Each action is the
  `ScreenEdits` method the window's menus call.
- **An input is named by its field** in `edit_map` and `try_input` (`picture: beerBuy`), as `read_map` lists it, or
  by its picture as before; a strip's field tries its first member by its notches. `read_map`, the 🗺 rows and the
  constant view say what the class form adds (`ScreenCards.Row.traits`): `repeated`, and a strip's scroll as measured
  (`wheel -5, step 120px, settles in 400 ms`, or `not measured`).
- `Protocol` and `SdkAssist.GUIDE` say when each runs (Discovery, Upkeep).

## The window (`plugin/map`)

- **`ScreenMap`**: one window per project (and a narrow copy in the overlay editor). Every 1.5 s it reads the map
  on a virtual thread only when `Screens.java` was written since (`ScreensFile`), or after 20 polls anyway, and at
  once after the user's own edit (a read the other overtook draws nothing). One read of a 45-screen map takes about
  a second. It redraws only when a `ScreenCards` value or a picture's file time changed; an empty read is trusted
  on the second in a row. The card a run is on is lit from telemetry.
- **Reads on the FX thread** go through `ScreenMapRead.orEmpty` (`plugin/pictures`): one read per FX event, shared
  by every card and editor drawn in it, dropped after it; a write calls `forget()`. A picture opens on its own cut
  (`TemplateUses.cut`), never on every picture constant's. A read after a step is skipped while the files beside
  `Screens.java` hash the same (`ScreenMapRead.stamp`, 2026-10-22): an edit to a body, or an undo back to the
  text last read, reads nothing; the hash takes milliseconds, a read of fifty screens up to a second.
- **The chain's layout** is the host's: a `setup()` chain that fits 120 columns stays on one line, a longer one is
  one call per line (Studio's `SourceFormatter.formatValue`, 2026-10-22), laid out only where a write changed it.
- **Search** (*Find a screen…*, `GraphCanvas.searchField`): each card's terms are `ScreenCards.terms` — its name,
  constant, `input <field>`, `picture <id>` and `notes …`; a term's label is shown, never searched. At most 12 hits
  are listed under the field, inside the map (a popup window would take the field's keys): names that begin with
  the query, then names that hold it, then other terms, each row the screen's marker, name and what matched.
  ↑ ↓ Enter or a click picks one: the map zooms to it at readable size (`zoomTo`, eased over 250 ms) and selects it;
  Esc clears the field and fits the map. Every hit is also lit on the map.
- **`ScreenCards`** decides, apart from JavaFX, each card (state, progress, `pills`), each link (`label()` names
  the default way back), each input's row and each list's `Strip`. **`ScreenCanvas`** draws them, laid out by ELK's
  layered algorithm (`ElkLayout`, the user's call of 2026-10-22; a dependency of the SDK, not the toolkit, so other
  plugins stay lean): ranks follow the entrances from the start screen, a way back goes in as a reversed edge,
  popups sit apart, and ELK's orthogonal routes are the links' paths. A card placed by hand keeps its `at` and only
  its own links are routed again (the toolkit's `Placement.pinned`); ELK failing to load falls back to the toolkit's
  `LayeredLayout`. It caches at most 400 images and places input boxes on any frame shape (`boxOn`, letterboxed). A card's shot loads in
  the background, sized by the file's header (`ScreenShots.size`); the panel's shot loads at once, since its boxes
  need its shape. A list's members are drawn as one scrolling strip over where the list sits, not boxed one by one.
- **`ScreenPanel`** is the picked screen: its shot with boxes, ▶ Try, 🧭 Go here, ✂ Cut, notes, and the hand
  edits of `ScreenHands`, all off while a try or a walk runs. Its boxes are where `MapAudit` (assist) finds each
  picture on the shot, made on a virtual thread and kept by file times and places; `read_map` lists the same audit:
  a shot without its marker, a picture off its place or not on the shot. **`ScreenDesk`** runs Try and Go here by the
  assistant's own code, so the window and the assistant cannot record an input two ways.
- **Gestures** are `GraphCanvas`'s (the toolkit): the wheel zooms about the pointer, a right drag pans, a left
  drag moves a card or the selected ones (Ctrl/Shift+click, a box drag). A move is written as each moved card's
  `at(new Point(x, y))` in `Screens.java` (`ScreenEdits.move`), so the map looks the same on the next open and on
  another computer; the first move pins every card shown, so the others stay put. **Tidy** (`ScreenEdits.tidy`)
  drops every `at`.
- **Links in focus** (the toolkit's `GraphCanvas.setFocusLinks`, 2026-10-22): at rest the entrances are drawn full
  and every other way at 15% without its label; the card under the pointer, else the selected ones, lights its ways
  in and out and fades the rest. Only opacity and order change, nothing is routed again. **⇄ All links** draws every
  way full, remembered per computer.
- **Undo** is Studio's own, through the contract's `StudioServices.history()`: each hand edit, move and Tidy is one
  `History.batch` step beside the user's code edits. ↶/↷ and Ctrl+Z/Ctrl+Y (`plugin/HistoryKeys`, shared with the
  Activity Flow) walk that history, and the assistant's MCP `undo`/`redo` walk it too. Nothing in the window
  keeps an undo of its own.
- **`PictureView`** is one window beside the map: an input's picture at whole-pixel zoom, never blurred, with
  "← <screen>" back to its screen's board, or a tree's drawing big. A screen's shot is shown big on the board.
- **`MapGuide`** is the toolbar's **?** legend, and holds the tooltips it repeats.
- Around it: the `Navigate` screen picker and the `Pick`/`Order` editors (`plugin/editors/ScreenEditors`), the
  input picker (`InputPicker`: a screen, then its fields of the slot's type on its shot), the `Screens.java` tab
  (`ScreenConstantView`: one line per screen — marker, name, how far Discovery got, a button to the map — whose ▸
  builds its full card on first open, kept open for the session), Manage Pictures' groups by screen (`plugin/pictures/ScreenGroups`).

## By hand (the user's calls of 2026-10-18)

🗺 maps a game without the assistant. Every hand edit is the `ScreenEdits` or `PictureTools` call the tools make, so
the window and the assistant write `Screens.java` one way. **`HandParityTest`** lists each `MapEdits.Edit` and each
map tool against the hand path that covers it, and fails on one with none.

- **The outline** (`ScreenOutline`, its model `Outline`, apart from JavaFX): the screens as a text tree, left of the
  `Graph | Screen` tabs (full window only). Under a screen are its inputs, under an input the screen it opens first;
  popups and screens not reached sit in groups below. The filter keeps the lines down to a hit; Owes and Untried
  narrow it. A click shows the screen on the Screen tab; a right-click opens the card's or the input's ⋯ menu.
  ⊟/⊞ fold or open every line; *Inputs that open a screen* folds away each tried input no link uses
  (`Outline.opening`); **Recent** lists the 10 screens whose shot was taken last (`Entry.recent`, by the shot
  file's time). A start screen heads the tree, a screen that follows another on its own sits beside it, and a panel
  says "panel of Home".
- **The window** hides the outline (☰) or the panel (▤), remembered per computer, and ⛶/F11 makes it full screen.
- **The board** (`ScreenBoard`, the Screen tab): the picked screen's shot as big as the tab, each input's cut boxed,
  its markers in their own colour; a box clicked picks its input. A card clicked on the graph brings this tab up at
  once (`ScreenMap`, a still primary click on `ScreenCanvas.CARD_VIEW`), so folding is in the card's right-click menu
  (`ScreenCanvas.fold`; the overlay's pane keeps the double-click). `ScreenPanel` beside it is the inspector, with
  no shot of its own: the picked input first, else the screen's markers, looks (each one's `when`), ways in, way
  back and notes. Each of those, and each part of the input inspector, is a `PanelParts` pane: folded by its
  title, its caption (`MapGuide.Part`) always under it, open at first when the input's kind needs it
  (`Part.opensFor`) and then as this computer last left it. ＋ Hotkey, ＋ Strip and ＋ Tree are in ⋯ Screen.
  **▶▶ Next untried** picks `ScreenGraph.nextToExplore`'s first untried input and shows it big. A ▶ Try that adds a
  screen owing its marker picks it, shows it big and starts its marker cut (`ScreenPanel.cutOpened`, once the map is
  read again after the try).
  **Leads to…** records a way into a screen already mapped (`ScreenEdits.addWay`, `MapEdits.AddWay` for the tools).
- **Live** (`LiveBoard`): the game read on a platform thread of its own, paced on the clock at up to 60 frames a
  second, straight into one of four reused images (`LiveImage`, each a `PixelBuffer`, swapped as a swap chain), and
  drawn on the FX pulse only when a new frame came (`LiveBoard.Pulse`, an `AnimationTimer`); a click, drag, wheel or
  key on it is sent through `ScreenDesk.hands`. A window is read through its grab (`PanelContext.live`, shared's
  `grabWindow`), kept open while the same window is found; a private session's attached window the same way,
  its display agent writing the frames into a file both processes map (`docs/display-pipeline.md` §4); an emulator,
  or a private display with no window attached, through `ScreenDesk.look`. Where the game is read is found once a second on a thread of its own (`PanelContext.found`: the
  window a class names, so a frame lists no windows). The line says "R fps read, S shown, M ms a read, D ms a draw",
  and "the capture is the limit" or "drawing is the limit" when one takes longer than a frame. The line under it
  names the
  screen the game shows (`which_screen`'s matcher, at most once a second); a click inside an input's box that took
  the game from one known screen to another with no way between them offers **Record** (`LiveWays`). **❄ Freeze**
  keeps the last lossless frame: retake a screen's shot, or a new screen under the one last recognised, opened by
  the input last clicked. A frame of no screen of the map offers New screen… first (its parent, or "none: the game
  opens on it", a start screen); once written, the frame is that screen's and cutting starts on its marker.
  Missed frames (`missed_frames`) open frozen. ▶ Launch starts the game when it is not up.
- **Cuts on the frozen frame** (`CutLayer`, `CutForm`, `assist/BoardCuts`): a box, an oval or a polygon drawn on the
  board, ▨ the part a run skips, then each cut said what it is — a marker (set, add or absent), a button, the way
  back, a look of a button or of the screen, a strip's button, a zone (`save_region`), digits (`save_digits`) or a
  new image for a picture — with crop_pictures' tags, sometimes, popup and "only while". The kinds are offered by
  what a cut is for (`CutForm.Intent`: tells the screen, acts, reads or fixes), and each cut's name is suggested
  (`CutNames`): the words OCR reads on it, else `<screen>_<kind>_<n>`, taken by no picture, zone or other cut, and
  replaced while the field still holds it. The pixels are the frame's,
  never grabbed again. ✂'s ▾ keeps the cut over the game window (`CaptureTemplates`) as a fallback. An object cut
  (the magic wand) is not on the board: crop_pictures has no shape for it.
  - A free shape closes on a click on its first corner, ✓ Close shape, a double-click or Enter; a drawn cut is
    picked by a click, moved by a drag, resized by its corners (a box or an oval) and taken off with Delete.
  - **Nothing fails silently**: `BoardCuts.save` returns what it said and the places it refused; each refusal shows
    on the board's line (`ScreenBoard.say`), a refused cut stays drawn, and the form or New screen reopens with the
    reason.
  - Every cut of one save marked a new strip makes one strip, in order (`BoardCuts.joined`), and likewise a new
    tree; *Buttons → one new strip / tree* marks them all.
- **The input inspector** (`InputInspector`), by the input's class:
  - **a button or a hotkey**: its result, information only, comes and goes, never, locked; a button's looks and
    whether they cycle, repeated and each way's copy (`ScreenEdits.setWayPick`); a hotkey's key. ▶ Try runs
    try_input's code (`ScreenDesk.Trying`): which copy, the name of the screen it opens, settle time, stay.
  - **a strip**: its members in order (↑ ↓ ✕, ＋ Member… of a button in no strip or tree), its scroll (the wheel and
    its notches, a drag, or none, which takes the strip off), and ▶ Scroll and measure, try_input's measure over a
    member in view. A dragged strip is kept but not measured: try_input measures the wheel only.
  - **a tree**: its nodes drawn by `TreeView`; a node dragged beside one it is linked to is placed there, its offset
    in that node's size and rounded to 0.05 (`TreeView.snapped`; the node it is placed from already wins, so its
    offset moves rather than a second one being written), a click picks its button. Each node's row: ↑ ↓ ✕,
    Unlocked by… (`ScreenEdits.link`), Place… by number (`ScreenEdits.placeNode`, `unplaceNode`). Its scroll is a
    drag, the wheel or none; ▶ Scroll and measure is `TreeMeasure` (`ScreenDesk.measureTree`), toward the tree's
    end or its start.
  - A button's Strip… or Tree… puts it in one of the screen's, or in a new one. The panel's ＋ Strip… and ＋ Tree…
    (`askNewStrip`, `askNewTree`) make one from a checklist of the screen's buttons in no strip or tree, with its
    scroll (`ScrollPick`, shared with Set scroll).
- **A screen's ⋯** also has Start screen, A panel of <parent>, and The next screen shows within…; Ways in has
  ＋ After a screen, on its own…. edit_map does the same by `set_kind` (`as` screen, popup, start, panel) and an
  `add_way` with no input, given `seconds`.
