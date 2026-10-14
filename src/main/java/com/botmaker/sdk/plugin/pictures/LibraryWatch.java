package com.botmaker.sdk.plugin.pictures;

import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

/**
 * Tells a window showing the project's pictures that their folder changed under it — a picture the assistant cut, a
 * tag it set, a file copied in — so it redraws without being reopened. The folder holds the pictures and their tags'
 * file alike. One daemon thread per open window, ended by {@link #close()}; a burst of changes is gathered until the
 * folder has been quiet for {@link #QUIET_MS}, then {@code onChange} runs once on the FX thread.
 */
final class LibraryWatch implements AutoCloseable {

    /** How long the folder stays still before a burst of writes counts as done. */
    private static final long QUIET_MS = 300;

    /** Null when the folder could not be watched: a gallery that redraws only on its own changes, as before. */
    private final WatchService service;
    /** Set by {@link #close()}: a change gathered just before it is not redrawn on a window gone or reopened. */
    private volatile boolean closed;

    private LibraryWatch(WatchService service) {
        this.service = service;
    }

    /** Watches {@code folder}; one that cannot be watched (missing, or a file system without events) is not. */
    static LibraryWatch start(Path folder, Runnable onChange) {
        WatchService service = null;
        try {
            service = folder.getFileSystem().newWatchService();
            folder.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            LibraryWatch watch = new LibraryWatch(service);
            Thread.ofPlatform().daemon().name("picture-folder-watch").start(() -> watch.run(() -> {
                if (!watch.closed) onChange.run();
            }));
            return watch;
        } catch (IOException | UnsupportedOperationException e) {
            close(service);
            return new LibraryWatch(null);
        }
    }

    private void run(Runnable onChange) {
        try {
            while (true) {
                drain(service.take());
                WatchKey more;
                while ((more = service.poll(QUIET_MS, TimeUnit.MILLISECONDS)) != null) drain(more);
                Platform.runLater(onChange);
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // closed with its window
        }
    }

    private static void drain(WatchKey key) {
        key.pollEvents();
        key.reset();
    }

    @Override public void close() {
        closed = true;
        close(service);
    }

    private static void close(WatchService service) {
        if (service == null) return;
        try {
            service.close();
        } catch (IOException ignored) {
            // its thread ends on the next take either way
        }
    }
}
