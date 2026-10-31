package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ElementNames.Named;
import com.botmaker.sdk.plugin.assist.ElementRows.Row;
import com.botmaker.shared.emulator.tree.UiTree;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rows ticked in the picker, saved together (spec § The picker, step 5): each a constant of the bot's
 * {@code Elements}, named again on the frozen tree with the bot's {@code Texts} for its words, with a shot cut from
 * the frozen frame at its node; the containers and neighbours its name says it is inside or below saved before it, as
 * constants of their own with no shot; then each written on the screen picked as a button, a marker or a readout. A
 * constant the bot holds already with the same clues is used, never saved twice. Nothing saved holds a bound or a
 * class name.
 */
public final class ElementSaves {

    /** Where the shots go, under the bot's resources: {@code elements/battery.png}. */
    static final String FOLDER = "elements";

    private static final ManagedSet<Element> ELEMENTS = ManagedSet.of(SdkValues.ELEMENTS);
    private static final ManagedSet<Words> TEXTS = ManagedSet.of(SdkValues.TEXTS);

    private ElementSaves() {}

    /** What a row is saved as on its screen. */
    public enum As {
        BUTTON("A button"),
        MARKER("Tells the screen"),
        READOUT("Read on the screen");

        private final String displayName;

        As(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        /** What {@code row} may be saved as, the first the one it is offered as. */
        public static List<As> offered(Row row) {
            if (row.tappable()) return List.of(BUTTON, MARKER);
            if (row.kind() == ElementKind.TEXT) return List.of(MARKER, READOUT);
            return List.of(MARKER);
        }
    }

    /** A ticked row and what it is saved as. */
    public record Pick(Row row, As as) {}

    /** A constant added to {@code Elements}; {@code cut} where its shot is cut on the frame, null for none. */
    record Constant(String name, Element element, Rectangle cut) {}

    /** What one pick writes on the screen: its element, read by {@code words} for a field's name. */
    record Part(As as, String words, Element element, boolean repeated) {}

    /**
     * What a save writes: the constants added, in order (an anchor before what names it), the screen's parts, the
     * words the names hold (each a {@code Texts} constant), and what to mind.
     */
    record Planned(List<Constant> constants, List<Part> parts, List<Words> words, List<String> warnings) {}

    /**
     * {@code picks} as saved on {@code tree} beside {@code existing}, the bot's {@code Elements} by name, in a frame of
     * {@code frame}'s bounds. {@code texts} gives the bot's {@code Texts} value for a text, a new one when it has none.
     */
    static Planned plan(UiTree tree, List<Pick> picks, Map<String, Element> existing, Function<String, Words> texts,
                        Rectangle frame) {
        Map<String, Element> known = new LinkedHashMap<>(existing);
        List<Constant> added = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        Set<Words> words = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        List<Optional<Named>> names = picks.stream().map(p -> ElementNames.name(p.row().node(), tree, texts)).toList();
        // A row ticked is cut a shot even when another ticked row names it as an anchor first.
        Map<Element, Rectangle> shots = new HashMap<>();
        for (int i = 0; i < picks.size(); i++) {
            Rectangle cut = picks.get(i).row().node().bounds().intersection(frame);
            if (names.get(i).isPresent() && !cut.isEmpty()) shots.put(names.get(i).get().element(), cut);
        }
        for (int i = 0; i < picks.size(); i++) {
            Pick pick = picks.get(i);
            Optional<Named> named = names.get(i);
            if (named.isEmpty()) {
                warnings.add(pick.row().said() + ": no name finds it on this frame; ✂ Cut it as a picture.");
                continue;
            }
            Element element = resolve(named.get().element(), shots, known, added, words);
            parts.add(new Part(pick.as(), pick.row().words(), element, named.get().repeated()));
            if (named.get().byText()) warnings.add(pick.row().said() + ": " + ElementRows.byTextWarning());
            if (named.get().byIndex()) {
                warnings.add(pick.row().said() + ": told apart by its place only, it breaks when the list changes.");
            }
        }
        return new Planned(List.copyOf(added), List.copyOf(parts), List.copyOf(words), List.copyOf(warnings));
    }

