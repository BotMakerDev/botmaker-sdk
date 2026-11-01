package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.console.Debug;
import com.botmaker.shared.emulator.SystemDialog;
import com.botmaker.shared.emulator.SystemDialog.Shown;

import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.Point;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Answers Android's own dialogs over the game while a bot runs, so no bot maps them (the user's call, 2026-11-01):
 * Wait on "isn't responding", Close app on "keeps stopping", Don't allow on a permission, each pressed by its id in the
 * whole tree ({@link SystemDialog#answerIn}), else Back. A crash, or a game
 * that stops answering {@value #HUNG_TIMES} times or for {@value #HUNG_MS} ms, owes a restart, which
 * {@link com.botmaker.sdk.internal.bot.Watchdog#checkpoint()} turns into the run's recovery.
 *
 * <p>Asked at most every {@value #EVERY_MS} ms, from {@link EmulatorSource#capture()} while the run's watchdog is
 * on, on a thread of its own: a capture never waits for {@code dumpsys}.
 */
public final class SystemDialogs {

    static final long EVERY_MS = 2_000;
    static final int HUNG_TIMES = 3;
    static final long HUNG_MS = 60_000;
    /** How long without "isn't responding" before its count starts again. */
    static final long CALM_MS = 120_000;
    private static final int KEYCODE_BACK = 4;

    /** What a look at the focus asks for. */
    enum Act { NOTHING, BACK, BACK_AND_RESTART }

    /**
     * The "isn't responding" count across looks: a dialog counts once when it shows, however many looks find it
     * still up (a tap that missed); one up for {@value #HUNG_MS} ms restarts the game by itself.
     */
    static final class Count {
        private int times;
        private long since;
        private long last;
        /** Whether the last look found the game's "isn't responding" up. */
        private boolean up;

        synchronized Act seen(Optional<Shown> shown, long now) {
            boolean hung = shown.isPresent() && shown.get().theApps()
                    && shown.get().dialog() == SystemDialog.NOT_RESPONDING;
            boolean wasUp = up;
            up = hung;
            if (shown.isEmpty()) {
                if (now - last > CALM_MS) times = 0;
                return Act.NOTHING;
            }
            // Another app's dialog is closed and says nothing about the game.
            if (!shown.get().theApps()) return Act.BACK;
            return switch (shown.get().dialog()) {
                case PERMISSION -> Act.BACK;
                case CRASHED -> {
                    times = 0;
                    yield Act.BACK_AND_RESTART;
                }
                case NOT_RESPONDING -> {
                    if (times == 0) since = now;
                    if (!wasUp) times++;
                    last = now;
                    if (times < HUNG_TIMES && now - since < HUNG_MS) yield Act.BACK;
                    times = 0;
                    up = false;
                    yield Act.BACK_AND_RESTART;
                }
            };
        }
    }

    /** Forgets what an earlier run left owed: a run starts with none. */
    public static void clear() {
        restartOwed = null;
    }

    private static final Count COUNT = new Count();
    private static final AtomicLong ASKED = new AtomicLong();
    private static final AtomicBoolean ASKING = new AtomicBoolean();
    private static volatile String restartOwed;

    private SystemDialogs() {}

    /** Looks for a dialog over {@code emu}'s app unless one was looked for in the last {@value #EVERY_MS} ms. */
    static void watch(Emulator emu, Supplier<Optional<UiTree>> wholeTree) {
        long now = System.currentTimeMillis();
        long asked = ASKED.get();
        if (now - asked < EVERY_MS || !ASKED.compareAndSet(asked, now)) return;
        if (!ASKING.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("system-dialogs").start(() -> {
            try {
                answer(emu, wholeTree);
            } finally {
                ASKING.set(false);
            }
        });
    }

    private static void answer(Emulator emu, Supplier<Optional<UiTree>> wholeTree) {
        Optional<Shown> shown = emu.device().systemDialog();
        Act act = COUNT.seen(shown, System.currentTimeMillis());
        if (act == Act.NOTHING) return;
        SystemDialog dialog = shown.get().dialog();
        String said = "Android says " + (shown.get().theApps() ? "the game " : "another app ") + dialog.said();
        // Its button, read off the tree; Back only when the tree cannot be read (it leaves an ANR up).
        Optional<Point> answer = wholeTree.get().flatMap(dialog::answerIn);
        if (answer.isPresent()) {
            Debug.log(said + ": answered");
            emu.device().tap(answer.get().x, answer.get().y);
        } else {
            Debug.log(said + ": Back");
            emu.device().key(KEYCODE_BACK);
        }
        if (act == Act.BACK_AND_RESTART) restartOwed = said + (dialog == SystemDialog.CRASHED ? "" : " again")
                + ": restarting it";
    }

    /** Why the game must be restarted, once: the next call answers empty until a dialog owes one again. */
    public static Optional<String> takeRestart() {
        String owed = restartOwed;
        restartOwed = null;
        return Optional.ofNullable(owed);
    }
}
