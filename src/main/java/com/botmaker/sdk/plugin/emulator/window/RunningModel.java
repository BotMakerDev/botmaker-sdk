package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.InstanceKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the Running tab shows, with no JavaFX: the cards and their groups, each running instance's live reading, the
 * host's memory headroom, and where a click on a shown screen lands on the instance.
 */
final class RunningModel {

    private RunningModel() {}

    /** How the cards are laid out. */
    enum Grouping {
        ALL("All together"),
        BY_EMULATOR("By emulator");

        private final String displayName;

        Grouping(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    // ---- the cards ----

    /**
     * One card: a running instance, or one a bot expects that is stopped.
     *
     * @param bot the open bot when it runs on this instance, else null
     */
    record Card(EmulatorsModel.InstanceRow row, boolean expected, String bot) {

        InstanceKey key() {
            return row.key();
        }

        /** What an expected card says. */
        String expectedLine() {
            return String.join(", ", row.usedBy()) + (row.usedBy().size() == 1 ? " targets it" : " target it")
                    + "; it is not running.";
        }
    }

    /** A group's title, the cards in it, and the running ones its Stop all stops. */
    record Group(String title, PlatformId platform, List<Card> cards) {

        Group {
            cards = List.copyOf(cards);
        }

        List<EmulatorsModel.InstanceRow> running() {
            return cards.stream().filter(card -> !card.expected()).map(Card::row).toList();
        }
    }

    /**
     * The groups to draw: every running instance, then every stopped one a bot targets ("expected, not running"), each
     * part by emulator then name. {@code botInstance} is the reference the open bot runs on, or null while none runs;
     * a launch target names an instance by name alone, so the bot is shown only when one running instance has it.
     */
    static List<Group> groups(EmulatorsModel.Snapshot snapshot, Grouping grouping, String bot, String botInstance) {
        Comparator<EmulatorsModel.InstanceRow> order = Comparator
                .comparing((EmulatorsModel.InstanceRow row) -> row.key().platform().displayName(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> row.instance().name(), String.CASE_INSENSITIVE_ORDER);
        List<EmulatorsModel.InstanceRow> running = snapshot.instances().stream()
                .filter(EmulatorsModel.InstanceRow::running).sorted(order).toList();
        List<EmulatorsModel.InstanceRow> botOn = bot == null ? List.of() : running.stream()
                .filter(row -> row.instance().reference().equals(botInstance)).toList();
        List<Card> cards = new ArrayList<>();
        running.forEach(row -> cards.add(new Card(row, false, botOn.size() == 1 && botOn.getFirst() == row ? bot
                : null)));
        snapshot.instances().stream().filter(row -> !row.running() && !row.usedBy().isEmpty()).sorted(order)
                .forEach(row -> cards.add(new Card(row, true, null)));
        if (grouping == Grouping.ALL) {
            return cards.isEmpty() ? List.of() : List.of(new Group(title("All instances", cards), null, cards));
        }
        Map<PlatformId, List<Card>> by = new LinkedHashMap<>();
        cards.stream().sorted(Comparator.comparing((Card card) -> card.key().platform().displayName(),
                        String.CASE_INSENSITIVE_ORDER).thenComparing(Card::expected))
                .forEach(card -> by.computeIfAbsent(card.key().platform(), p -> new ArrayList<>()).add(card));
        List<Group> out = new ArrayList<>();
        by.forEach((platform, own) -> out.add(new Group(title(platform.displayName(), own), platform, own)));
        return out;
    }

    private static String title(String name, List<Card> cards) {
        long running = cards.stream().filter(card -> !card.expected()).count();
        long expected = cards.size() - running;
        return name + " · " + running + " running" + (expected > 0 ? ", " + expected + " expected" : "");
    }

    // ---- the host's memory ----

    /**
     * What Windows can still give, against what the running instances hold.
     *
     * @param availableMb what Windows can still commit, or -1 when unknown
     * @param heldMb      the memory the running instances asked for, as far as it was read
     */
    record Headroom(long availableMb, long heldMb) {

        static Headroom of(long availableMb, EmulatorsModel.Snapshot snapshot) {
            long held = snapshot.instances().stream().filter(EmulatorsModel.InstanceRow::running)
                    .mapToLong(RunningModel::memoryMb).sum();
            return new Headroom(availableMb, held);
        }

        boolean known() {
            return availableMb >= 0;
        }

        /** The line above the cards. */
        String line() {
            String held = heldMb > 0 ? "The running instances hold " + heldMb + " MB. " : "";
            return known() ? held + "Windows can still give " + availableMb + " MB." : held
                    + "How much memory Windows can still give is not known here.";
        }

        /** Why starting {@code rows} together would fail; each one's ask is added to the last. Empty when unknown. */
        Optional<String> warning(List<EmulatorsModel.InstanceRow> rows) {
            if (!known()) return Optional.empty();
            long left = availableMb;
            List<String> over = new ArrayList<>();
            for (EmulatorsModel.InstanceRow row : rows) {
                long asks = memoryMb(row);
                if (asks <= 0) continue;
                if (asks > left) over.add(row.instance().name() + " (" + asks + " MB)");
                left -= asks;
            }
            if (over.isEmpty()) return Optional.empty();
            return Optional.of("Windows can still give " + availableMb + " MB; " + String.join(", ", over)
                    + (over.size() == 1 ? " does" : " do") + " not fit after the others. Start fewer, or stop another"
                    + " instance first.");
        }
    }

    /** The memory an instance asks for, as last read; 0 when not read. */
    static long memoryMb(EmulatorsModel.InstanceRow row) {
        return row.now(ConfigField.MEMORY).filter(ConfigValue.Megabytes.class::isInstance)
                .map(value -> (long) ((ConfigValue.Megabytes) value).value()).orElse(0L);
    }

    // ---- a running instance, read live ----

    /** Android's CPU counters, from {@code /proc/stat}'s first line. */
    record CpuTimes(long busy, long total) {

        static Optional<CpuTimes> parse(String stat) {
            if (stat == null) return Optional.empty();
            for (String line : stat.split("\\R")) {
                String[] parts = line.strip().split("\\s+");
                if (parts.length < 5 || !parts[0].equals("cpu")) continue;
                try {
                    long total = 0;
                    for (int i = 1; i < parts.length; i++) total += Long.parseLong(parts[i]);
                    long idle = Long.parseLong(parts[4]) + (parts.length > 5 ? Long.parseLong(parts[5]) : 0);
                    return Optional.of(new CpuTimes(total - idle, total));
                } catch (NumberFormatException odd) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        /** The share of the CPU busy since {@code before}, 0 to 100; -1 when it can't be said. */
        int percentSince(CpuTimes before) {
            if (before == null) return -1;
            long total = this.total - before.total;
            long busy = this.busy - before.busy;
            if (total <= 0 || busy < 0) return -1;
            return (int) Math.round(Math.min(100.0, 100.0 * busy / total));
        }
    }

    /**
     * One reading of a running instance.
     *
     * @param adbMs      how long one round trip took, or -1 when ADB did not answer
     * @param uptimeS    Android's uptime in seconds, or -1
     * @param cpu        the counters, kept for the next reading's share, or null
     * @param cpuPercent the share busy since the last reading, or -1
     * @param memUsedMb  memory in use inside Android, or -1
     * @param memTotalMb memory Android has, or -1
     * @param front      the app in front, or null
     */
    record Live(long adbMs, long uptimeS, CpuTimes cpu, int cpuPercent, long memUsedMb, long memTotalMb,
                String front) {

        static final Live NO_ANSWER = new Live(-1, -1, null, -1, -1, -1, null);

        String adb() {
            if (adbMs < 0) return "ADB: no answer";
            return "ADB: " + (adbMs > 1000 ? "slow, " : "ok, ") + adbMs + " ms";
        }

        String uptime() {
            return uptimeS < 0 ? "Up: —" : "Up " + duration(uptimeS);
        }

        String cpuAndMemory() {
            String cpuText = cpuPercent < 0 ? "CPU —" : "CPU " + cpuPercent + "%";
            String mem = memUsedMb < 0 ? "memory —" : "memory " + memUsedMb + " / " + memTotalMb + " MB";
            return cpuText + " · " + mem;
        }

        String inFront() {
            return front == null || front.isBlank() ? "In front: —" : "In front: " + front;
        }
    }

    /**
     * Reads one running instance over {@code device}; {@code last} gives the CPU share its start. Off the UI thread.
     * Each part that fails is left unknown; only the round trip decides whether ADB answered.
     */
    static Live read(AdbDevice device, Live last) {
        long started = System.nanoTime();
        String echo;
        try {
            echo = device.shell("echo ok");
        } catch (RuntimeException | Error noAnswer) {
            // dadb surfaces some failures as Errors
            return Live.NO_ANSWER;
        }
        if (echo == null || !echo.contains("ok")) return Live.NO_ANSWER;
        long adbMs = (System.nanoTime() - started) / 1_000_000;
        long uptime = uptimeSeconds(quietly(device, "cat /proc/uptime"));
        CpuTimes cpu = CpuTimes.parse(quietly(device, "head -1 /proc/stat")).orElse(null);
        int percent = cpu == null || last == null ? -1 : cpu.percentSince(last.cpu());
        long[] mem = memoryMb(quietly(device, "cat /proc/meminfo"));
        String front = null;
        try {
            front = device.focusedPackage().orElse(null);
        } catch (RuntimeException | Error unread) {
            // left unknown
        }
        return new Live(adbMs, uptime, cpu, percent, mem[0], mem[1], front);
    }

    private static String quietly(AdbDevice device, String command) {
        try {
            return device.shell(command);
        } catch (RuntimeException | Error unread) {
            return null;
        }
    }

    /** {@code /proc/uptime}'s first number, in whole seconds; -1 when it does not read. */
    static long uptimeSeconds(String uptime) {
        if (uptime == null || uptime.isBlank()) return -1;
        try {
            return (long) Double.parseDouble(uptime.strip().split("\\s+")[0]);
        } catch (NumberFormatException odd) {
            return -1;
        }
    }

    /** {@code /proc/meminfo}'s used and total memory in MB, used being total less available; -1 each when unread. */
    static long[] memoryMb(String meminfo) {
        long total = -1;
        long available = -1;
        if (meminfo != null) {
            for (String line : meminfo.split("\\R")) {
                String[] parts = line.strip().split("\\s+");
                if (parts.length < 2) continue;
                try {
                    if (parts[0].equals("MemTotal:")) total = Long.parseLong(parts[1]) / 1024;
                    else if (parts[0].equals("MemAvailable:")) available = Long.parseLong(parts[1]) / 1024;
                } catch (NumberFormatException odd) {
                    // left unknown
                }
            }
        }
        if (total < 0 || available < 0) return new long[] {-1, -1};
        return new long[] {total - available, total};
    }

    /** {@code seconds} as "45 s", "12 min", "3 h 05 min" or "2 d 4 h". */
    static String duration(long seconds) {
        if (seconds < 60) return seconds + " s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h " + String.format("%02d", minutes % 60) + " min";
        return hours / 24 + " d " + hours % 24 + " h";
    }

    // ---- a click on a shown screen ----

    /** A point in the instance's own pixels. */
    record Tap(int x, int y) {
    }

    /**
     * Where a click at ({@code x}, {@code y}) on the screen drawn {@code shownW}×{@code shownH} lands in the frame of
     * {@code frameW}×{@code frameH}; empty outside the drawing. The drawing keeps the frame's ratio.
     */
    static Optional<Tap> tap(double x, double y, double shownW, double shownH, int frameW, int frameH) {
        if (shownW <= 0 || shownH <= 0 || frameW <= 0 || frameH <= 0) return Optional.empty();
        if (x < 0 || y < 0 || x >= shownW || y >= shownH) return Optional.empty();
        int tx = (int) Math.min(frameW - 1, Math.floor(x * frameW / shownW));
        int ty = (int) Math.min(frameH - 1, Math.floor(y * frameH / shownH));
        return Optional.of(new Tap(tx, ty));
    }

    // ---- Show ----

    /**
     * The title of the window most likely the instance's among {@code titles}: one that is its name exactly, else one
     * that holds it as a word; empty when none or several tie.
     */
    static Optional<String> windowTitle(String instance, List<String> titles) {
        if (instance == null || instance.isBlank()) return Optional.empty();
        List<String> exact = titles.stream().filter(title -> title != null && title.strip().equalsIgnoreCase(instance))
                .toList();
        if (exact.size() == 1) return Optional.of(exact.getFirst());
        if (exact.size() > 1) return Optional.empty();
        String word = "(?i).*(^|[^\\p{L}\\p{N}])" + Pattern.quote(instance) + "($|[^\\p{L}\\p{N}]).*";
        List<String> holding = titles.stream().filter(title -> title != null && title.matches(word)).toList();
        return holding.size() == 1 ? Optional.of(holding.getFirst()) : Optional.empty();
    }
}
