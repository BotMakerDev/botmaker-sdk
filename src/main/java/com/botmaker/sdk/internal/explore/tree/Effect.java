// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.Guard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one press did, judged from the screen's tree before and after it and what the guard stopped (web discovery
 * spec §2, step 5). A press that navigates or opens an overlay is a way; every other effect is a note on its input.
 */
public sealed interface Effect {

    /** The press led to another page, at {@code path}. */
    record Navigates(String path) implements Effect {}

    /** The press opened {@code overlay} over the same page. */
    record Opens(Overlay overlay) implements Effect {}

    /** The press changed {@code parts} of the page, each a line saying the part and what it is now. */
    record Changes(List<String> parts) implements Effect {
        public Changes {
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }

    /** The page answered the press with an error it shows, {@code text}. */
    record ShowsError(String text) implements Effect {}

    /** Nothing a tester sees changed. */
    record Nothing() implements Effect {}

    /** How a {@link Fails}'s {@link #line()} begins. */
    String FAILS = "Fails: ";

    /** The press failed: a write the guard blocked, a page robots.txt keeps out, or an error in the console. */
    record Fails(String why) implements Effect {}

    /** The press leads off the site, to {@code url}; the guard kept it from loading. */
    record Exits(String url) implements Effect {}

    /** Whether the bot writes this effect as a way to a screen; a note on the input otherwise. */
    default boolean way() {
        return this instanceof Navigates || this instanceof Opens;
    }

    /** One line for a note or a report: {@code Leaves the site for https://x.com}. */
    default String line() {
        return switch (this) {
            case Navigates(String path) -> "Goes to " + path;
            case Opens(Overlay overlay) -> "Opens " + overlay.line();
            case Changes(List<String> parts) -> "Changes " + String.join("; ", parts);
            case ShowsError(String text) -> "Shows the error \"" + text + "\"";
            case Nothing() -> "Does nothing a visitor sees";
            case Fails(String why) -> FAILS + why;
            case Exits(String url) -> "Leaves the site for " + url;
        };
    }

    /**
     * What a screen showed when it was read: its tree, its place (a website's path, an app's activity; {@code ""}
     * when not known), the errors it shows (a field's message, an alert) and the errors its console printed since the
     * last read.
     */
    record State(UiTree tree, String place, List<String> errorsShown, List<String> consoleErrors) {
        public State {
            tree = tree == null ? new UiTree(List.of()) : tree;
            place = place == null ? "" : place;
            errorsShown = errorsShown == null ? List.of() : List.copyOf(errorsShown);
            consoleErrors = consoleErrors == null ? List.of() : List.copyOf(consoleErrors);
        }

        /** A state of {@code tree} at {@code place}, no error shown or printed. */
        public static State of(UiTree tree, String place) {
            return new State(tree, place, List.of(), List.of());
        }
    }

    /**
     * The effect of a press from {@code before} to {@code after}, {@code stopped} what the guard stopped meanwhile: an
     * exit first, then another page, an overlay opened, then a blocked write or a page robots.txt keeps out, an error
     * shown, an error printed, parts changed, and last nothing.
     *
     * <p>A page that came is the press's doing whatever the guard stopped beside it: a site posts its own telemetry
     * ({@code POST /monitoring}) on every page, and that write blocked takes nothing from the way. A write the press
     * itself made, a form sent, leaves the page where it was, so it is still told.
     */
    static Effect judge(State before, State after, List<Guard.Stopped> stopped) {
        List<Guard.Stopped> stops = stopped == null ? List.of() : stopped;
        for (Guard.Stopped stop : stops) {
            if (stop.kind() == Guard.Kind.EXIT) return new Exits(stop.url());
        }
        // A place read on one side only says nothing; a website's path is compared bare, an app's activity as it is.
        if (!before.place().isEmpty() && !after.place().isEmpty()) {
            String was = path(before.place());
            String now = path(after.place());
            if (!was.equals(now)) return new Navigates(now);
        }
        Overlay was = Overlay.of(before.tree());
        Overlay now = Overlay.of(after.tree());
        if (!now.none() && !now.equals(was)) return new Opens(now);
        for (Guard.Stopped stop : stops) {
            if (stop.kind() == Guard.Kind.WRITE) {
                return new Fails("blocked write: " + (stop.method().isEmpty() ? "" : stop.method() + " ") + stop.url());
            }
            if (stop.kind() == Guard.Kind.ROBOTS) return new Fails("not visited (robots.txt): " + stop.url());
        }
        for (String error : after.errorsShown()) {
            if (!before.errorsShown().contains(error)) return new ShowsError(error);
        }
        if (!after.consoleErrors().isEmpty()) return new Fails("console error: " + after.consoleErrors().getFirst());
        List<String> changed = changed(before.tree(), after.tree());
        if (!was.equals(now)) changed.addFirst("closes " + was.line());
        return changed.isEmpty() ? new Nothing() : new Changes(changed);
    }

    /** {@code place} as compared: a website's path bare, an app's activity as it is. */
    private static String path(String place) {
        return place.startsWith("/") ? RouteGuess.bare(place) : place;
    }

    /** The parts whose state differs from {@code before} to {@code after}, or that one of them lacks. */
    private static List<String> changed(UiTree before, UiTree after) {
        Map<String, String> was = states(before);
        Map<String, String> now = states(after);
        List<String> changed = new ArrayList<>();
        now.forEach((part, state) -> {
            if (!was.containsKey(part)) changed.add(part + " shown");
            else if (!Objects.equals(was.get(part), state)) changed.add(part + " " + state);
        });
        was.keySet().forEach(part -> {
            if (!now.containsKey(part)) changed.add(part + " hidden");
        });
        return changed;
    }

    /** Each input of {@code tree} by its locator's line, and its state: its value and its traits. */
    private static Map<String, String> states(UiTree tree) {
        Map<String, String> states = new LinkedHashMap<>();
        for (Locator.Located located : Locator.inputs(tree)) {
            UiNode node = located.node();
            Set<String> traits = new TreeSet<>();
            for (Trait trait : Trait.values()) {
                if (trait != Trait.FOCUSED && node.has(trait)) traits.add(trait.name().toLowerCase());
            }
            String value = node.text().isBlank() || node.text().equals(node.label()) ? "" : "\"" + node.text() + "\" ";
            states.put(located.locator().line(), (value + String.join(" ", traits)).strip());
        }
        return states;
    }
}
