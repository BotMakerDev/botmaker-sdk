package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.vision.TemplateNames;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A picture as the bot's Java names it — the constant {@code Pictures.ORE} in the open set
 * {@code @SdkValue(SdkValue.Id.PICTURES)} — and the four changes the picture library makes to it.
 *
 * <h2>The host changes the Java, by binding; this class says which constant</h2>
 *
 * <p>A picture's uses are what javac resolves to its constant — not tokens found by text, which miss a static
 * import, a class the user renamed, and the declaration itself —
 * ({@link PluginValues#uses}), and a rename, repoint or remove is the host's, compiled as the whole bot before
 * it lands. What stays here is the mapping from a file name to a constant ({@link TemplateNames#constantFor})
 * and the order of steps.
 *
 * <p><b>A path literal the user wrote in their own code is theirs</b>: nothing matches it any more. A
 * picture whose name predates the constant rule has no constant, so it has no uses to find — and every
 * picture captured or imported since is {@linkplain #declare declared} as one.
 */
public final class TemplateUses {

    /** The open set the pictures are: {@code Pictures.java}. */
    static final ManagedSet<ImageTemplate> SET = ManagedSet.of(SdkValues.PICTURES);
    /** The bot's screen map, {@code Screens.java}, whose pictures go with them. */
    private static final ManagedSet<GameScreen> MAP = ManagedSet.of(SdkValues.SCREEN_MAP);
    private static final String SCREENS_FILE = "Screens.java";

    private TemplateUses() {}

    /**
     * Everything found for one template. Empty means it can be deleted with nothing to fix.
     *
     * <p>The {@link PluginValues.Use}s inside are the host's own record, passed through rather than re-wrapped:
     * a file and a line are not this plugin's vocabulary.
     */
    public record Scan(String baseName, List<PluginValues.Use> uses) {

        public boolean isEmpty() {
            return uses.isEmpty();
        }

        /** How many distinct files use it — what a refusal message leads with. */
        public int fileCount() {
            return (int) uses.stream().map(PluginValues.Use::file).distinct().count();
        }

        /** "3 uses in 2 files" — the phrase both the delete refusal and the rename report open with. */
        public String describe() {
            return uses.size() + (uses.size() == 1 ? " use" : " uses")
                    + " in " + fileCount() + (fileCount() == 1 ? " file" : " files");
        }
    }

    /** Every use of the template called {@code baseName}'s constant; empty when it has none. */
    public static Scan find(PluginValues values, String baseName) {
        Member constant = declared(values, baseName);
        return new Scan(baseName, constant == null ? List.of() : List.copyOf(SET.uses(values, constant)));
    }

    /**
     * {@link #find}, after taking {@code baseName} out of the bot's {@code Screens} when only they use it: a screen's
     * marker, an input, a look of a button or of a screen, a lock or a way back goes with its picture rather than
     * keeping it (the user's call, 2026-10-15). A way into a screen stays, so a picture one opens by keeps it used;
     * then nothing is written, and the scan is as {@link #find} gives it. A use the map's own cannot take with them
     * (one written by hand in {@code Screens.java}) puts the screens back. The delete that stops after this calls
     * {@link MapCut#undo} before anything else writes the map: the undo writes each screen back whole. On the FX
     * thread.
     */
    public static MapCut offTheMap(PluginValues values, String baseName) {
        Scan scan = find(values, baseName);
        MapCut none = MapCut.none(scan);
        boolean mapOnly = scan.uses().stream().allMatch(use -> use.file().getFileName().toString()
                .equals(SCREENS_FILE));
        if (scan.isEmpty() || !mapOnly) return none;
        record Held(Member member, ValueContext at, GameScreen screen) {}
        List<Held> screens = new ArrayList<>();
        for (Member member : MAP.members(values)) {
            Optional<ValueContext> at = MAP.open(values, member);
            Optional<GameScreen> screen = at.flatMap(MAP::read);
            if (screen.isEmpty()) return none;    // a screen written by hand may name it: leave the map alone
            if (ScreenGraph.enteredBy(screen.get(), baseName)) return none;
            screens.add(new Held(member, at.get(), screen.get()));
        }
        // Every use goes with the map's own: only then is the map written.
        List<MapCut.Kept> kept = new ArrayList<>();
        try {
            for (Held held : screens) {
                GameScreen without = ScreenGraph.without(held.screen(), baseName);
                if (without.equals(held.screen())) continue;
                held.at().set(without);
                kept.add(new MapCut.Kept(held.member(), held.screen()));
            }
        } catch (RuntimeException refused) {
            // A screen the host would not write: the ones written already go back as they were.
            new MapCut(scan, List.copyOf(kept)).undo(values);
            throw refused;
        }
        MapCut cut = new MapCut(find(values, baseName), List.copyOf(kept));
        if (cut.scan().isEmpty()) return cut;
        cut.undo(values);
        return none;
    }

    /**
     * What {@link #offTheMap} did: the uses left once the map let go of the picture, and each screen it was taken off,
     * as it was before, to put back when the delete goes no further.
     */
    public record MapCut(Scan scan, List<Kept> kept) {

        /** A screen the picture was taken off, as it was. */
        public record Kept(Member member, GameScreen was) {}

        static MapCut none(Scan scan) {
            return new MapCut(scan, List.of());
        }

        /** Writes each screen back as it was: the picture stays, so its uses do. On the FX thread. */
        public void undo(PluginValues values) {
            for (Kept screen : kept) MAP.open(values, screen.member()).ifPresent(at -> at.set(screen.was()));
        }

        /** The names the game shows of the screens the picture was taken off, in the map's order. */
        public List<String> screens() {
            return kept.stream().map(k -> k.was().name()).toList();
        }
    }

    /**
     * Declares {@code Pictures.<NAME>} for a picture that can have a constant and has none yet — what a
     * capture and an import do, so the canvas writes {@code Pictures.ORE} rather than the path.
     *
     * @return empty when it is declared now or cannot be; else the host's sentence
     */
    public static Optional<String> declare(PluginValues values, String baseName) {
        Member constant = constantFor(baseName);
        if (constant == null || SET.contains(values, constant)) return Optional.empty();
        return SET.add(values, constant, picture(baseName));
    }

    /**
     * Writes {@code Pictures.<NAME>} as cut at {@code cut} — {@code .at(Rect)}, and {@code .in(Size)} when its frame
     * is not the bot's screen size — declaring it when it has no constant yet. What a capture and a replacement do,
     * so where a picture came from is in the bot's Java. On the FX thread.
     *
     * @return empty when written or when the picture can have no constant; else the host's sentence
     */
    public static Optional<String> cut(StudioServices services, String baseName, FrameCut cut) {
        PluginValues values = services.pluginValues();
        Member constant = constantFor(baseName);
        if (constant == null) return Optional.empty();
        try {
            if (!SET.contains(values, constant)) {
                return SET.add(values, constant, ScreenSize.cut(services, picture(baseName), cut));
            }
            Optional<ValueContext> ctx = SET.open(values, constant);
            Optional<ImageTemplate> held = ctx.flatMap(SET::read);
            if (held.isEmpty()) {
                return Optional.of("Pictures." + constant + " is written by hand; it was left as it is.");
            }
            // Its own path, which the user may have pointed elsewhere, and its tags: only where it was cut changes.
            ctx.get().set(ScreenSize.cut(services, filed(new ImageTemplate(held.get().filePath()), held.get()), cut));
            return Optional.empty();
        } catch (RuntimeException refused) {
            return Optional.of("Pictures." + constant + " was not rewritten: " + refused.getMessage());
        }
    }

    /**
     * Renames {@code oldName}'s constant and every use to {@code newName}'s, then points it at the new file.
     * Lossless — the same picture under a new name — so nothing is marked. A picture with no constant yet is
     * declared under the new name instead.
     *
     * @return empty when done; else why nothing was renamed
     */
    public static Optional<String> rename(PluginValues values, String oldName, String newName) {
        Member from = declared(values, oldName);
        if (from == null) return declare(values, newName);
        Member to = constantFor(newName);
        if (to == null) return Optional.of("\"" + newName + "\" cannot be a constant's name, so "
                + TemplateNames.CLASS_NAME + "." + from + " cannot follow it.");
        Optional<String> refused = SET.rename(values, from, to.name());
        if (refused.isPresent()) return refused;
        return SET.open(values, to).map(value -> {
            // Where it was cut and its tags stay: the same picture under a new name.
            ImageTemplate held = SET.read(value).orElse(null);
            ImageTemplate renamed = picture(newName);
            value.set(held == null ? renamed : filed(renamed.at(held.place()).in(held.frame()), held));
            return Optional.<String>empty();
        }).orElse(Optional.of(TemplateNames.CLASS_NAME + "." + to + " was renamed, but its path could not be "
                + "rewritten."));
    }

    /**
     * Points every use of {@code oldName}'s constant at {@code replacement}'s, declaring that one first when
     * it has none. A guess — the bot now watches for a different picture — so each function it touched is
     * marked with {@link #repointNote}. Empty when there was nothing to point.
     */
    public static Optional<String> repoint(PluginValues values, String oldName, String replacement) {
        Member from = declared(values, oldName);
        if (from == null || SET.uses(values, from).isEmpty()) return Optional.empty();
        Optional<String> undeclared = declare(values, replacement);
        if (undeclared.isPresent()) return undeclared;
        Member to = constantFor(replacement);
        if (to == null) return Optional.of("\"" + replacement + "\" has no constant to point "
                + TemplateNames.CLASS_NAME + "." + from + "'s uses at.");
        return SET.repoint(values, from, to, repointNote(oldName, replacement));
    }

    /**
     * Removes {@code baseName}'s constant, as its file goes. Refused by the host while anything uses it; empty
     * when it has none.
     */
    public static Optional<String> forget(PluginValues values, String baseName) {
        Member constant = declared(values, baseName);
        return constant == null ? Optional.empty() : SET.remove(values, constant);
    }

    /**
     * Every picture whose file is gone while its {@code Pictures} constant still names it, sorted: a file deleted in
     * a file manager or lost to a checkout left the constant, and its tags with it, behind.
     *
     * <p>Only a constant written as one of ours counts ({@code ORE = new ImageTemplate(".../ore.png")}): one the
     * user pointed at some other path is theirs, and nothing here answers for it.
     */
    public static List<String> missing(PluginValues values, Path resourcesDir) {
        Set<String> gone = new TreeSet<>();
        for (Member member : SET.members(values)) {
            String baseName = TemplateNames.baseNameFor(member.name());
            if (baseName == null || TemplateLibrary.exists(resourcesDir, baseName)) continue;
            String path = SET.read(values, member)
                    .map(ImageTemplate::filePath)
                    .orElse(null);
            if (TemplateNames.pathFor(baseName).equals(path)) gone.add(baseName);
        }
        return List.copyOf(gone);
    }

    /**
     * Where each picture was cut, by its name: what its constant's {@code .at} and {@code .in} say. Only a constant
     * written as one of ours counts, as in {@link #missing}; a picture with none is not in the map. On the FX thread.
     */
    public static Map<String, FrameCut> cuts(PluginValues values) {
        Map<String, FrameCut> cuts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Member member : SET.members(values)) {
            String baseName = TemplateNames.baseNameFor(member.name());
            if (baseName == null) continue;
            SET.read(values, member)
                    .filter(held -> TemplateNames.pathFor(baseName).equals(held.filePath()))
                    .ifPresent(held -> cuts.put(baseName, new FrameCut(held.frame(), held.place())));
        }
        return cuts;
    }

    /**
     * What a repointed block leaves the user to check.
     *
     * <p>Named rather than inlined because both places that repoint — deleting a template that is in use, and
     * repairing one whose file has gone — owe the same sentence: the blocks compile and run, and they are now
     * looking for a different picture.
     */
    public static String repointNote(String oldName, String replacement) {
        return "this looked for the template \"" + oldName + "\", which is gone — it now looks for \""
                + replacement + "\", which may not be what it should be watching for.";
    }

    /** {@code baseName}'s constant when the open set declares it, else null. */
    private static Member declared(PluginValues values, String baseName) {
        Member constant = constantFor(baseName);
        return constant != null && SET.contains(values, constant) ? constant : null;
    }

    /** The {@code Pictures} constant {@code baseName} is declared as, or null when it can have none. */
    private static Member constantFor(String baseName) {
        String constant = TemplateNames.constantFor(baseName);
        return constant == null ? null : Member.of(constant);
    }

    /** {@code picture} filed under what {@code held} is: its tags and activities. */
    private static ImageTemplate filed(ImageTemplate picture, ImageTemplate held) {
        return picture.tags(held.tags()).inActivities(held.activities());
    }

    private static ImageTemplate picture(String baseName) {
        return new ImageTemplate(TemplateLibrary.pathForName(baseName));
    }
}
