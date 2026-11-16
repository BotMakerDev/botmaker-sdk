// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.plan;

import com.botmaker.plugin.api.managed.RunHostValues;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.screens.Conditions;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * When each {@code Do} was last done and whether its check held, keyed {@code <Screen constant>.<field>}: what runs
 * learned of a game, kept in the SDK's values of this PC ({@link #VALUE}, 2026-11-15), one set per instance and game
 * ({@link DoneKey}), so every bot that plays a game on an instance reads the same dailies, and two runs on two
 * instances never mix theirs. Never in a bot, versioned or published. Nothing kept is no record yet, and a value
 * that cannot be written leaves the run's own memory of it: neither ends a run.
 */
public final class DoneRecords {

    /** The SDK's coordinate, which names its state folder. */
    public static final String GROUP = "com.github.BotMakerDev";
    public static final String ARTIFACT = "botmaker-sdk";

    /** Every instance's records, in {@code Runs.java} of the SDK's folder of this PC. */
    public static final ManagedMethod<HostDone> VALUE = ManagedValue.method(SdkRunValue.Id.DONE)
            .in(SdkRunValue.Holder.RUNS)
            .holds(HostDone.class, HostDone.none())
            .because("These are when each action was last done in a game on an instance, and whether it held."
                    + " The bots' runs write them.");

    /**
     * An action's last time.
     *
     * @param at    when it was done
     * @param held  whether its check held
     * @param fails how many times in a row it has not, up to this one
     */
    public record Done(Instant at, boolean held, int fails) {}

    /** Where records are kept: read once, written whole on each change. */
    public interface Store {
        Map<String, Done> read();

        void write(Map<String, Done> all);
    }

    private static final class Run {
        // Read on a run's first walk, after the bot's parameters put its capture on its instance; only a Do done
        // writes it. Timed in the game server's zone, which a reset with none of its own is read in.
        static final DoneRecords RECORDS = new DoneRecords(
                new HostStore(DoneKey.runInstance(), DoneKey.runGame()), new GameClock());
    }

    /**
     * The system clock in the game server's zone as the bot's parameters say it now ({@link InstalledGame#gameZone}),
     * read on each use: records made before the parameters were installed, or kept across a second run in one JVM,
     * still read the zone that run set.
     */
    private static final class GameClock extends Clock {
        @Override
        public ZoneId getZone() {
            return InstalledGame.gameZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.system(zone);
        }

        @Override
        public Instant instant() {
            return Instant.now();
        }
    }

    private final Store store;
    private final Clock clock;
    private final Map<String, Done> done;
    /** What is being done now, which a walk under it leaves alone. */
    private final Set<Do> doing = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The records {@code store} keeps, each new one timed by {@code clock}. */
    public DoneRecords(Store store, Clock clock) {
        this.store = store;
        this.clock = clock;
        this.done = new LinkedHashMap<>(store == null ? Map.of() : store.read());
    }

    /** The running bot's. */
    public static DoneRecords run() {
        return Run.RECORDS;
    }

    /** {@code instance}'s records of {@code game} in {@code kept}, as Studio reads them; written nowhere. */
    public static DoneRecords shown(HostDone kept, String instance, String game, Clock clock) {
        Map<String, Done> read = rows(kept, instance, game);
        return new DoneRecords(new Store() {
            @Override
            public Map<String, Done> read() {
                return read;
            }

            @Override
            public void write(Map<String, Done> all) {
                // Studio shows them; only a run records.
            }
        }, clock);
    }

    /** No records, kept nowhere: what a check that only reads the map plans with. */
    public static DoneRecords none() {
        return new DoneRecords(null, Clock.systemDefaultZone());
    }

    /** The key of the action {@code field} of the screen {@code constant}. */
    public static String key(String constant, String field) {
        return constant + "." + field;
    }

    /** {@code key}'s last time; empty when it was never done. */
    public synchronized Optional<Done> last(String key) {
        return Optional.ofNullable(done.get(key));
    }

    /** Records {@code key} done now, its check having {@code held} or not, and keeps it. */
    public void done(String key, boolean held) {
        done(key, held, clock.instant());
    }

    /** Records {@code key} done {@code at}, its check having {@code held} or not, and keeps it. */
    public synchronized void done(String key, boolean held, Instant at) {
        int fails = held ? 0 : last(key).map(Done::fails).orElse(0) + 1;
        done.put(key, new Done(at, held, fails));
        if (store != null) store.write(Map.copyOf(done));
    }

    /**
     * Records {@code key}, the field holding {@code action}, pressed: it held when its check does not say no; with no
     * check, when the game went {@code asTheMapSays}.
     */
    public void pressed(String key, Do action, boolean asTheMapSays) {
        pressed(key, action, asTheMapSays, clock.instant());
    }

    /** {@link #pressed(String, Do, boolean)}, the press made {@code at}: one proven after it is timed by the press. */
    public void pressed(String key, Do action, boolean asTheMapSays, Instant at) {
        done(key, action.checks() == null ? asTheMapSays : Conditions.holds(action.checks()), at);
    }

    /** Takes {@code action} as being done now, this very value; false when it is already. */
    public synchronized boolean start(Do action) {
        return doing.add(action);
    }

    /** {@code action} is no more being done. */
    public synchronized void finish(Do action) {
        doing.remove(action);
    }

    /** The clock these records are timed by, whose zone a time of day is read in. */
    public Clock clock() {
        return clock;
    }

    /** {@code instance}'s records of {@code game} in {@code kept}; a row whose time does not read is skipped. */
    static Map<String, Done> rows(HostDone kept, String instance, String game) {
        Map<String, Done> read = new LinkedHashMap<>();
        if (kept == null) return read;
        for (HostDone.Row row : kept.list()) {
            if (!row.of(instance, game) || row.action().isEmpty()) continue;
            try {
                read.put(row.action(), new Done(Instant.parse(row.at()), row.held(), row.fails()));
            } catch (DateTimeException unreadable) {
                // Not a record: as if never done.
            }
        }
        return read;
    }

    /** {@code kept} with {@code instance}'s rows of {@code game} replaced by {@code all}; every other row as it was. */
    static HostDone replaced(HostDone kept, String instance, String game, Map<String, Done> all) {
        List<HostDone.Row> rows = new ArrayList<>();
        if (kept != null) kept.list().stream().filter(row -> !row.of(instance, game)).forEach(rows::add);
        all.forEach((action, d) -> rows.add(new HostDone.Row(instance, game, action, d.at().toString(), d.held(),
                d.fails())));
        return new HostDone(rows);
    }

    /**
     * A run's records, in the SDK's values of this PC ({@link RunHostValues}): read whole, and each write puts back
     * only this instance's rows of this game, over what another run may have written since.
     */
    static final class HostStore implements Store {

        private final String instance;
        private final String game;
        /** Whether a refused write was said already, so a run says it once. */
        private final AtomicBoolean said = new AtomicBoolean();

        HostStore(String instance, String game) {
            this.instance = instance;
            this.game = game;
        }

        @Override
        public Map<String, Done> read() {
            return rows(RunHostValues.read(VALUE).orElse(null), instance, game);
        }

        @Override
        public synchronized void write(Map<String, Done> all) {
            RunHostValues.update(VALUE, kept -> replaced(kept, instance, game, all)).ifPresent(why -> {
                if (!said.getAndSet(true)) System.err.println("What this run did is not kept on this PC: " + why);
            });
        }
    }
}
