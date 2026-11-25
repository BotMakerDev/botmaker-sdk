// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.plugin.api.managed.RunHostValues;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.plan.SdkRunValue;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * The window a run picked for a desktop game when its capture is automatic (2026-11-25, spec
 * {@code 2026-11-25-auto-capture-design.md}), kept by launch target in the SDK's values of this PC ({@link #VALUE}):
 * a window's class is a fact of this PC, which every bot that launches the game reads, and a run writes values of this
 * PC, never a parameter that is not during-a-run. Nothing kept means the next launch is watched; a value that cannot
 * be read or written means the same, and neither ends a run.
 */
public final class AutoPicks {

    /** Every game's window, in {@code Runs.java} of the SDK's folder of this PC. */
    public static final ManagedMethod<HostWindows> VALUE = ManagedValue.method(SdkRunValue.Id.WINDOWS)
            .in(SdkRunValue.Holder.RUNS)
            .holds(HostWindows.class, HostWindows.none())
            .because("These are the windows the bots' runs picked for their games on this PC.");

    /** Where the picks are kept. */
    public interface Store {
        HostWindows read();

        /** Applies {@code change} to what is kept; empty when it was, else why not. */
        Optional<String> update(UnaryOperator<HostWindows> change);
    }

    private static final class Run {
        static final AutoPicks PICKS = new AutoPicks(new HostStore());
    }

    private final Store store;

    public AutoPicks(Store store) {
        this.store = store;
    }

    /** The running bot's, in the values of this PC its host serves. */
    public static AutoPicks run() {
        return Run.PICKS;
    }

    /** The picks in {@code kept}, as Studio reads them; written nowhere. */
    public static AutoPicks shown(HostWindows kept) {
        HostWindows read = kept == null ? HostWindows.none() : kept;
        return new AutoPicks(new Store() {
            @Override
            public HostWindows read() {
                return read;
            }

            @Override
            public Optional<String> update(UnaryOperator<HostWindows> change) {
                return Optional.of("Studio shows the picks; only a run makes one.");
            }
        });
    }

    /** The window picked for {@code target}; empty when none is, or what is kept does not read as a window. */
    public Optional<CaptureSource> saved(LaunchSpec target) {
        return picked(target).map(NamedWindow::of);
    }

    /** The identity of the window picked for {@code target}; empty as {@link #saved} is. */
    public Optional<WindowIdentity> picked(LaunchSpec target) {
        if (target == null) return Optional.empty();
        try {
            return read().window(target.spec()).map(WindowIdentity::parse);
        } catch (RuntimeException notAWindow) {
            // A row a newer BotMaker or a hand wrote that does not read: as if none, so the launch is watched.
            return Optional.empty();
        }
    }

    /** Keeps {@code window} as {@code target}'s pick; empty when kept, else why not. */
    public Optional<String> save(LaunchSpec target, WindowIdentity window) {
        if (target == null || window == null) return Optional.of("no game or no window to keep");
        return store.update(kept -> orNone(kept).with(target.spec(), window.spec()));
    }

    /** Forgets {@code target}'s pick, so its next launch is watched again; every other game's is kept. */
    public Optional<String> forget(LaunchSpec target) {
        if (target == null) return Optional.empty();
        return store.update(kept -> orNone(kept).with(target.spec(), null));
    }

    private HostWindows read() {
        try {
            return orNone(store.read());
        } catch (RuntimeException unreadable) {
            return HostWindows.none();
        }
    }

    private static HostWindows orNone(HostWindows kept) {
        return kept == null ? HostWindows.none() : kept;
    }

    /** A run's picks, in the SDK's values of this PC ({@link RunHostValues}); each write changes one game's row. */
    static final class HostStore implements Store {

        /** Whether a refused write was said already, so a run says it once. */
        private final AtomicBoolean said = new AtomicBoolean();

        @Override
        public HostWindows read() {
            return RunHostValues.read(VALUE).orElse(HostWindows.none());
        }

        @Override
        public Optional<String> update(UnaryOperator<HostWindows> change) {
            Optional<String> refused = RunHostValues.update(VALUE, change);
            refused.ifPresent(why -> {
                if (!said.getAndSet(true)) System.err.println("The game's window is not kept on this PC: " + why);
            });
            return refused;
        }
    }
}
