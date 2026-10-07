package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The games this computer launched for the open bot most recently, newest first — the top row of
 * {@link GameDialog} and where the toolbar's game button reads the current target's name and cover.
 *
 * <p>A fact about this machine, like the target itself, so it is a run property ({@value #PROPERTY}) beside
 * {@code botmaker.launch.target} rather than anything in the bot. Each entry keeps the name and cover the
 * picker showed, so the button can say <i>Hades II</i> without scanning a library on the JavaFX thread.
 */
public final class RecentTargets {

    /** The run property holding the list. */
    public static final String PROPERTY = "botmaker.launch.recent";

    /** How many are kept. */
    static final int LIMIT = 8;

    /**
     * Between entries, and between an entry's fields: the printable record and unit separator symbols, which no
     * launcher's name, id or path carries. Printable on purpose — every run property reaches the bot as a
     * {@code -D} argument, and a newline or a tab there is one more thing a Windows command line has to quote.
     */
    private static final String ENTRY = "␞";
    private static final String FIELD = "␟";

    private RecentTargets() {}

    /**
     * One remembered target.
     *
     * @param spec    the launch target, {@code steam:1145350}
     * @param name    what the picker called it
     * @param artwork its cover or icon, or {@code null}
     */
    public record Recent(String spec, String name, Path artwork) {}

    /** The remembered targets, newest first. */
    public static List<Recent> list(StudioServices services) {
        String value = services == null ? null : services.runs().property(PROPERTY);
        return decode(value);
    }

    /** The remembered entry for {@code spec}, if it was picked from the dialog. */
    public static Optional<Recent> find(StudioServices services, String spec) {
        if (spec == null) return Optional.empty();
        return list(services).stream().filter(r -> r.spec().equals(spec)).findFirst();
    }

    /** Puts {@code recent} first, dropping its older copy and anything past {@link #LIMIT}. */
    public static void remember(StudioServices services, Recent recent) {
        if (services == null || recent == null) return;
        services.runs().setProperty(PROPERTY, encode(pushed(list(services), recent)));
    }

    static List<Recent> pushed(List<Recent> before, Recent recent) {
        List<Recent> after = new ArrayList<>();
        after.add(recent);
        for (Recent old : before) {
            if (after.size() >= LIMIT) break;
            if (!old.spec().equals(recent.spec())) after.add(old);
        }
        return List.copyOf(after);
    }

    static String encode(List<Recent> recents) {
        List<String> lines = new ArrayList<>();
        for (Recent r : recents) {
            lines.add(clean(r.spec()) + FIELD + clean(r.name()) + FIELD
                    + (r.artwork() == null ? "" : clean(r.artwork().toString())));
        }
        return String.join(ENTRY, lines);
    }

    static List<Recent> decode(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<Recent> recents = new ArrayList<>();
        for (String line : value.split(java.util.regex.Pattern.quote(ENTRY))) {
            String[] fields = line.split(java.util.regex.Pattern.quote(FIELD), -1);
            if (fields[0].isBlank()) continue;
            String name = fields.length > 1 && !fields[1].isBlank() ? fields[1] : fields[0];
            Path art = fields.length > 2 && !fields[2].isBlank() ? Path.of(fields[2]) : null;
            recents.add(new Recent(fields[0], name, art));
        }
        return List.copyOf(recents);
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace(ENTRY, " ").replace(FIELD, " ");
    }
}
