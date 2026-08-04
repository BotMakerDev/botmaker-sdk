# ROADMAP

A running history of features and refactors for future Claude Code sessions. **Append here whenever
you add a feature or refactor** (this is required — see `CLAUDE.md` › Planning).

Format: newest first. Each dated entry has a **Done** list (what shipped) and, when relevant, updates
to **Deferred / next** (intentionally left for later, with enough context to pick up cold).

---

## 2026-08-04 — `Matches`: the group lambdas hand back the whole combination

**164 → 177 tests.** Added `api/vision/Matches.java`, `api/vision/MatchesTest.java`,
`api/vision/MatchesFindTest.java`. Changed: `api/vision/ImageFinder.java`, `api/vision/ImageClicker.java`,
`api/vision/VisionContext.java`.

The group lambda helpers were unusable for the job real bots need them for — "which of these templates are on
screen right now, and what do I do about *that combination*". `ifFindAny`/`whileFindAny` passed a single
`MatchResult` (the first hit, so a second template visible in the same frame was simply invisible to the
body), and `ifFindAll`/`whileFindAll` passed a bare `Runnable` on the recorded grounds that "every template is
present has no single meaningful `MatchResult`". Both shapes are gone; per this repo's *API stability* note
they were removed outright, with no compatibility overload.

### Done

- **`api/vision/Matches`** — immutable, built from one frame, keyed by `MatchResult.getTemplateId()`:
  `has` / `hasAll` / `hasAny` / `get` / `all` / `best` / `isEmpty` / `count`. Two contracts that bot code
  leans on without thinking, both tested: `get` returns `MatchResult.notFound()` rather than null, so
  `click(found.get(x))` is safe to write unguarded for a template that wasn't there; and lookups key on
  *template id*, not object identity, so a template reloaded from the same file still answers `has`. Two
  templates sharing an id can only hold one slot — the higher-confidence match wins it.
- **All four group lambda helpers now take `Consumer<Matches>`** (`ifFindAny`, `whileFindAny`, `ifFindAll`,
  `whileFindAll`). Single-template `ifFind`/`whileFind` keep `Consumer<MatchResult>` — one template has one
  answer. `untilFind*` keep their `Runnable`: they run *while nothing is found*, so there is genuinely nothing
  to hand over.
- **`ImageFinder.findAllTemplates(group, source, confidence)`** — one capture, one `Mat`, re-matched per
  template. It deliberately does not loop `findInternal` (that screenshots once per template) and cannot
  delegate to `findAnyInternal` (that short-circuits on the first hit — precisely the information loss
  `Matches` undoes). N templates therefore still cost one frame, and every answer in a `Matches` describes the
  same instant. `MatchesFindTest` asserts the capture count directly, since nothing else would catch a
  regression to the N-frame shape.
- **`ImageClicker.click(MatchResult)` / `click(MatchResult, CaptureSource)`** — the missing companion. Picking
  a match out of a `Matches` and acting on *that one* had no expression: every `click` overload re-located a
  template first, so the only way to click a chosen match was `Mouse.click(match.getCenter())`, which bypasses
  the `CaptureSource` click routing an emulator source depends on.
- **`VisionContext.getLastMatches()`** beside the existing slots, set by the four helpers.
  `setLastMatches` also seeds `lastMatch` with `matches.best()`, so Studio's palette-seeded
  `MatchResult match = VisionContext.getLastMatch()` keeps meaning something after a group check.

### Deferred / next

- **Studio still generates the old shapes.** `blocks/vision/LambdaCallBlock` declares
  `Variant("ifFindAll", true, false)` — `hasParam = false`, i.e. a `Runnable` body — which no longer compiles
  against this API, and it never renders the lambda parameter at all, so `found` is unreachable in the block
  editor. Both are the next phase's work, in the Studio repo.
- `findAllTemplates` is package-private: the lambda helpers are the intended surface. If a bot ever wants the
  combination without a loop, promote it as `ImageFinder.matches(group)` rather than growing a second path.

---

## 2026-08-04 — CI: the OCR tests error where there is no libtesseract

**164 tests, unchanged.** Changed: `api/vision/TextTest.java`, `.github/workflows/build.yml`. This repo's
GitHub Actions run was red with 3 errors, all of them `TextTest` — the SDK's only tests that recognize text,
routing straight into shared's `OcrEngine`.

### Done

- **`TextTest` skips instead of erroring without a system `libtesseract`,** via a `@BeforeEach` assumption
  probing `Class.forName("net.sourceforge.tess4j.TessAPI")`. The probe catches `Throwable`: the binding loads
  lazily and fails as an `UnsatisfiedLinkError` (then `NoClassDefFoundError` on every later attempt), which
  surefire counts as an *error* rather than a failure, and `OcrEngine` intentionally does not catch it so a
  genuine load failure can't masquerade as "no text". Same guard shape as shared's `OcrEngineTest`.
- **CI installs the natives** (`libtesseract-dev libleptonica-dev`) so that guard never fires there — a
  skipped test is not a passing one. Windows needs nothing: Tess4J bundles its DLLs.

---

## 2026-08-02 — `Tolerance` + `MinPixels` → one `Precision`

**157 → 164 tests.** Deleted `api/vision/{Tolerance,MinPixels}.java`; added `api/vision/Precision.java`.
Changed: `api/vision/Pixel.java`, `api/vision/ColorMatch.java`, `api/vision/PixelTest.java`,
`ToleranceAndMinPixelsTest` → `PrecisionTest`.

(Landed in two steps the same day: `MinPixels` first became a paired `MinMatch(area, count)`, then that and
`Tolerance` collapsed into `Precision`. Only the end state is described here — `MinMatch` never left this
repo.)

### Done

- **`record Precision(double deltaE, int minArea, int minCount)`** is now the single strictness argument of
  every `Pixel` search. Anchors `EXACT`/`TIGHT`/`DEFAULT`/`LOOSE` (the old `Tolerance` constants, each
  carrying the default area floor of 4 and no count requirement), `of(deltaE)` / `of(deltaE, area, count)`,
  and withers `tolerance(d)` / `minArea(n)` / `minCount(n)` so you start from an anchor and move one knob.
- **`minCount` is new** — matching pixels in total, however they clump, gated on the raw mask by shared's
  `ColorMatcher` before clustering. An area floor over an unbounded search mostly says "not a speck", which
  is rarely the question anyone had; the count is what turns it into a real assertion.
- **Argument order is now `(what, where, how)`** — `find(RED, hud, Precision.TIGHT.minArea(400))`. The
  source comes before the precision throughout, including `waitFor`/`waitForGone` (timeout last).
- **Three operations read only part of it, by design and by test.** `matchesAt` tests one pixel and
  `coverage` never clusters, so both read only `deltaE`; `findInRange` takes a colour band, so it reads only
  the two quantity gates. Each says so in its javadoc, and `PixelTest` pins that a knob they cannot use has
  *exactly* no effect — the collapse is only safe while that holds.
- **Validation is asymmetric on purpose:** `deltaE ≥ 0` and non-NaN, `minArea ≥ 1` (a cluster of nothing
  cannot be honoured), `minCount ≥ 0` (the honest "no requirement", and what every anchor uses).

### Deferred / next

- Studio's `ToleranceArgPicker` + `MinPixelsArgPicker` still dispatch on the old type names and must become
  one `Precision` editor — which should show only the knobs the call it is attached to actually uses, turning
  the javadoc caveat above into something the UI enforces. Planned as Phase B alongside the colour sampler.

---

## 2026-08-02 — improvements Phase 9: the launch step moves into `Bot.start`

**156 → 157 tests.** Changed: `api/bot/Bot.java`, `api/bot/StartMode.java`, `api/launch/Target.java`,
`api/bot/BotTest.java`.

### Done

- **`Bot.start(Runnable body, Runnable goHome)` now supplies the start-up step itself** —
  `Target.startIfNotRunning()` on the cold start, `Target.restart()` on a recovery, switched on the
  `StartMode` the supervisor already hands out. That is the entire body of the `Startup.java` Studio used to
  generate into every game bot: the launch target was never in that file, it is read from
  `botmaker-project.properties` at runtime, so the file was a per-project copy of SDK behaviour with only its
  `package` line to make it the project's. It is no longer generated (see the Studio roadmap).
- **This retypes the existing 2-arg overload rather than adding one.** `start(body, recovery)` and
  `start(body, goHome)` have the same erasure, so they cannot coexist. The generic
  "run forever, recover with this" form is subsumed by the 3-arg `start(body, goHome, startGame)` — pass a
  no-op start-up step — and the shape that every generated bot actually calls is now the short one. Free to
  do because no published bot consumes the API yet; a shim would have been the wrong trade.
- **`launchConfiguredTarget` is private.** A bot wanting different start-up passes its own
  `Consumer<StartMode>`; the two `Target` calls it delegates to are public, so nothing is walled off.
- The new test asserts the observable half — with no target configured the launch is a documented no-op, so
  what would have been lost silently if the overload had simply dropped the step is that `goHome` still runs
  once, before the first body pass.

---

## 2026-08-01 — improvements Phase 8: `Tolerance` and `MinPixels`, the two `Pixel` knobs as types

**152 → 156 tests.** New: `api/vision/Tolerance.java`, `api/vision/MinPixels.java`,
`api/vision/ToleranceAndMinPixelsTest.java`.

### Done

- **`Pixel`'s `double tolerance` → `Tolerance`, `int minPixels` → `MinPixels`.** Both are records with the
  named constants that used to sit on `Pixel` as bare numbers (`Pixel.TIGHT` → `Tolerance.TIGHT`,
  `Pixel.DEFAULT_MIN_PIXELS` → `MinPixels.DEFAULT`) plus a validating `of(...)`. The old constants are gone
  from `Pixel` — the API is freely breakable and there is no shim.
- **Why types.** The numbers are unreadable on their own: ΔE has no obvious scale and is not a percentage
  (so `0.12` meant as "12%" silently becomes a near-exact match), and `minPixels` is an *area* that reads as
  a width (`20` asks for a 4×5 blob, not one 20 across). Both failures are silent — the bot simply never
  sees its colour. The constructors now reject a negative ΔE and a sub-1 area rather than degrading into
  "matches nothing" / "matches everything".
- **`MinPixels.equivalentSide()`** lives on the type, not in the Studio's editor, so the value and its
  preview cannot disagree about what the unit is.
- **The dispatch payoff.** Studio now selects the editors for these arguments by *type*. The alternative was
  a `(method, argIndex)` table in Studio duplicating `Pixel`'s overload list — tolerance is index 1 of
  `find`/`coverage`/`waitFor` but index 3 of `matchesAt` — which would have gone stale on the next overload.

---

## 2026-08-01 — improvements Phase 7: `ClickConfig` → `api.BotSettings`, seeded from the project

**143 → 152 tests.** New: `api/BotSettings.java`, `api/BotSettingsTest.java`. Removed:
`api/vision/ClickConfig.java`.

### Done

- **`api.vision.ClickConfig` → `api.BotSettings`.** It moves out of `api.vision` because it was never only
  about vision (it carried the input backend and the retry budget), and it is now shaped like
  `api.capture.Source`: one ambient value, seeded lazily from the project, overridable at runtime.
