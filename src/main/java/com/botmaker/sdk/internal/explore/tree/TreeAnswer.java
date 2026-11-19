// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Field;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.Role;
import com.botmaker.shared.web.WebTree;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import javax.lang.model.SourceVersion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A stop of <em>Keep exploring</em> that a screen's tree settles, answered by the run itself (web discovery spec §2):
 * typed, so the side that applies it writes the bot's Java by its edits, as Claude's fixes are written. Each answer
 * names what it adds by the names the bot will give it. It crosses the run's journal as JSON, tagged by its
 * {@code answer}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "answer")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TreeAnswer.NewScreen.class, name = "new_screen"),
        @JsonSubTypes.Type(value = TreeAnswer.NewRoute.class, name = "new_route"),
        @JsonSubTypes.Type(value = TreeAnswer.NewInput.class, name = "new_input"),
        @JsonSubTypes.Type(value = TreeAnswer.NewWay.class, name = "new_way"),
        @JsonSubTypes.Type(value = TreeAnswer.InputNote.class, name = "input_note"),
        @JsonSubTypes.Type(value = TreeAnswer.InputRule.class, name = "input_rule"),
        @JsonSubTypes.Type(value = TreeAnswer.NewTarget.class, name = "new_target")})
public sealed interface TreeAnswer {

    /**
     * A screen to add: {@code name} its class, {@code route} and {@code examples} a website's (null and none in an
     * app), {@code markers} the parts that tell it, {@code overlay} what is open over its page.
     */
    record NewScreen(String name, String route, List<String> examples, List<Locator> markers, Overlay overlay)
            implements TreeAnswer {
        public NewScreen {
            examples = examples == null ? List.of() : List.copyOf(examples);
            markers = markers == null ? List.of() : List.copyOf(markers);
            overlay = overlay == null ? Overlay.NONE : overlay;
        }
    }

    /**
     * {@code screen}'s route widened to {@code route}, with {@code examples}: a sibling page of the one it was made
     * for showed the same skeleton ({@link RouteGuess#of}).
     */
    record NewRoute(String screen, String route, List<String> examples) implements TreeAnswer {
        public NewRoute {
            examples = examples == null ? List.of() : List.copyOf(examples);
        }
    }

    /**
     * An input to add on {@code screen} as its field {@code field}, found by {@code locator}; {@code never} why the
     * guard refuses it, null when it is free; {@code picture} the file the run rendered it to, null for none.
     */
    record NewInput(String screen, String field, Locator locator, String never, String picture)
            implements TreeAnswer {
        public NewInput {
            picture = picture == null || picture.isBlank() ? null : picture;
        }

        /** An input with no picture. */
        public NewInput(String screen, String field, Locator locator, String never) {
            this(screen, field, locator, never, null);
        }
    }

    /** A way: {@code field} of {@code screen} leads to the screen {@code to}. */
    record NewWay(String screen, String field, String to) implements TreeAnswer {}

    /** A note on {@code field} of {@code screen}: what pressing it does, when that is no way. */
    record InputNote(String screen, String field, String note) implements TreeAnswer {}

    /**
     * A form's rule (web discovery phase 7): {@code field} of {@code screen} is enabled when every one of
     * {@code checks} holds, each of a field of the same screen, as the form's tries showed; {@code note} the rule and
     * the tries that showed it.
     */
    record InputRule(String screen, String field, List<FieldCheck> checks, String note) implements TreeAnswer {
        public InputRule {
            checks = checks == null ? List.of() : List.copyOf(checks);
        }
    }

    /**
     * One check of what the field {@code field} holds: {@code text} the {@link Field.Kind} id of {@link Test#VALID}
     * or the text of {@link Test#HOLDS} and {@link Test#LACKS}, {@code characters} that of {@link Test#AT_LEAST},
     * {@code min} and {@code max} those of {@link Test#IN_RANGE}, null for an open side.
     */
    record FieldCheck(String field, Test test, String text, Integer characters, Double min, Double max) {

        /** Which {@link Field} check it is. */
        public enum Test { FILLED, VALID, HOLDS, LACKS, AT_LEAST, IN_RANGE }

        public static FieldCheck filled(String field) {
            return new FieldCheck(field, Test.FILLED, null, null, null, null);
        }

        public static FieldCheck valid(String field, Field.Kind kind) {
            return new FieldCheck(field, Test.VALID, kind.id(), null, null, null);
        }

        public static FieldCheck holds(String field, String text) {
            return new FieldCheck(field, Test.HOLDS, text, null, null, null);
        }

        public static FieldCheck atLeast(String field, int characters) {
            return new FieldCheck(field, Test.AT_LEAST, null, characters, null, null);
        }

