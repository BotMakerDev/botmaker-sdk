package com.botmaker.sdk.internal.state;

import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.state.Fact;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.state.StateFile.Entry;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.sdk.internal.trace.SdkRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The running bot's game facts ({@code GameState}): the {@link Fact} constants of its {@code Facts} class, beside
 * {@code Sdk}, and their readings, kept by {@link StateFile} per launch target and, once the account fact is read,
 * per account. Read once per run, written after each change. A file that does not read leaves every fact unknown,
 * said once; the run goes on.
 */
public final class KeptFacts {

    /** The class the fact constants live in, beside {@code Sdk}. */
    public static final String HOLDER = "Facts";

    /** Reads readouts of the screen the game shows now. */
    @FunctionalInterface
    public interface Reader {
        /**
         * The readouts {@code fields} of the screen {@code constant}, read off one look, by field; a field read as
         * nothing is left out. Empty when that look does not show the screen.
         */
        Optional<Map<String, String>> read(String constant, List<String> fields);
    }

    /** The bot's facts, each by its constant's name, and in the order the bot declares them, the account first. */
    private static Map<Fact<?>, String> names = new IdentityHashMap<>();
    private static List<Fact<?>> order = List.of();
    private static Path sdkState;
    private static Supplier<String> target = () -> null;
    private static LongSupplier clock = System::currentTimeMillis;
    private static Reader reader = KeptFacts::readNow;

