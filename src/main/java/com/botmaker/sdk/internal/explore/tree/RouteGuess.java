// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.internal.screens.Routes;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.Role;
import com.botmaker.shared.web.WebTree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A website screen's route, guessed from the pages that sibling links of one list lead to (web discovery spec §2): a
 * segment that differs between them, while the rest of their paths and their pages' skeletons are the same, is
 * {@code {slug}} ({@code /case-studies/stormforge} and {@code /case-studies/hogwarts-legacy} are
 * {@code /case-studies/{slug}}). A screen keeps up to {@value #EXAMPLES} of its pages as examples.
 */
public final class RouteGuess {

    /** How many of a route's pages a screen keeps as its examples. */
    public static final int EXAMPLES = 3;

    /** What a varying segment becomes. */
    static final String SLUG = "{slug}";

    private RouteGuess() {}

    /** A page reached: its path and its skeleton ({@link #skeleton}). */
    public record Page(String path, List<String> skeleton) {
        public Page {
            path = path == null ? "/" : path;
            skeleton = skeleton == null ? List.of() : List.copyOf(skeleton);
        }
    }

    /** A screen's route and the pages it keeps as examples, oldest first. */
    public record Route(String pattern, List<String> examples) {
        public Route {
            examples = examples == null ? List.of() : List.copyOf(examples);
        }

        /** Whether {@code path} is a page of this route. */
        public boolean matches(String path) {
            return Routes.matches(pattern, path);
        }

        /** This route with {@code path} as an example too, while it keeps fewer than {@value #EXAMPLES}. */
        public Route with(String path) {
            if (examples.size() >= EXAMPLES || examples.contains(path)) return this;
            List<String> more = new ArrayList<>(examples);
            more.add(path);
            return new Route(pattern, more);
        }
    }

    /**
     * A page's skeleton: the roles of its landmarks and headings in tree order, their texts aside. Two pages of one
     * template share it, whatever each says.
     */
    public static List<String> skeleton(UiTree tree) {
        List<String> roles = new ArrayList<>();
        for (UiNode node : tree.all()) {
            Role role = WebTree.roleOf(node.className()).orElse(Role.UNKNOWN);
            switch (role) {
                case HEADING, MAIN, NAVIGATION, BANNER, CONTENT_INFO, REGION, FORM, ARTICLE -> roles.add(role.id());
                default -> {
                }
            }
        }
        return List.copyOf(roles);
    }

    /**
     * The route the pages {@code siblings} share, when two or more differ in one segment only, the same one, and all
     * share one skeleton; empty otherwise. Its examples are the first {@value #EXAMPLES} pages.
     */
    public static Optional<Route> of(List<Page> siblings) {
        Set<String> paths = new LinkedHashSet<>();
        for (Page page : siblings) paths.add(bare(page.path()));
        if (paths.size() < 2) return Optional.empty();
        List<String[]> split = paths.stream().map(RouteGuess::segments).toList();
        int length = split.getFirst().length;
        if (length == 0 || split.stream().anyMatch(s -> s.length != length)) return Optional.empty();
        if (siblings.stream().map(Page::skeleton).distinct().count() > 1) return Optional.empty();
        int varying = -1;
        for (int i = 0; i < length; i++) {
            int at = i;
            if (split.stream().map(s -> s[at]).distinct().count() == 1) continue;
            if (varying >= 0) return Optional.empty();
            varying = i;
        }
        String[] pattern = split.getFirst().clone();
        pattern[varying] = SLUG;
        List<String> examples = paths.stream().limit(EXAMPLES).toList();
        return Optional.of(new Route("/" + String.join("/", pattern), examples));
    }

    /** The route among {@code known} that {@code path} is a page of, or {@code path} as its own route. */
    public static Route routeOf(String path, List<Route> known) {
        String bare = bare(path);
        for (Route route : known) {
            if (route.matches(bare)) return route.with(bare);
        }
        return new Route(bare, List.of(bare));
    }

    /** {@code path} without its query, fragment and trailing {@code /}; {@code /} for the site's root. */
    public static String bare(String path) {
        String bare = path == null ? "" : path.strip();
        // A single-page app's hash route (/#/pricing) is its path; any other fragment is a place on the page.
        int hash = bare.indexOf('#');
        if (hash >= 0 && bare.startsWith("#/", hash)) {
            String route = bare.substring(hash + 1);
            int query = route.indexOf('?');
            return bare(query < 0 ? route : route.substring(0, query));
        }
        int cut = bare.length();
        for (char mark : new char[]{'?', '#'}) {
            int at = bare.indexOf(mark);
            if (at >= 0) cut = Math.min(cut, at);
        }
        bare = bare.substring(0, cut);
        while (bare.endsWith("/")) bare = bare.substring(0, bare.length() - 1);
        return bare.startsWith("/") ? bare : "/" + bare;
    }

    private static String[] segments(String path) {
        String inner = path.substring(1);
        return inner.isEmpty() ? new String[0] : inner.split("/", -1);
    }
}
