# Screen capture

`com.botmaker.shared.capture.ScreenCapture` is the **single** desktop-capture facade, in shared beside
per-window capture. The desktop and monitor sources (`internal/capture/Desktop`, `Monitor`) route through
it; there is one `getVirtualScreenBounds()` (the AWT all-monitor union). A match's coordinates are in its
`CaptureSource`'s pixels, and `origin()` places them on the desktop. See `../botmaker-shared/CLAUDE.md` for
the backend selection (`RobotCapture` vs `SpectacleCapture`) and the Wayland notes; `../docs/display-pipeline.md`
before touching `api/capture` or `internal/session`.

### The capture source is Automatic (2026-11-25)

A new bot's `captureSource()` is `CaptureSource.automatic()` (`internal/capture/AutomaticSource`), and
`AutoCapture.decide` turns it into a source from the launch target (spec `2026-11-25-auto-capture-design.md`). One
decision serves the run (`Bot`, `InstalledGame`) and Studio's views (`plugin/screen/CaptureValue.resolved`):

- **Fixed**: an emulator or phone app reads its instance (the one the host gave the run,
  `botmaker.capture.emulator`, else the one `instance()` deals, else the target's, else the one running; several
  running and none named fails), a website its run's browser, no target the whole desktop, a source set by hand
  itself (an emulator's moved onto the dealt instance). `desktop()` with a launch target counts as Automatic
  (`AutoCapture.isAutomatic`).
- **WatchLaunch**: a desktop game with no window kept. The run closes the game when it runs, launches it and
  `LaunchWatch` reads the window the launch opened once it stayed the one for 3 s, the launched process's own first.
  The pick is kept by launch target in the SDK's values of this PC (`HostWindows` in `Runs.java`, through
  `AutoPicks`), so the next run reads it straight away; a kept window the running game no longer shows is forgotten
  and the launch watched again. Off the desktop (a private display, a game VM) nothing is watched.
- **Fail**: the run stops with the reason; Studio's views show nothing.

Which new window is the game's (`NewWindows.ranked`, 2026-11-25):

