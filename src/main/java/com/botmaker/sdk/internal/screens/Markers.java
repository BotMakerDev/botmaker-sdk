package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Mark;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.ScreenPart;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What tells a screen or a look: pictures that all show, pictures none of which may show, words that all show, each
 * in its zone, and an app's elements that all show in the screen's tree. The one rule every reader of the map tells a
 * screen by — a walk, {@code Navigate.isOn}, the assistant's tools and the audit.
 *
 * <p>A screen's set is its parts that mark and those absent from it ({@link #of(MapScreen)}), with what its setup
 * still names the old way (2026-11-02): a marking button is found by any of its looks, one group of {@link #either}.
 *
 * @param shown          pictures that all show; empty: no picture tells it
 * @param absent         pictures none of which may show
 * @param texts          words that all show, each read in its zone
 * @param elements       elements the screen's tree holds, each once or more
 * @param picked         elements the screen's tree holds picked ({@link State#SELECTED}): an app's tab button, which
 *                       shows on every tab and tells only its own picked
 * @param either         groups of pictures, one of each showing: a marking button's looks
 * @param absentTexts    words none of which may be read, each in its zone
 * @param absentElements elements the screen's tree may not hold
 */
public record Markers(List<ImageTemplate> shown, List<ImageTemplate> absent, List<Caption> texts,
                      List<Element> elements, List<Element> picked, List<List<ImageTemplate>> either,
                      List<Caption> absentTexts, List<Element> absentElements) {

    public Markers {
        shown = shown == null ? List.of() : List.copyOf(shown);
        absent = absent == null ? List.of() : List.copyOf(absent);
        texts = texts == null ? List.of() : List.copyOf(texts);
        elements = elements == null ? List.of() : List.copyOf(elements);
        picked = picked == null ? List.of() : List.copyOf(picked);
        either = either == null ? List.of() : either.stream().filter(group -> !group.isEmpty()).map(List::copyOf)
                .toList();
        absentTexts = absentTexts == null ? List.of() : List.copyOf(absentTexts);
        absentElements = absentElements == null ? List.of() : List.copyOf(absentElements);
    }

    /** Pictures, words and elements, some picked; nothing in groups, nothing but pictures absent. */
    public Markers(List<ImageTemplate> shown, List<ImageTemplate> absent, List<Caption> texts,
                   List<Element> elements, List<Element> picked) {
        this(shown, absent, texts, elements, picked, List.of(), List.of(), List.of());
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
     * What tells {@code screen}: its parts that mark or are absent, what its setup names, and for a tab its way in's
     * button picked, when that way in is the button itself. One the host reads names it by reference, so {@link
     * ScreenGraph#markers} finds it instead.
     */
    public static Markers of(MapScreen screen) {
        return of(screen, screen.isTab() && screen.fromInput() instanceof Button tab ? tab : null);
    }

    /**
     * What tells {@code screen}, a tab told by {@code tab} picked; null for none. A tab button of an app is picked in
     * the tree; one found by its picture, in its {@link State#SELECTED} looks.
     */
    public static Markers of(MapScreen screen, Button tab) {
        Sets sets = new Sets(screen);
        if (tab != null && tab.isElement()) sets.picked.add(tab.element());
        else if (tab != null) {
            tab.looks().stream().filter(look -> look.state() == State.SELECTED).map(Look::picture)
                    .forEach(sets.shown::add);
        }
        return sets.markers();
    }

    public static Markers of(ScreenVariant look) {
        return new Markers(look.markers(), look.absent());
    }

    /** A screen's set as it is gathered: its setup's lists, then each part's. */
    private static final class Sets {
        final List<ImageTemplate> shown;
        final List<ImageTemplate> absent;
        final List<Caption> texts;
        final List<Element> elements;
        final List<Element> picked = new ArrayList<>();
        final List<List<ImageTemplate>> either = new ArrayList<>();
        final List<Caption> absentTexts = new ArrayList<>();
        final List<Element> absentElements = new ArrayList<>();

        Sets(MapScreen screen) {
            // The setup's own lists: the screen's accessors are this merge.
            shown = new ArrayList<>(screen.setup().markers());
            absent = new ArrayList<>(screen.setup().absent());
            texts = new ArrayList<>(screen.setup().texts());
            elements = new ArrayList<>(screen.setup().elements());
            for (ScreenPart part : screen.parts().values()) add(part);
        }

        private void add(ScreenPart part) {
            switch (part) {
                case Mark m when m.picture() != null -> (m.isAbsent() ? absent : shown).add(m.picture());
                case Mark m when m.caption() != null -> (m.isAbsent() ? absentTexts : texts).add(m.caption());
                case Mark m when m.element() != null -> (m.isAbsent() ? absentElements : elements).add(m.element());
                case Button b when b.isElement() && (b.isMarking() || b.isAbsent()) ->
                        (b.isAbsent() ? absentElements : elements).add(b.element());
                case Button b when b.isMarking() -> {
                    List<ImageTemplate> looks = b.allLooks().stream().map(Look::picture).toList();
                    if (looks.size() == 1) shown.add(looks.getFirst());
                    else either.add(looks);
                }
                case Button b when b.isAbsent() -> b.allLooks().stream().map(Look::picture).forEach(absent::add);
                case TextButton t when t.caption() != null && (t.isMarking() || t.isAbsent()) ->
                        (t.isAbsent() ? absentTexts : texts).add(t.caption());
                case Readout r when r.isMarking() || r.isAbsent() ->
                        (r.isAbsent() ? absentElements : elements).add(r.element());
                default -> {}
            }
        }

        Markers markers() {
            return new Markers(shown, absent, texts, elements, picked, either, absentTexts, absentElements);
        }
    }

    /** This set with {@code shown} and {@code absent} as its pictures, the rest as it is. */
    public Markers withPictures(List<ImageTemplate> shown, List<ImageTemplate> absent) {
        return new Markers(shown, absent, texts, elements, picked, either, absentTexts, absentElements);
    }

    /** This set with {@code texts} as its words, the rest as it is. */
    public Markers withTexts(List<Caption> texts) {
        return new Markers(shown, absent, texts, elements, picked, either, absentTexts, absentElements);
    }

    /** Whether anything tells it: at least one marker, a picture, words or an element. */
    public boolean any() {
        return !shown.isEmpty() || !texts.isEmpty() || !elements.isEmpty() || !picked.isEmpty() || !either.isEmpty();
    }

    /** How many things tell it, each picture, group, words and element once: of two sets shown, the larger wins. */
    public int size() {
        return keys().size();
    }

    /**
     * What tells it, each named once: a picture by its id, a group by its ids joined with {@code |}, words as said and
     * an element by what finds it. One set holds another's when its keys hold all of them.
     */
    public Set<String> keys() {
        Set<String> keys = new LinkedHashSet<>(ids(shown));
        either.forEach(group -> keys.add(String.join("|", ids(group))));
        texts.forEach(words -> keys.add(words.said()));
        elements.forEach(element -> keys.add(ElementMatch.named(element)));
        picked.forEach(element -> keys.add(ElementMatch.named(element) + " picked"));
        return keys;
    }

    /** What tells it apart, each named once as {@link #keys} names it: absent pictures, words and elements. */
    public Set<String> absentKeys() {
        Set<String> keys = new LinkedHashSet<>(ids(absent));
        absentTexts.forEach(words -> keys.add(words.said()));
        absentElements.forEach(element -> keys.add(ElementMatch.named(element)));
        return keys;
    }

    /**
     * Its markers as a reply names them: {@code shop_title + shop_tab|shop_tab_on + "Shop" + element "Battery" + wifi
     * picked}.
     */
    public String ids() {
        return String.join(" + ", keys());
    }

    /** Whether {@code other} holds the same pictures, by id, and the same words and elements, in the same order. */
    public boolean same(Markers other) {
        return ids(shown).equals(ids(other.shown)) && ids(absent).equals(ids(other.absent))
                && either.stream().map(Markers::ids).toList().equals(other.either.stream().map(Markers::ids).toList())
                && texts.equals(other.texts) && elements.equals(other.elements) && picked.equals(other.picked)
                && absentTexts.equals(other.absentTexts) && absentElements.equals(other.absentElements);
    }

    private static List<String> ids(List<ImageTemplate> pictures) {
        return pictures.stream().map(ImageTemplate::id).toList();
    }

    /** Its markers and what tells it apart as a reply names them: {@code shop_title + shop_tab, not gems}. */
    public String said() {
        Set<String> not = absentKeys();
        return (any() ? ids() : "no marker") + (not.isEmpty() ? "" : ", not " + String.join(", ", not));
    }

    /** Its markers, its groups' pictures and its absent pictures, each once. */
    public List<ImageTemplate> pictures() {
        Map<String, ImageTemplate> all = new LinkedHashMap<>();
        for (ImageTemplate p : shown) all.putIfAbsent(p.id(), p);
        for (List<ImageTemplate> group : either) for (ImageTemplate p : group) all.putIfAbsent(p.id(), p);
        for (ImageTemplate p : absent) all.putIfAbsent(p.id(), p);
        return List.copyOf(all.values());
    }

    /**
     * Whether the frame {@code found} and {@code read} answer for shows it: every marker found, a picture of each group,
     * no absent picture found, every one of its words read and none of its absent words. Its elements need the
     * screen's tree, which only {@link #on(Reach, BufferedImage)} reads: a set with elements is never on here.
     */
    public boolean on(Predicate<ImageTemplate> found, Predicate<Caption> read) {
        return any() && elements.isEmpty() && picked.isEmpty() && absentElements.isEmpty()
                && shown.stream().allMatch(found) && either.stream().allMatch(group -> group.stream().anyMatch(found))
                && absent.stream().noneMatch(found) && texts.stream().allMatch(read)
                && absentTexts.stream().noneMatch(read);
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
            for (List<ImageTemplate> group : set.either) for (ImageTemplate p : group) markers.putIfAbsent(p.id(), p);
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
        Predicate<ImageTemplate> found = p -> shownNear.getOrDefault(p.id(), false);
        Predicate<Element> held = element -> inTree.computeIfAbsent(element, e -> !reach.elements(e, frame).isEmpty());
        Predicate<Caption> said = words -> read.computeIfAbsent(words, w -> reach.findText(w, frame).isPresent());
        return sets.stream().map(set -> set.any()
                && set.shown.stream().allMatch(found)
                && set.either.stream().allMatch(group -> group.stream().anyMatch(found))
                && set.absent.stream().noneMatch(p -> anywhere.getOrDefault(p.id(), false))
                && set.elements.stream().allMatch(held)
                && set.absentElements.stream().noneMatch(held)
                && set.picked.stream().allMatch(element -> pickedInTree.computeIfAbsent(element,
                        e -> reach.elements(e, frame).stream()
                                .anyMatch(node -> ElementMatch.state(node) == State.SELECTED)))
                && set.texts.stream().allMatch(said)
                && set.absentTexts.stream().noneMatch(said)).toList();
    }

    private static Map<String, Boolean> seen(Map<String, ImageTemplate> pictures, List<Optional<Rect>> found) {
        List<String> ids = new ArrayList<>(pictures.keySet());
        Map<String, Boolean> seen = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) seen.put(ids.get(i), found.get(i).isPresent());
        return seen;
    }
}
