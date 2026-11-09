package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Mark;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.ScreenPart;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What tells a screen, written as its parts (2026-11-02): a part that tells it already keeps doing so, a button, text
 * button or readout of the same picture, words or element is marked, and anything left is a new {@link Mark}. A
 * setup naming its markers the old way ({@code Setup.markers}, {@code absent}, {@code texts}, {@code elements}) is
 * moved so whenever 🗺 Screens or the assistant saves the screen.
 */
public final class MarkParts {

    private MarkParts() {}

    /** Whether {@code screen}'s setup still names what tells it, the way parts replace. */
    @SuppressWarnings("removal")
    public static boolean old(MapScreen screen) {
        Setup s = screen.setup();
        return !s.markers().isEmpty() || !s.absent().isEmpty() || !s.texts().isEmpty() || !s.elements().isEmpty();
    }

    /** {@code screen} with what its setup names moved to its parts; itself when its setup names none. */
    public static MapScreen move(MapScreen screen) {
        return old(screen) ? toldBy(screen, Markers.of(screen, null)) : screen;
    }

    /**
     * {@code screen} told by {@code now} and nothing else, as parts: its setup names no marker after. A part is kept,
     * marked, made absent or unmarked; a mark nothing holds is dropped. A group of pictures no button has is lost, as
     * no mark is found by any of several pictures; a tab's picked button is its way in's, never written.
     */
    public static MapScreen toldBy(MapScreen screen, Markers now) {
        return toldBy(screen, now, null);
    }

    /**
     * {@link #toldBy(MapScreen, Markers)} for a tab told by {@code tab} picked, null for none: what that button adds
     * to the set, its picked looks, is the tab's and never a part of the screen.
     */
    @SuppressWarnings("removal")
    public static MapScreen toldBy(MapScreen screen, Markers now, Button tab) {
        Left left = new Left(now);
        if (tab != null) {
            tab.looks().stream().filter(look -> look.state() == State.SELECTED)
                    .forEach(look -> left.shown.remove(look.picture().id()));
        }
        Map<String, ScreenPart> parts = screen.parts();
        Map<String, ScreenPart> settled = new LinkedHashMap<>();
        // Parts telling it already go first, so a mark is never moved to a button that has the same picture.
        parts.forEach((field, part) -> {
            if (left.keeps(part)) settled.put(field, part);
        });
        MapScreen next = new MapScreen(screen.setup().markers(List.of()).absent(List.of()).texts(List.of())
                .elements(List.of()), Map.of(), Map.of(), Map.of(), screen.actions());
        for (Map.Entry<String, ScreenPart> e : parts.entrySet()) {
            ScreenPart part = settled.containsKey(e.getKey()) ? e.getValue() : left.claim(e.getValue());
            next = switch (part) {
                case null -> next;
                case Input input -> next.withInput(e.getKey(), input);
                case Readout readout -> next.withReadout(e.getKey(), readout);
                case Mark mark -> next.withMark(e.getKey(), mark);
            };
        }
        for (Mark mark : left.marks()) next = next.withMark(next.fieldFor(name(mark)), mark);
        return next;
    }

    private static String name(Mark mark) {
        if (mark.picture() != null) return mark.picture().id();
        if (mark.caption() != null && mark.caption().words() != null) return mark.caption().words().text();
        return mark.element() == null ? "mark" : mark.element().shown();
    }

    private static boolean telling(ScreenPart part) {
        return switch (part) {
            case Mark ignored -> true;
            case Button b -> b.isMarking() || b.isAbsent();
            case TextButton t -> t.isMarking() || t.isAbsent();
            case Readout r -> r.isMarking() || r.isAbsent();
            default -> false;
        };
    }

    /** What of a set no part holds yet, each taken once. */
    private static final class Left {
        final Map<String, ImageTemplate> shown = new LinkedHashMap<>();
        final Map<String, ImageTemplate> absent = new LinkedHashMap<>();
        final List<String> groups = new ArrayList<>();
        final List<Caption> texts;
        final List<Caption> absentTexts;
        final List<Element> elements;
        final List<Element> absentElements;

        Left(Markers set) {
            set.shown().forEach(p -> shown.putIfAbsent(p.id(), p));
            set.absent().forEach(p -> absent.putIfAbsent(p.id(), p));
            set.either().forEach(group -> groups.add(key(group)));
            texts = new ArrayList<>(set.texts());
            absentTexts = new ArrayList<>(set.absentTexts());
            elements = new ArrayList<>(set.elements());
            absentElements = new ArrayList<>(set.absentElements());
        }

