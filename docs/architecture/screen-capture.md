# Screen capture

`com.botmaker.shared.capture.ScreenCapture` is the **single** desktop-capture facade, in shared beside
per-window capture. The desktop and monitor sources (`internal/capture/Desktop`, `Monitor`) route through
it; there is one `getVirtualScreenBounds()` (the AWT all-monitor union). A match's coordinates are in its
`CaptureSource`'s pixels, and `origin()` places them on the desktop. See `../botmaker-shared/CLAUDE.md` for
the backend selection (`RobotCapture` vs `SpectacleCapture`) and the Wayland notes; `../docs/display-pipeline.md`
before touching `api/capture` or `internal/session`.

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
PipeWire) interface — deferred; see `ROADMAP.md`.
