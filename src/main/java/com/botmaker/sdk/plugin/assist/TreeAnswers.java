// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * What a run settled off a screen's tree ({@link TreeAnswer}, web discovery phase 6b), written into the bot as
 * Discovery's own tools would: a new screen with its marking parts and its inputs, a website screen's route widened,
 * a way, a note of what pressing an input did. The elements go into the bot's {@code Elements} and their words into
 * its {@code Texts}, each a constant the bot holds already when one has the same clues. Planned without the host
 * ({@link #plan}), then written in one step of Studio's undo ({@link #apply}).
 */
final class TreeAnswers {

    private static final ManagedSet<Element> ELEMENTS = ManagedSet.of(SdkValues.ELEMENTS);
    private static final ManagedSet<Words> TEXTS = ManagedSet.of(SdkValues.TEXTS);

    private TreeAnswers() {}

    /** A screen to add as {@code constant}. */
    record Added(String constant, MapScreen screen) {}

    /** A change of the screen {@code constant}. */
    record Change(String constant, UnaryOperator<MapScreen> change) {}

    /**
     * What the answers write, in order: the {@code words} kept as {@code Texts}, the new {@code Elements} constants by
     * name, the screens added, then the changes of screens the map has (or has once the added ones are), and the
     * launch specs made the bot's preferred targets, last first. {@code skipped} says each answer that could not be
     * written, and why. {@code pictures} are the files the run rendered, by the shot path in the bot's resources each
     * new element names.
     */
    record Planned(List<Words> words, Map<String, Element> elements, List<Added> added, List<Change> changes,
                   List<String> targets, List<String> skipped, Map<String, String> pictures) {

        boolean empty() {
            return added.isEmpty() && changes.isEmpty() && targets.isEmpty();
        }
    }

    /**
     * {@code answers} planned on {@code graph}, the bot's {@code Elements} by name {@code existing}. A screen is named
     * by its constant or the name it shows; a screen added here by its {@link TreeAnswer.NewScreen#name}.
     */
    static Planned plan(ScreenGraph graph, List<TreeAnswer> answers, Map<String, Element> existing) {
        Map<String, Element> known = new LinkedHashMap<>(existing);
        Map<String, Element> made = new LinkedHashMap<>();
        Set<Words> words = new LinkedHashSet<>();
        Map<String, String> pictures = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        // The new screens' constants, by the name the answers call them.
        Map<String, String> newConstants = new HashMap<>();
        Set<String> takenConstants = new LinkedHashSet<>(graph.screens().keySet());
        Set<String> wayTargets = new LinkedHashSet<>();
        for (TreeAnswer answer : answers) if (answer instanceof TreeAnswer.NewWay way) wayTargets.add(way.to());

        Map<String, MapScreen> screens = new LinkedHashMap<>();
        boolean empty = graph.screens().isEmpty();
        for (TreeAnswer answer : answers) {
            if (!(answer instanceof TreeAnswer.NewScreen screen)) continue;
            if (ScreenBook.find(graph, screen.name()).isPresent() || newConstants.containsKey(screen.name())) {
                skipped.add(screen.name() + ": the map has it already.");
                continue;
            }
            String constant = free(ScreenBook.constantFor(graph, screen.name()), takenConstants);
            takenConstants.add(constant);
            newConstants.put(screen.name(), constant);
            List<Element> markers = screen.markers().stream()
                    .map(locator -> kept(locator.element(), known, made, words)).toList();
            Setup setup = Setup.named(screen.name()).elements(markers);
            if (screen.route() != null) setup = setup.route(screen.route()).examples(screen.examples());
            // A map's first screen is the one the game opens on; an overlay no press opened is the game's own popup.
            if (empty && screens.isEmpty()) setup = setup.start();
            else if (!screen.overlay().none() && !wayTargets.contains(screen.name())) setup = setup.popup();
            screens.put(constant, MapScreen.of(setup));
        }

        List<Change> changes = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        // The screens given a way back by this plan: one each.
        Set<String> backs = new HashSet<>();
        for (TreeAnswer answer : answers) {
            switch (answer) {
                case TreeAnswer.NewScreen ignored -> {
                }
                case TreeAnswer.NewTarget target -> {
                    if (target.spec() == null || target.spec().isBlank()) skipped.add("a launch target with no spec.");
                    else targets.add(target.spec().strip());
                }
                case TreeAnswer.NewInput input -> {
                    Optional<String> constant = constantOf(graph, newConstants, input.screen());
                    if (constant.isEmpty()) {
                        skipped.add(input.field() + ": no screen " + input.screen() + " in the map.");
                        continue;
                    }
                    Element element = kept(input.locator().element(), known, made, words);
                    // A new constant shows the picture the run rendered of it; one the bot had keeps its own.
                    Optional<String> named = input.picture() == null || element.shot() != null ? Optional.empty()
                            : nameOf(made, element);
                    if (named.isPresent()) {
                        String shot = ElementSaves.FOLDER + "/" + named.get().toLowerCase(Locale.ROOT) + ".png";
                        element = element.shot(shot);
                        made.put(named.get(), element);
                        known.put(named.get(), element);
                        pictures.put(shot, input.picture());
                    }
                    Button button = Button.of(element);
                    Input pressed = input.never() != null ? button.never(input.never()) : button.free();
                    UnaryOperator<MapScreen> add = s -> s.withInput(s.fieldFor(input.field()), pressed);
                    if (screens.containsKey(constant.get())) screens.compute(constant.get(), (c, s) -> add.apply(s));
                    else changes.add(new Change(constant.get(), add));
                }
                case TreeAnswer.NewRoute route -> {
                    Optional<String> constant = constantOf(graph, newConstants, route.screen());
                    if (constant.isEmpty()) {
                        skipped.add(route.route() + ": no screen " + route.screen() + " in the map.");
                        continue;
                    }
                    changes.add(new Change(constant.get(), s -> s.setup(x -> x.route(route.route())
                            .examples(examples(x.examples(), route.examples())))));
                }
                case TreeAnswer.NewWay way -> {
                    Optional<String> from = constantOf(graph, newConstants, way.screen());
                    Optional<String> to = constantOf(graph, newConstants, way.to());
                    if (from.isEmpty() || to.isEmpty()) {
                        skipped.add(way.screen() + "'s " + way.field() + ": no screen "
                                + (from.isEmpty() ? way.screen() : way.to()) + " in the map.");
                        continue;
                    }
                    if (screens.containsKey(to.get())) {
                        // Its way in, read once the field it names is written.
                        Way in = Way.of(BotConstants.input(from.get(), way.field()));
                        screens.compute(to.get(), (c, s) -> s.setup(x -> x.fromWay(in)));
                    } else if (graph.screens().get(from.get()) instanceof MapScreen on && on.setup().back() == null
                            && on.input(way.field()).isPresent() && goesBack(graph, from.get(), to.get())
                            && backs.add(from.get())) {
                        // A link up to the screen it was reached from is its way back, as Discovery makes it; the
                        // first such link of a batch, the rest ways as any.
                        Input back = BotConstants.input(from.get(), way.field());
                        // Kept tried, so no run presses it as new; what an earlier press noted of it is gone.
                        changes.add(new Change(from.get(), s -> ScreenEdits.rooted(withoutNote(s, way.field()))
                                .setup(x -> x.back(back))));
                        continue;
                    } else {
                        Optional<String> refused = ScreenEdits.wayRefused(graph, to.get(), from.get(), way.field());
                        if (refused.isPresent()) {
                            // A link to a screen no way may lead into (the start, a popup, one this screen is
                            // reached through) is what the page is, not a mistake: noted and tried, so no run
                            // presses it as new and nothing settled is left unwritten.
                            MapScreen source = graph.screens().get(from.get());
                            if (source != null && source.input(way.field()).isPresent()) {
                                TreeAnswer.InputNote leads = new TreeAnswer.InputNote(way.screen(), way.field(),
                                        "Leads to " + graph.shown(to.get()));
                                changes.add(new Change(from.get(), s -> noted(s, leads)));
                            } else {
                                skipped.add(way.screen() + "'s " + way.field() + ": " + refused.get());
                            }
                            continue;
                        }
                        changes.add(new Change(to.get(), s -> ScreenEdits.withWay(s, from.get(), way.field())));
                    }
                    if (!screens.containsKey(from.get())) {
                        // A way now: what an earlier press noted of it is gone with its tried mark.
                        changes.add(new Change(from.get(), s -> {
                            MapScreen clean = withoutNote(s, way.field());
                            return clean.input(way.field())
                                    .map(i -> clean.withInput(way.field(), ScreenEdits.untried(i))).orElse(clean);
                        }));
                    }
                }
                case TreeAnswer.InputNote note -> {
                    Optional<String> constant = constantOf(graph, newConstants, note.screen());
                    if (constant.isEmpty()) {
                        skipped.add(note.field() + ": no screen " + note.screen() + " in the map.");
                        continue;
                    }
                    changes.add(new Change(constant.get(), s -> noted(s, note)));
                }
                case TreeAnswer.SameInput same -> {
                    Optional<String> constant = constantOf(graph, newConstants, same.screen());
                    if (constant.isEmpty()) {
                        skipped.add(same.field() + ": no screen " + same.screen() + " in the map.");
                        continue;
                    }
                    changes.add(new Change(constant.get(), s -> s.input(same.field()).isEmpty() ? s
                            : withoutNote(s, same.field()).withoutInput(same.field())));
                }
                case TreeAnswer.InputRule rule -> {
                    Optional<String> constant = constantOf(graph, newConstants, rule.screen());
                    if (constant.isEmpty()) {
                        skipped.add(rule.field() + "'s rule: no screen " + rule.screen() + " in the map.");
                        continue;
                    }
                    Optional<BooleanSupplier> when = when(constant.get(), rule);
                    if (when.isEmpty()) {
                        skipped.add(rule.field() + "'s rule: a check its parts cannot make.");
                        continue;
                    }
                    UnaryOperator<MapScreen> ruled = s -> ruled(s, rule, when.get());
                    if (screens.containsKey(constant.get())) screens.compute(constant.get(), (c, s) -> ruled.apply(s));
                    else changes.add(new Change(constant.get(), ruled));
                }
            }
        }
        List<Added> added = new ArrayList<>();
        screens.forEach((constant, screen) -> added.add(new Added(constant, screen)));
        // In the order found, so the same page writes its constants in the same order each time.
        return new Planned(List.copyOf(words), Collections.unmodifiableMap(made), List.copyOf(added), List.copyOf(changes),
                List.copyOf(targets), List.copyOf(skipped), Map.copyOf(pictures));
    }

    /**
     * What {@link #apply} did: {@code failed} says why nothing was written, else empty; {@code skipped} each answer
     * left out, and each value that could not be written, why with it (2026-11-19: never swallowed, the run raises
     * them again).
     */
    record Written(Optional<String> failed, List<String> skipped) {

        Written {
            skipped = List.copyOf(skipped);
        }
    }

    /**
     * Writes {@code answers} into the bot in one step named {@code label}, on the FX thread: the words, the elements,
     * the screens added, then each change. A value that does not write is listed and the rest written.
     */
    static Written apply(StudioServices services, String label, List<TreeAnswer> answers) {
        return FxCall.call(() -> {
            PluginValues values = services.pluginValues();
            if (values == null) return new Written(Optional.of("Studio has no source tree open."), List.of());
            Planned planned = plan(ScreenBook.read(services), answers, elements(values));
            List<String> skipped = new ArrayList<>(planned.skipped());
            if (!planned.empty()) {
                ScreenBook.step(services, label, () -> {
                    skipped.addAll(write(services, values, planned));
                    return Optional.empty();
                });
            }
            for (String line : skipped) SdkLog.warn(label + ": " + line);
            return new Written(Optional.empty(), skipped);
        });
    }

    /** Where {@link #write(Planned, Writes)} puts each value: the bot, or a test's record of it. Empty when written. */
    interface Writes {
        Optional<String> text(Words words);

        /** Makes the bot's {@code Elements} class, before its first constant. */
        Optional<String> elements();

        Optional<String> element(String name, Element element);

        Optional<String> screen(Added added);

        Optional<String> change(Change change);
    }

    private static List<String> write(StudioServices services, PluginValues values, Planned planned) {
        // The launch target first: what the run was started on is the bot's game, whatever else fails to write.
        for (String target : planned.targets()) {
            LaunchTargetValue.set(services, target).ifPresent(why -> SdkLog.warn(target
                    + " was not made the bot's launch target: " + why));
        }
        for (Map.Entry<String, String> picture : planned.pictures().entrySet()) {
            copy(services, picture.getKey(), picture.getValue());
        }
        return write(planned, new Writes() {
            @Override
            public Optional<String> text(Words words) {
                return ScreenEdits.keptText(services, words);
            }

            @Override
            public Optional<String> elements() {
                return values.create(ELEMENTS.value());
            }

            @Override
            public Optional<String> element(String name, Element element) {
                return ELEMENTS.add(values, Member.of(name), element);
            }

            @Override
            public Optional<String> screen(Added added) {
                return ScreenBook.add(services, added.constant(), added.screen());
            }

            @Override
            public Optional<String> change(Change change) {
                return ScreenBook.update(services, change.constant(), change.change());
            }
        });
    }

    /**
     * Writes {@code planned}'s values through {@code writes}: the words, the elements, the screens added, then each
     * change. One that does not write is listed, {@code "could not write <value>: <why>"}, and the rest are written.
     */
    static List<String> write(Planned planned, Writes writes) {
        List<String> unwritten = new ArrayList<>();
        for (Words words : planned.words()) {
            writes.text(words).ifPresent(why -> unwritten.add("could not write the text " + words + ": " + why));
        }
        Optional<String> noElements = planned.elements().isEmpty() ? Optional.empty() : writes.elements();
        for (Map.Entry<String, Element> element : planned.elements().entrySet()) {
            noElements.or(() -> writes.element(element.getKey(), element.getValue())).ifPresent(why ->
                    unwritten.add("could not write the element " + element.getKey() + ": " + why));
        }
        for (Added added : planned.added()) {
            writes.screen(added).ifPresent(why -> unwritten.add("could not write the screen " + added.constant()
                    + ": " + why));
        }
        for (Change change : planned.changes()) {
            writes.change(change).ifPresent(why -> unwritten.add("could not write a change of " + change.constant()
                    + ": " + why));
        }
        return unwritten;
    }

    /**
     * Copies the run's {@code file} into the bot's resources as {@code shot}; false when it could not, which leaves its
     * element showing "?", as before: nothing to fail a write for.
     */
    private static boolean copy(StudioServices services, String shot, String file) {
        Path resources = services.resourcesDir();
        if (resources == null) return false;
        try {
            Path to = resources.resolve(shot);
            Files.createDirectories(to.getParent());
            Files.copy(Path.of(file), to, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException uncopied) {
            SdkLog.warn(shot + " could not be copied: " + uncopied.getMessage());
            return false;
        }
    }

    /**
     * Fills in what a screen the map holds lacked, from the pictures the run took as it reached it: the page as the
     * screen's shot when it has none, and each input's picture as its element's shot when that shows none. One step
     * of Studio's history; what has a picture keeps it, and what fails is said and left.
     */
    static void pictured(StudioServices services, ExploreEvent.Pictured pictured) {
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> constant = ScreenBook.find(graph, pictured.screen());
        if (constant.isEmpty()) return;
        if (!pictured.frame().isEmpty()) {
            try {
                BufferedImage frame = ImageIO.read(Path.of(pictured.frame()).toFile());
                if (frame != null) ScreenShots.saveIfMissing(services, graph, constant.get(), frame);
            } catch (IOException | RuntimeException unread) {
                // The screen keeps showing its marker.
            }
        }
        MapScreen screen = graph.screens().get(constant.get());
        if (pictured.inputs().isEmpty() || screen == null) return;
        Optional<String> failed = FxCall.call(() -> {
            PluginValues values = services.pluginValues();
            if (values == null) return Optional.of("Studio has no source tree open.");
            return ScreenBook.step(services, "Explore: " + pictured.screen() + " pictured",
                    () -> elementShots(services, values, screen, pictured.inputs()));
        });
        failed.ifPresent(why -> SdkLog.warn(pictured.screen() + "'s pictures were not written: " + why));
    }

    /** Each of {@code screen}'s inputs in {@code pictures} gives its element, when it shows none, its picture. */
    private static Optional<String> elementShots(StudioServices services, PluginValues values, MapScreen screen,
                                                 Map<String, String> pictures) {
        for (Map.Entry<String, String> picture : pictures.entrySet()) {
            if (!(screen.input(picture.getKey()).orElse(null) instanceof Button button)
                    || button.element() == null) continue;
            for (Member member : ELEMENTS.members(values)) {
                Optional<ValueContext> ctx = ELEMENTS.open(values, member);
                Optional<Element> held = ctx.flatMap(ELEMENTS::read);
                if (held.isEmpty() || held.get().shot() != null
                        || !ElementSaves.sameClues(held.get(), button.element())) continue;
                String shot = ElementSaves.FOLDER + "/" + member.name().toLowerCase(Locale.ROOT) + ".png";
                if (!copy(services, shot, picture.getValue())) break;
                Optional<String> refused = ctx.get().write(held.get().shot(shot));
                if (refused.isPresent()) return refused;
                break;
            }
        }
        return Optional.empty();
    }

    /** The bot's {@code Elements} by name. */
    private static Map<String, Element> elements(PluginValues values) {
        Map<String, Element> elements = new LinkedHashMap<>();
        for (Member member : ELEMENTS.members(values)) {
            ELEMENTS.read(values, member).ifPresent(e -> elements.put(member.name(), e));
        }
        return elements;
    }

    /** {@code element} as a constant: the bot's own with the same clues, else a new one, its words kept. */
    private static Element kept(Element element, Map<String, Element> known, Map<String, Element> made,
                                Set<Words> words) {
        for (Element held : known.values()) if (ElementSaves.sameClues(held, element)) return held;
        for (Words said : new Words[] {element.label(), element.text()}) if (said != null) words.add(said);
        String name = ElementSaves.constantName(element, known.keySet());
        known.put(name, element);
        made.put(name, element);
        return element;
    }

    /**
     * Whether a link of {@code screen} to {@code known} is its way back ({@link MapTools#goesBack}), the screen taken
     * as {@link ScreenEdits#rooted} hangs it: a page whose every way in is from {@code known} goes back there.
     */
    private static boolean goesBack(ScreenGraph graph, String screen, String known) {
        MapScreen on = graph.screens().get(screen);
        if (on == null || on.from() != null) return MapTools.goesBack(graph, screen, known);
        Map<String, MapScreen> screens = new LinkedHashMap<>(graph.screens());
        screens.put(screen, ScreenEdits.rooted(on));
        return MapTools.goesBack(ScreenGraph.of(screens), screen, known);
    }

    /** The name {@code made} gives {@code element}, the very one; empty when it is none of them. */
    private static Optional<String> nameOf(Map<String, Element> made, Element element) {
        return made.entrySet().stream().filter(e -> e.getValue() == element).map(Map.Entry::getKey).findFirst();
    }

    /** The constant of the screen {@code name}: one added here, else the map's. */
    private static Optional<String> constantOf(ScreenGraph graph, Map<String, String> added, String name) {
        String constant = added.get(name);
        return constant != null ? Optional.of(constant) : ScreenBook.find(graph, name);
    }

    private static String free(String constant, Set<String> taken) {
        String name = constant;
        for (int n = 2; taken.contains(name); n++) name = constant + "_" + n;
        return name;
    }

    /** {@code had} then each of {@code seen} it lacks, at most {@link Setup#EXAMPLES}. */
    private static List<String> examples(List<String> had, List<String> seen) {
        Set<String> all = new LinkedHashSet<>(had == null ? List.of() : had);
        all.addAll(seen);
        return all.stream().limit(Setup.EXAMPLES).toList();
    }

    /** {@code screen} with {@code note} on its notes, over what it said of that input before, the input marked tried. */
    private static MapScreen noted(MapScreen screen, TreeAnswer.InputNote note) {
        MapScreen now = withNote(withoutNote(screen, note.field()), note.field() + ": " + note.note());
        return now.input(note.field()).map(i -> now.withInput(note.field(), i.tried())).orElse(now);
    }

    /** {@code screen} without the lines of its notes that say something of its input {@code field}. */
    private static MapScreen withoutNote(MapScreen screen, String field) {
        String notes = screen.setup().notes();
        if (notes == null || notes.isBlank()) return screen;
        String kept = notes.lines().filter(line -> !line.startsWith(field + ": "))
                .collect(Collectors.joining("\n"));
        return kept.equals(notes) ? screen : screen.setup(s -> s.notes(kept));
    }

    /** {@code screen} with {@code line} on its notes, once. */
    private static MapScreen withNote(MapScreen screen, String line) {
        return screen.setup(s -> s.notes(s.notes() == null || s.notes().isBlank() ? line
                : s.notes().contains(line) ? s.notes() : s.notes() + "\n" + line));
    }

    /**
     * The {@code .when(…)} of {@code rule}'s input on the screen {@code constant}: its one check, or all of them; each
     * field a lazy reference to the screen's own, so the screen names its sibling once they are made. Empty when a
     * check's parts make none.
     */
    static Optional<BooleanSupplier> when(String constant, TreeAnswer.InputRule rule) {
        if (rule.checks().isEmpty()) return Optional.empty();
        List<Condition> checks = new ArrayList<>();
        try {
            for (TreeAnswer.FieldCheck check : rule.checks()) {
                checks.add(check.check(new Sibling(BotConstants.input(constant, check.field()))));
            }
        } catch (IllegalArgumentException unmade) {
            return Optional.empty();
        }
        return Optional.of(checks.size() == 1 ? checks.getFirst() : Condition.all(checks.toArray(Condition[]::new)));
    }

    /**
     * {@code screen} with {@code rule}'s input enabled {@code when}, and the rule in its notes. An input that has a
     * {@code .when(…)} already keeps it, the author's or an earlier run's, and gets the note only.
     */
    private static MapScreen ruled(MapScreen screen, TreeAnswer.InputRule rule, BooleanSupplier when) {
        MapScreen now = withNote(screen, rule.field() + ": " + rule.note());
        return now.input(rule.field()).filter(i -> i.when() == null)
                .map(i -> now.withInput(rule.field(), i.when(when))).orElse(now);
    }

    /** A lazy reference to a screen's input, equal to another naming the same one: what a rule's check names. */
    private record Sibling(Button input) implements Supplier<Button> {
        @Override
        public Button get() {
            return input;
        }
    }
}
