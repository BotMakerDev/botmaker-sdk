# Android emulator (`internal.emulator`, since 2026-10-01; a bot writes `CaptureSource.emulator(name)`)

The emulator **capability** — the dadb transport (`AdbDevice`) and product discovery (`Platforms`,
`BlueStacks`/`LdPlayer`, `WindowsRegistry`, `EmulatorInstance`) — lives in **shared**
(`com.botmaker.shared.emulator`), because both the SDK (connect at runtime) and the plugin's picker need it.
dadb therefore comes in transitively via shared — it is **not** a direct SDK dependency.

The SDK owns only the facade `internal.emulator`: **`Emulator implements CaptureSource`** (wraps a shared
`AdbDevice`; `origin()` is `(0,0)` so a match's coords are already emulator pixels and the whole vision/click
stack works unchanged; `click(Point)` → `adb input tap`) and **`Emulators`** (static discovery over shared's
`Platforms`: `list`/`first`/`named`/`connect`, plus `use()`/`use(String)` connect-and-set-`Source` shorthands).

## The Emulators window (2026-11-12)

Spec `../docs/superpowers/specs/2026-11-12-emulators-window-design.md`, plan `…/plans/2026-11-12-emulators-window.md`.

- **The configuration is the SDK's values of this PC** (`plugin/emulator/host`): `@SdkHostValue`, seven method
  values in three host holders (`Emulators.java`, `Instances.java`, `Accounts.java` in
  `UserDirs.config()/plugins/com_github_botmakerdev/botmaker_sdk/`), each a list of text rows
  (`HostSettings.of(new HostSettings.Setting("ldplayer", "screen", "1080x1920@320"))`). The types are under
  `plugin`, not `api`: no bot names them and no run reads them. `HostConfig` turns the rows into shared's
  `EmulatorConfig` and back, keeping every row it does not understand; rows of text are what lets an older BotMaker
  keep a newer one's field, product or account kind. No password is a value.
- **What is not a value** (saved filters, the applies log) is JSON in that folder's `state/` (`HostConfig.stateDir`).
- **Used by** is remembered, not read: a plugin sees only the open bot, so `UsedBy.remember` notes the bot beside the
  instances its `launchTargets` name each time one is opened in a host with a screen (the user's call).
- **The window** (`plugin/emulator/window`): `EmulatorsModel` has no JavaFX and owns the rows, the columns' text
  and order, the filters and their saved form; `EmulatorsWindow`, `EmulatorsTab` and `InstancesTab` draw one
  `Snapshot`, read again by Refresh and after each operation. `ApplyPlan` says what an Apply would do and is its
  operation: shared's `Apply`, then `InstanceChanges` (accounts, games), queued on shared's `EmulatorOperations`,
  which is the process's and outlives the window. A batch's `Passwords` are asked in the window's own dialog and
  cleared when its last operation ends.
