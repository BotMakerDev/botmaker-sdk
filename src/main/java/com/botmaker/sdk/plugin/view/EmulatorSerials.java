// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * An emulator's ADB serial by its name, remembered for a while: the lookup behind it scans every emulator product on
 * this PC, and the host asks a view for its target several times a second.
 */
final class EmulatorSerials implements Function<String, Optional<String>> {

    /** How long a serial found is believed: a restarted instance may come back on another port. */
    static final long FOUND_MS = 10_000;

    /** How long a miss is believed: an instance that is starting should be shown soon. */
    static final long MISSED_MS = 2_000;

    private record Answer(Optional<String> serial, long at) {

        boolean stands(long now) {
            return now - at < (serial.isPresent() ? FOUND_MS : MISSED_MS);
        }
    }

    private final Function<String, Optional<String>> lookup;
    private final LongSupplier clock;
    private final Map<String, Answer> answers = new HashMap<>();

    EmulatorSerials(Function<String, Optional<String>> lookup) {
        this(lookup, () -> System.nanoTime() / 1_000_000);
    }

    EmulatorSerials(Function<String, Optional<String>> lookup, LongSupplier clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    @Override
    public synchronized Optional<String> apply(String name) {
        Answer known = answers.get(name);
        if (known != null && known.stands(clock.getAsLong())) return known.serial();
        Optional<String> serial = lookup.apply(name);
        // Stamped after the scan, which may itself take longer than a miss is believed.
        answers.put(name, new Answer(serial, clock.getAsLong()));
        return serial;
    }
}