- **The public mutable fields became accessors, and that is the load-bearing part.** `DEFAULT_CONFIDENCE`,
  `RANDOMIZE_CLICKS`, `MAX_RETRY_ATTEMPTS` and friends were `public static` fields read directly by ~60 call
  sites in `ImageFinder`/`ImageClicker`/`ImageWaiter`/`Watchdog`. A bare field read cannot trigger a lazy
  load, so the project's values would have applied only if something happened to call a method first. They
  are now `confidence()`, `randomizeClicks()`, `maxRetryAttempts()`, … each going through `ensureLoaded()`.
  The `DEFAULT_*` names survive as genuine constants (the SDK's own defaults).
- **`useRealInput` no longer needs a generated call, and that is what makes dropping the file safe.** It
  swaps the process-wide Linux input backend, one-way, and must run *before the first click* — which is why
  generated projects put it at the top of `main`. `input.real` is now applied inside `ensureLoaded()`, so
  every click path performs the swap by reading a setting, ahead of the click that needs it. `loaded` is set
  *before* the swap, so anything the native controller touches that reads a setting back does not re-enter.
  This fails silently when wrong (the click is dropped and neither OS reports it), so it is tested directly:
  `readingAnySettingEscalatesInputWhenTheProjectAsksForIt` asserts a plain `confidence()` read escalates.
- **`DEFAULT_COMPARE_MARGIN` gained a real setter** (`setCompareMargin`), validated to 0–1. It used to be
  assigned as a bare field, which is why the generated file wrote `ClickConfig.DEFAULT_COMPARE_MARGIN = x;`
  rather than a call.
- **`resetToDefaults()` marks itself loaded** rather than clearing the flag: the point of a reset is to end
  up on the SDK defaults, so a later read must not re-seed the project's values over the top.

### Deferred / next

- `Debug`'s own project default (`debug`) is still read by `ProjectDefaults` rather than by `BotSettings`.
  Folding it in would make one class own every project-seeded runtime value, but `Debug` has its own
  lifecycle (it is toggled from Studio at run time over the telemetry wire) and is deliberately left alone.

---

## 2026-07-31 — refactor Phase 3: the test floor (SD5 partial, SD6)

Part of the repo-wide refactor scheduled in `../docs/refactor/02-execution-order.md`; this module's share is
units **SD5** and **SD6**, test-only. **118 → 143 tests**, no production code touched.

### Done

- **MISSING 1 — `MissingTemplateTest`, and B9 is reproduced.** `find()` and `findAny()` on a template whose
  PNG does not exist return normally, indistinguishable from "not on screen"; both red on this commit,
  `@Disabled` pending **SD3**. The two tests that already pass are as important: `getMat()` *does* produce the
  precise diagnostic (naming the file), so every fix is about propagating it rather than writing it. And a
  fourth test pins the distinction the fix must not lose — a template that **loads** and is genuinely absent
  stays an ordinary quiet miss, or SD3 trades a silent wrong answer for a bot that throws on most polls.
- **MISSING 2 — `MatchResultNullContractTest`.** All eight accessors, both directions (`!found ⇒ null` and
  `found ⇒ non-null`), plus the geometry that makes the guard worth passing and 500 samples proving the random
  click point lands inside the match. `miss()` gets its own case: it looks like an exception to the rule and
  is not — a real below-threshold score for telemetry, everything else still null. A ninth test asserts the
  **accessor list is complete** by reflection, so adding an accessor cannot silently shrink what the other
  tests claim to cover.
- **MISSING 5 — `SessionBackendLadderTest`.** `SessionBootstrapTest` covered the isolation ladder; the
  *backend* ladder had nothing. Four rungs pinned in order, plus the property that matters most: the bottom
  rung is **not a constant but a function of the launch kind**, so flattening the ladder into a default would
  look equivalent and send every game to Xephyr's software GL. Totality is pinned too — `auto` and typos fall
  through — because that exact case has already been a bug here once.
- **MISSING 6 — `ImageTemplateReloadTest`.** The reload guard overwrites a non-null-but-empty `Mat` without
  releasing it, leaking one native handle per reload; red on this commit, `@Disabled` pending **SD9**. Worth
  noting what writing it turned up: the only way to reach that state through the public API is to release the
  returned `Mat` directly, which the javadoc forbids. So either the branch is reachable and leaks, or it is
  dead code shaped like a reload path — and SD9 should decide which rather than patching it.

### Not written — SD5's remaining items

**MISSING 3** (`compare*` agreement across `ImageClicker` and `ImageFinder`), **MISSING 4** (`api.emulator`,
0.0% over 176 lines) and **MISSING 7** (`Bots`' optional-bridge `LinkageError` degradation) are **not in this
commit**. They are the three that need a fixture this module does not have yet — a matcher harness, a fake ADB
device, and a classloader that can fail one class on demand — and writing any of them badly would produce the
green-but-vacuous test Phase 2 deleted a class for. Scheduled to land with the rest of Phase 3.

---

## 2026-07-31 — refactor Phase 2: the harnesses leave the published jar

Part of the repo-wide refactor scheduled in `../docs/refactor/02-execution-order.md`; this module's
share is units **SD1** and **SD2**.

### Done

- **Deleted six dead files, 728 lines — 8.5% of the module.** None was referenced by anything, in
  any of the four modules or any test:

  | File | Lines | What was in it |
  |---|---:|---|
  | `internal/capture/linux/LinuxControllerTest` | 348 | 96 prints, a `main()`, the module's only `ProcessBuilder`, 3 broad catches, a `printStackTrace` |
  | `internal/opencv/OpencvTest` | 149 | 9 prints, a `main()` |
  | `internal/capture/CaptureTest` + `internal/capture/ImageDisplay` | 180 | 18 prints, 3 `printStackTrace`, a Swing `JFrame` |
  | `internal/Main` | 25 | `ImageDisplay imageDisplay = null;` — declared, assigned null, never used — and a `placeholder()` that printed `"[Debug] Placeholder function executed."` |
  | `com/botmaker/sdk/Main` | 26 | an IDE hello-world scaffold **shipped in the published jar**: printed "Hello and welcome!", then clicked `src/main/resources/images/accept_button.png`, a path `.gitignore` guarantees is absent from a clean checkout |

  `CLAUDE.md` documented them as "manual `main`-method harnesses, not JUnit tests", so they were
  deliberate once. They were also the module's only untested code, they were `main()`s no one had
  run, and they were on the classpath of every bot the SDK ships to. **All five `printStackTrace`
  calls in this module went with them** — the SDK now has none.
- **Deleted `ImageFinderGroupTest`** — a class with zero `@Test` methods whose javadoc claimed the
  screen-dependent group paths were "exercised by the manual `com.botmaker.sdk.Main` harness". Both
  halves of that sentence were false, and a reader had no way to tell. The group paths are genuinely
  uncovered; the audit's MISSING list is where that is now recorded.
- **Coverage moved 32.0% → 38.4% line, 73.1% → 80.3% class, without a test being written** — 417
  missed lines stopped existing. (The audit predicted ≈65%; see the note below.)

### Note on the ≈65% estimate

`13-sdk.md` §14 projected "post-deletion the same tests read **≈65%**". That is arithmetically out of
reach for this deletion and the plan should not be read as having a missed target: 797 covered lines
against 2494 total is 32.0%, and 65% would need the total down near 1226 — a cut of ~1270 missed
lines from a deletion of 728 source lines, of which only 417 were executable. The real number is
**38.4%**. Recorded here rather than silently absorbed, per `01-audit-method.md`'s rule about items
the plan got wrong.

---

## 2026-07-30 — Phase 12: `Mouse` stops throwing away every click it makes in a session

The reported symptom was a bot that found its template, clicked, and got only the game's **hover** effect — on a
session it had correctly adopted. `Mouse.click`/`rightClick`/`middleClick` called
`NativeController.clickRestoringCursor` unconditionally, and `doubleClick`/`drag` did their own
`cursorPosition()` → `mouseMove(origin)`, so on a private display the pointer was warped off the target
microseconds after the release. A UI that polls pointer position per frame (rather than reading the event's
coordinate) then applied the click wherever the cursor had gone.

### Done

- Every gesture now goes through shared's `PointerPolicy`: `click` for the press paths, `restoreTo` for the
  end-of-gesture restore in `doubleClick`/`drag`. On `:0` nothing changes — the cursor is still handed back, which
  is the only reason synthesized input is tolerable there. The policy lives in shared because Studio's pilot had
  implemented it and the SDK had not; see `botmaker-shared/ROADMAP.md` for that half.
- **`Mouse.doubleClick` now holds each press** for `controller().pressHoldMs()` instead of being
  `down(); up(); down(); up();` — back-to-back that is under one frame at 60 fps, so a target sampling input once
  per frame could drop it entirely. Same reason `NativeController.click` and `ControllerPointer.click` hold.
- `MouseSessionPointerTest` (4) pins all of it by asserting the *absence* of the trailing warp in a session, its
  presence on `:0`, the same for a drag, and that the double-click's holds actually elapse.

---

## 2026-07-30 — Phase 11 (step 3): adopt the session we were handed

`SessionBootstrap.launchIsolated` gains a rung above every other: when the spawning process offers a live private
display (`botmaker.session.display` — Studio's background launcher passes it after "▶ Launch now"), the bot joins
it via shared's `AdoptedSession` and **does not launch the target again**. Building a second session instead means
handing the launch to the copy already running there (every store launcher is single-instance), so the game ends up
on a display nobody is watching.

The rung sits *below* the isolation gate, so `session.isolated=false` still means `:0` — an offer, not an
imposition. An offered display with no window on it is declined and closed: the target isn't up there, and an
adopted session can't launch it, so adopting would hand the bot a black frame with no way out.

---

## 2026-07-30 — Phase 11 (step 2): sweep before deciding whether to isolate

`SessionBootstrap.launchIsolated` now calls `NestedSession.reapOrphanSessions()` **before**
`LaunchIsolation.check`. The probes behind that verdict read a dead session's leftovers as a launcher that is up
— measured: a bot refused to isolate, naming a Heroic that had been closed for hours — and the only other sweep
lives inside a successful `NestedSession.start`, which a refusal never reaches. Sweeping late meant refusing a
launch on a dead session's account and running the bot on the user's real desktop instead.

---

## 2026-07-30 — Follow-on: `postLeftClickScreen` → `click(x, y, button)`

shared folded `NativeController.postLeftClickScreen` onto the new `click(x, y, button)` — the same call
hardcoded to button 1, under a name left over from the `XSendEvent` era (see shared's Phase 10 A3/A4 entry for
why the click paths were reworked). Nothing in the SDK's public API touches it; this is the mechanical
follow-on.

### Done

- `internal/capture/CaptureTest` and `internal/capture/linux/LinuxControllerTest` (the manual harnesses) call
  `click(x, y, 1)`.
- The `NativeController` test doubles drop their `postLeftClickScreen` overrides — `click` has a portable
  default (move → settle → press → hold → release), so `RecordingNativeController` keeps recording the same
  `move`/`button` calls it always did.
- `Mouse.click` is unchanged: it still takes `clickRestoringCursor`, which is the right policy on `:0`, and is
  now that default plus a warp back rather than a second copy of the sequence.

---

## 2026-07-29 — Isolated-launch fixes, Phase 7: decline to `:0` with a reason, before spawning anything

`SessionBootstrap.launchIsolated` used to find out that a target couldn't be confined only *after* bringing up a
display and burning the whole window budget on it — then fall back to `:0` with a guess as the explanation.

**Done**

- **The isolatability question is asked first.** `launchIsolated` consults shared's new
  `LaunchIsolation.check(spec)` before choosing a backend; a non-isolatable target logs the verdict's reason and
  returns `false` (the caller's normal `:0` launch) without spawning an X server. The reason is shared's wording,
  so a headless bot run and Studio's Launch button say the same thing.
- **The post-launch message reports what happened instead of guessing.** When a window never appears on `:N`,
  `LaunchIsolation.noWindowDiagnosis(spec)` distinguishes "it is running, but outside the private display" from
  "nothing is running under that target" by reading the process table — the two used to be offered as one "or".

---

## 2026-07-29 — Isolated-launch fixes, Phase 3: `api.Session`, isolation as a first-class SDK setting

Isolation was real but unspoken: it lived only in `botmaker-project.properties` plus two environment overrides,
so a bot's own source never said whether it ran on a private display. It is now a facade shaped exactly like
`api.Debug` — the setting a bot reads and writes the way it reads and writes every other setting.

**Done:**
- **`api.Session`**: `isEnabled()`/`enable()`/`disable()`/`set(boolean)` plus `useBackend("gamescope"|"xephyr"|
  "auto")`. Default on, so the *default costs no code* — the generated `BotSettings` will emit `Session.disable()`
  only when the project opts out (Phase 4), leaving a default project's source free of session boilerplate.
- **One precedence ladder, bot code at the top**: explicit `Session` call → `botmaker.session.isolated` sysprop →
  `BOTMAKER_SESSION_ISOLATED` → the project's `session.isolated` key → `true`; `useBackend` follows the same
  shape over `botmaker.session.backend` / `session.backend`, bottoming out in the *kind-driven* choice rather
  than a fixed backend. Bot code outranks the environment so a bot can force its own behaviour on a machine that
  disagrees. `Session.isEnabled()` reports the **resolved** answer, not merely what bot code asked for.
- **Deliberately not seeded from the project file** the way `Debug` seeds its flag: seeding would make "the
  project says true" indistinguishable from "the bot said true", and the override could then never be ranked
  above the environment. `Session` holds a nullable override; `SessionBootstrap` owns the ladder.
- **Bug fixed on the way**: `SessionBootstrap.backend` resolved every rung with
  `"gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR`, so an explicit `session.backend=auto` — or any typo —
  pinned a *game* to Xephyr's software GL, the exact crash the kind-driven choice exists to prevent. Every rung
  now parses through the new total `NestedSession.Backend.fromId` (shared owns it, since Studio needs the same
  parse), which is empty for `auto`/unknown and therefore falls through to the next rung.
- **The bot-side fallback now explains itself**: when the display comes up but nothing maps on `:N`, the trace
  appends `HostLauncherProbe.refusalMessage(kind)` if a host launcher is running — the same wording Studio uses,
  because a forwarded Heroic/Steam invocation mapping the game on `:0` is the overwhelmingly common cause.
- **Tests**: `SessionBootstrapTest` covers each rung — an explicit call beating the sysprop in both directions,
  `isEnabled()` reporting the resolved value, `useBackend` beating the property, `"auto"` un-pinning, and the
  `auto`/typo regression above. `Session.clearOverrides()` exists for the teardown (process-wide statics).

**Deferred / next:** Phase 4 — Studio's generated `BotSettings` line, the "Session" dialog section, and using
the refusal message on the Launch buttons.

---

## 2026-07-29 — Bot-owned-display plan, Phase I: explicit `Game.launch*` isolates too

`Target.start()` routed through `SessionBootstrap.launchIsolated`, but a hand-written
`Game.launchHeroic("Firestone")` went straight to `GameLauncher` on `:0`. Extend the same seam to every
explicit launch entry point.

**Done:**
- `Game.launch`/`launchSteam`/`launchEpic`/`launchHeroic`/`launchFaugus` build the call's `LaunchSpec` and
  try `SessionBootstrap.launchIsolated(spec)` first, falling back to the `GameLauncher.*` host launch only
  when isolation is off / declined. `launch(exe)` with no args → `EXE` (gamescope under isolation);
  `launch(exe, args…)` → a `CLI` command preserving the args (Xephyr, per the cli policy); a blank path → the
  host path so the usual validation error still fires. `launch()` returns `null` when routed into `:N` (no
  host process handle) — documented. The `…IfNotRunning` variants isolate transitively via these.
- `GameTest`: a fake active session proves a launch is routed into `:N` (returns null / no host spawn);
  opt-out falls back to a real host process; the reject-empty contracts still hold.

**Deferred / next:** Phase J surfaces the opt-out toggle in Studio and backgrounds its Launch buttons.
Carrying `exe:` arguments into an isolated display (today args force the CLI/Xephyr path) needs a shared
`LaunchCommands`/`NestedSession` change — left for when it matters.

## 2026-07-29 — Bot-owned-display plan, Phase H: isolation is on by default

Isolation was gated on a system property nothing set, so a bot only isolated from BotPilot. Drive it from the
project's `session.isolated` setting (default true) so a bot run *anywhere* with its project file on the
classpath isolates — no `DebuggingService`/`CodeExecutionService` change needed, since those already run the
bot JVM with the project root as cwd and `src/main/resources` on the classpath.

**Done:**
- `ProjectDefaults.sessionIsolated()` (delegates to shared, default true) and `sessionBackend()`.
- `SessionBootstrap.isolationRequested()` now defaults from `ProjectDefaults.sessionIsolated()`, with the
  `botmaker.session.isolated` system property / `BOTMAKER_SESSION_ISOLATED` env as an override in either
  direction. `backend(spec)` also honours the project's `session.backend` as an override alongside the
  system property.
- `SessionBootstrapTest`: default-on, system-property-overrides-to-off, opt-out disables bring-up.

**Deferred / next:** Phase I routes explicit `Game.launch*` through the same seam; Phase J surfaces the
opt-out toggle in Studio and backgrounds its Launch buttons.

## 2026-07-29 — Bot-owned-display plan, Phase G: auto-select the backend by launch kind

`SessionBootstrap` picked its backend from `botmaker.session.backend` alone (default Xephyr), so an isolated
game launch crashed on Xephyr's software GL. It now consumes shared's `SessionBackends` so the backend follows
the launch kind, and declines bring-up (loud install hint, graceful `:0`) when the required backend is absent.

**Done:**
- `backend()` / `options()` → `backend(LaunchSpec)` / `options(LaunchSpec)`: the `BACKEND_PROPERTY` override
  still wins, but with no override the backend is `SessionBackends.preferredBackend(spec)` — a game gets
  gamescope, a plain command gets Xephyr.
- `launchIsolated`: before bring-up, if the chosen backend isn't installed (`SessionBackends.isAvailable`),
  logs `SessionBackends.installHint` and returns `false` (runs on `:0`) — a bot never crashes on a Xephyr that
  can't run its game, and gamescope-missing is a clear message, not a SIGTRAP loop.
- `SessionBootstrapTest` updated: backend auto-selects from kind, override wins, options track the kind.

**Deferred / next:** Phase H makes isolation the default via a `session.isolated` project setting; Phase I
routes explicit `Game.launch*` through the same seam.

## 2026-07-28 — Bot-owned-display plan, Phase D: `Window.find` uses shared ranked matching

`Window.find` (and thus `NamedWindow`/`CaptureSource.window(title)`) took the **first** window whose title
contained the needle, so a bot pointed at "Firestone" could bind a wiki tab or launcher entry instead of the
game.

**Done:**
- `find` now delegates to shared `WindowMatch.best(controller().getAllWindows(), needle)` — an exact/prefix/
  whole-word hit beats an incidental substring, dynamic title suffixes are tolerated, and the shortest/largest
  matching window wins ties. Same matcher the Studio pilot uses, so runtime and editor resolve identically.
  No API change; `all()`/`foreground()` unchanged. SDK input/routing suites stay green.

## 2026-07-28 — Bot-owned-display plan, Phase B: bot-runtime routing onto a nested `:N` session

The session infrastructure (shared) and the Studio pilot producer (Phase A) were in place, but the **generated
bot's own runtime** still drove the global `:0` singleton — `Mouse`/`Keyboard` used `NativeControllerFactory.get()`
and `Source` resolved windows by title. Phase B routes the bot runtime onto the active session, so an isolated
bot clicks/types/reads its game on its private `:N` display exactly as a plain bot does on `:0`.

**Done**

- **Input choke points** — `Mouse.controller()` / `Keyboard.controller()` now return
  `ActiveSession.get().controller()` when a session is registered, else the `:0` singleton. With no session
  (the default) behaviour is byte-for-byte today's; `InputApiTest` is unchanged and still green.
- **`api.capture.SessionSource`** — a `CaptureSource` over the active `DesktopSession`: `capture()` is the
  session's frame of its owned window, `targetWindow()`/`origin()` come from `attached()`. This is where the
  "a nested session owns one window, so there's no capture *target* to pick" answer lands in the SDK — the
  `capture.source` title selector is bypassed while a session is active.
- **`Source.current()`** prefers a `SessionSource` whenever `ActiveSession` is set and the bot hasn't pinned a
  source; an explicit `Source.set(...)` still wins (and `set(null)` clears the pin, handing control back to the
  session). Non-isolated bots keep the project-default path unchanged.
- **The bot-runtime producer** — `internal.session.SessionBootstrap`, reached from `Target.start()`/
  `startIfNotRunning()`: gated **off by default** behind the `botmaker.session.isolated` system property (or
  `BOTMAKER_SESSION_ISOLATED` env). When on, it brings up a `NestedSession` (Xephyr default;
  `botmaker.session.backend=gamescope` opts into 3D) sized from the project's authored resolution, registers it
  via `ActiveSession`, and launches the target into it — idempotent, with graceful fallback to `:0` when
  bring-up fails or no window maps. A persisted project setting + Studio UX for isolated runs is the follow-up;
  the property gate keeps the seam testable and reversible without touching Studio or the project file format.
- Tests: `SessionRoutingTest` (input routes to the session controller when active, the global one when not;
  `Source` follows the session window; an explicit pin wins) and `SessionBootstrapTest` (the pure gate /
  backend / size selection). Full sdk suite green (106). Live bring-up stays manual (needs a real X server).

**Deferred / next** — Phase C (shared): gamescope-variant live test. Then real bot-on-`:N` end-to-end on a box
with a display; a persisted `session.isolated` project key + Studio toggle; `restart()` relaunch into `:N`.

---

## 2026-07-23 — `Keyboard`'s javadoc stops promising something it never did

**Done**

- The class javadoc said keys are "Backed per-OS by XTest (Linux)". Linux input has been a pluggable backend
  (xsendevent / uinput / xdotool / XTest) for a while, and which one is active decides whether a key reaches a
  game at all — so the one line a bot author reads was both stale and hiding the thing that matters.
- Documented the actual trade the targeted overloads make on Linux: under the cursor-safe default the key is
  delivered in the background but games reject it; once real input is on, it comes from a kernel virtual
  device a game cannot distinguish from a real keyboard — and that device carries no window, so targeting is
  implemented as *raise the window, then type*. Keys land, but the game is brought forward. See shared's
  ROADMAP entry of the same date for the backend work behind this.
- No signature changes: the fix is in `LinuxController` behind the existing `keyDown(window, code)` entry
  point, so `Keyboard`'s targeted overloads keep their meaning and simply start working.

---

## 2026-07-23 — `internal` shrinks to what is genuinely SDK-shaped

**Done**

- **Moved to shared:** `internal/opencv/*` (the matching engines, the raw records and the OpenCV loader) and
  the desktop-capture backends `internal/capture/{ScreenCapture,CaptureBackend,RobotCapture,SpectacleCapture}`,
  together with the `opencv` and `CaptureBackend` tests. Studio matches at edit time and captures at edit
  time; only the SDK had this code. See shared's ROADMAP for the full move and for the three-OpenCV-loaders
  finding.
- **`internal/config/ProjectDefaults` is now a thin typed accessor.** The file, its key names, the caching and
  the parsing belong to shared's `ProjectProperties` — Studio writes exactly those keys — leaving here only
  what shared cannot do: mapping the raw values onto `CaptureSource` and `Size`.
- **The SDK owns the mapping to its public types.** `api.vision` maps `RawMatch`/`RawColorMatch` onto
  `MatchResult`/`ColorMatch` as before, and the new `ImageTemplate.authoredSize()` is the single place the
  authored resolution converts from `api.Size` to the `java.awt.Dimension` shared's matcher takes. No SDK
  type crosses into shared.
- **What stayed, and why:** `internal/observe/IpcObserver` implements `api.observe.BotObserver` and consumes
  `MatchEvent`/`ClickEvent`/`Surface`/`Bots` — it *is* the SDK-side adapter onto shared's telemetry wire, so
  moving it would drag the SDK types along. The `main`-method dev harnesses (`internal/Main`, `CaptureTest`,
  `OpencvTest`, `capture/linux/LinuxControllerTest`) stay, as does the Swing `ImageDisplay` they are the only
  callers of.
- No compatibility shims, per the freely-breakable note. 95 tests pass.

---

## 2026-07-23 — `Game`/`LaunchTarget` become facades over `shared.launch`

**Done**

- The OS half of `api.launch.Game` (protocol URLs, CLI ladders, `kill`, `isRunning(String)`),
  `api.launch.RunningProbe` and `internal.launch.UriLauncher` **moved to `com.botmaker.shared.launch`** so
  Studio can launch and describe a target without depending on the SDK. See shared's ROADMAP for the full
  move and for the Heroic false-positive fix that rode along with it.
- What stayed, because it needs SDK types: `Game`'s `CaptureSource`-shaped methods (`isRunning(CaptureSource)`,
  `waitForLaunch`, `launchIfNotRunning`, `launchAndWait`, `launchEpicIfNotRunning`) are unchanged, and
  `LaunchTarget` keeps its sealed hierarchy plus the one running-detection layer shared cannot see — the
  ambient `Source.current()` window. Each variant is now just its `launchSpec()`; `start`/`isRunning`/
  `restart`/`spec`/`runningToken` are defaults delegating to `Launcher`/`LaunchSpec`.
- No compat shims (the API is freely breakable until a published bot consumes it): `RunningProbe` is gone from
  `api.launch` and `internal/launch` no longer exists. Bot-facing names and behaviour are unchanged.

---

## 2026-07-22 — `startIfNotRunning` stops relaunching a game that is already up

**Done**

- **The probe asked the wrong thing.** `LaunchTarget.startIfNotRunning()` decided "already running" purely from
  the ambient capture source's window. When a project captures the **desktop or a monitor**,
  `CaptureSource.hasWindowIdentity()` is false, so the answer was an unconditional "not running" and every
  Steam/Epic/Heroic/Faugus target relaunched on every run. A plain PID probe can't replace it either: the
  `steam://` / `xdg-open` / `faugus-launcher` process we spawn hands off and exits within a second, so the game
  was never our child.
- **New `LaunchTarget.isRunning()`, layered over observations — no cooldown, no "we launched it recently".**
  First hit wins: (1) the ambient source's window, when it has a window identity at all — the cheap answer;
  (2) a process *we* spawned for this `spec()` still being alive; (3) any live process whose **command line**
  mentions the target's `runningToken()`, via `ProcessHandle.allProcesses()`; (4) a window titled after that
  token, enumerated from the OS through `NativeController.getAllWindows()` rather than from the capture source.
  `startIfNotRunning()` is now `if (isRunning()) return; start();`, and `Target.isRunning()` exposes it to bots.
- **`runningToken()` is the launcher's launch identity, not our `spec()`.** Steam's is `AppId=<id>`, *not* the
  bare id — a 3-digit number would match unrelated command lines by accident, whereas Steam's own wrapper
  spells it `reaper SteamLaunch AppId=570 --`. Epic/Heroic use the `AppName`, Faugus the `gameid`, `exe:`/`cli:`
  the executable's file name. Matching the **wrapper** is the point: `reaper`, `proton`, `umu-run` and
  `legendary` all carry the token, and one of them is what a launcher-started game actually runs as.
- **Steam gets its own authority first.** Steam publishes the app id it is running — Windows
  `HKCU\Software\Valve\Steam\RunningAppID` (via shared's `WindowsRegistry`, normalising the `0x…` DWORD form),
  Linux `~/.steam/registry.vdf`. A mismatch **falls through** to the shared layers rather than answering "no":
  the key isn't written for every launch path (non-Steam shortcuts), so it can confirm but must not veto.
- **`EmulatorApp` answers over ADB** — instance up *and* `currentApp()` matching the package. Nothing on the
  host process table describes an app running *inside* an emulator, so the generic layers can't see it.
- `Game.launch` returns the spawned `Process` so layer 2 can record it; `exe:`/`cli:` are the only variants that
  do, since theirs is the only spawned process that *is* the target. `tryStart` deliberately stayed `boolean` —
  recording a launcher process that exits a second later would be a handle that always says "dead".
- The old `Exe`/`Cli` `startIfNotRunning` overrides (which shelled out to `pgrep -f`/`tasklist` via
  `Game.isRunning(String)`) are gone — layer 3 does the same job in-process. `Game.isRunning(String)` stays as a
  bot-facing block.
- **Accepted residual risk:** for the ~second between `start()` handing off and the wrapper appearing, every
  layer is legitimately false, so a bot calling `startIfNotRunning()` in a tight loop could double-launch.
  `Game.launchAndWait` (blocks on the window) is the existing answer, and the `[Target]` traces make it visible.
- Covered by `LaunchTargetProbeTest`: the desktop-source regression, Steam's token shape, the command-line scan
  against a real process, and a recorded handle that stops counting once it exits.

**Deferred / next**

- Nothing verifies the Steam registry/VDF read on a real machine yet — both paths are keyed off files this repo
  has no fixture for. Manual check: start a Steam game, confirm `isRunning()` traces "Steam reports it".

---

## 2026-07-22 — Clicks and keys that land in a game

**Done**

- **`Mouse.click` was the odd one out.** `rightClick`/`middleClick`/`doubleClick`/`drag` already drove real
  input, while `click` alone called `postLeftClickScreen` — a posted `WM_LBUTTONDOWN` on Windows and an
  `XSendEvent` on Linux, both of which games drop by design. All of them now go through shared's
  `NativeController.clickRestoringCursor`, so the pointer moves to the target and returns to where it was.
  `doubleClick` restores only after both presses (restoring between them would read as two single clicks),
  and `drag` restores only after the button is released.
- **`ClickConfig.useRealInput(boolean)`** — the switch a bot uses to say "my target is a game". Lives here
  with the other tuning knobs, in the same shape as the existing `enableDebugMode` delegate. This cannot be
  auto-detected: neither X nor Windows reports whether a synthetic event was accepted — the target simply
  drops it — so "the click didn't land" is unobservable and it has to be a setting. One-way, because on
  Linux it swaps the process-wide input backend; call it once before the first click. Studio's generated
  `Game bot` template now emits it at the top of `main`, so it is visible and editable as a block rather
  than hidden in a properties file.

**Deferred / next**

- An earlier iteration routed this through a `ProjectDefaults` key (`input.real`) read at start-up. Replaced
  because it made the SDK's behaviour depend on a file format Studio had to write correctly — an implicit
  contract — where `ClickConfig` is explicit API the bot author can see. If a Studio-side toggle is wanted
  later, it should rewrite that generated statement, not reintroduce the key.

---

## 2026-07-22 — Debug: one switch, every method, silent when off

**Done**

- **`Debug` is now a delegate over `botmaker-shared`'s new `com.botmaker.shared.Diag`** — same API
  (`isEnabled`/`enable`/`disable`/`set`/`log`/`error`), plus an `error(String, Throwable)` overload that
  replaces every `printStackTrace()`. The flag lives in `shared` because `shared` prints diagnostics of its
  own and can't depend on the SDK; one toggle now silences both modules.
- **Ungated prints routed through `Debug`** — `ImageClicker`, `ImageFinder`, `OpencvManager`,
  `RobotCapture`, `SpectacleCapture`, `UriLauncher`. These bypassed the flag entirely, which is the "it
  debugs even when it's off" symptom. `ImageWaiter`/`Pixel`/`Text` had the *right* gate but wrote through
  `System.out` inside an `if (Debug.isEnabled())` block; collapsed to a plain `Debug.log`/`Debug.error`.
- **Traces added to the facades that had none** — `Mouse` (every click/move/button/drag/scroll),
  `Keyboard` (press/release/tap/combo/type, naming the target window or "focused window"), `Wait`,
  `Emulator` + `Emulators`, `Source.set`/default resolution, `Window` (find/foreground/click/focus/move/
  resize), `NamedWindow` (an unresolvable title is now an explicit error line, not a silent `null` capture),
  `Watchdog` (enable/disable + the stuck throw). Previously debug output was vision- and launch-only, so an
  input problem produced nothing at all — the `Mouse`/`Keyboard` lines are what make the click fixes
  observable.
- **Not gated, deliberately:** `BotMaker.print` (that's the bot's own output) and the
  `\u0001BM-INPUT:` marker Studio parses.

---

## 2026-07-22 — Faugus Launcher as a launch target

**Done**

- **`Game.launchFaugus(String gameId)`** — Faugus registers no protocol handler, so it goes straight to the
  CLI: `faugus-launcher --game <id>`, then `flatpak run io.github.Faugus.faugus-launcher --game <id>`. The id
  is Faugus's `gameid` (matched exactly by its runner), not the title.
- **`LaunchTarget.Faugus(gameId)`** with spec `faugus:<gameId>`, wired into `parse` and the spec table.
  `startIfNotRunning` comes free from the window-based default. Faugus is how non-Steam Windows launchers
  (Battle.net, EA App, HoYoPlay) run under umu/Proton, which is what the maintainer actually automates.

---

## 2026-07-22 — "Already running" is decided on the window, not the process

**Done**

- **`LaunchTarget.startIfNotRunning()` now has a real shared default**: it asks the ambient
  `Source.current()` whether the target's window is open and skips the cold launch when it is. Previously
  `Steam`/`Epic`/`Heroic` inherited a default that just called `start()` (so they always relaunched), and
  `Exe`/`Cli` probed a *process name* — which never matches a game started through Steam's `reaper`, Heroic's
  `legendary` or a Proton/Wine wrapper, where the running process has nothing to do with the spec token. The
  window the bot captures is by definition the thing that has to be up, so it is the right probe, and it is
  uniform across every launcher and both OSes.
- **`CaptureSource.hasWindowIdentity()`** (default `false`, `true` on `NamedWindow`, delegated by
  `region(...)`) gates that check. `isPresent()` is hardcoded `true` for `desktop()`/`monitor()` because they
  always exist, so trusting it there would report "already running" forever; when it is false the target falls
  through to `start()` — or, for `Exe`/`Cli` (the only variants whose spec token *is* a real process name), to
  the existing process probe, which they keep as a fallback. `EmulatorApp`'s ADB path is untouched.
- **`Game.isRunning(String)` no longer matches the bot's own JVM**: `pgrep -f` matches whole command lines, so
  a name that appeared in the bot's own arguments made every game look running. It now passes `--` (so a name
  starting with `-` isn't read as a flag), reads the pids and discards `ProcessHandle.current().pid()`.
- `LaunchTargetRunningTest` covers the three branches (window present → skip, window absent → launch, source
  with no window identity → never skips) against a fake `NativeController`.

## 2026-07-22 — No-arg `Keyboard` targets the project source; Heroic + CLI launch targets

**Done**

- **No-arg `Keyboard.press/release/type` now target the ambient `Source.current()`** instead of whatever holds
  focus — the same "where" every no-source vision/mouse call uses, so a bot configured to a game window types
  into that window. They delegate to the `(CaptureSource, …)` overloads, which already fall back to the
  focused-window path when the source has no single window (`desktop()`/`monitor()`/emulator/unopened). `tap`/
  `combo` compose from `press`/`release`, so they follow automatically.
- **`Game.launchHeroic(appName)`** — launches Epic/GOG games through the Heroic Games Launcher (the practical
  path on Linux): opens `heroic://launch/<appName>` via `UriLauncher`, falling back to the Heroic CLI
  (`heroic --no-gui launch`, then the Flatpak `flatpak run com.heroicgameslauncher.hgl --no-gui launch`).
- **Two new `LaunchTarget` variants**: `Heroic(appName)` (`heroic:<appName>`) and `Cli(commandLine)`
  (`cli:<command>`, tokenized on whitespace and run via `Game.launch`; `startIfNotRunning`/`restart` keyed on
  the first token as the process name). Both added to `LaunchTarget.parse`; round-trip covered in
  `LaunchTargetTest`. Studio's picker (`HeroicLibraryScanner` + "Heroic game…"/"CLI command…") produces them.

## 2026-07-22 — `Keyboard` is targetable at a `CaptureSource` (symmetric with `Mouse`)

**Done**

- **`Keyboard.press/release/tap/combo/type(CaptureSource, …)`** overloads, mirroring
  `Mouse.click(CaptureSource, …)`: they deliver keys to *that* window instead of whatever holds focus, so a bot
  can type into a background window without focusing it. Pure additions — the no-arg methods are unchanged.
- **The seam is `CaptureSource.targetWindow()`** (default `null`): a resolved `Window` (and a `region(...)` of
  one) returns its native `GenericWindow`; `desktop()`/`monitor()`/an unopened `window(...)`/an emulator return
  `null`, so the targeted call transparently falls back to the focused-window path. `NamedWindow` resolves
  lazily like its capture path.
- Backed by shared's new `NativeController.keyDown/keyUp/typeText(GenericWindow, …)` (2026-07-22, shared
  ROADMAP). No Studio change: the palette discovers the new overloads by scanning the SDK jar at runtime, and
  a `CaptureSource`-typed first argument is already auto-seeded from the project default.
- Verified: `InputApiTest` gained targeted tap/combo/type routing + a no-window fallback case (12 tests green).

---

## 2026-07-20 — `Activity` is outcome-typed (`Activity<O extends Enum<O>>`)

**Done**

- **`Activity.run()` returns an outcome instead of `void`**, and `execute()` carries it back:
  `Activity<O extends Enum<O>>`, `abstract O run()`, `final O execute()`. An activity reports *what happened*
  (`BAG_FULL`, `NO_ORE`) and the flow drawn in Studio decides where each outcome goes — the activity never
  names another activity, so rewiring the canvas touches no Java. (API break — allowed; lands with the
  matching Studio flow-driver codegen.)
- **Generic rather than a shared marker interface** because `execute()` is `final`: a marker would force every
  driver dispatch through a cast, and a covariant override can't remove it on a final method. The type
  parameter makes `MINING.execute()` statically `Mining.Outcome`, so the generated `switch` over it is
  exhaustive and compiler-checked — the whole reason for using an enum here.
- The static name registry is now `Map<String, Activity<?>>`: enabling by name has nothing to do with the
  outcome type, so it deliberately forgets it. `disable`/`enable`/`setEnabled(String, …)` are unchanged.
- A stuck activity still produces **no** outcome — `BotStuckException` propagates to the supervisor rather
  than becoming a routable result, since recovery, not the flow, decides what happens next.

## 2026-07-20 — `Emulator.platform()` returns `PlatformId`

**Done**

- Followed shared's `String platformId` → `PlatformId` enum change. `Emulator.platform()` and
  `EmulatorRef.platform()` now return the enum (callers wanting the old string use `.id()`, or
  `.displayName()` for UI); `toString()` still prints the wire id.
- `Emulators.connect(host, port)` stamped a hand-rolled sixth id, `"custom"`, when an endpoint matched no
  discovered instance — it now uses `PlatformId.UNKNOWN`, which is exactly what that constant is for.

## 2026-07-20 — Start-vs-restart-aware startup (`StartMode`)

**Done**
- **New `api.bot.StartMode { COLD, RESTART }`** and the supervisor now hands the start-up step which one it is.
  `Bot.start`/`supervise`'s `startGame` param changed from `Runnable` to `Consumer<StartMode>`: cold start calls
  `startGame(COLD)` then `goHome`; recovery calls `goHome` then `startGame(RESTART)`. (API break — allowed;
  lands with the matching Studio `Startup` template.)
- **`Target.startIfNotRunning()`** (cold path) + `LaunchTarget.startIfNotRunning()` default. **`Exe`** overrides
  it to skip launch when its process is already running (`Game.isRunning(name)`), and overrides `restart()` to
  force-stop by process name (`Game.kill`) then relaunch — the "shut a frozen game down before restarting" case.
  Steam/Epic keep the idempotent-relaunch default; `EmulatorApp.restart()` already stop-then-starts the app.
- Wired `Game.kill` / `Game.isRunning(String)` / the skip-if-running primitives that existed but were unused.

---

## 2026-07-19 — Unified debug output switch (`api.Debug`)

**Done**
- **New `api.Debug` facade** — one global switch governing *all* SDK diagnostic printing. `isEnabled()` /
  `enable()` / `disable()` / `set(boolean)` plus `log(String)` / `error(String)` helpers that print only when on.
  **Default on**, seeded once from the project's `debug` key (see below); overridable at runtime.
- **Unified the two prior debug paths onto it.** The formerly *unconditional* lifecycle/launch traces
  (`[Bot]` / `[Game]` / `[Target]` / `[Activity]` in `Bot`/`Activity`/`Target`/`LaunchTarget`/`Game`) now route
  through `Debug.log`/`Debug.error`, and the vision traces (find/click/wait/pixel/text) now consult
  `Debug.isEnabled()` instead of the separate `ClickConfig.DEBUG_MODE`. **`ClickConfig.DEBUG_MODE` field
  removed**; `ClickConfig.enableDebugMode(boolean)` kept as a thin delegate to `Debug.set(...)`.
- **`ProjectDefaults.debug()`** parses the optional `debug` key (`true/1/yes/on` ↔ `false/0/no/off`) →
  `Boolean` (null when absent/unparseable so `Debug` keeps its default-on).

## 2026-07-19 — `Text.findFuzzy` (edit-distance OCR matching)

**Done**
- **`Text.findFuzzy(needle[, maxDistance], source[, opts])`** (+ current-source overload) — approximate,
  case-insensitive text search tolerant of OCR noise (`l↔1`, `O↔0`, a dropped letter). Slides the needle across
  each recognized line and accepts a window within `maxDistance` Levenshtein edits (`DEFAULT_FUZZY_DISTANCE = 2`);
  exact substring is a distance-0 fast path. Fills `VisionContext.getLastTextMatch()` like the other finders.
  Complements the existing `find` (substring) / `findExact` / `findMatching` (regex).

---

## 2026-07-19 — `Bot.start` is the single public entry point

**Done**
- **`Bot.start(body, recovery)` / `Bot.start(body, goHome, startGame)`** added as the only public way to run a
  bot; both delegate to the existing loop. **`Bot.supervise(...)` is now package-private** — it stays the
  internal machinery but is no longer part of the public palette (Studio surfaces only `public` facade methods
  as blocks, so `supervise` disappears from the menus). Generated game-bot `main` now calls
  `Bot.start(GameLoop::run, GoHome::run, Startup::run)`. `BotTest` stays in-package so it still drives
  `supervise` directly.

---

## 2026-07-19 — Launch target holder + emulator capture source (Phase 3)

**Done**
- **New `api.launch.Target`** — the ambient launch-side counterpart to `api.capture.Source`. `current()` lazily
  initialises from the project default (`ProjectDefaults.launchTarget()`), `set(String|LaunchTarget)` overrides,
  `start()`/`restart()` launch it. `null` target = no-op (a game-bot that hasn't picked a game yet just doesn't
  launch anything). The generated game-bot `Startup.run()` is now simply `Target.start()`.
- **New sealed `api.launch.LaunchTarget`** with variants `Steam`/`Epic`/`Exe`/`EmulatorApp`, each knowing how to
  `start()` (delegating to `Game`/`Emulators`) and round-trip to a `spec()` string. Parsed from the
  `launch.target` project key: `steam:<id>` | `epic:<name>` | `exe:<path>` | `emu-app:<pkg>@<instance>`
  (exe keeps its Windows drive colon; emu-app splits on the last `@`). `EmulatorApp.start()` ensures the named
  instance is running (launch + poll up to 120s), connects, starts the app, disconnects; `restart()` force-stops
  first. Pure parsing is unit-tested (`LaunchTargetTest`).
- **New `api.emulator.EmulatorSource implements CaptureSource`** — resolves an emulator **by instance name**,
  connecting lazily and dispatching a one-time launch if the instance is down (non-blocking: `capture()` returns
  null until it boots, then connects). This is the auto-launch-on-set capture source deferred from Phase 2.
- **`ProjectDefaults`** gained `launchTarget()` (raw `launch.target` spec) and `capture.source = emulator:<name>`
  support (→ `EmulatorSource`). **`Emulator.stopApp(pkg)`** added (`am force-stop`) for `EmulatorApp.restart()`.

**Deferred / next**
- The Studio game/emulator picker writing `launch.target` (+ the full emulator picker dialog) is Phase 4.

## 2026-07-19 — Emulator facade: launch/stop, all-instances, app queries (Phase 2)

**Done**
- **`Emulators.listAll()`** returns every *configured* instance (running or not) as new lightweight
  **`EmulatorRef`** DTOs (name/platform/endpoint, no ADB connection). `EmulatorRef` exposes `running()` (cheap
  TCP probe of the ADB port), `launch()`/`stop()` (host console tool via shared `EmulatorLauncher`), and
  `connect()` → live `Emulator`. This is the "show every instance I could pick without pre-launching" list the
  Studio picker (Phase 4) and the target holder (Phase 3) build on. `list()`/`first()`/`named()` stay the
  running-only connectors.
- **`Emulators.launch(name)` / `stop(name)`** start/stop a configured instance by name without connecting.
- **`Emulators.connect(host,port)` no longer stamps `"adb"`** — it recovers the real product identity when the
  endpoint matches a discovered instance, else labels it `"custom"` (with no launch/stop commands).
- **`Emulator` now carries its `EmulatorInstance`** (instead of loose name/platformId) and gained
  `installedApps()`, `isInstalled(pkg)`, `currentApp()`, `reboot()` (guest `adb reboot`), and `stop()` (host
  console tool, falling back to powering off the guest). Its ctor is `(AdbDevice, EmulatorInstance)`.

**Deferred / next**
- Auto-launch-on-set (resolve an emulator CaptureSource by name, launching + waiting if stopped) lands with
  the Phase 3 current-target holder.

## 2026-07-19 — Fix Epic Games launch opening the Documents folder

**Done**
- `UriLauncher.tryNativeOpener` (Windows) switched from `explorer.exe <uri>` back to
  `rundll32 url.dll,FileProtocolHandler <uri>`. `explorer.exe` treats a custom scheme carrying a query
  string — `com.epicgames.launcher://apps/<AppName>?action=launch&silent=true` — as a filesystem target,
  fails to resolve it, and silently opens a default Explorer window (the user's Documents) instead of the
  game. `rundll32`/ShellExecute takes the full URI as a single argument (no shell, so the `&` isn't split)
  and routes it to the registered protocol handler. Steam (`steam://rungameid/N`, no query string) worked
  either way and still does. The blank-browser bug the `explorer.exe` switch originally targeted was actually
  `Desktop.browse` (already gated to http/https/file only), so this is a safe revert of that one line.

## 2026-07-18 — Emulator capability hoisted to shared; SDK keeps only the `api.emulator` facade

**Done** (Phase 3 refactor — supersedes the Slice A layout below)
- **Moved the whole emulator capability to `botmaker-shared` (`com.botmaker.shared.emulator`):** the dadb
  transport `AdbDevice` **and** discovery (`Platforms`, `EmulatorPlatform`, `BlueStacks`/`LdPlayer`/scaffolds,
  `WindowsRegistry`, `EmulatorInstance`). Reason: discovery is needed by both the SDK (connect) and Studio (list
  instances in the picker), so it can't be SDK-only; moving the transport too lets a future Studio capture-picker
  preview the emulator screen. `dev.mobile:dadb` moved to shared's pom and was **removed from the SDK pom** — the
  SDK now gets it transitively. The SDK's `internal/emulator` package no longer exists.
- **SDK keeps only `api.emulator`** (`Emulator`/`Emulators`), repointed to import `com.botmaker.shared.emulator.*`.
  `Emulator` still `implements CaptureSource` (the SDK-only type that anchors it here). Added `Emulators.use()` /
  `use(String)` — connect-and-`Source.set` shorthands (the one-block "use emulator as source" flow Studio inserts).
- **The click-routing seam stays in the SDK** (it's an `api.capture`/`api.vision` change): `CaptureSource` has
  `default void click(Point)` (region delegates to parent) and `ImageClicker` routes clicks through `source.click`
  so an emulator overrides to `adb input tap`. Guarded by `ImageClickerRoutingTest` (stays in the SDK). The
  discovery parser tests moved to shared.

**Deferred / next**
- Live smoke test on real BlueStacks/LDPlayer (screencap decode, `input tap`, per-instance ADB-port discovery) —
  can't run without an emulator installed.

---

## 2026-07-18 — Android emulator, Slice A: dadb transport + `Emulator` capture source

> Superseded by the entry above: the `internal/emulator/*` classes named here (`AdbDevice`, `Platforms`,
> `BlueStacks`/`LdPlayer`, `WindowsRegistry`) now live in `com.botmaker.shared.emulator`. Kept for history.

**Done** (Phase 3, Slice A — SDK side; Studio picker is Slice B, deferred)
- **Cleared the old ADB stack** — deleted `internal/emulator/*` (ddmlib-based), `internal/inspector/RegistryInspector`,
  `internal/interaction/*`, and the `com.android.tools.ddms:ddmlib` dependency + its Google Maven repo. Rebuilt from scratch.
- **Transport = dadb** (`dev.mobile:dadb:1.2.9`, pure-JVM ADB — no `adb.exe`/server to ship). Pulls kotlin-stdlib
  transitively into generated bots — accepted cost of shipping no adb binary. dadb owns the RSA auth key
  (`~/.android/adbkey`), so there's no key lifecycle to manage here. Note: the Kotlin package is `dadb.*`, not the
  `dev.mobile` groupId.
- **`internal/emulator/AdbDevice`** — one live connection: `screencap()` (binary-safe via `exec:screencap -p`, decoded
  with ImageIO), `tap`/`swipe`/`key`/`text`/`startApp`/`getProp`/`shell`, `isConnected`.
- **Discovery** — `EmulatorPlatform` interface + `EmulatorInstance` record; `BlueStacksPlatform` (parses
  `bluestacks.conf` `bst.instance.<n>.status.adb_port`) and `LdPlayerPlatform` (parses `leidian<i>.config`; ADB port
  = 5555 + 2·i) discover for real; MEmu/MuMu/Gameloop scaffolded (return empty). `WindowsRegistry` (`reg query`)
  replaces the deleted BlueStacks-specific inspector. `Platforms.discoverAll()` aggregates. Windows-first; empty
  elsewhere; never throws. Parsers are pure + unit-tested.
- **`api.emulator.Emulator implements CaptureSource`** — the crux: `origin()` is `(0,0)` so a match's coords are
  already emulator pixels, and `capture()` = `screencap`, so `ImageFinder`/`Pixel`/`Text` work on it unchanged.
  Plus native verbs (`tap`/`swipe`/`back`/`home`/`text`/`key`/`startApp`), `use()` = `Source.set(this)`.
  `api.emulator.Emulators` = static discovery (`list`/`first`/`named`/`connect`).
- **Click-routing seam** — `CaptureSource.click(Point)` (default `Mouse.click`; `region(...)` delegates to parent);
  `ImageClicker` now dispatches every click through `source.click(...)` instead of `Mouse.click(...)`, so an emulator
  source taps via ADB. `ImageClickerRoutingTest` pins it (real OpenCV match → click recorded on the source, incl.
  region delegation). SDK suite green (90 tests).

**Deferred / next**
- **Slice B (Studio):** register `Emulators` facade in `palette/SdkApi`, add a `CONNECT_EMULATOR` block, a Studio-side
  `EmulatorInstanceScanner` (reads the same BlueStacks/LDPlayer configs), and an `EmulatorArgPicker` + `PickerContext`
  /`PickerRegistry` wiring — mirrors the Steam/Epic game picker.
- **Live smoke test** on real BlueStacks/LDPlayer (couldn't run here — no emulator installed): verify `screencap`
  decode, `input tap`, and per-instance ADB port discovery. BlueStacks needs ADB enabled in its settings.
- **Native-window capture backend** behind the same `CaptureSource` as a throughput optimization (a full-frame PNG
  per `screencap` is the ceiling). MEmu/MuMu/Gameloop discovery parsers.

## 2026-07-18 — Epic Games launch (`Game.launchEpic`)

**Done**
- **`Game.launchEpic(String appName)` + `launchEpicIfNotRunning(appName, source)`** — mirror the Steam pair.
  Opens `com.epicgames.launcher://apps/<appName>?action=launch&silent=true` through the scheme-agnostic
  `UriLauncher` (no `UriLauncher` change needed). `appName` is the Epic manifest `AppName` launch token
  (Studio's game picker fills it in), not the store title. Unlike Steam there's no CLI fallback, so a genuine
  failure (launcher not installed / no protocol handler) throws. Empty-input validation mirrors `launchSteam`;
  `GameTest.launchEpicRejectsEmptyAppId` pins it.

**Deferred / next**
- GOG / other stores follow the same shape when wanted (new `launch<Store>` + a `GameLibraryProvider` in Studio).

## 2026-07-18 — `Text` OCR facade (`api.vision.Text`)

**Done**
- **New `api.vision.Text` facade — on-screen text recognition, shaped exactly like `Pixel`.** Per-call
  `CaptureSource`, region via `source.region(...)`, results in **absolute** screen coords, parked in
  `VisionContext`, no-source overloads use `Source.current()`. The heavy lifting lives in
  `botmaker-shared`'s new `com.botmaker.shared.ocr.OcrEngine` (OpenCV preprocessing + Tesseract), so Studio
  can reuse OCR later without depending on the SDK. Surface:
  - `read(source[, opts])` — all recognized text as one string.
  - `find(needle, source)` (case-insensitive substring), `findExact`, `findMatching` (regex) — booleans that
    store the hit in `VisionContext.getLastTextMatch()`.
  - `findAll(needle, source)` / `readAll(source)` — counts, list in `VisionContext.getLastTextMatchList()`.
  - `waitFor` / `waitForGone` — poll loops identical to `Pixel`'s. Every call accepts an `OcrOptions` overload
    (languages, PSM, upscale, binarize, char whitelist). Default options read whole **lines** so multi-word
    phrases (`"Game Over"`) match by substring.
- **`api.vision.TextMatch`** — result type mirroring `ColorMatch`/`MatchResult` (package-private ctor,
  `notFound()` sentinel, `null` accessors when not found): `getText`, `getBounds` (absolute `Rect`),
  `getCenter`, `getConfidence`.
- **`api.vision.VisionContext`** — added a **separate** thread-local text slot (`lastTextMatch` /
  `lastTextMatchList` + getters/`ifLastTextMatch`/`clearLastTextMatch`), not shared with the template or
  colour slots, since bots interleave all three.
- **Bridge** maps shared `TextResult` (source-local box) → `TextMatch` (absolute via `source.origin()`),
  exactly like `Pixel.map(...)`. A genuine native-load failure surfaces as an `Error` (not caught), matching
  the rest of the vision layer.
- **Tests** — `TextTest` drives `Text` against a fixed-origin `CaptureSource` stub (rendered text) and
  asserts recognition, `VisionContext` storage, and absolute-coordinate mapping.

**Deferred / next**
- **Studio palette** — register `Text` in `palette/SdkApi.FACADE_CLASSES` and add palette blocks (a new
  facade needs registering; its methods are then auto-discovered). Not started.

---

## 2026-07-16 — `Game.kill` + name-based `Activity` control

**Done**
- **`Game.kill(name)` / `Game.isRunning(name)`** — cross-platform process control by executable name
  (Windows `taskkill`/`tasklist`, Linux/mac `pkill`/`pgrep`), best-effort and never throwing on "no such
  process". **Why:** the Firestone restart routine does `Process, Close, Firestone.exe` → relaunch, which the
  launch-only `Game` couldn't reproduce. Complements the existing window-based `isRunning(CaptureSource)`.
- **`Activity` self-registers by name; static `Activity.disable(name)`/`enable(name)`/`setEnabled(name,bool)`.**
  The constructor registers `this` in a static name→instance map, so any code can toggle an activity by name
  without a reference (one activity disabling another, or `GoHome`/`Startup` toggling one). Unknown name →
  stderr warning + no-op (never crashes a running bot). Instance `active()/setEnabled/enable/disable` unchanged.
  Package-private `clearRegistry()` for test isolation. **Why:** the Studio "disable this activity" self-call
  only worked inside an Activity and only self-targeted; the block now emits `Activity.disable("Name")` with a
  picker (see `../botmaker-studio/ROADMAP.md`).
- Tests: `GameTest` (kill/isRunning validation + no-throw on a bogus name); `ActivityTest` (static
  disable/enable by name toggles only that activity; unknown name warns + no-ops).

## 2026-07-16 — `Bot.stop()`: let the bot end cleanly

**Done**
- **New `Bot.stop()` ends the bot** — throws a **private nested** `BotStoppedException` (extends
  `RuntimeException`) which both `supervise` overloads catch *before* the `BotStuckException`/`RuntimeException`
  catches, log `[Bot] Stopped by request.` and **return** (not recover). The 3-arg cold-start try also honours
  it (a `stop()` during Startup/GoHome ends the bot before the loop). **Why:** `supervise`'s `while (true)` had
  no exit — once a bot disabled every activity, the loop spun forever with nothing to do ("the bot can't end").
  Exception (not a boolean body return) so `stop()` unwinds cleanly from arbitrarily deep in an activity's
  `run()`; nested-private so the only public surface is `Bot.stop()` (users never see/throw the exception),
  mirroring how `BotStuckException` is caught internally. `Activity.execute()` only catches `BotStuckException`,
  so the stop propagates through it untouched.
- Studio pairs this with an auto-`Bot.stop()` in the generated `GameLoop` (registry non-empty + no active
  activity) and a "Stop This Bot" palette block — see `../botmaker-studio/ROADMAP.md`.
- Tests: `BotTest` — `stop()` breaks the loop and `supervise` returns without recovering; a `stop()` during
  cold start ends the bot before the first body pass.

## 2026-07-16 — Cold-start launch sequence + runtime activity enable/disable + launch diagnostics

**Done**
- **`Bot.supervise(body, goHome, startGame)` now runs the start-up sequence once at launch** — `startGame()`
  then `goHome()` — *before* the first loop pass, reusing one shared `recovery` runnable. A cold-start failure
  routes through that same recovery instead of aborting the bot. **Why:** the 3-arg supervisor only ran
  GoHome/Startup *after* the loop threw, so "launch the game in Startup" never fired on a normal run (the
  reported bug: prints in Startup were silent while the activity `run()` printed fine). The 2-arg
  `supervise(body, recovery)` is unchanged.
- **`Activity` gained a runtime enable override** — nullable `enabledOverride` plus `setEnabled(boolean)`,
  `enable()`, `disable()`, and `active()` (the *effective* state: override if set, else the configured
  `isEnabled()`). The macro loop should consult `active()`, so a mid-run `disable()` actually stops the
  activity next pass (the reported "can't turn an activity off → GameLoop runs forever"). `isEnabled()`'s
  javadoc reworded to "configured default"; it stays the `Activities.<FLAG>` wiring.
- **`Game.launch`/`launchSteam` now log the exact command/URI they invoke** (`[Game] launch: …`,
  `[Game] launchSteam <id> → <uri>` + opener result) so a silent "nothing happened" launch becomes
  diagnosable in the Studio console. Behavior is otherwise identical.
- Tests: `BotTest` now asserts cold-start order (SHB / SHBHS / SHSB); new `ActivityTest` covers
  `active()`/`setEnabled`/`enable`/`disable`.
- **Release note:** generated bots pinned to a *released* SDK don't get cold-start or `active()` until this
  ships; the Studio GameLoop template emits `activity.active()`, which needs this SDK. Local
  `0.0.0-SNAPSHOT` dev runs pick it up immediately. Cut with `../release.sh` (shared unchanged → sdk → studio).

---

## 2026-07-15 — `Activity` names itself (no-arg constructor)

**Done**
- **`api/bot/Activity` gained `protected Activity()`**, defaulting `name` to `getClass().getSimpleName()`.
  Additive — `Activity(String)` stays for a name that shouldn't track the class name, and `name` stays final.
- **Why:** the only constructor was `Activity(String name)`, so every Studio-generated activity subclass had to
  carry a `public Mining() { super("Mining"); }` that restated the class name and asked the bot author for
  nothing. The Studio now generates a stub with `run()` and `isEnabled()` and no constructor at all; the
  generated `ActivityRegistry`'s `new Mining()` binds this inherited ctor.
- **Release note:** generated bots pinned to a *released* SDK can't use the constructor-less stub until this
  ships. Local `0.0.0-SNAPSHOT` dev runs pick it up immediately. Cut with `../release.sh` when ready.

---

## 2026-07-15 — Pixel colour detection (new `Pixel` facade)

**Done**
- **`api/vision/Pixel`** — the colour counterpart to `ImageFinder`, following the same conventions
  (`CaptureSource` per call, region-as-`source.region(...)`, absolute coords via `source.origin()`, results
  parked in `VisionContext`, no-source overloads use `Source.current()`). Surface: `colorAt`, `matchesAt`,
  `distance`, `find`, `findAll`, `findInRange`, `coverage`, `waitFor`, `waitForGone`.
- **Two precisions are separate knobs, deliberately.** `tolerance` is *colour* precision only — a **CIELAB
  ΔE76** distance (constants `EXACT`/`TIGHT`/`DEFAULT_TOLERANCE`/`LOOSE` = 0/5/12/25). *Location* precision is
  the searched region plus `minPixels`, the smallest connected blob that counts (kills stray anti-aliased
  pixels). Coupling the two into one knob is what makes Studio's old magic wand unusable; don't repeat it.
- **`api/vision/ColorMatch`** — mirrors `MatchResult` (package-private ctors, `notFound()` sentinel, null
  accessors when not found). Exposes `getCenter()` as the **centroid**, not the bbox centre — an L-shaped
  blob's bbox centre can lie outside the blob.
- **`internal/opencv/ColorMatcher`** + **`RawColorMatch`** (OpenCV-free, crosses internal/api like `RawMatch`).
  Pipeline: BGR → **float** Lab → per-pixel ΔE → threshold → `connectedComponentsWithStats` → `minPixels`
  filter → largest-first. The float conversion matters: converting an 8-bit image via `COLOR_BGR2Lab` gives L
  rescaled to 0..255 and a/b offset by 128, so the distances would not be ΔE.
- **`VisionContext`** gains `getLastColorMatch` / `getLastColorMatchList` / `lastColorMatchFound` /
  `clearLastColorMatch` / `ifLastColorMatch`. Colour results use their own thread-locals rather than sharing
  the template slots — bots interleave the two and would otherwise clobber each other.
- Studio: `SdkApi.FACADE_CLASSES` gains `Pixel` (a new *facade* needs registering; new *methods* on an
  existing facade do not — ClassGraph discovers those at runtime).

---

## 2026-07-14 — Bot lifecycle: supervisor + watchdog + Activity (new `api.bot`)

**Done**
- **New package `com.botmaker.sdk.api.bot`** with the runtime primitives a game bot needs:
  - **`BotStuckException`** (first custom exception in `api.*`) — unchecked; thrown by the watchdog, caught
    by the supervisor.
  - **`Watchdog`** — stuck detector. Piggybacks on existing `api.observe` match telemetry: while enabled it
    installs a `BotObserver` that counts consecutive identical match *signatures* (`templateId + coarse
    location`, or `"miss"`). The observer only counts (never throws — `ImageFinder.findInternal` swallows
    `Exception`); the throw is deterministic at `checkpoint()` once `repeats >= ClickConfig.MAX_RETRY_ATTEMPTS`.
    `progress()`/`reset()` clear the per-thread counter.
  - **`Bot.supervise(body, recovery)`** / `supervise(body, goHome, startGame)` — the outer restart loop:
    runs `body` forever, catches `BotStuckException`/`RuntimeException`, resets the watchdog, runs recovery.
  - **`Activity`** — abstract base a bot author subclasses per game task: `isEnabled()`/`run()` abstract,
    `before()`/`after()`/`onStuck()` overridable no-ops, `final execute()` orchestrates them. Studio
    generates one subclass per activity and a registry of instances.
- **`ClickConfig.MAX_RETRY_ATTEMPTS`** is now genuinely live (the watchdog's threshold); javadoc updated.
- Tests: `api/bot/WatchdogTest` (counter → checkpoint throws / resets) and `api/bot/BotTest` (supervisor
  recovery ordering) — standalone, no OpenCV/screen.
- Studio mirror: added `Bot`, `Watchdog` to `palette/SdkApi.FACADE_CLASSES` (`Activity` is a base class, not
  a static facade, so it's intentionally not listed).

## 2026-07-14 — Transparent-background templates: alpha-as-mask matching

**Done**
- **`ImageTemplate.getMat()` loads with `Imgcodecs.IMREAD_UNCHANGED`** so a transparent PNG keeps its alpha
  channel (4-channel BGRA); opaque PNGs still load as 3-channel BGR.
- **`OpencvManager` uses the alpha channel as a match mask.** New `extractAlphaMask` + `runMatch` helpers:
  when a template carries alpha, transparent pixels are ignored via `TM_CCORR_NORMED` with a mask (the
  reliably mask-supporting normed method); opaque templates keep `TM_CCOEFF_NORMED`. Threaded through
  `matchScaled`, `findMultipleMatches`, and `scoreAround`. This makes Studio's new "Capture object"
  (transparent-background) templates actually match regardless of the scene behind the object.
  Note: masked scores use CCORR_NORMED, so the 0.8 default threshold reads slightly differently for
  transparent templates. New `MaskedMatchTest` covers the path.

---

## 2026-07-11 — Game window-detection takes `CaptureSource`; add `ImageFinder.findAnyCompare`/`findAllCompare`

**Done**
- **`Game` window-detection now uses `CaptureSource`, not a bare window-title `String`** (so the Studio
  offers the visual capture-source picker instead of a free-text field, matching the vision API):
  `isRunning(CaptureSource)`, `waitForLaunch(CaptureSource, long)`,
  `launchIfNotRunning(String executablePath, CaptureSource, String... args)`,
  `launchSteamIfNotRunning(String appId, CaptureSource)`,
  `launchAndWait(String executablePath, CaptureSource, long timeout, String... args)`. The executable path
  and Steam appId are now the **first** parameter. Still window-based (no process detection yet).
- **Capture-layer presence**: new `CaptureSource.isPresent()` (default `true`; desktop/monitor are always
  present). New lazy `NamedWindow` source — `CaptureSource.window(title)` now returns it instead of eagerly
  resolving + falling back to `desktop()`. It re-resolves `Window.find(title)` on every use, so a
  window source survives the window not existing yet (before launch) and reports `isPresent()` correctly;
  `Game`'s detection delegates to it. Also makes ordinary vision matching more robust (re-binds a moved/
  reopened window per frame). `GameRunningTest` updated to drive `CaptureSource.window(...)`.
- **New compare finders** on `ImageFinder`: `findAnyCompare(good, bad[, source][, margin])` → `boolean`
  (first good template, in order, that beats every bad by the margin) and
  `findAllCompare(...)` → `int` (every winning good location; stored in `VisionContext` last-match-list).
  Mirror the existing `findCompare` overload set; new private `compareAny`/`compareAll` + shared
  `beatsAllBads` helper (which `compare` was refactored to reuse). `ImageClicker.clickCompare*` untouched.

## 2026-07-11 — Stamp the jar manifest with a build identifier

**Done**
- `pom.xml` now runs `maven-jar-plugin` with `addDefaultImplementationEntries` + a `Build-Time`
  manifest entry (`${maven.build.timestamp}`, ISO-8601). The pom `version` is cosmetic (`0.0.0-SNAPSHOT`
  locally; JitPack overrides it with the tag), so `Build-Time` is the signal that distinguishes local
  rebuilds that all reuse the same `botmaker-sdk-0.0.0-SNAPSHOT.jar` file name. Studio reads it at project
  open to report which SDK build the editor indexed — closes the "no way to tell which SDK a bot loaded" gap
  behind the stale-editor-cache bug fixed in the Studio ROADMAP (same date).

## 2026-07-11 — Fix `steam://` launch opening a blank browser page

**Done**
- `UriLauncher.open` no longer routes custom protocol schemes through `Desktop.browse`. On Windows
  `Desktop.browse("steam://…")` handed the URI to the default *browser* (blank page) instead of Steam. Now
  only `http`/`https`/`file` URLs use `Desktop.browse`; everything else goes straight to the OS protocol
  handler. The Windows native opener switched from `rundll32 url.dll,FileProtocolHandler` to `explorer.exe`
  (ShellExecute — the reliable way to invoke a registered protocol handler). `Game.launchSteam`'s CLI
  fallback is unchanged.

## 2026-07-10 — Compare API trim, click Any/All Compare, Game running-detection, per-template resolution

**Done**
- **Trimmed the Compare surface.** Removed every `clickCompare`/`findCompare` overload that took a solo
  `ImageTemplate` (`ImageClicker`, `ImageFinder`); the `ImageTemplateGroup` overloads remain the single
  Compare shape. This is an intentional breaking API change (no bot consumes the SDK yet).
- **New `ImageClicker.clickAnyCompare` / `clickAllCompare`** over `ImageTemplateGroup` (with
  `CaptureSource` + `margin` overloads). `clickAnyCompare` clicks the first good in group order that beats
  the bad set; `clickAllCompare` clicks every winning location and returns the count. Backed by new private
  `compareAnyInternal` / `compareAllInternal` (+ shared `beatsAll` neighbour-scoring helper).
- **`Game` running-detection & wait** (window-title based, via `NativeControllerFactory.get()
  .getAllWindows()`): `isRunning(title)`, `waitForLaunch(title, timeoutMs)` (~250ms poll),
  `launchIfNotRunning(...)`, `launchSteamIfNotRunning(...)`, `launchAndWait(...)`. Avoids relaunching an
  already-running game and lets a bot block until the game window appears. `launch`/`launchSteam` unchanged.
- **Per-template capture resolution.** Resolution-independent matching now prefers each template's own
  authored resolution over the project-wide `ProjectDefaults.defaultResolution()`. `ImageTemplate
  .captureResolution()` lazily reads `captureWidth`/`captureHeight` from a `<name>.json` sidecar (written by
  Studio; best-effort, null when absent). Threaded through `ResolutionScaler.primaryScale(live, authored)`
  and new authored-aware overloads of `OpencvManager.findBest`/`findBestMatch`/`findMultipleMatches`;
  templates without a sidecar keep the previous project-wide behaviour.

## 2026-07-10 — Verify `Game.launchSteam`

**Done**
- Reviewed `api/launch/Game.launchSteam(int)` → delegates to `launchSteam(String)`, which opens
  `steam://rungameid/<id>` via `UriLauncher` (Desktop.browse → `xdg-open`/`open`/`rundll32` fallback) and
  then falls back to the `steam -applaunch <id>` CLI. URI + fallback are correct; kept the numeric overload
  as a documented convenience (no signature change).
- Added `api/launch/GameTest` pinning the reject-empty-input contract for `launch` and `launchSteam`
  (String + numeric overload) — deliberately does not perform a real launch (no process/Steam spawned in CI).

## 2026-07-09 — API cleanup: global Source, concrete capture sources, Mouse ergonomics, resolution-independent matching

**Done**
- **Global ambient capture source.** New `api.capture.Source` holds a mutable global `current()`
  source; `Source.set(CaptureSource)` overrides it until changed. Every no-source overload in
  `ImageFinder`/`ImageClicker`/`ImageWaiter` now resolves through `Source.current()` instead of the
  inlined `CaptureSource.desktop()`. `current()` initialises lazily to the **project default source**
  (`internal.config.ProjectDefaults`, read from the classpath resource `/botmaker-project.properties`
  that Studio bakes into a bot), falling back to the whole `Desktop` when unset.
- **Concrete capture-source hierarchy.** Added `api.capture.Desktop` and `api.capture.Monitor`
  implementing `CaptureSource` (replacing the anonymous inner classes `Screen.asSource()` /
  `Screen.monitorSource(int)`, now removed). `CaptureSource.desktop()`/`monitor(int)` construct these;
  `Screen` remains the low-level static desktop-capture utility.
- **Mouse ergonomics.** Added `down(MouseButton, Point)` (move-then-press), `drag(Point, Point, long
  durationMs)` (timed interpolated drag), `move(int, int)` (merged with `move(Point)`; `moveTo(int,int)`
  removed), and `scrollUp(int)`/`scrollDown(int)` helpers with a clearer `scroll(int notches)` sign doc.
- **Vision facade aligned.** Removed `find(ImageTemplateGroup ...)` overloads (find is single-template);
  added `findAny(ImageTemplateGroup ...)` and `findAll(ImageTemplateGroup ...)`; removed
  `findBest(ImageTemplate ...)` (redundant — `find` already returns the best single-template match),
  keeping `findBest(ImageTemplateGroup ...)`. `ImageClicker.clickBest(ImageTemplate)` now delegates to
  `find`.
- **Resolution-independent template matching.** `internal.opencv.ResolutionScaler` derives a primary
  scale = `liveCaptureSize / projectDefaultResolution` (from `ProjectDefaults.defaultResolution()`;
  `1.0` when unset or implausible). `OpencvManager` resizes the template by that scale before matching:
  `findBest` (single scale for near-miss telemetry), `findBestMatch` (primary scale, then a small
  fallback pyramid ±10–20% **only on a threshold miss**, early-out on a hit), and `findMultipleMatches`
  (primary scale). Pre-existing pixel-exact behaviour is preserved when no default resolution is set.

**Deferred / next**
- **Studio side of resolution independence:** UI to set the project default capture resolution and to
  write `capture.width`/`capture.height` (+ `capture.source`) into `/botmaker-project.properties` of a
  generated bot. Until Studio writes it, the SDK falls back to native scale (no behaviour change).

## 2026-07-08 — Report best score on a template-match miss + SNAPSHOT shared pin

**Done**
- **Confidence is no longer always `0` on a miss.** `OpencvManager` split into `findBest(...)` (returns the top
  `TM_CCOEFF_NORMED` peak regardless of threshold; `null` only when the template can't fit) and
  `findBestMatch(...,threshold)` (unchanged gate, delegates to `findBest`). `ImageFinder.find` emits
  `MatchResult.miss(bestScore)` on a below-threshold miss so the telemetry Match carries the real near-miss
  confidence (e.g. `0.77`) — explaining "detected half the time" as a score straddling the threshold. The public
  find contract is unchanged (`isFound()` still `false`, click points still `null`); no telemetry wire change.
- **`botmaker.shared.version` committed value is now `0.0.0-SNAPSHOT`** (was a pinned tag). The real shared tag
  is resolved and injected at build time by `jitpack.yml` (`-Dbotmaker.shared.version=<newest v* tag>`); the
  committed pom is never edited by `release.sh`.

**Deferred / next**
- Consider surfacing the near-miss best score on the public API (today it's telemetry-only via `MatchResult.miss`).

## 2026-07-08 — CaptureSource redesign: three kinds, region-as-modifier, full method coverage

**Done**
- **`CaptureSource` is now exactly one of three, on one class:** `CaptureSource.desktop()` (whole virtual
  desktop, replaces `screen()`), `CaptureSource.monitor(int)` (a single screen, replaces `Screen.at(int)` as
  the public factory), `CaptureSource.window(String)`. `Screen.at` → internal `Screen.monitorSource(int)`
  backing `monitor(int)`. **Breaking** (sanctioned — early dev): callers of `CaptureSource.screen()` /
  `Screen.at(i)` move to `desktop()` / `monitor(i)`.
- **Region is a modifier on a source, not a separate parameter:** `source.region(Rect)` / `region(x,y,w,h)`
  returns a sub-source that actually **crops** `capture()` to that rect (in the source's own pixel space) and
  shifts `origin()` — so it both restricts the search area (fixes the old offset-only `region` that never
  cropped) and keeps absolute click coords. Regions compose.
- **Vision facades collapsed to `(template, CaptureSource[, double confidence])`.** Dropped every bare `Rect
  region` overload across `ImageFinder`/`ImageClicker`/`ImageWaiter`; each op now has a whole-desktop default
  + a `CaptureSource` form + optional trailing confidence. Uniform source coverage by construction — notably
  **`ImageClicker.clickCompare` gained CaptureSource overloads** (previously zero, though `findCompare` had
  them), plus the missing `ImageWaiter`/`findAny` forms.
- **`Mouse.click(CaptureSource src, int x, int y)`** — plain click at `src.origin() + (x,y)`: a fixed point
  inside a window/monitor/region, monitor-independent.
- **Observability preserved without a region param:** added `CaptureSource.base()` / `subRegion()` hooks;
  `ImageFinder` emits `MatchEvent(Surface.of(source.base()), source.subRegion(), result)` so overlays still
  know the window/screen + searched sub-rect.
- Supersedes the prior additive-overload entry below (which piled Rect+source overloads on); those Rect
  overloads are now gone in favour of `source.region(...)`.

**Deferred / next**
- Studio-side visual rubber-band region selection (interim: numeric x/y/w/h entry in the capture chooser).

## 2026-07-08 — Full CaptureSource overload coverage + `CaptureSource.window`

**Done**
- **`CaptureSource.window(String titleSubstring)`** — new static factory returning the first window whose
  title contains the substring as a `CaptureSource`, or the whole `screen()` if none matches (unwraps the
  `Window.find` `Optional` in one call). Studio now emits this inline instead of a generated `BotConfig`
  helper (the `BotConfig.java` sidecar was dropped Studio-side).
- **`ImageFinder` — CaptureSource-targeted overloads for the whole family**: `findAll`, `findAny`, group
  `find`, `findBest` (single + group), `findCompare` (single/varargs/group), `exists`/`notExists`/`existsAny`
  /`existsAll` (single + group), and the `if`/`while`/`untilExists*` lambda control-flow (single + group).
  Previously only `find`/`findAll` accepted a source. All additive, routed through the existing source-aware
  cores (`find(t,source,region,conf)`, `findAll(...)`, `compare(...)`), so coordinates stay absolute.
- **`ImageClicker` / `ImageWaiter`** — matching source overloads for `click`/`clickBest`/`clickAny`/`clickAll`
  and `waitFor`/`waitUntilGone`/`waitAndClick`, so a targeted window/monitor can drive click + wait blocks too.
- No existing public signature changed. Released bots need this SDK version to use the new source overloads;
  Studio's capture-source picker now attaches to every `CaptureSource`/`Window` parameter across these.

## 2026-07-08 — Telemetry carries the bot source line

**Done**
- `internal/observe/IpcObserver` now stamps each `Match`/`Click` telemetry event with the bot's source line
  (`botLine()` walks the current stack for the first non-`com.botmaker.*`/non-JDK frame — the user's bot
  class). Uses the shared wire v2 field (see `../botmaker-shared/ROADMAP.md`). Enables the Studio's
  running-block highlight on a plain run. Additive; a bot with no Studio attached still registers no observer.

## 2026-07-07 — Per-monitor CaptureSource (`Screen.at`)

**Done**
- **`Screen.at(int index)`** — a new public `CaptureSource` for a single monitor (0-based index into the OS
  screen-device list), so a bot can match against just one screen on a multi-monitor desktop instead of the
  whole virtual desktop (`CaptureSource.screen()` / `Screen.asSource()`). `origin()` is the monitor's
  top-left in virtual-screen space, so in-image matches convert to absolute clickable coordinates as usual.
- **`internal/capture/ScreenCapture`** gained `monitorBounds(int)` (AWT device bounds, falls back to the
  virtual desktop for an out-of-range index) and `captureMonitor(int)` (crops the single `captureDesktop()`
  grab to those bounds — one backend selection, no second capture path).
- Consumed by the Studio's generated `BotConfig.screen(index)` helper (capture-source picker → block code).
  Additive only; no existing public signature changed. Released bots need this SDK version for a
  specific-screen block; local dev picks it up via `./dev-install.sh`.

## 2026-07-07 — Observer SPI + Studio telemetry bridge

**Done**
- **New public, Studio-agnostic observability SPI** (`com.botmaker.sdk.api.observe`): `BotObserver`
  (default no-op `onMatch`/`onClick`), event records `MatchEvent`/`ClickEvent`, a `Surface` target
  (window title+bounds, or screen; `Surface.of(CaptureSource)` resolves it), and the `Bots` registry
  (copy-on-write `add/removeObserver`, `hasObservers`, `fireMatch`/`fireClick`). A first-class feature
  usable standalone — log actions, assert on them in tests, drive custom tooling. `Bots` is the one bit
  of static facade state (a deliberate, documented exception to the stateless-dispatcher style).
- **Emit sites** in the vision/interaction facades, each guarded by `Bots.hasObservers()` so a normal run
  builds/pays nothing: `ImageFinder.find(...)` (found + not-found), private `compare(...)`, `findAll(...)`
  (one event per match, or a not-found), and `ImageClicker.click`/`clickResult`/`clickAll` (left-click).
- **Internal, env-gated Studio bridge** `internal/observe/IpcObserver`: translates SDK events →
  `com.botmaker.shared.ipc.TelemetryEvent` and ships them via `TelemetryClient.fromEnvironment()`. It
  self-installs only when `BM_IPC_PORT` is set (Studio-launched). `Bots` loads it *by name* in a static
  block, so the public API keeps zero compile-time dependency on the bridge/socket — a bot never needs
  the Studio, and outside it no observer registers and no socket opens.
- Tests: `api/observe/BotsTest` (registration, fan-out, error isolation, `Surface`). Real end-to-end
  (find→click overlays) is validated with the Studio's stub bot.
- **`dev-install.sh` now routes a local SDK build to the local `botmaker-shared` build.** It rewrites
  `botmaker.shared.version` → `0.0.0-SNAPSHOT` in the temporary pom (restored on exit), alongside the
  existing groupId/version rewrites. Without this, once a release pins the property to a real tag (e.g.
  `v0.0.2`) a `local-SNAPSHOT` SDK build silently pulled shared from JitPack, so local shared changes were
  ignored. Studio also now auto-lists the installed `local-SNAPSHOT` in its version dropdown (see Studio
  ROADMAP), so there's nothing to type.

**Deferred / next**
- Optional raw `Mouse.click` (bare point, no template context) click events — left out for now.
- The SDK pom still pins `botmaker.shared.version` to the released tag; the umbrella `release.sh` bumps it
  to the new shared tag that carries the `ipc` package when this ships. For local dev build with
  `-Dbotmaker.shared.version=0.0.0-SNAPSHOT` after `botmaker-shared/dev-install.sh`.

## 2026-07-06 — Group/`Any`/`All` variants for the loop & existence helpers

**Done**
- **Lambda control-flow over an `ImageTemplateGroup`** (`ImageFinder`) — `whileExistsAny`/`ifExistsAny`
  hand the action the first visible match (`Consumer<MatchResult>`, first-match via `find(group)`);
  `whileExistsAll`/`ifExistsAll`/`untilExistsAny`/`untilExistsAll` take a `Runnable` ("all present" /
  "waiting" has no single meaningful match, mirroring `untilExists`). All are one-capture-per-check
  like the single-template originals.
- **Group/`All` existence booleans** — `exists(ImageTemplateGroup)` (any, first-match),
  `existsAll(ImageTemplate...)` and `existsAll(ImageTemplateGroup)` (every one visible; empty input is
  false), `notExists(ImageTemplateGroup)`. Complements the pre-existing `existsAny(...)`.
- **`findCompare(good, bad)` Javadoc** now documents `@param good`/`@param bad` (Studio surfaces these
  as argument labels + the "learn about it" description).
- All additive — no existing public signature changed. Tests: `ImageFinderGroupTest` (headless-safe
  `existsAll()` empty guard; screen-dependent paths stay in the manual `Main` harness).

**Deferred / next**
- `ImageWaiter.waitForAny/waitForAll/waitUntilGone` group overloads (nice-to-have; `ImageClicker`
  already covers group clicking).

## 2026-07-05 — Multi-template vision: `ImageTemplateGroup`, best-match, compare

**Done**
- **New value type `ImageTemplateGroup`** (`api.vision`) — immutable, non-empty, ordered wrapper
  around `List<ImageTemplate>` with `of(ImageTemplate...)` / `of(List)` factories and `toArray()`.
  Serves as the first-class multi-template value (Studio detects it as a special type → dedicated
  list picker).
- **`ImageFinder.find(ImageTemplateGroup)`** (+ region/confidence) — first-match over the group
  (delegates to `findAny`; keeps cheap short-circuit). Mirror `ImageClicker.click(group)`.
- **Re-introduced `findBest`/`clickBest`** (previously deleted 2026-07-03) with clearer semantics:
  exhaustive highest-score match. Overloads for a **single `ImageTemplate`** (returns the global
  argmax vs. `find`'s first-acceptable) and for an **`ImageTemplateGroup`** (best score across all
  templates).
- **`findCompare`/`clickCompare`** — a "good" template must out-score similar "bad" variants (other
  in-game states of the same element) **at the same location** by `ClickConfig.DEFAULT_COMPARE_MARGIN`
  (0.05). Overloads: `(good, bad)`, `(good, bad...)`, `(ImageTemplateGroup good, ImageTemplateGroup bad)`
  (+ region/margin). Single-capture: one screenshot, good located via `findBestMatch`, each bad
  re-scored in a padded window at good's location via new internal `OpencvManager.scoreAround(...)`.
- **Tests:** `ImageTemplateGroupTest` (guards/immutability), `ScoreAroundTest` (synthetic
  good-vs-distractor ranking, self-contained — no fixture image needed).

**Note:** the beta status let us add these to the public `api.vision` surface freely.

## 2026-07-03 — Vision API simplification

**Done**
- **Collapsed `api.vision` from 9 classes to 3 action classes** (+ the unchanged value/config types
  `MatchResult`/`ImageTemplate`/`ClickConfig`):
  - **`ImageFinder`** — now owns single-frame lookup *and* existence: `find`/`findAll`/`findAny`, the boolean
    `exists`/`notExists`/`existsAny` (moved from `ImageMatcher`), and lambda control-flow `whileExists` /
    `ifExists` (take `Consumer<MatchResult>` — one capture per check, hands the action the live match) and
    `untilExists` (takes `Runnable`, since no match exists while the template is absent).
  - **`ImageClicker`** — trimmed to `click`/`clickAny`/`clickAll`.
  - **`ImageWaiter`** — unchanged: `waitFor`/`waitUntilGone`/`waitAndClick`.
- **Deleted** `Vision` (+ `evaluate`/`snapshot`), `ImageState` (+ `ScreenState`), `ImageMatcher`, and the
  `…then…`/long-tail variants (`clickBest`, `clickFirst`, `clickUntilSuccess`, `clickWhileVisible`, `findBest`,
  `retryUntilFound`, `clickAndThen`, `clickThenWaitFor`, `waitForGoneThenClick`, `clickOrWaitAndClick`). The
  multi-template single-capture branch (`Vision.evaluate`/`ScreenState`) is replaced by the `whileExists`-style
  lambdas; deleted `VisionEvaluateTest`.
- **Callers updated:** `capture/CaptureSource` + `capture/Window` javadocs no longer reference
  `Vision`/`ImageState`; `Main` unaffected. Studio drops `ImageMatcher`/`ImageState` from its SDK-facade list.

## 2026-07-02 — Game launch API

**Done**
- **`api.launch.Game` facade.** New public static entry point exposed as Studio blocks:
  `Game.launch(String executablePath, String... args)` starts any executable via `ProcessBuilder`
  (detached), and `Game.launchSteam(String appId)` / `launchSteam(int)` hands a Steam appId to the
  local Steam client via the cross-platform `steam://rungameid/<appId>` URL, falling back to
  `steam -applaunch <appId>`. Launching a Steam game needs no login of ours — the installed, signed-in
  Steam client owns the session; we never touch credentials.
- **`internal/launch/UriLauncher`.** Small OS URL-handler opener (Desktop.browse → `xdg-open`/`open`/
  `rundll32`), mirroring the Studio's `util.BrowserLauncher` (duplicated because the SDK can't depend
  on the Studio). Used by `launchSteam` to invoke `steam://` URLs.

**Deferred / next**
- No "wait for game window" helper yet — the vision blocks (`ImageWaiter`) already poll for on-screen
  templates, which is how a test bot detects the game is up.

## 2026-06-30 — Linux click support

**Done**
- **Linux click works.** `api.interaction.Mouse.click` now routes through
  `internal/capture/core/NativeControllerFactory.get()` instead of calling the Windows-only
  `internal/capture/Clicker` (JNA `User32`) directly. This fixes
  `UnsatisfiedLinkError: Unable to load library 'user32'` on Linux, which surfaced whenever a click
  actually fired (i.e. when `ImageFinder` found the template on screen). Windows path is unchanged
  (factory → `WindowsController` → `Clicker` → `User32 PostMessage`).
- **Multi-monitor coordinates fixed.** Added `api.capture.Screen.captureOrigin()` (the virtual-screen
  origin from `ScreenCapture.getVirtualScreenBounds()`). `ImageFinder.find/findAll` and
  `ImageState.findWhichAreVisibleDetailed` now add this origin to match coordinates, so reported
  points are **absolute** screen coordinates. Previously they were image-local, so clicks landed off
  by the virtual origin whenever a monitor was placed left/above the primary (negative origin).
  Note: `ImageFinder.find/findAll` now return absolute coords (corrected contract).
- **Cursor save/restore on Linux click (X11 only).** `LinuxController.postLeftClickScreen` reads the
  pre-click pointer via X11 `XQueryPointer` (added the binding in `linux/X11.java`) against the
  default root window — same coordinate space as `XTestFakeMotionEvent` — and warps back after the
  click. Gated on `WAYLAND_DISPLAY == null`; skipped under Wayland (see below).

**Deferred / next**
- **Click without disturbing the cursor on native Wayland.** Under native Wayland the JVM is an
  XWayland client: it can *write* the pointer (warp/click via XTest) but **cannot read** the global
  cursor position, so save/restore is impossible there (it would teleport the cursor to a stale
  constant). Current interim: on Wayland we skip restore and leave the cursor on the click target.
  Proper fix: implement input injection via the **xdg-desktop-portal RemoteDesktop** interface
  (libei / PipeWire) — this is also what raises the one-time "allow control of pointer/keyboard"
  prompt. Alternative (weaker): window-relative motionless click via `XSendEvent`, but many apps
  ignore synthetic events and it can't reach native Wayland windows. Likely belongs alongside the
  capture backends as a new injection strategy.
- **GNOME/sway capture.** Add a portal/PipeWire `CaptureBackend` and wire into
  `CaptureBackend.select()` (noted in `CLAUDE.md` › Screen capture).