    /**
     * {@code element} as a constant: its anchors first, each the bot's constant with the same clues or a new one; then
     * itself, the bot's own, or a new one with its shot where {@code shots} cuts it (none: an anchor not ticked, or
     * a row off the frame).
     */
    private static Element resolve(Element element, Map<Element, Rectangle> shots, Map<String, Element> known,
                                   List<Constant> added, Set<Words> words) {
        Rectangle cut = shots.get(element);
        Element inside = element.inside() == null ? null : resolve(element.inside(), shots, known, added, words);
        Element below = element.below() == null ? null : resolve(element.below(), shots, known, added, words);
        Element clues = new Element(element.id(), element.label(), element.text(), element.holding(), inside, below,
                element.index(), null);
        for (Element held : known.values()) if (sameClues(held, clues)) return held;
        for (Words said : new Words[] {clues.label(), clues.text(), clues.holding()}) if (said != null) words.add(said);
        String name = constantName(clues, known.keySet());
        Element saved = cut == null ? clues : clues.shot(FOLDER + "/" + name.toLowerCase(Locale.ROOT) + ".png");
        known.put(name, saved);
        added.add(new Constant(name, saved, cut));
        return saved;
    }

    /** Whether {@code a} and {@code b} find the same: every clue alike, the shot aside. */
    static boolean sameClues(Element a, Element b) {
        return a.shot(null).equals(b.shot(null));
    }

    /** A constant's name for {@code element}, none of {@code taken}: from its words, else its id. */
    static String constantName(Element element, Set<String> taken) {
        Words words = element.text() != null ? element.text() : element.label() != null ? element.label()
                : element.holding();
        String base = words != null ? words.text() : element.id() != null ? element.id() : "element";
        String name = ScreenEdits.textName(base, taken);
        // textName's own fallback, for words of no letters or starting with a digit, is a text's: an element's is
        // named the same way, its taken names checked again.
        boolean fallback = name.startsWith("TEXT") && !base.strip().toUpperCase(Locale.ROOT).startsWith("TEXT");
        return fallback ? ScreenEdits.textName("element " + base, taken) : name;
    }

