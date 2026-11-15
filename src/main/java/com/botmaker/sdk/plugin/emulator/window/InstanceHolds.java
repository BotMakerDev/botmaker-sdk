package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.run.LiveRuns;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.Operation;

/**
 * Setup holds the instance it works on (spec {@code 2026-11-15-run-claims-design.md} §5, phase 8b): an operation of
 * the Emulators window on an existing instance (Apply, its Google sign-in included, clone, rename, delete) holds it in
 * this PC's live runs while it runs, as the instance {@code instance()} is dealt, so a start of several runs is dealt
 * another meanwhile; and it ends at once, saying so, on one a live run plays on.
 */
final class InstanceHolds {

    /** Who holds an instance, as a refused start of several runs names it. */
    static final String HOLDER = "the Emulators window";

    private InstanceHolds() {}

    /** {@code body}, run holding {@code instance} in {@code runs}; an instance with no name holds nothing. */
    static Operation.Body holding(LiveRuns runs, EmulatorInstance instance, Operation.Body body) {
        return context -> {
            String name = instance.reference();
            if (name == null || name.isBlank()) return body.run(context);
            LiveRuns.Held held;
            try {
                held = runs.hold(SdkValues.INSTANCE, Instance.named(name), HOLDER);
            } catch (LiveRuns.Taken e) {
                return "A bot's run plays on " + instance.name() + ", so it was left alone: " + e.getMessage();
            } catch (RuntimeException e) {
                // A registry that cannot be read must not stop setup: the work goes on, unheld, and says so.
                SdkLog.warn("The live runs could not be checked, so " + instance.name()
                        + " is not held from bots' runs while the Emulators window works on it: " + e.getMessage());
                return body.run(context);
            }
            try (held) {
                return body.run(context);
            }
        };
    }
}