        public static FieldCheck inRange(String field, Double min, Double max) {
            return new FieldCheck(field, Test.IN_RANGE, null, null, min, max);
        }

        /**
         * The check, of the field {@code input} gives: an open side of a range the largest double, as a bot writes it.
         *
         * @throws IllegalArgumentException for a check its parts cannot make, as {@link Field}'s own refuse
         */
        public Field check(Supplier<Button> input) {
            return switch (test) {
                case FILLED -> Field.filled(input);
                case VALID -> Field.valid(input, Field.Kind.fromId(text));
                case HOLDS -> Field.holds(input, text);
                case LACKS -> Field.lacks(input, text);
                case AT_LEAST -> Field.atLeast(input, characters == null ? 0 : characters);
                case IN_RANGE -> {
                    // Both sides open says nothing: refused, never written as a range every number passes.
                    if (min == null && max == null) throw new IllegalArgumentException("a range with no bound");
                    yield Field.inRange(input, min == null ? -Double.MAX_VALUE : min,
                            max == null ? Double.MAX_VALUE : max);
                }
            };
        }
    }

    /**
     * The launch spec {@code spec} made the bot's preferred launch target: <em>Discover</em> started on a bot with none
     * ({@code web:https://…}).
     */
    record NewTarget(String spec) implements TreeAnswer {}

    /** How many marker parts a new screen gets. */
    int MARKERS = 2;

    /**
     * The screen the page {@code tree} shows, of {@code route} (null in an app) with {@code examples}, named after
     * what is open over it, else its route, else its first heading, never one of {@code taken}.
     *
     * <p>A website's page with nothing open over it is told by its route alone: its headings are content, which a
     * page changes as it likes (a hero that cycles its headline, a case study's title), so a marker of them stops
     * matching the very page it was cut from. Two pages at one route are one screen; a dialog or a menu over it is
     * its own, told by its route and by itself, so it outranks the page under it.
     */
    static NewScreen screen(UiTree tree, String route, List<String> examples, Set<String> taken) {
        Overlay overlay = Overlay.of(tree);
        boolean page = route != null && overlay.none();
        String said = !overlay.name().isEmpty() ? overlay.name() : page ? lastSegment(route)
                : firstHeading(tree).orElseGet(() -> route == null ? "Screen" : lastSegment(route));
        return new NewScreen(unique(className(said), taken), route, examples,
                page ? List.of() : markers(tree, overlay), overlay);
    }

    /**
     * The inputs of {@code screen} its tree shows that none of {@code known} already finds, each named after its
     * locator, never one of {@code taken} (the screen's field names), and checked by {@code refusal}: why the guard
     * refuses it, or empty when it is free.
     */
    static List<NewInput> inputs(String screen, UiTree tree, Set<Locator> known, Set<String> taken,
                                 Function<Locator.Located, Optional<String>> refusal) {
        return inputs(screen, tree, known, taken, refusal, (field, located) -> null);
    }

    /** As above, each input given the picture {@code picture} renders of it, by its field's name: a file, or null. */
    static List<NewInput> inputs(String screen, UiTree tree, Set<Locator> known, Set<String> taken,
                                 Function<Locator.Located, Optional<String>> refusal,
                                 BiFunction<String, Locator.Located, String> picture) {
        List<NewInput> inputs = new ArrayList<>();
        Set<String> names = new HashSet<>(taken);
        for (Locator.Located located : Locator.inputs(tree)) {
            if (known.contains(located.locator())) continue;
            String field = unique(fieldName(said(located.locator())), names);
            names.add(field);
            inputs.add(new NewInput(screen, field, located.locator(), refusal.apply(located).orElse(null),
                    picture.apply(field, located)));
        }
        return List.copyOf(inputs);
    }

    /**
     * The control of {@code screen} labelled {@code label} that {@link Locator#inputs} leaves out, being disabled now: a
     * form's submit its rule enables. Named after its locator, never one of {@code taken}; empty when the tree has no
     * website control of that label, or more than one.
     */
    public static Optional<NewInput> disabledInput(String screen, UiTree tree, String label, Set<String> taken) {
        ElementMatch.Matcher matcher = ElementMatch.in(tree);
        List<UiNode> labelled = matcher.nodes().stream().filter(node -> WebTree.roleOf(node.className()).isPresent()
                && !node.has(Trait.ENABLED) && node.label().strip().equalsIgnoreCase(label.strip())).toList();
        if (labelled.size() != 1) return Optional.empty();
        return Locator.of(labelled.getFirst(), matcher).map(locator -> new NewInput(screen,
                unique(fieldName(said(locator)), new HashSet<>(taken)), locator, null));
    }

