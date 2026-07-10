package com.botmaker.sdk.internal.launch;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

/**
 * Opens a URI (e.g. a {@code steam://} protocol URL) through the operating system's registered handler.
 *
 * <p>{@link Desktop#browse(URI)} is tried first but is a silent no-op under many Linux desktop
 * environments (and unsupported headless), so this falls back to the platform's URL opener
 * ({@code xdg-open} / {@code open} / {@code rundll32}). Returns whether a handler was successfully invoked;
 * never throws.
 *
 * <p>Mirrors the Studio's {@code util.BrowserLauncher}; duplicated here because the SDK cannot depend on the
 * Studio.
 */
public final class UriLauncher {

    private UriLauncher() {}

    /** Opens {@code uri} with the OS handler. Returns {@code true} if a launcher was invoked. */
    public static boolean open(String uri) {
        if (uri == null || uri.isBlank()) return false;
        // Custom protocol schemes (steam://, discord://, …) must go to the OS protocol handler. On Windows
        // Desktop.browse hands them to the default *browser*, which shows a blank page instead of launching
        // Steam — so only use Desktop.browse for real web/file URLs and route everything else natively.
        if (isWebOrFileScheme(uri) && tryDesktop(uri)) return true;
        return tryNativeOpener(uri);
    }

    private static boolean isWebOrFileScheme(String uri) {
        String lower = uri.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:");
    }

    private static boolean tryDesktop(String uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(uri));
                return true;
            }
        } catch (Exception e) {
            System.err.println("Desktop.browse failed for " + uri + ": " + e.getMessage());
        }
        return false;
    }

    private static boolean tryNativeOpener(String uri) {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> command;
        if (os.contains("win")) {
            // explorer.exe routes the URI through ShellExecute, which honours registered protocol handlers
            // (steam://, etc.) — the most reliable way to hand a custom scheme to its app on Windows.
            command = List.of("explorer.exe", uri);
        } else if (os.contains("mac")) {
            command = List.of("open", uri);
        } else {
            command = List.of("xdg-open", uri);
        }
        try {
            new ProcessBuilder(command).inheritIO().start();
            return true;
        } catch (Exception e) {
            System.err.println("Native opener failed for " + uri + ": " + e.getMessage());
            return false;
        }
    }
}
