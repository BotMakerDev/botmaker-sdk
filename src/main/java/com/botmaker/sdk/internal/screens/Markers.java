package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What tells a screen or a look: pictures that all show, pictures none of which may show, words that all show, each
 * in its zone, and an app's elements that all show in the screen's tree. The one rule every reader of the map tells a
 * screen by — a walk, {@code Navigate.isOn}, the assistant's tools and the audit.
 *
 * @param shown    pictures that all show; empty: no picture tells it
 * @param absent   pictures none of which may show
 * @param texts    words that all show, each read in its zone
 * @param elements elements the screen's tree holds, each once or more
 * @param picked   elements the screen's tree holds picked ({@link State#SELECTED}): an app's tab button, which shows
 *                 on every tab and tells only its own picked
 */
public record Markers(List<ImageTemplate> shown, List<ImageTemplate> absent, List<Caption> texts,
                      List<Element> elements, List<Element> picked) {

    public Markers {
        shown = shown == null ? List.of() : List.copyOf(shown);
        absent = absent == null ? List.of() : List.copyOf(absent);
        texts = texts == null ? List.of() : List.copyOf(texts);
        elements = elements == null ? List.of() : List.copyOf(elements);
        picked = picked == null ? List.of() : List.copyOf(picked);
    }

    /** Pictures, words and elements, none of them picked. */
    public Markers(List<ImageTemplate> shown, List<ImageTemplate> absent, List<Caption> texts,
                   List<Element> elements) {
        this(shown, absent, texts, elements, List.of());
    }

    /** Pictures and words, no element. */
    public Markers(List<ImageTemplate> shown, List<ImageTemplate> absent, List<Caption> texts) {
        this(shown, absent, texts, List.of());
    }

    /** Pictures only, no words. */
    public Markers(List<ImageTemplate> shown, List<ImageTemplate> absent) {
        this(shown, absent, List.of());
    }

    /**
     * What tells {@code screen}: its own markers, and for a tab its way in's button picked, when that way in is the
     * button itself. One the host reads names it by reference, so {@link ScreenGraph#markers} finds it instead.
     */
    public static Markers of(MapScreen screen) {
        return of(screen, screen.isTab() && screen.fromInput() instanceof Button tab ? tab : null);
    }

    /**
     * What tells {@code screen}, a tab told by {@code tab} picked; null for none. A tab button of an app is picked in
     * the tree; one found by its picture, in its {@link State#SELECTED} looks.
     */
    static Markers of(MapScreen screen, Button tab) {
        if (tab == null) return new Markers(screen.markers(), screen.absent(), screen.texts(), screen.elements());
        if (tab.isElement()) {
            return new Markers(screen.markers(), screen.absent(), screen.texts(), screen.elements(),
                    List.of(tab.element()));
        }
        List<ImageTemplate> shown = new ArrayList<>(screen.markers());
        tab.looks().stream().filter(look -> look.state() == State.SELECTED).map(Look::picture).forEach(shown::add);
        return new Markers(shown, screen.absent(), screen.texts(), screen.elements());
    }

    public static Markers of(ScreenVariant look) {
        return new Markers(look.markers(), look.absent());
    }

    /** Whether anything tells it: at least one marker, a picture, words or an element. */
    public boolean any() {
        return !shown.isEmpty() || !texts.isEmpty() || !elements.isEmpty() || !picked.isEmpty();
    }

    /** Its markers as a reply names them: {@code shop_title + shop_tab + "Shop" + element "Battery" + wifi picked}. */
    public String ids() {
        List<String> all = new ArrayList<>(shown.stream().map(ImageTemplate::id).toList());
        all.addAll(texts.stream().map(Caption::said).toList());
        all.addAll(elements.stream().map(ElementMatch::named).toList());
        all.addAll(picked.stream().map(element -> ElementMatch.named(element) + " picked").toList());
        return String.join(" + ", all);
    }

    /** Whether {@code other} holds the same pictures, by id, and the same words and elements, in the same order. */
    public boolean same(Markers other) {
        return ids(shown).equals(ids(other.shown)) && ids(absent).equals(ids(other.absent))
                && texts.equals(other.texts) && elements.equals(other.elements) && picked.equals(other.picked);
    }

    private static List<String> ids(List<ImageTemplate> pictures) {
        return pictures.stream().map(ImageTemplate::id).toList();
    }

    /** Its markers and its absent pictures as a reply names them: {@code shop_title + shop_tab, not gems}. */
    public String said() {
        return (any() ? ids() : "no marker") + (absent.isEmpty() ? ""
                : ", not " + String.join(", ", absent.stream().map(ImageTemplate::id).toList()));
    }

    /** Its markers and its absent pictures, each once. */
    public List<ImageTemplate> pictures() {
        Map<String, ImageTemplate> all = new LinkedHashMap<>();
        for (ImageTemplate p : shown) all.putIfAbsent(p.id(), p);
        for (ImageTemplate p : absent) all.putIfAbsent(p.id(), p);
        return List.copyOf(all.values());
    }

    /**
     * Whether the frame {@code found} and {@code read} answer for shows it: every marker found, no absent picture
     * found, and every one of its words read. Its elements need the screen's tree, which only {@link #on(Reach,
     * BufferedImage)} reads: a set with elements is never on here.
     */
    public boolean on(Predicate<ImageTemplate> found, Predicate<Caption> read) {
        return any() && elements.isEmpty() && picked.isEmpty() && shown.stream().allMatch(found) && absent.stream().noneMatch(found)
                && texts.stream().allMatch(read);
    }

    /** Whether {@code frame} shows it, by {@code reach}. */
    public boolean on(Reach reach, BufferedImage frame) {
        return onEach(reach, List.of(this), frame).getFirst();
    }

    /**
     * For each of {@code sets}, whether {@code frame} shows it: every picture of them looked for once, all on that one
     * frame, so a picture shared by several sets costs one match. A marker is looked for near its place
     * ({@link Reach#findEachNear}); an absent picture anywhere, as one shown elsewhere still tells the screens apart.
     */
    public static List<Boolean> onEach(Reach reach, List<Markers> sets, BufferedImage frame) {
        return onEach(reach, sets, frame, true);
    }

    /**
     * {@link #onEach}, each marker looked for over the whole frame unless {@code near}: a window whose shape changed
     * since the cut (its UI pinned to the edges) shows its titles away from their places. The whole frame costs many
     * times more, so it is the fallback when no set shows near.
     *
     * <p>Elements are found in the one tree read for the frame ({@link Reach#elements}), after the pictures and only
     * for a set they leave in. Words are read last, and only for a set the rest leaves in: reading costs many times a
     * picture's look, so the pictures decide whenever they can. Words several sets name are read once.
     */
    public static List<Boolean> onEach(Reach reach, List<Markers> sets, BufferedImage frame, boolean near) {
        Map<String, ImageTemplate> markers = new LinkedHashMap<>();
        Map<String, ImageTemplate> absents = new LinkedHashMap<>();
        for (Markers set : sets) {
            for (ImageTemplate p : set.shown) markers.putIfAbsent(p.id(), p);
            for (ImageTemplate p : set.absent) absents.putIfAbsent(p.id(), p);
        }
        List<ImageTemplate> looked = List.copyOf(markers.values());
        Map<String, Boolean> shownNear = looked.isEmpty() ? Map.of() : seen(markers,
                near ? reach.findEachNear(looked, frame) : reach.findEach(looked, frame));
        Map<String, Boolean> anywhere = absents.isEmpty() ? Map.of()
                : seen(absents, reach.findEach(List.copyOf(absents.values()), frame));
        Map<Element, Boolean> inTree = new IdentityHashMap<>();
        Map<Element, Boolean> pickedInTree = new IdentityHashMap<>();
        Map<Caption, Boolean> read = new HashMap<>();
        return sets.stream().map(set -> set.any()
                && set.shown.stream().allMatch(p -> shownNear.getOrDefault(p.id(), false))
                && set.absent.stream().noneMatch(p -> anywhere.getOrDefault(p.id(), false))
                && set.elements.stream().allMatch(element -> inTree.computeIfAbsent(element,
                        e -> !reach.elements(e, frame).isEmpty()))
                && set.picked.stream().allMatch(element -> pickedInTree.computeIfAbsent(element,
                        e -> reach.elements(e, frame).stream()
                                .anyMatch(node -> ElementMatch.state(node) == State.SELECTED)))
                && set.texts.stream().allMatch(words -> read.computeIfAbsent(words,
                        w -> reach.findText(w, frame).isPresent()))).toList();
    }

    private static Map<String, Boolean> seen(Map<String, ImageTemplate> pictures, List<Optional<Rect>> found) {
        List<String> ids = new ArrayList<>(pictures.keySet());
        Map<String, Boolean> seen = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) seen.put(ids.get(i), found.get(i).isPresent());
        return seen;
    }
}