    /**
     * {@code picks}, rows of {@code tree} frozen with {@code frame}, saved: their constants and shots, then each on
     * {@code screen} (null: on none) as its pick says. What was done in sentences, or why nothing was. Off the FX
     * thread: it writes the shots.
     */
    public static String save(AgentContext context, String screen, UiTree tree, BufferedImage frame,
                              List<Pick> picks) {
        StudioServices services = context.services();
        if (picks.isEmpty()) return ScreenEdits.NOT_WRITTEN + "tick the rows to save first.";
        Path resources = services == null ? null : services.resourcesDir();
        if (resources == null || services.pluginValues() == null) {
            return ScreenEdits.NOT_WRITTEN + "Studio has no project open.";
        }
        Held held = FxCall.call(() -> held(services.pluginValues()));
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        if (screen != null && !graph.screens().containsKey(screen)) {
            return ScreenEdits.NOT_WRITTEN + "no screen " + screen + " in the map: make it first.";
        }
        Planned planned = plan(tree, picks, held.elements(), text -> held.texts().getOrDefault(text, Words.of(text)),
                new Rectangle(0, 0, frame.getWidth(), frame.getHeight()));
        if (planned.parts().isEmpty()) return ScreenEdits.NOT_WRITTEN + String.join("\n", planned.warnings());
        // The shots written new, taken off again when nothing is saved: none is left without its constant.
        List<Path> written = new ArrayList<>();
        Optional<String> failed = Optional.empty();
        for (Constant constant : planned.constants()) {
            if (constant.cut() == null) continue;
            Rectangle at = constant.cut();
            Path file = resources.resolve(constant.element().shot());
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) written.add(file);
                ImageIO.write(frame.getSubimage(at.x, at.y, at.width, at.height), "png", file.toFile());
            } catch (IOException | RuntimeException unwritten) {
                failed = Optional.of(constant.name() + "'s shot could not be written: " + unwritten.getMessage());
                break;
            }
        }
        if (failed.isEmpty()) {
            failed = FxCall.call(() -> ScreenBook.step(services, "Save " + count(planned.parts().size()),
                    () -> write(services, screen, planned)));
        }
        if (failed.isPresent()) {
            for (Path file : written) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException kept) {
                    // A shot left behind is a picture no constant names: harmless, and overwritten by the next save.
                }
            }
            return ScreenEdits.NOT_WRITTEN + failed.get();
        }
        List<String> said = new ArrayList<>();
        said.add(planned.constants().isEmpty() ? "The bot's Elements had them already."
                : "Saved " + planned.constants().stream().map(c -> "Elements." + c.name())
                .collect(Collectors.joining(", ")) + ".");
        if (screen != null) {
            said.add(graph.shown(screen) + ": " + planned.parts().stream().map(ElementSaves::described)
                    .collect(Collectors.joining(", ")) + ".");
        }
        said.addAll(planned.warnings());
        return String.join("\n", said);
    }

    /** The bot's {@code Elements} by name and its {@code Texts} by their text, read on the FX thread. */
    private record Held(Map<String, Element> elements, Map<String, Words> texts) {}

    private static Held held(PluginValues values) {
        Map<String, Element> elements = new LinkedHashMap<>();
        for (Member member : ELEMENTS.members(values)) {
            ELEMENTS.read(values, member).ifPresent(e -> elements.put(member.name(), e));
        }
        Map<String, Words> texts = new LinkedHashMap<>();
        for (Member member : TEXTS.members(values)) {
            TEXTS.read(values, member).ifPresent(w -> texts.putIfAbsent(w.text(), w));
        }
        return new Held(elements, texts);
    }

    /** Writes {@code planned}: its words, its constants, then its parts on {@code screen}. On the FX thread. */
    private static Optional<String> write(StudioServices services, String screen, Planned planned) {
        PluginValues values = services.pluginValues();
        for (Words words : planned.words()) {
            Optional<String> failed = ScreenEdits.keptText(services, words);
            if (failed.isPresent()) return failed;
        }
        if (!planned.constants().isEmpty()) {
            Optional<String> missing = values.create(ELEMENTS.value());
            if (missing.isPresent()) return missing;
        }
        for (Constant constant : planned.constants()) {
            Optional<String> failed = ELEMENTS.add(values, Member.of(constant.name()), constant.element());
            if (failed.isPresent()) return failed;
        }
        if (screen == null) return Optional.empty();
        return ScreenBook.update(services, screen, s -> withParts(s, planned.parts()));
    }

    /** {@code screen} with {@code parts} on it: a button and a readout under a field named from their words. */
    static MapScreen withParts(MapScreen screen, List<Part> parts) {
        MapScreen now = screen;
        for (Part part : parts) {
            Element element = part.element();
            now = switch (part.as()) {
                case BUTTON -> now.withInput(now.fieldFor(field(part)),
                        part.repeated() ? Button.of(element).repeated() : Button.of(element));
                case READOUT -> now.withReadout(now.fieldFor(field(part)), Readout.element(element));
                case MARKER -> now.setup(x -> x.elements().contains(element) ? x : x.elements(plus(x.elements(),
                        element)));
            };
        }
        return now;
    }

    private static String field(Part part) {
        return part.words().isBlank() ? part.element().id() : part.words();
    }

    private static List<Element> plus(List<Element> elements, Element element) {
        List<Element> more = new ArrayList<>(elements);
        more.add(element);
        return more;
    }

    private static String described(Part part) {
        String what = part.words().isBlank() ? "an element" : "\"" + part.words() + "\"";
        return what + switch (part.as()) {
            case BUTTON -> part.repeated() ? " as a repeated button" : " as a button";
            case MARKER -> " tells it";
            case READOUT -> " is read on it";
        };
    }

    private static String count(int n) {
        return n == 1 ? "an element" : n + " elements";
    }
}
