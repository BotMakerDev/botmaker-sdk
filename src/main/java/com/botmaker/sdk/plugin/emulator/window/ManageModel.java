package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The words and names of Clone, Rename and Delete on an instance, with no JavaFX. */
final class ManageModel {

    private ManageModel() {}

    /** Whether BotMaker clones, renames and deletes {@code product}'s instances (its console, or its own manager). */
    static boolean manageable(PlatformId product) {
        return switch (product) {
            case BLUESTACKS, LDPLAYER, MEMU, MUMU -> true;
            default -> false;
        };
    }

    /** The instances of {@code row}'s emulator, as last read: the names a new one may not take. */
    static List<EmulatorInstance> siblings(EmulatorsModel.Snapshot snapshot, EmulatorsModel.InstanceRow row) {
        return snapshot.instances().stream().filter(one -> one.key().platform() == row.key().platform())
                .map(EmulatorsModel.InstanceRow::instance).toList();
    }

    /** The name a copy of {@code source} is offered: "<name> copy", then "<name> copy 2" and on, the first free. */
    static String cloneName(String source, List<EmulatorInstance> siblings) {
        Set<String> taken = siblings.stream().map(EmulatorInstance::name).filter(name -> name != null)
                .map(name -> name.strip().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        String base = (source == null ? "" : source.strip()) + " copy";
        String name = base;
        for (int n = 2; taken.contains(name.toLowerCase(Locale.ROOT)); n++) name = base + " " + n;
        return name;
    }

    /** What Delete asks: the instance, that it is stopped first when it runs, and the bots seen launching it. */
    static String deleteQuestion(EmulatorsModel.InstanceRow row) {
        String name = row.instance().name();
        StringBuilder words = new StringBuilder("Delete " + name + " (" + row.key().platform().displayName()
                + ")? Its Android, its apps and its data are deleted, and this can't be undone.");
        if (row.running()) words.append("\n\nIt is running: it is stopped first.");
        if (!row.usedBy().isEmpty()) {
            words.append("\n\n").append(row.usedBy().size() == 1 ? "A bot launches it: " : "Bots launch it: ")
                    .append(String.join(", ", row.usedBy()))
                    .append(". Their launch targets will name an instance that is gone.");
        }
        return words.toString();
    }

    /** What Rename says under its field: the bots that are pointed at the new name, and when. */
    static String renameNote(EmulatorsModel.InstanceRow row) {
        if (row.usedBy().isEmpty()) return "No bot was seen launching it.";
        return "Launched by " + String.join(", ", row.usedBy()) + ". Each is pointed at the new name the next time it"
                + " is opened here; a bot on another PC keeps the old name.";
    }
}
