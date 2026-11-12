package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The instances BotMaker renamed on this PC, and the hand-off to the bots that still name them (spec
 * {@code 2026-11-12-emulators-window-design.md}, amended: no file of its own, the rows are the SDK's value of this PC
 * {@link SdkHostValues#INSTANCE_RENAMES}). Each row is one rename, old key → new key, with no chain: renaming B to C
 * after A to B leaves A → C and B → C.
 * <p>
 * A launch target names an instance by its name alone. A bot is pointed at the new name when it is opened, and only
 * when nothing on this PC is called the old name any more: an instance of another emulator that kept it is still what
 * the bot means. A bot not on this PC keeps the old name, and its checks say "no instance".
 */
public final class InstanceRenames {

    private InstanceRenames() {}

    /**
     * {@code rows} with {@code from} renamed {@code to}: the renames that led to {@code from} now lead to {@code to}. An
     * old name renamed before, to another instance, is kept beside the new row: two ways out of one name is
     * ambiguous, and {@link #moves} leaves a bot naming it alone.
     */
    public static HostLabels recorded(HostLabels rows, InstanceKey from, InstanceKey to) {
        if (from.equals(to)) return rows;
        String old = from.text();
        String now = to.text();
        List<HostLabels.Label> next = new ArrayList<>();
        for (HostLabels.Label row : rows.list()) {
            // the new name is an instance's own again
            if (row.instance().equals(now)) continue;
            HostLabels.Label moved = row.text().equals(old) ? new HostLabels.Label(row.instance(), now) : row;
            if (!next.contains(moved)) next.add(moved);
        }
        HostLabels.Label added = new HostLabels.Label(old, now);
        if (!next.contains(added)) next.add(added);
        return new HostLabels(next);
    }

    /** The names {@code instances} have now, for {@link #moves}. */
    public static Set<String> names(Collection<EmulatorInstance> instances) {
        Set<String> out = new LinkedHashSet<>();
        for (EmulatorInstance instance : instances) {
            if (instance.reference() != null) out.add(instance.reference());
        }
        return out;
    }

    /** {@code rows} without the renames that lead to {@code key}, an instance that was deleted. */
    public static HostLabels dropped(HostLabels rows, InstanceKey key) {
        String gone = key.text();
        return new HostLabels(rows.list().stream().filter(row -> !row.text().equals(gone)).toList());
    }

    /**
     * Old name → new name for the launch targets, from {@code rows}: only an old name nothing in {@code known} (the
     * names this PC's instances have now) is called, and only when it leads to one new name.
     */
    public static Map<String, String> moves(HostLabels rows, Set<String> known) {
        Map<String, String> moves = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (HostLabels.Label row : rows.list()) {
            InstanceKey from = InstanceKey.parse(row.instance());
            InstanceKey to = InstanceKey.parse(row.text());
            if (from == null || to == null || known.contains(from.reference())) continue;
            String was = moves.putIfAbsent(from.reference(), to.reference());
            if (was != null && !was.equals(to.reference())) ambiguous.add(from.reference());
        }
        ambiguous.forEach(moves::remove);
        moves.entrySet().removeIf(move -> move.getKey().equals(move.getValue()));
        return moves;
    }

    /** {@code spec} naming the new instance when it is an emulator app's on an old one, else as it was. */
    public static String respelled(String spec, Map<String, String> moves) {
        LaunchSpec parsed = spec == null ? null : LaunchSpec.parse(spec);
        if (parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP) return spec;
        String instance = parsed.emulatorInstance();
        String now = instance == null ? null : moves.get(instance);
        if (now == null) return spec;
        return LaunchKind.EMULATOR_APP.id() + ":" + parsed.emulatorPackage() + "@" + now;
    }

    public static List<String> respelled(List<String> specs, Map<String, String> moves) {
        return specs.stream().map(spec -> respelled(spec, moves)).toList();
    }

    /**
     * Points the open bot at the new names: its launch targets, the runner's pick and the capture source, each a
     * write of its own that Undo takes back. Call it on the JavaFX application thread.
     *
     * @return what was changed, for the log and the status line; empty when the bot named no old instance
     */
    public static Optional<String> handOff(StudioServices services, Map<String, String> moves) {
        if (services == null || moves.isEmpty()) return Optional.empty();
        List<String> specs = new ArrayList<>(LaunchTargetValue.all(services));
        specs.add(LaunchTargetValue.chosen(services));
        // only the renames this bot names, for its log line
        Map<String, String> named = new LinkedHashMap<>();
        for (String spec : specs) {
            LaunchSpec parsed = spec == null || spec.isEmpty() ? null : LaunchSpec.parse(spec);
            String instance = parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP ? null
                    : parsed.emulatorInstance();
            if (instance != null && moves.containsKey(instance)) named.put(instance, moves.get(instance));
        }
        CaptureValue.followed(services).filter(moves::containsKey).ifPresent(old -> named.put(old, moves.get(old)));
        if (named.isEmpty()) return Optional.empty();
        Optional<String> refused = LaunchTargetValue.respell(services, spec -> respelled(spec, moves));
        if (refused.isEmpty()) refused = CaptureValue.renamed(services, moves);
        List<String> pairs = new ArrayList<>();
        named.forEach((old, renamed) -> pairs.add(old + " → " + renamed));
        String said = refused.map(why -> "The bot still names a renamed emulator instance (" + String.join(", ", pairs)
                        + "): " + why)
                .orElse("The bot now names the renamed emulator instance " + String.join(", ", pairs)
                        + ". Undo takes it back.");
        if (refused.isPresent()) SdkLog.warn(said);
        else SdkLog.info(said);
        return Optional.of(said);
    }
}
