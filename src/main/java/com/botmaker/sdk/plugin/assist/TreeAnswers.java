// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

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
     * written, and why.
     */
    record Planned(List<Words> words, Map<String, Element> elements, List<Added> added, List<Change> changes,
                   List<String> targets, List<String> skipped) {

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
                    Button button = Button.of(kept(input.locator().element(), known, made, words));
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
                            && on.input(way.field()).isPresent() && MapTools.goesBack(graph, from.get(), to.get())
                            && backs.add(from.get())) {
                        // A link up to the screen it was reached from is its way back, as Discovery makes it; the
                        // first such link of a batch, the rest ways as any.
                        Input back = BotConstants.input(from.get(), way.field());
                        changes.add(new Change(from.get(), s -> s.setup(x -> x.back(back))));
                        continue;
                    } else {
                        Optional<String> refused = ScreenEdits.wayRefused(graph, to.get(), from.get(), way.field());
                        if (refused.isPresent()) {
                            skipped.add(way.screen() + "'s " + way.field() + ": " + refused.get());
                            continue;
                        }
                        changes.add(new Change(to.get(), s -> ScreenEdits.withWay(s, from.get(), way.field())));
                    }
                    if (!screens.containsKey(from.get())) {
                        changes.add(new Change(from.get(), s -> s.input(way.field())
                                .map(i -> s.withInput(way.field(), ScreenEdits.untried(i))).orElse(s)));
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
            }
        }
        List<Added> added = new ArrayList<>();
        screens.forEach((constant, screen) -> added.add(new Added(constant, screen)));
        // In the order found, so the same page writes its constants in the same order each time.
        return new Planned(List.copyOf(words), Collections.unmodifiableMap(made), List.copyOf(added), List.copyOf(changes),
                List.copyOf(targets), List.copyOf(skipped));
    }

    /**
     * Writes {@code answers} into the bot in one step named {@code label}, on the FX thread: the words, the elements,
     * the screens added, then each change. Empty when written (or nothing was to be), else why not.
     */
    static Optional<String> apply(StudioServices services, String label, List<TreeAnswer> answers) {
        return FxCall.call(() -> {
            PluginValues values = services.pluginValues();
            if (values == null) return Optional.of("Studio has no source tree open.");
            Planned planned = plan(ScreenBook.read(services), answers, elements(values));
            for (String skipped : planned.skipped()) SdkLog.warn(label + " left out " + skipped);
            if (planned.empty()) return Optional.empty();
            return ScreenBook.step(services, label, () -> write(services, values, planned));
        });
    }

    private static Optional<String> write(StudioServices services, PluginValues values, Planned planned) {
        // The launch target first: what the run was started on is the bot's game, whatever else fails to write.
        for (String target : planned.targets()) {
            LaunchTargetValue.set(services, target).ifPresent(why -> SdkLog.warn(target
                    + " was not made the bot's launch target: " + why));
        }
        for (Words words : planned.words()) {
            Optional<String> failed = ScreenEdits.keptText(services, words);
            if (failed.isPresent()) return failed;
        }
        if (!planned.elements().isEmpty()) {
            Optional<String> missing = values.create(ELEMENTS.value());
            if (missing.isPresent()) return missing;
        }
        for (Map.Entry<String, Element> element : planned.elements().entrySet()) {
            Optional<String> failed = ELEMENTS.add(values, Member.of(element.getKey()), element.getValue());
            if (failed.isPresent()) return failed;
        }
        for (Added added : planned.added()) {
            Optional<String> failed = ScreenBook.add(services, added.constant(), added.screen());
            if (failed.isPresent()) return failed;
        }
        for (Change change : planned.changes()) {
            Optional<String> failed = ScreenBook.update(services, change.constant(), change.change());
            if (failed.isPresent()) return failed;
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

    /** {@code screen} with {@code note} on its notes and the input it says of marked tried. */
    private static MapScreen noted(MapScreen screen, TreeAnswer.InputNote note) {
        String line = note.field() + ": " + note.note();
        MapScreen now = screen.setup(s -> s.notes(s.notes() == null || s.notes().isBlank() ? line
                : s.notes().contains(line) ? s.notes() : s.notes() + "\n" + line));
        MapScreen at = now;
        return now.input(note.field()).map(i -> at.withInput(note.field(), i.tried())).orElse(now);
    }
}
