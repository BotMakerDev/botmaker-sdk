// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.plan;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Records kept in memory by a test's own path, so a second {@code DoneRecords} at one path reads what the first did. */
public final class DoneStores {

    private static final Map<Path, Map<String, DoneRecords.Done>> KEPT = new ConcurrentHashMap<>();

    private DoneStores() {}

    /** The records kept at {@code at}, each new one timed by {@code clock}. */
    public static DoneRecords at(Path at, Clock clock) {
        Path key = at.toAbsolutePath().normalize();
        return new DoneRecords(new DoneRecords.Store() {
            @Override
            public Map<String, DoneRecords.Done> read() {
                return KEPT.getOrDefault(key, Map.of());
            }

            @Override
            public void write(Map<String, DoneRecords.Done> all) {
                KEPT.put(key, Map.copyOf(all));
            }
        }, clock);
    }
}