        /** Whether {@code part} tells the screen as the set still says, taking what it holds. */
        boolean keeps(ScreenPart part) {
            if (!telling(part)) return false;
            // No set holds it (Markers.marksAtTimes): it keeps its marking as the author wrote it.
            if (Markers.marksAtTimes(part)) return true;
            return switch (part) {
                case Mark m when m.picture() != null -> (m.isAbsent() ? absent : shown).remove(m.picture().id()) != null;
                case Mark m when m.caption() != null -> (m.isAbsent() ? absentTexts : texts).remove(m.caption());
                case Mark m -> (m.isAbsent() ? absentElements : elements).remove(m.element());
                case Button b when b.isElement() -> (b.isAbsent() ? absentElements : elements).remove(b.element());
                case Button b when b.isAbsent() -> takeAll(absent, ids(b));
                case Button b -> ids(b).size() == 1 ? shown.remove(ids(b).getFirst()) != null
                        : groups.remove(String.join("|", ids(b)));
                case TextButton t -> (t.isAbsent() ? absentTexts : texts).remove(t.caption());
                case Readout r -> (r.isAbsent() ? absentElements : elements).remove(r.element());
                default -> false;
            };
        }

        /** {@code part} telling the screen as what is left says: marked, absent, unmarked, or null for a mark gone. */
        ScreenPart claim(ScreenPart part) {
            return switch (part) {
                case Mark m when m.picture() != null -> shown.remove(m.picture().id()) != null ? Mark.of(m.picture())
                        : absent.remove(m.picture().id()) != null ? Mark.of(m.picture()).absent() : null;
                case Mark m when m.caption() != null -> texts.remove(m.caption()) ? Mark.of(m.caption())
                        : absentTexts.remove(m.caption()) ? Mark.of(m.caption()).absent() : null;
                case Mark m -> elements.remove(m.element()) ? Mark.of(m.element())
                        : absentElements.remove(m.element()) ? Mark.of(m.element()).absent() : null;
                case Button b when b.isElement() -> elements.remove(b.element()) ? b.marking()
                        : absentElements.remove(b.element()) ? b.absent() : b.unmarked();
                case Button b when b.isBeside() || b.picture() == null -> b;
                case Button b -> claimByPicture(b);
                case TextButton t when t.caption() != null -> texts.remove(t.caption()) ? t.marking()
                        : absentTexts.remove(t.caption()) ? t.absent() : t.unmarked();
                case Readout r when r.element() != null -> elements.remove(r.element()) ? r.marking()
                        : absentElements.remove(r.element()) ? r.absent() : r.unmarked();
                default -> part;
            };
        }

        /**
         * A button by its picture: its group, or its one look, marks it; every look absent makes it absent. A button of
         * several looks whose usual picture alone marked keeps that picture as a Mark: marking, any look would tell.
         */
        private Button claimByPicture(Button b) {
            List<String> ids = ids(b);
            if (groups.remove(String.join("|", ids)) || ids.size() == 1 && shown.remove(ids.getFirst()) != null) {
                return b.marking();
            }
            if (takeAll(absent, ids)) return b.absent();
            return b.unmarked();
        }

        /** New marks for what no part holds, in the set's order: pictures, words, then elements. */
        List<Mark> marks() {
            List<Mark> marks = new ArrayList<>();
            shown.values().forEach(p -> marks.add(Mark.of(p)));
            texts.forEach(c -> marks.add(Mark.of(c)));
            elements.forEach(e -> marks.add(Mark.of(e)));
            absent.values().forEach(p -> marks.add(Mark.of(p).absent()));
            absentTexts.forEach(c -> marks.add(Mark.of(c).absent()));
            absentElements.forEach(e -> marks.add(Mark.of(e).absent()));
            return marks;
        }

        private static boolean takeAll(Map<String, ImageTemplate> pictures, List<String> ids) {
            if (!pictures.keySet().containsAll(ids)) return false;
            ids.forEach(pictures::remove);
            return true;
        }

        private static List<String> ids(Button b) {
            return b.allLooks().stream().map(look -> look.picture().id()).toList();
        }

        private static String key(List<ImageTemplate> group) {
            return String.join("|", group.stream().map(ImageTemplate::id).toList());
        }
    }
}
