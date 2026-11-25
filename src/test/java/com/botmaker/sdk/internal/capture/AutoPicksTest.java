// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The windows runs picked for their games, kept by launch target in the values of this PC. */
class AutoPicksTest {

    private static final LaunchSpec TF2 = LaunchSpec.parse("steam:440");
    private static final LaunchSpec DOTA = LaunchSpec.parse("steam:570");

    /** A host keeping the value in memory, as the run's agent would in {@code Runs.java}. */
    private static final class Kept implements AutoPicks.Store {
        HostWindows value = HostWindows.none();

        @Override
        public HostWindows read() {
            return value;
        }

        @Override
        public Optional<String> update(UnaryOperator<HostWindows> change) {
            value = change.apply(value);
            return Optional.empty();
        }
    }

    @Test
    void savesAndReadsByTarget() {
        AutoPicks picks = new AutoPicks(new Kept());
        assertEquals(Optional.empty(), picks.save(TF2, WindowIdentity.of("hl2_linux", "Team Fortress 2")));

        NamedWindow window = assertInstanceOf(NamedWindow.class, picks.saved(TF2).orElseThrow());
        assertEquals("hl2_linux", window.windowClass());
        assertTrue(picks.saved(LaunchSpec.parse("steam:440")).isPresent(), "the same target, parsed again");
    }

    @Test
    void forgetRemovesOnlyThatTarget() {
        AutoPicks picks = new AutoPicks(new Kept());
        picks.save(TF2, WindowIdentity.of("hl2_linux", ""));
        picks.save(DOTA, WindowIdentity.of("dota2", ""));

        picks.forget(TF2);

        assertTrue(picks.saved(TF2).isEmpty());
        assertTrue(picks.saved(DOTA).isPresent());
    }

    @Test
    void twoTargetsKeptApart() {
        Kept kept = new Kept();
        AutoPicks picks = new AutoPicks(kept);
        picks.save(TF2, WindowIdentity.of("hl2_linux", ""));
        picks.save(DOTA, WindowIdentity.of("dota2", ""));
        picks.save(TF2, WindowIdentity.of("tf_linux64", ""));

        assertEquals(2, kept.value.list().size(), "a second pick replaces the game's row");
        assertEquals("tf_linux64", ((NamedWindow) picks.saved(TF2).orElseThrow()).windowClass());
        assertEquals("dota2", ((NamedWindow) picks.saved(DOTA).orElseThrow()).windowClass());
    }

    @Test
    void unreadableValueIsEmpty() {
        AutoPicks broken = new AutoPicks(new AutoPicks.Store() {
            @Override
            public HostWindows read() {
                throw new IllegalStateException("no host");
            }

            @Override
            public Optional<String> update(UnaryOperator<HostWindows> change) {
                return Optional.of("no host");
            }
        });
        assertTrue(broken.saved(TF2).isEmpty(), "nothing read is nothing saved: the launch is watched");
        assertTrue(AutoPicks.shown(null).saved(TF2).isEmpty());
        assertTrue(AutoPicks.shown(HostWindows.of(new HostWindows.Row("steam:440", ""))).saved(TF2).isEmpty(),
                "a row with no window is none");
        assertTrue(AutoPicks.shown(HostWindows.none()).save(TF2, WindowIdentity.of("x", "")).isPresent(),
                "Studio's reading keeps nothing");
    }
}
