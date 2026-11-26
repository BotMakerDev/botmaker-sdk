// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.run.LiveRuns;
import com.botmaker.plugin.api.source.ManagedParam;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.Operation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceHoldsTest {

    private static final EmulatorInstance FARM = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);

    private static final Operation.Context CONTEXT = new Operation.Context() {
        @Override public void step(String words) {}
        @Override public void progress(double fraction) {}
        @Override public void needsYou(String why) {}
        @Override public void resumed() {}
        @Override public boolean cancelled() { return false; }
    };

    /** Records each hold and close in order; refuses every hold when {@code taken} is set. */
    private static final class Recorded implements LiveRuns {
        final List<String> events = new ArrayList<>();
        final String taken;

        Recorded(String taken) {
            this.taken = taken;
        }

        @Override
        public <T> Held hold(ManagedParam<T> parameter, T value, String by) throws Taken {
            if (taken != null) throw new Taken(taken);
            events.add("hold " + parameter.id() + " " + value + " by " + by);
            return () -> events.add("close");
        }
    }

    @Test
    void theWorkRunsWhileTheInstanceIsHeldAsInstanceIsDealt() throws Exception {
        Recorded runs = new Recorded(null);
        String ended = InstanceHolds.holding(runs, FARM, context -> {
            runs.events.add("work");
            return null;
        }).run(CONTEXT);

        assertNull(ended);
        assertEquals(List.of("hold " + SdkValues.PLACE.id() + " " + Place.android(FARM.reference())
                + " by the Emulators window", "work", "close"), runs.events);
    }

    @Test
    void anInstanceALiveRunPlaysOnIsLeftAloneWithTheRunsWords() throws Exception {
        Recorded runs = new Recorded("Place.android(\"Farm\") is held by a run of farm (r-1)");
        String ended = InstanceHolds.holding(runs, FARM, context -> {
            runs.events.add("work");
            return null;
        }).run(CONTEXT);

        assertTrue(runs.events.isEmpty());
        assertTrue(ended.startsWith("A bot's run plays on Farm"), ended);
        assertTrue(ended.endsWith("is held by a run of farm (r-1)"), ended);
    }

    @Test
    void aRegistryThatCannotBeReadDoesNotStopTheWork() throws Exception {
        List<String> events = new ArrayList<>();
        LiveRuns broken = new LiveRuns() {
            @Override
            public <T> Held hold(ManagedParam<T> parameter, T value, String by) {
                throw new IllegalStateException("Could not read the live runs");
            }
        };
        String ended = InstanceHolds.holding(broken, FARM, context -> {
            events.add("work");
            return null;
        }).run(CONTEXT);

        assertNull(ended);
        assertEquals(List.of("work"), events);
    }

    @Test
    void aFailedWorkStillFreesTheInstance() {
        Recorded runs = new Recorded(null);
        Operation.Body failing = InstanceHolds.holding(runs, FARM, context -> {
            throw new IllegalStateException("adb went away");
        });
        try {
            failing.run(CONTEXT);
        } catch (Exception expected) {
            // the queue reports it
        }
        assertEquals("close", runs.events.getLast());
    }
}
