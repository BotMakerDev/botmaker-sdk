# Screen capture

`com.botmaker.shared.capture.ScreenCapture` is the **single** desktop-capture facade, in shared beside
per-window capture. The desktop and monitor sources (`internal/capture/Desktop`, `Monitor`) route through
it; there is one `getVirtualScreenBounds()` (the AWT all-monitor union). A match's coordinates are in its
`CaptureSource`'s pixels, and `origin()` places them on the desktop. See `../botmaker-shared/CLAUDE.md` for
the backend selection (`RobotCapture` vs `SpectacleCapture`) and the Wayland notes; `../docs/display-pipeline.md`
before touching `api/capture` or `internal/session`.

### Mouse clicks & the Wayland input limitation

`api.input.Mouse.click` routes through `NativeControllerFactory.get()` (Windows → `Clicker`/
`User32 PostMessage`; Linux → `LinuxController` XTest, with an AWT `Robot` fallback).

On Linux the click warps the real cursor, then restores it. **Restore is X11-only:** under native
Wayland the JVM is an **XWayland** client that can *write* the pointer (warp + click work) but
**cannot read the global cursor position** (XQueryPointer / AWT `MouseInfo` return a stale constant
when the cursor isn't over our surface — and the bot has no window). `LinuxController` therefore
skips the restore when `WAYLAND_DISPLAY` is set, leaving the cursor on the target. The Wayland-correct
"click without disturbing the cursor" path is the xdg-desktop-portal **RemoteDesktop** (libei/
PipeWire) interface — deferred; see `ROADMAP.md`.
