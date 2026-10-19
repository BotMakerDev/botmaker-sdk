package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.shared.Diag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The bot's map read out of its {@code Screens} class: the one read the assistant's tools, 🗺 Screens, the
 * {@code Navigate} screen picker, the {@code Screens} constant view and Manage Pictures' screen groups all make. Here,
 * not in {@code assist}, so the picker and the pictures window read it without naming the assistant's code.
 *
 * <p>Each screen is a class of the set ({@code PluginValues.groups}): its setup is the class's member
 * ({@code Member.of("MARKET")}), its inputs the fields ({@code Member.of("MARKET", "beerBuy")}), each read as a value.
 */
public final class ScreenMapRead {

    private static final String HOLDER = "Screens";

    private ScreenMapRead() {}

    /**
     * The map, in the order the bot declares its screens; a field the grammar cannot read is left out, and a setup
     * it cannot read is the screen's name alone. Throws when the class will not read at all: 🗺 Screens keeps its last
     * drawing then. On the FX thread.
     */
    public static ScreenGraph of(PluginValues values) {
        long start = System.nanoTime();
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        int inputs = 0;
        if (values != null) {
            for (String constant : values.groups(SdkValues.SCREEN_MAP)) {
                screens.put(constant, new MapScreen(setup(values, constant).orElse(Setup.named(constant)),
                        Map.of()));
            }
            for (Member member : values.members(SdkValues.SCREEN_MAP)) {
                MapScreen screen = member.grouped() ? screens.get(member.group()) : null;
                if (screen == null) continue;
                inputs++;
                input(values, member).ifPresent(input ->
                        screens.put(member.group(), screens.get(member.group()).withInput(member.name(), input)));
            }
        }
        Diag.log("map read: " + screens.size() + " screens, " + inputs + " inputs in "
                + (System.nanoTime() - start) / 1_000_000 + " ms on " + Thread.currentThread().getName() + ", for "
                + askedBy());
        return ScreenGraph.of(screens);
    }