- A window of a generic class (`WindowIdentity.GENERIC`: Proton's, Wine's, Java's, `ApplicationFrameHost.exe`) is
  saved with its title. While it is untitled, or titled with its own class, it is a placeholder and no candidate. A
  window that takes the game's name starts the 3 s again.
- A launcher's window goes last and is never picked: told by its process, or by its class (`launcherClass`) when the
  launched process does not own it, so that a store can be the target itself.
- Among the others, a window of the class the target names goes first (`steam_app_<id>`: a Steam game's appid, or
  umu's id of a Faugus game), then one whose process its launcher named the game in (shared's `GameMarker`,
  2026-11-26: Steam's `SteamAppId`, Faugus's `FAUGUSID`, Lutris's `GAME_NAME`; read from `/proc`, so Linux only),
  then one of the launch's own: the launched process or a child of it, or a process carrying the launch's
  `BOTMAKER_LAUNCH` id, which every process the run starts detached gets and a game re-parented out of the tree keeps
  (Wine's `start.exe` gone, a double fork; lost when the launch is handed to a launcher that runs already, or to
  Flatpak). Then the bigger. A target whose launcher sets none of these (Heroic, Bottles, a plain program) falls back on
  the launch's tree and size. A window its launcher named is never a launcher's, whatever its class: Lutris's
  Battle.net can be the target.
- Titles are compared without their numbers and white space: `Game - 60 fps` finds `Game - 144 fps`.
- A launch that opened no window in 10 s only brought the game to the front: the biggest open window of the target's
  class is picked. Only a Steam target names one.
- When only size tells two windows apart (neither of the target's class, or both named by its launcher: a
  pre-launcher and the game inherit one environment), a run Studio or the core started asks in the *Which window is the game?*
  picker (`plugin/source/WindowDoubtPicker`) for 30 s, then takes its guess and keeps nothing. A run no host started
  keeps its guess.
- A hidden window is not listed, since its pixels cannot be read: a minimized one, and on KDE a game's that opened
  while the displays were off, so that watch ends with no window.

The trace says what was picked and why (`capture: Waydroid, from the game's target`). Studio's views, which never
launch and never ask which emulators run, read the whole desktop for a WatchLaunch and for an instance a run would
have to ask about. The Choose capture source window opens on its Automatic tab, which
says what Automatic reads and offers *Forget the pick* (`plugin/source/AutomaticTab`).

### Point at the window (2026-11-26)

Besides a run's pick and the doubt picker's thumbnails, the user can point at the game's window: *⌖ Point at the
game's window* on the Automatic tab (a desktop game only) and in the doubt picker's button row
(`plugin/source/PointAtWindow`, spec `2026-11-26-point-at-window-design.md`). Studio's windows are iconified, shared's
`WindowPointer` waits up to 60 s for one click, and the windows come back. Esc or the timeout changes nothing.

- **Backends** (shared's `WindowPointer.current()`): KDE Wayland asks KWin (`queryWindowInfo` over `gdbus`, KWin's own
  crosshair); X11 grabs the pointer; Windows hooks the mouse and keyboard (low-level hooks, the system cursor a
  crosshair); macOS taps the session's events (needs Accessibility). The click never reaches the game. The clicked
  window is matched to one BotMaker can capture; a native Wayland window, or the desktop, is refused with why.
- **GNOME Wayland, or a Wayland session without KWin**: no pointer, so the button is hidden.
- **The confirm step** (layout A): *Is this the game's window?*, a still of the window, its title, class and size,
  then **Keep this window**, **Point again** and **Cancel**. Keep saves the window as this PC's pick for the game,
  the doubt picker's path, so a run reads it like any pick. Studio's own window is refused, and so is a window
  nothing names (an untitled Proton or Wine window: `WindowIdentity.of` is null). A launcher's class
  (`RunningProbe.launcherClass`) and an untitled window of its own class are warned, and kept if the user insists.
  From the doubt picker, a window clicked after the run took its guess is not kept.
- **Tried live**: X11 under Xephyr only. KDE Wayland, Windows and macOS are not tried live yet.

### Live frames: a grab kept open (2026-10-22)

`captureWindow` reads one frame and lets go: it finds the window, asks X about it, and allocates a new image, which
suits a bot's look. 🗺's live board reads 60 times a second, as OBS's X11 capture does, through a grab it keeps open
(shared's `NativeController.grabWindow`, a `FrameGrab` writing into a `FrameSink`'s `int[]`):

- **Linux** (`XShmGrab`): an X connection of its own, an MIT-SHM segment per size, and the window's composite pixmap
  named once. Each frame is one `XShmGetImage` and one pass to ARGB, nothing allocated. When XWayland refuses the
  pixmap (BadMatch), it reads the window itself; a frame that fails goes through `captureWindow`, and the shared path
  is tried again every 30 frames. Measured on Firestone (1280×692): 1.4–5 ms a frame, against 12–27 ms.
- **Windows and Mac** use the default `grabWindow`: `captureWindow` plus a copy into the sink.
- **A private display**: the display agent holds the grab on `:N` and writes each frame into a file under
  `/dev/shm` that Studio maps (`RemoteDisplay.SharedGrab`, `../docs/display-pipeline.md` §4).

The board (`plugin/map/LiveBoard`) grabs on a platform thread paced on `System.nanoTime`, into four `PixelBuffer`
slots (`LiveImage`), and an `AnimationTimer` shows the newest one on the FX pulse. Its line says the read and shown
rates, the ms a read and a draw, and which one is the limit.

### Mouse clicks & the Wayland input limitation

`api.input.Mouse.click` routes through `NativeControllerFactory.get()` (Windows → `WindowsController`:
posted messages, or `SendInput` once the bot takes over; Linux → `LinuxController` XTest, with an AWT
`Robot` fallback).

On Linux the click warps the real cursor, then restores it. **Restore is X11-only:** under native
Wayland the JVM is an **XWayland** client that can *write* the pointer (warp + click work) but
**cannot read the global cursor position** (XQueryPointer / AWT `MouseInfo` return a stale constant
when the cursor isn't over our surface — and the bot has no window). `LinuxController` therefore
skips the restore when `WAYLAND_DISPLAY` is set, leaving the cursor on the target. The Wayland-correct
"click without disturbing the cursor" path is the xdg-desktop-portal **RemoteDesktop** (libei/
PipeWire) interface — deferred (there is no roadmap file since 2026-11-10).