    /**
     * What a press of {@code field} on {@code screen} did, as the bot keeps it: a way to the screen {@code screenOf}
     * names for a navigation or an overlay, else a note; empty for a way whose screen the bot has not yet, which the
     * next round adds and links.
     */
    static Optional<TreeAnswer> effect(String screen, String field, Effect effect,
                                       Function<Effect, Optional<String>> screenOf) {
        if (!effect.way()) return Optional.of(new InputNote(screen, field, effect.line()));
        return screenOf.apply(effect).map(to -> new NewWay(screen, field, to));
    }

    /**
     * The parts that tell the screen: with a dialog or a menu open, its own part and what is in it only, so the page
     * under it marks nothing; then its headings, else its first named parts.
     */
    private static List<Locator> markers(UiTree tree, Overlay overlay) {
        ElementMatch.Matcher matcher = ElementMatch.in(tree);
        List<Locator> markers = new ArrayList<>();
        List<UiNode> others = new ArrayList<>();
        List<UiNode> scope = matcher.nodes();
        for (UiNode node : matcher.nodes()) {
            Role role = WebTree.roleOf(node.className()).orElse(Role.UNKNOWN);
            if ((role == Role.DIALOG || role == Role.ALERT_DIALOG) && overlay.kind() == Overlay.Kind.DIALOG
                    || role == Role.MENU && overlay.kind() == Overlay.Kind.MENU) {
                Locator.of(node, matcher).ifPresent(markers::add);
                List<UiNode> inside = new UiTree(List.of(node)).all();
                scope = inside.subList(1, inside.size());
                break;
            }
        }
        for (UiNode node : scope) {
            Role role = WebTree.roleOf(node.className()).orElse(Role.UNKNOWN);
            if (role == Role.HEADING && !node.label().isBlank()) {
                Locator.of(node, matcher).ifPresent(markers::add);
            } else if (!Locator.acted(node) && node.children().isEmpty()) {
                others.add(node);
            }
        }
        for (UiNode node : others) {
            if (markers.size() >= MARKERS) break;
            Locator.of(node, matcher).filter(l -> l.index() == null).ifPresent(markers::add);
        }
        return List.copyOf(markers.subList(0, Math.min(markers.size(), MARKERS)));
    }

    private static Optional<String> firstHeading(UiTree tree) {
        for (UiNode node : tree.all()) {
            if (WebTree.roleOf(node.className()).orElse(Role.UNKNOWN) == Role.HEADING && !node.label().isBlank()) {
                return Optional.of(node.label());
            }
        }
        return Optional.empty();
    }

    private static String lastSegment(String route) {
        String bare = RouteGuess.bare(route);
        if (bare.equals("/")) return "Home";
        int slash = bare.lastIndexOf('/');
        String last = bare.substring(slash + 1);
        if (!last.equals(RouteGuess.SLUG)) return last;
        return slash == 0 ? "Page" : bare.substring(1, slash).replace('/', ' ') + " page";
    }

    private static String said(Locator locator) {
        if (locator.name() != null) return locator.name();
        if (locator.text() != null) return locator.text();
        if (locator.id() != null) return locator.id();
        return locator.role() == null ? "input" : locator.role().id() + (locator.index() == null ? "" : " " + locator.index());
    }

    /** {@code "talk to-us!"} as {@code TalkToUs}; {@code Screen} when nothing is left. */
    static String upperCamel(String said) {
        StringBuilder name = new StringBuilder();
        for (String word : said.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
            if (name.length() >= 40) break;
        }
        String done = name.isEmpty() ? "Screen" : name.toString();
        return Character.isDigit(done.charAt(0)) ? "S" + done : done;
    }

    static String lowerCamel(String said) {
        String upper = upperCamel(said);
        return Character.toLowerCase(upper.charAt(0)) + upper.substring(1);
    }

    /** A screen's class name: {@code Screen} after one a bot's code would mistake for {@code java.lang}'s. */
    static String className(String said) {
        String name = upperCamel(said);
        return javaLang(name) ? name + "Screen" : name;
    }

    /** An input's field name: {@code Input} after a Java keyword ({@code new}, {@code default}). */
    static String fieldName(String said) {
        String name = lowerCamel(said);
        return SourceVersion.isKeyword(name) ? name + "Input" : name;
    }

    private static boolean javaLang(String name) {
        try {
            Class.forName("java.lang." + name, false, TreeAnswer.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** {@code name}, or with the first number from 2 that makes it none of {@code taken}. */
    static String unique(String name, Set<String> taken) {
        if (!taken.contains(name)) return name;
        for (int n = 2; ; n++) {
            if (!taken.contains(name + n)) return name + n;
        }
    }
}