    /** The three frames that asked for a read, outside this class: which view or tool reads the map so often. */
    private static String askedBy() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(ScreenMapRead.class.getName()))
                .limit(3)
                .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "."
                        + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining(" < ")));
    }

    /** {@code constant}'s setup as the bot's class writes it; empty when there is none the grammar reads. */
    public static Optional<Setup> setup(PluginValues values, String constant) {
        return values.open(SdkValues.SCREEN_MAP, Member.of(constant)).flatMap(ctx -> ctx.value(Setup.class));
    }

    /** The input {@code member} holds; empty when there is none the grammar reads. */
    public static Optional<Input> input(PluginValues values, Member member) {
        return values.open(SdkValues.SCREEN_MAP, member).flatMap(ctx -> ctx.value(Input.class));
    }

    /**
     * Writes what differs from {@code was} to {@code now} on {@code constant}'s class: each changed field written,
     * each new one added last, then its setup when it changed, then each field gone removed. In that order a setup
     * names only fields that are there: a new way back is added before the setup names it, and a field the old setup
     * named goes once it names it no more. Empty when written, else why not. On the FX thread.
     */
    public static Optional<String> write(PluginValues values, String constant, MapScreen was, MapScreen now) {
        try {
            return written(values, constant, was, now);
        } finally {
            forget();
        }
    }

    private static Optional<String> written(PluginValues values, String constant, MapScreen was, MapScreen now) {
        Optional<String> fields = writeFields(values, constant, was, now);
        if (fields.isPresent()) return fields;
        if (!now.setup().equals(was.setup())) {
            Optional<String> refused = values.open(SdkValues.SCREEN_MAP, Member.of(constant))
                    .map(ctx -> ctx.write(now.setup()))
                    .orElse(Optional.of(HOLDER + "." + constant + "'s setup is written by hand, so it is left as it is."));
            if (refused.isPresent()) return refused;
        }
        for (String field : was.inputs().keySet()) {
            if (now.inputs().containsKey(field)) continue;
            Optional<String> refused = values.remove(SdkValues.SCREEN_MAP, Member.of(constant, field));
            if (refused.isPresent()) return refused;
        }
        return Optional.empty();
    }

    /** Each field of {@code now} that {@code was} holds otherwise written, then each new one added. */
    private static Optional<String> writeFields(PluginValues values, String constant, MapScreen was, MapScreen now) {
        Map<String, Input> added = new LinkedHashMap<>();
        for (Map.Entry<String, Input> e : now.inputs().entrySet()) {
            Input before = was.inputs().get(e.getKey());
            if (before == null) {
                added.put(e.getKey(), e.getValue());
                continue;
            }
            if (before.equals(e.getValue())) continue;
            Optional<String> refused = values.open(SdkValues.SCREEN_MAP, Member.of(constant, e.getKey()))
                    .map(ctx -> ctx.write(e.getValue()))
                    .orElse(Optional.of(HOLDER + "." + constant + "." + e.getKey() + " is written by hand, so it is "
                            + "left as it is."));
            if (refused.isPresent()) return refused;
        }
        return add(values, constant, added);
    }

    /** Declares each of {@code inputs} in {@code constant}'s class, in order. Empty when written, else why not. */
    public static Optional<String> add(PluginValues values, String constant, Map<String, Input> inputs) {
        try {
            for (Map.Entry<String, Input> e : inputs.entrySet()) {
                Optional<String> refused = addField(values, Member.of(constant, e.getKey()), e.getValue());
                if (refused.isPresent()) return refused;
            }
            return Optional.empty();
        } finally {
            forget();
        }
    }

    /** {@code member = input;}: the set is of screens, its fields of inputs, which the host checks. */
    @SuppressWarnings("unchecked")
    private static Optional<String> addField(PluginValues values, Member member, Input input) {
        return values.add((ManagedValue<Object>) (ManagedValue<?>) SdkValues.SCREEN_MAP, member, input);
    }

    /**
     * {@link #of} the project's map, kept until the bot's code changes (2026-10-19): a read of fifty screens holds the
     * FX thread 300–900 ms, and the live feed asks once a second and every {@code Screens.java} card at each draw.
     * Every change Studio makes is a history step ({@link History#onChanged}); a write here and a {@code Screens.java}
     * changed on disk drop it too ({@link #forget}). A host whose history is {@link History#NONE} says nothing of
     * changes, so nothing is kept there. A class that will not read throws, the same failure each time until it changes.
     */
    public static ScreenGraph read(StudioServices services) {
        PluginValues values = services.pluginValues();
        History history = services.history();
        if (history == History.NONE) return of(values);
        Kept read = kept;
        if (read == null || read.values() != values || read.history() != history) {
            long seen = watch(history);
            read = readKept(values, history);
            keep(seen, read);
        }
        if (read.failure() != null) throw read.failure();
        return read.graph();
    }

    private static Kept readKept(PluginValues values, History history) {
        try {
            return new Kept(values, history, of(values), null);
        } catch (RuntimeException unreadable) {
            return new Kept(values, history, null, unreadable);
        }
    }

    /** {@link #read}, or no screens when the class will not read: what a view that must draw anyway shows. */
    public static ScreenGraph orEmpty(StudioServices services) {
        if (services == null) return ScreenGraph.of(Map.of());
        try {
            return read(services);
        } catch (RuntimeException unreadable) {
            return ScreenGraph.of(Map.of());
        }
    }

    /** The last read and what it read, or why it could not: {@link #read}'s, until the bot's code changes. */
    private record Kept(PluginValues values, History history, ScreenGraph graph, RuntimeException failure) {}

    private static volatile Kept kept;
    /** Counts {@link #forget}s, so a read a change overtook is not kept. */
    private static long changes;
    private static History watched;
    private static AutoCloseable watching;

    /** Listens to {@code history} once (a project's own), and answers how many changes were seen so far. */
    private static synchronized long watch(History history) {
        if (watched != history) {
            try {
                if (watching != null) watching.close();
            } catch (Exception closed) {
                // The project it listened to is gone already.
            }
            watched = history;
            watching = history.onChanged(ScreenMapRead::forget);
        }
        return changes;
    }

    private static synchronized void keep(long seen, Kept read) {
        if (changes == seen) kept = read;
    }

    /**
     * Drops the read kept: each history step does, a write here, and 🗺 Screens when {@code Screens.java} changed on
     * disk, so the next view reads the class again.
     */
    public static synchronized void forget() {
        changes++;
        kept = null;
    }
}
