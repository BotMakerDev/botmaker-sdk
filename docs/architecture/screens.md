# Screens

The game's structure lives in the bot, as Java: the `Screens` class beside `Sdk.java` holds one
`GameScreen` constant per screen. A run walks it to reach a screen; the assistant's Discovery tools and the
🗺 Screens window write it. What changed and why is in `CHANGELOG.md` (the Screens audit, 2026-10-15).

## The model (`api/screens`)

- **`GameScreen`**: a name, `markers` (pictures that all show while the game shows it) and `absent` (pictures none of
  which shows: what tells it from a screen showing the same markers; `internal/screens/Markers` is the one rule every
  reader tells a screen by, 2026-10-16), a `from` way (the tree), `alsoFrom`
  ways, a `back` input (null: a close button, then Escape), `inputs`, looks (`ScreenVariant`), `popup`, `notes`,
  `reached` (SURVEYED → BUILT → TRIED → TUNED, or LOCKED), `owes`, and `at(Point)`, the corner its card was
  moved to on 🗺 Screens (none: the layout places it). A screen names the one it is reached from,
  never the ones it leads to, so no constant names one declared after it. A popup is in no tree: a marker and a
  way out only.
- **`Input`**: a picture click, a key or a scroll, with `did` (IN_PLACE, NONE, LEAVES; null while untried),
  `kind` (CHORE, SHORTCUT, CHOICE, INFO), `never`, `sometimes`, `locked`, `when` and button looks (`variants`). A
  picture shown more than once is one input per copy, `nth` (from 1, in reading order: `Reach.readingOrder`), since
  copies may open different screens; `each` does every copy in turn (the user's call, 2026-10-16).
- **`ScreenList`** (`GameScreen.lists`): some of a screen's inputs in one list that scrolls or column whose slots
  shift, named by their pictures and its `notches` — never by a rectangle (the user's call, 2026-10-16, after Home's
  left column over a moving fight). A member is looked for anywhere in the band its members were cut in
  (`Reach.band`: their places, grown by the largest).
- **`Way`**: a parent and the input that opens the screen. Its input is a bare copy; `ScreenGraph.via` reads the
  parent's current input that `sameAs` it, so a look, a lock or a `when` added later counts (2026-10-15).
- **Conditions** (`Input.when`, `Way.when`, `ScreenVariant.when`) are method references only
  (`Firestone::eventOn`); `ScreenTypes.isMethod` refuses anything else before it reaches the Java.
- **`Navigate.to / isOn / where / press / state`** (palette, Interaction) are the bot's calls. `press(Screens.X.field[,
  Pick])` walks to the input's screen, scrolls its strip, clicks the copy, closes popups and waits where the map says
  it leads (`ScreenWalk.press`); its `Pressed.result()` is DONE, DISABLED, ABSENT, NOT_NOW or LEFT, its `landed()` the
  screen after. `Pick.untilNone()` presses until the button is gone or disabled (at most 50). `state(button)` is how
  it looks now, nothing walked or scrolled.

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
  every screen only when none of them shows; markers looked for near their cut place, `Reach.findEachNear`) and
  walks one edge at a time: do the input, wait up to `STEP_MS` (10 s) for the next marker, close a popup that
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
  method, else a walk to `ScreenGraph.root()` (the first screen reached from none, popups aside).
- **`Reach`** is what acts: `RunReach` for a run (the bot's capture, confidence and click spread),
  `ToolReach` for the assistant. `perform` is the one place an input is done. A list member not in view is scrolled
  to over a member that shows (past them all, where the last one showed; none yet, the band's middle),
  `ScreenList.SCROLLS` each way. After each scroll the list is looked at until two looks agree (`Reach.settled`, up
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
  `not_a_screen` turns a screen back into an IN_PLACE result on each of its ways in; `go_to_screen` and
  `which_screen` walk the map as a run does; `read_map` prints it (`plugin/map/MapReport`). The tool refuses while a
  screen owes its marker; a screen finished walks to `nextToExplore`.
- **Cuts** are a box, a round or a free polygon (`plugin/screen/CutShape`, `PictureCuts.cut/ellipse/polygon`): outside
  the shape is transparent and the matcher masks by alpha, so a small round icon over a moving background is cut
  whole without it. `crop_pictures` and its replace take `shape`/`points`; the Capture window's ✎ toggles the shape.
- **`edit_map`** (`MapEdits`) fixes a wrong record by hand, nothing clicked: rename, delete, popup, notes, an
  input's result, kind or copies (`repeated`), reset or remove an input, use one as the way back, remove a way, a
  way's, input's or look's `when`, a new screen under another. Each action is the `ScreenEdits` method the window's
  menus call.
- **An input is named by its field** in `edit_map` and `try_input` (`picture: beerBuy`), as `read_map` lists it, or
  by its picture as before; a strip's field tries its first member by its notches. `read_map`, the 🗺 rows and the
  constant view say what the class form adds (`ScreenCards.Row.traits`): `repeated`, and a strip's scroll as measured
  (`wheel -5, step 120px, settles in 400 ms`, or `not measured`).
- `Protocol` and `SdkAssist.GUIDE` say when each runs (Discovery, Upkeep).

## The window (`plugin/map`)

- **`ScreenMap`**: one window per project (and a narrow copy in the overlay editor). It reads the map every
  1.5 s on a virtual thread, and at once after the user's own edit (a read the other overtook draws nothing). It
  redraws only when a `ScreenCards` value or a picture's file time changed; an empty read is trusted on the second
  in a row. The card a run is on is lit from telemetry.
- **`ScreenCards`** decides, apart from JavaFX, each card (state, progress, `pills`), each link (`label()` names
  the default way back), each input's row and each list's `Strip`. **`ScreenCanvas`** draws them, folds the tree,
  caches at most 400 images and places input boxes on any frame shape (`boxOn`, letterboxed). A card's shot loads in
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
- **Undo** is Studio's own, through the contract's `StudioServices.history()`: each hand edit, move and Tidy is one
  `History.batch` step beside the user's code edits. ↶/↷ and Ctrl+Z/Ctrl+Y (`plugin/HistoryKeys`, shared with the
  Activity Flow) walk that history, and the assistant's MCP `undo`/`redo` walk it too. Nothing in the window
  keeps an undo of its own.
- **`PictureView`** is one window beside the map: a screen's shot big (its inputs boxed, the wheel zooming), or an
  input's picture at whole-pixel zoom, never blurred, with "← <screen>" back to the shot it was opened from. A box
  clicked on the big shot that opens a screen shows that screen's shot in its place, its ← back to the one before.
- **`MapGuide`** is the toolbar's **?** legend, and holds the tooltips it repeats.
- Around it: the `Navigate` screen picker and the `Pick`/`Order` editors (`plugin/editors/ScreenEditors`), the
  input picker (`InputPicker`: a screen, then its fields of the slot's type on its shot), the `Screens` constant preview
  (`ScreenConstantView`), Manage Pictures' groups by screen (`plugin/pictures/ScreenGroups`).
