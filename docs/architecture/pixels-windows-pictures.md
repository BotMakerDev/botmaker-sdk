# Pixels, windows and pictures

- **This plugin grabs its own pixels**, through `botmaker-shared`; the contract has no capture service.
  `plugin/screen/EditorFrame` has two `grabAsync` overloads and the second is not the first with a flag: an
  editor samples the target *as it is*, while a capture raises the window first so what it saves is what is
  on screen (at whatever size it is — the matcher rescales against each picture's sidecar). A blank per-window
  grab on Wayland falls through to a desktop capture cropped to the window. `EditorFrame.Failure` tells *no
  target* from *the grab came back blank*, because they send a user to two different places.
- **The whole desktop is read through shared's `ScreenCapture`**, the grab a bot uses too (Robot, or under
  Wayland the first installed of Spectacle, grim, gnome-screenshot). There is no second desktop grab here.
- **One overlay, `plugin/screen/ScreenOverlay`**: a rubber band, a point lens and a colour lens over a frozen
  frame. Each shows a hint, previews the exact numbers it will write (through the slot's `PickSpace`), and
  cancels on Esc or a right-click; over a frame it is promoted above fullscreen windows (`OverlayStage`).
- **`OverlayStage` is here, not in the toolkit**: the raise is shared's
  (`NativeControllerFactory.promoteOverlayAboveFullscreen`), and the toolkit names no BotMaker upstream but the
  contract. The capture surfaces are deliberately ownerless, so a user can minimise the editor and keep
  capturing, and opt out of theming with the toolkit's `Styles.UNTHEMED`.
- **The pictures are this plugin's folder** (`plugin/pictures/TemplateLibrary`, keyed on
  `StudioServices.resourcesDir()`, with `TagCatalog` and `TemplateManifest`), and **their names are
  `Pictures` constants changed by binding**: `TemplateUses` maps a file to its constant and calls the
  contract's `PluginValues` open-set operations on `SdkValues.PICTURES` (`uses`, `add`, `rename`, `repoint`,
  `remove`); Studio resolves the constant, compiles each change as the whole bot and refuses one that breaks
  it. A path literal a user wrote is not rewritten. Naming a capture (`TemplateNaming`, one class for one crop
  and a batch) refuses blank, taken and reserved names with one wording.
- **The Remote Pilot is this plugin's feature** (`plugin/pilot`, the web client under
  `src/main/resources/pilot/`): a toolbar button opens it and `projectClosing()` releases its port and nested
  display. Telemetry crosses as `TelemetryFrame` bytes (`Runs.onTelemetry`), decoded with the shared codec the
  bot encoded it with — **strings and bytes cross the contract, shapes do not**. It needed nothing added to
  `StudioServices`, which is the test for the next feature that wants to move here.