    /** Loaded on first use, for the target the run had then: its file, and the account's once its fact is known. */
    private static boolean loaded;
    private static String loadedTarget;
    private static Map<String, Entry> targetEntries;
    private static Map<String, Entry> accountEntries;
    private static String account;
    private static boolean warned;
    private static final Set<Fact<?>> UNNAMED = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Set while {@link #seen} reads, so a read that sees its screen again reads nothing more. */
    private static final ThreadLocal<Boolean> READING = ThreadLocal.withInitial(() -> false);

    private KeptFacts() {}

    /**
     * Learns the running bot's facts: the {@code Facts} class beside the first of {@code holders} that has one, kept
     * in the bot's folder (the run's working directory) for the run's launch target. Read whenever the run sees a
     * screen ({@link ScreenMaps#onSeen}).
     */
    public static void install(Class<?>... holders) {
        Map<String, Fact<?>> found = new LinkedHashMap<>();
        for (Class<?> holder : holders == null ? new Class<?>[0] : holders) {
            Optional<Class<?>> facts = beside(holder);
            if (facts.isEmpty()) continue;
            found = read(facts.get());
            break;
        }
        use(found, Path.of(System.getProperty("user.dir", ".")), KeptFacts::targetNow, System::currentTimeMillis,
                KeptFacts::readNow);
    }

    /**
     * Uses {@code facts}, in their order, kept under the bot at {@code botDir} for {@code target}, timed by {@code now}.
     * Tests too.
     */
    public static synchronized void use(Map<String, Fact<?>> facts, Path botDir, Supplier<String> target,
                                        LongSupplier now, Reader reader) {
        Map<Fact<?>, String> named = new IdentityHashMap<>();
        List<Fact<?>> ordered = new ArrayList<>();
        facts.forEach((name, fact) -> {
            named.put(fact, name);
            if (fact.isAccount()) ordered.addFirst(fact);
            else ordered.add(fact);
        });
        names = named;
        order = List.copyOf(ordered);
        sdkState = StateFile.folder(botDir);
        KeptFacts.target = target;
        clock = now;
        KeptFacts.reader = reader;
        loaded = false;
        warned = false;
        UNNAMED.clear();
        ScreenMaps.onSeen(named.isEmpty() ? null : KeptFacts::seen);
    }

    private static Optional<Class<?>> beside(Class<?> holder) {
        if (holder == null) return Optional.empty();
        try {
            return Optional.of(Class.forName(holder.getPackageName() + "." + HOLDER, true, holder.getClassLoader()));
        } catch (ClassNotFoundException none) {
            return Optional.empty();
        }
    }

    /** {@code holder}'s {@code static final} {@link Fact} constants, in the order it declares them. */
    static Map<String, Fact<?>> read(Class<?> holder) {
        Map<String, Fact<?>> out = new LinkedHashMap<>();
        for (Field field : holder.getDeclaredFields()) {
            int m = field.getModifiers();
            if (!Fact.class.isAssignableFrom(field.getType()) || !Modifier.isStatic(m) || !Modifier.isFinal(m)) continue;
            try {
                field.setAccessible(true);
                if (field.get(null) instanceof Fact<?> fact) out.put(field.getName(), fact);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
                // A fact that cannot be read is not kept: conditions on it stay unknown.
            }
        }
        return out;
    }

    /**
     * The value {@code fact} holds: its fresh reading; its assumed value while no reading is kept (or the kept one no
     * longer reads); empty when its reading is stale.
     */
    public static synchronized <T> Optional<T> get(Fact<T> fact) {
        Optional<Entry> kept = entry(fact);
        if (kept.isEmpty()) return fact.assumed();
        Optional<T> value = decode(fact, kept.get().value());
        if (value.isEmpty()) return fact.assumed();
        return stale(fact, kept.get()) ? Optional.empty() : value;
    }

    /** Keeps {@code value} as {@code fact}'s reading, set by a step. */
    public static synchronized <T> void set(Fact<T> fact, T value) {
        keep(fact, value, null, null);
    }

    /** Goes to {@code fact}'s screen and reads it there, unless arriving read it already. */
    public static <T> Optional<T> refresh(Fact<T> fact) {
        if (fact.screen().isEmpty()) return get(fact);
        ScreenGraph map = ScreenMaps.installed();
        Optional<Screen> screen = map.screens().keySet().stream().flatMap(c -> map.instance(c).stream())
                .filter(s -> fact.screen().get().isInstance(s)).findFirst();
        if (screen.isEmpty() || !Navigate.to(screen.get())) return Optional.empty();
        Optional<String> constant = map.constantOf(screen.get());
        if (constant.isPresent() && !fresh(fact)) readOn(map, constant.get(), screen.get(), List.of(fact));
        return fresh(fact) ? get(fact) : Optional.empty();
    }

    /** Drops {@code fact}'s reading. */
    public static synchronized void forget(Fact<?> fact) {
        String name = nameOf(fact);
        if (name == null) return;
        load();
        Map<String, Entry> entries = entriesOf(fact);
        if (entries.remove(name) == null) return;
        save(fileOf(fact), entries);
        if (fact.isAccount()) switchAccount(null);
    }

    /** Drops every reading of the account in play; the account itself stays known. */
    public static synchronized void forgetAll() {
        load();
        Fact<?> accountFact = accountFact();
        Map<String, Entry> entries = account == null ? targetEntries : accountEntries;
        String kept = accountFact == null ? null : names.get(accountFact);
        if (!entries.keySet().removeIf(name -> !name.equals(kept))) return;
        save(account == null ? targetFile() : accountFile(account), entries);
    }

    /**
     * The game was just seen on {@code constant}: each fact read on that screen whose reading is stale or missing is
     * read now, all off one look of the screen as it shows, the account first and always. A fresh fact is not read
     * again. A fact that cannot be read is left as it was; only a stop goes on.
     */
    public static void seen(String constant) {
        if (READING.get()) return;
        ScreenGraph map = ScreenMaps.installed();
        Optional<Screen> screen = map.instance(constant);
        if (screen.isEmpty()) return;
        List<Fact<?>> facts;
        synchronized (KeptFacts.class) {
            facts = order;
        }
        List<Fact<?>> here = facts.stream().filter(f -> f.kind() != Fact.Kind.FLAG
                && f.screen().map(c -> c.isInstance(screen.get())).orElse(false)).toList();
        // The account first, on its own look: which account plays decides which readings the others' freshness is of.
        List<Fact<?>> account = here.stream().filter(Fact::isAccount).toList();
        if (!account.isEmpty()) readOn(map, constant, screen.get(), account);
        List<Fact<?>> due = here.stream().filter(f -> !f.isAccount() && !fresh(f)).toList();
        if (!due.isEmpty()) readOn(map, constant, screen.get(), due);
    }

    /** Reads {@code facts} of {@code screen}, held by {@code constant}, off one look, and keeps what reads. */
    private static void readOn(ScreenGraph map, String constant, Screen screen, List<Fact<?>> facts) {
        READING.set(true);
        try {
            Map<Fact<?>, String> fields = new LinkedHashMap<>();
            for (Fact<?> fact : facts) {
                try {
                    fact.readoutOf(screen).flatMap(r -> fieldOf(map, r)).ifPresent(field -> fields.put(fact, field));
                } catch (RuntimeException badReadout) {
                    // The bot's lambda for this fact threw (a screen reshaped): this fact stays as it was.
                    if (FlowWalker.isStop(badReadout)) throw badReadout;
                    Debug.log("A fact on " + constant + " names no readout: " + badReadout);
                }
            }
            if (fields.isEmpty()) return;
            Optional<Map<String, String>> read = reader.read(constant, fields.values().stream().distinct().toList());
            if (read.isEmpty()) return;
            fields.forEach((fact, field) -> keepRead(fact, constant, field, read.get().get(field)));
        } catch (RuntimeException unreadable) {
            for (Throwable at = unreadable; at != null; at = at.getCause() == at ? null : at.getCause()) {
                if (FlowWalker.isStop(at)) throw (RuntimeException) at;
            }
            Debug.log("The facts on " + constant + " were not read: " + unreadable);
        } finally {
            READING.set(false);
        }
    }

    private static <T> void keepRead(Fact<T> fact, String constant, String field, String text) {
        parse(fact, Optional.ofNullable(text)).ifPresent(v -> {
            synchronized (KeptFacts.class) {
                keep(fact, v, constant, field);
            }
        });
    }

    private static Optional<String> fieldOf(ScreenGraph map, Readout readout) {
        return map.nameOf(readout).map(n -> n.field());
    }

    /** Whether {@code fact} holds a reading that is young enough and still reads as its type. */
    private static synchronized boolean fresh(Fact<?> fact) {
        Optional<Entry> kept = entry(fact);
        return kept.isPresent() && !stale(fact, kept.get()) && decode(fact, kept.get().value()).isPresent();
    }

    private static boolean stale(Fact<?> fact, Entry entry) {
        return fact.staleAfter().map(d -> clock.getAsLong() - entry.at() > d.toMillis()).orElse(false);
    }

    private static Optional<Entry> entry(Fact<?> fact) {
        String name = nameOf(fact);
        if (name == null) return Optional.empty();
        load();
        return Optional.ofNullable(entriesOf(fact).get(name));
    }

    /** {@code fact}'s constant name; null, said once, for a fact that is no constant of the bot's {@code Facts}. */
    private static String nameOf(Fact<?> fact) {
        String name = names.get(fact);
        if (name == null && UNNAMED.add(fact)) {
            Say.warn("A fact that is not a constant of the bot's " + HOLDER + " class is not kept: name it there, as "
                    + "it is, without a call after it.");
        }
        return name;
    }

    private static <T> void keep(Fact<T> fact, T value, String screen, String readout) {
        String name = nameOf(fact);
        if (name == null || value == null) return;
        load();
        Map<String, Entry> entries = entriesOf(fact);
        entries.put(name, new Entry(encode(value), clock.getAsLong(), screen, readout, runId()));
        save(fileOf(fact), entries);
        if (fact.isAccount()) switchAccount(value.toString());
    }

    private static void switchAccount(String now) {
        if (Objects.equals(now, account)) return;
        account = now;
        accountEntries = now == null ? targetEntries : readFile(accountFile(now));
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        loadedTarget = target.get();
        targetEntries = readFile(targetFile());
        account = null;
        accountEntries = targetEntries;
        Fact<?> accountFact = accountFact();
        if (accountFact == null) return;
        Entry e = targetEntries.get(names.get(accountFact));
        if (e != null && e.value().isTextual()) switchAccount(e.value().asText());
    }

    private static Fact<?> accountFact() {
        return order.isEmpty() || !order.getFirst().isAccount() ? null : order.getFirst();
    }

    private static Map<String, Entry> entriesOf(Fact<?> fact) {
        return fact.isAccount() || account == null ? targetEntries : accountEntries;
    }

    private static Path fileOf(Fact<?> fact) {
        return fact.isAccount() || account == null ? targetFile() : accountFile(account);
    }

    private static Path targetFile() {
        return StateFile.game(sdkState, loadedTarget, null);
    }

    private static Path accountFile(String account) {
        return StateFile.game(sdkState, loadedTarget, account.toLowerCase(Locale.ROOT));
    }

    private static Map<String, Entry> readFile(Path file) {
        Optional<Map<String, Entry>> read = StateFile.read(file);
        if (read.isEmpty()) warnOnce("The game state in " + file + " could not be read: every fact is unknown until "
                + "the game shows it again.");
        return new LinkedHashMap<>(read.orElse(Map.of()));
    }

    private static void save(Path file, Map<String, Entry> entries) {
        try {
            StateFile.write(file, entries);
        } catch (IOException | RuntimeException unwritable) {
            warnOnce("The game state could not be kept in " + file + ": " + unwritable.getMessage());
        }
    }

    private static void warnOnce(String message) {
        if (warned) return;
        warned = true;
        Say.warn(message);
    }

    private static String runId() {
        return SdkRun.channel().folder().map(p -> p.getFileName().toString()).orElse(null);
    }

    private static String targetNow() {
        LaunchTarget t = Target.current();
        return t == null ? null : t.spec();
    }

    private static Optional<Map<String, String>> readNow(String constant, List<String> fields) {
        return new ScreenWalk(ScreenMaps.installed(), new RunReach(), ScreenWalk.STEP_MS, null).readAll(constant, fields);
    }

    /** {@code value} as the file keeps it: a number, a boolean, or a text (an enum's constant by its name). */
    private static JsonNode encode(Object value) {
        if (value instanceof Integer i) return IntNode.valueOf(i);
        if (value instanceof Boolean b) return BooleanNode.valueOf(b);
        if (value instanceof Enum<?> e) return TextNode.valueOf(e.name());
        return TextNode.valueOf(value.toString());
    }

    /** What the file kept as {@code fact}'s type; empty when it does not read as one. */
    static <T> Optional<T> decode(Fact<T> fact, JsonNode value) {
        Object out = switch (fact.kind()) {
            case NUMBER -> value.isInt() ? value.intValue() : null;
            case SHOWN, FLAG -> value.isBoolean() ? value.booleanValue() : null;
            case TEXT -> value.isTextual() ? value.asText() : null;
            case CHOICE -> value.isTextual() ? constant(fact.type(), value.asText(), false) : null;
            case UNKNOWN -> null;
        };
        return Optional.ofNullable(out).map(fact.type()::cast);
    }

    /**
     * The text a readout showed on its screen, as {@code fact}'s type; empty when it reads as none. A shown fact is
     * whether anything was read: the look showed the screen, so nothing read is its element not there.
     */
    static <T> Optional<T> parse(Fact<T> fact, Optional<String> text) {
        Object out = switch (fact.kind()) {
            case SHOWN -> text.isPresent();
            case FLAG, UNKNOWN -> null;
            case NUMBER -> text.map(t -> t.replaceAll("\\D", "")).filter(d -> !d.isEmpty()).map(KeptFacts::integer)
                    .orElse(null);
            case TEXT -> text.map(String::strip).filter(t -> !t.isEmpty()).orElse(null);
            case CHOICE -> text.map(t -> constant(fact.type(), t, true)).orElse(null);
        };
        return Optional.ofNullable(out).map(fact.type()::cast);
    }

    private static Integer integer(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException tooLong) {
            return null;
        }
    }

    /** {@code type}'s constant named {@code text}; read loosely ({@code "Fire mage"} is {@code FIRE_MAGE}) when shown. */
    private static Object constant(Class<?> type, String text, boolean shown) {
        String wanted = shown ? text.strip().replaceAll("[^\\p{Alnum}]+", "_").replaceAll("^_|_$", "") : text;
        for (Object c : type.getEnumConstants()) {
            String name = ((Enum<?>) c).name();
            if (shown ? name.equalsIgnoreCase(wanted) : name.equals(wanted)) return c;
        }
        return null;
    }
}
