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
- **A layer's view and the Configurations tab**: `ConfigViews` (no JavaFX) turns one `Target` (an emulator or an
  instance) into its setting, account and game rows, each with where its value comes from and, for an instance, what
  it holds now; every change is a pure `EmulatorConfig` → `EmulatorConfig` written through `window.change`. The same
  class lays out the Configurations grid (a column per layer, the configured instances not found last), `compare` and
  `copy`. `ConfigView` draws a layer over the tab it was opened from, `ConfigurationsTab` the grid, and
  `ConfigDialogs` asks a value, an account (a name and a note, never a password) or a game. A value is typed as it is
  shown (`1080x1920@320`, `4 GB`, `on`), not through the SDK's slot editors: these are not a bot's values. No export
  or import: a PC's configuration is that PC's (the user's call).
- **The Running tab and the live screen**: `RunningModel` (no JavaFX) groups the running instances and the stopped
  ones a bot expects, parses Android's `/proc/stat`, `/proc/uptime` and `/proc/meminfo`, weighs starts against
  `WindowsEmulatorChecks.availableMb()`, and maps a click on a drawn screen to the frame's pixels. `RunningTab` reads
  each running instance on one thread of its own every two seconds, only while the tab is shown, and updates its card
  in place. `LiveScreenDialog` opens by itself the first time an operation needs the user; its frames, taps and
  typing share one thread and one ADB connection, and typing goes through `AdbDevice.text`, which logs the length
  only. The open bot's run is the contract's `Runs` (stop only: the contract has no *open run*).
- **Clone, Rename, Delete and the rename hand-off**: `ManageActions` puts shared's `InstanceManager` behind the
  Instances tab's row menu (`ManageModel`, no JavaFX, has the words and the offered name); a console's work is an
  operation, BlueStacks' manager is opened. A rename moves the instance's rows (`HostConfig.rename`) and adds a row to
  the host value `INSTANCE_RENAMES` (old key → new key, chains collapsed, `InstanceRenames.recorded`); a delete drops
  both (`HostConfig.forget`). A launch target names an instance by its name only, so `InstanceRenames.moves` maps an
  old name to a new one only while no instance on this PC still has the old name, and only when it leads one way.
  `UsedBy.remember` applies the map on every bind with a screen, and the window right after a rename:
  `InstanceRenames.handOff` rewrites `launchTargets`, the runner's `launchTarget` and an `EmulatorSource` capture
  (its region kept), each a plugin write Undo takes back, and logs it. The picker's *Manage…* opens the window
  filtered to its row (`EmulatorsWindow.open(services, instance)`).
