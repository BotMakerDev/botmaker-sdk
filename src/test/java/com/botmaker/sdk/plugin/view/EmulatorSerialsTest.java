// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.view;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmulatorSerialsTest {

    private final AtomicLong now = new AtomicLong(50_000);
    private final AtomicInteger scans = new AtomicInteger();
    private Optional<String> running = Optional.empty();

    private final EmulatorSerials serials = new EmulatorSerials(name -> {
        scans.incrementAndGet();
        return running;
    }, now::get);

    @Test
    void aMissIsAskedAgainSoonSoAStartingInstanceShows() {
        assertEquals(Optional.empty(), serials.apply("Pixel 10"));
        running = Optional.of("127.0.0.1:5555");

        now.addAndGet(EmulatorSerials.MISSED_MS - 1);
        assertEquals(Optional.empty(), serials.apply("Pixel 10"));
        assertEquals(1, scans.get());

        now.addAndGet(1);
        assertEquals(running, serials.apply("Pixel 10"));
        assertEquals(2, scans.get());
    }

    @Test
    void aFoundSerialStandsLongerThenIsLookedUpAgain() {
        running = Optional.of("127.0.0.1:5555");
        serials.apply("Pixel 10");

        now.addAndGet(EmulatorSerials.FOUND_MS - 1);
        serials.apply("Pixel 10");
        assertEquals(1, scans.get());

        running = Optional.of("127.0.0.1:5557");
        now.addAndGet(1);
        assertEquals(running, serials.apply("Pixel 10"));
    }

    @Test
    void eachNameHasItsOwnAnswer() {
        serials.apply("Pixel 10");
        serials.apply("Tablet");

        assertEquals(2, scans.get());
    }
}
