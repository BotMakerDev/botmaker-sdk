package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Grid;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.plugin.assist.MapTools.Mark;
import com.botmaker.sdk.plugin.assist.PictureTools.Box;
import com.botmaker.sdk.plugin.assist.PictureTools.Corner;
import com.botmaker.sdk.plugin.assist.PictureTools.Skip;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.CutShape;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Cuts drawn by hand on 🗺's board, on a frame it froze (the user's call, 2026-10-18), saved by the code the tools run:
 * crop_pictures for a picture of a screen, save_region for a zone, save_digits for digits and edit_pictures' replace
 * for a picture cut anew. So both paths write the same, and refuse the same.
 */
public final class BoardCuts {

    /** What a cut is. */
    public sealed interface As {
    }

    /** The screen's marker: the first replaces the ones it has, the next ones join it. */
    public record Marker(boolean popup) implements As {
    }

    /** One more marker, keeping the ones the screen has. */
    public record MoreMarker(boolean popup) implements As {
    }

    /** A picture the screen never shows, where a screen like it does. */
    public record Absent() implements As {
    }

    /**
     * One of its buttons, shown only at times when {@code sometimes}, only while {@code when} holds when given, pressed
     * by {@code gesture} (null: a click).
     */
    public record Press(boolean sometimes, String when, Gesture gesture) implements As {
    }

    /** Its way back: its close button. */
    public record Back() implements As {
    }

    /** Another look of the button whose picture is {@code input}, meaning {@code state}. */
    public record LookOf(String input, State state) implements As {
    }

    /** Part of the screen's look {@code look}: what tells the look apart when {@code marker}, else an input of it. */
    public record ScreenLook(String look, boolean marker, String when) implements As {
    }

    /**
     * A button of the screen's strip or tree {@code group}, by its field (null: a new one, a strip turning by
     * {@code notches} of the wheel; the cuts of one save marked so make one new strip, or tree, together).
     */
    public record Member(String group, boolean tree, int notches) implements As {
    }

    /** An area of the game, a Zones constant. */
    public record Place(Anchor anchor) implements As {
    }

    /**
     * A zone around words the screen shows, {@code text} as the game writes them: what tells the screen, or a text
     * button clicked on them when {@code button}, its field named from the cut's name. The zone is saved in Zones
     * under that name.
     */
    public record Reads(String text, boolean button) implements As {
    }

    /**
     * A button with no picture of its own, pressed beside the picture {@code anchor}: the cut is the spot, or a grid's
     * {@code rows} by {@code columns} cells, each an equal share of it. Its field is named from the cut's name.
     */
    public record Spot(String anchor, int rows, int columns) implements As {
    }

    /** The digits of the number it shows, {@code shows} as the game writes it. */
    public record Digits(String shows) implements As {
    }

    /** A new image for the picture {@code picture}, keeping its name and every use. */
    public record Recut(String picture) implements As {
    }

    /**
     * One cut in the frame's pixels: {@code box} around it, {@code points} a polygon's corners (else none), and
     * {@code ignore} the part of it matching skips (null: none).
     */
    public record Cut(String name, Rect box, CutShape shape, List<Point> points, Rect ignore, As as) {

        public Cut {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    private BoardCuts() {}

    /**
     * What a save said, in sentences, and the places in its list of the cuts refused: the board keeps those, to fix
     * and save again, and drops the ones saved.
     */
    public record Saved(String said, List<Integer> refused) {

        public Saved {
            refused = List.copyOf(refused);
        }

        public boolean anyRefused() {
            return !refused.isEmpty();
        }
    }

    /**
     * {@code cuts}, drawn on {@code frame}, saved: the pictures noted on {@code screen} (null: on none) and tagged
     * {@code tags}. {@code watched} when the game still shows the frame: each picture is then watched still, as
     * crop_pictures watches it; a frame a run saved or a screen's shot is not. Off the FX thread.
     */
    public static Saved save(AgentContext context, String screen, List<String> tags, List<Cut> cuts,
                             BufferedImage frame, boolean watched) {
        Optional<BufferedImage> shown = Optional.of(frame);
        Supplier<List<BufferedImage>> live = watched ? PictureTools.liveLooks(context) : List::of;
        List<String> said = new ArrayList<>();
        List<Integer> refused = new ArrayList<>();
        List<Box> boxes = cuts.stream().filter(BoardCuts::picture).map(BoardCuts::box).toList();
        if (!boxes.isEmpty()) {
            AgentReply cropped = PictureTools.cropPictures(new PictureTools.Crops(boxes, screen, tags), context, shown,
                    live);
            String text = ScreenDesk.text(cropped);
            for (int i = 0; i < cuts.size(); i++) {
                // All refused, or each box refused on its own while the others were saved.
                if (picture(cuts.get(i)) && (cropped.isRefused() || notSaved(text, cuts.get(i).name()))) refused.add(i);
            }
            said.add(text);
        }
        Map<Integer, List<Integer>> joined = joined(cuts);
        for (int i = 0; i < cuts.size(); i++) {
            Cut cut = cuts.get(i);
            Rect b = cut.box();
            // A cut joining the new strip or tree an earlier one of the form made was written with it.
            if (joined.containsKey(i) && joined.get(i).isEmpty()) continue;
            AgentReply reply = switch (cut.as()) {
                case Place place -> PictureTools.saveRegion(new PictureTools.NamedRegion(cut.name(), b.x(), b.y(),
                        b.width(), b.height(), place.anchor()), context, shown);
                case Reads ignored -> PictureTools.saveRegion(new PictureTools.NamedRegion(cut.name(), b.x(), b.y(),
                        b.width(), b.height(), Anchor.CENTER), context, shown);
                case Digits digits -> PictureTools.saveDigits(new PictureTools.NamedDigits(cut.name(), b.x(), b.y(),
                        b.width(), b.height(), digits.shows()), context, shown);
                case Recut recut -> PictureTools.replacePicture(replace(cut, recut.picture()), context, shown, live);
                default -> null;
            };
            // A member whose picture was not saved stays refused alone: the others join without it, as the first of
            // them says (a new strip's notches).
            List<Integer> writes = joined.getOrDefault(i, List.of(i)).stream().filter(at -> !refused.contains(at))
                    .toList();
            if (cut.as() instanceof Member && writes.isEmpty()) continue;
            String one = cut.as() instanceof Member ? member(context, screen,
                    writes.stream().map(at -> cuts.get(at).name()).toList(), (Member) cuts.get(writes.getFirst()).as())
                    : reply == null ? null : ScreenDesk.text(reply);
            if (reply != null ? reply.isRefused() : one != null && one.startsWith(ScreenEdits.NOT_WRITTEN)) {
                for (int at : writes) if (!refused.contains(at)) refused.add(at);
            }
            if (one != null && !one.isBlank()) said.add(one);
        }
        // Words are written on the screen once their zone is saved, as edit_map's add_text writes them.
        for (int i = 0; i < cuts.size(); i++) {
            if (screen == null || refused.contains(i) || !(cuts.get(i).as() instanceof Reads reads)) continue;
            String zone = FlowNames.constantFor(cuts.get(i).name());
            String field = reads.button() ? cuts.get(i).name() : null;
            String one = FxCall.call(() -> ScreenEdits.addText(context.services(), screen, zone, reads.text(), field));
            if (one.startsWith(ScreenEdits.NOT_WRITTEN)) refused.add(i);
            said.add(one);
        }
        // A spot is measured from its anchor where the anchor was cut, as edit_map's add_spot writes it.
        for (int i = 0; i < cuts.size(); i++) {
            if (!(cuts.get(i).as() instanceof Spot spot)) continue;
            Cut cut = cuts.get(i);
            String one = screen == null ? ScreenEdits.NOT_WRITTEN + cut.name() + ": a spot is a screen's: pick it."
                    : FxCall.call(() -> spot(context, screen, cut, spot));
            if (one.startsWith(ScreenEdits.NOT_WRITTEN)) refused.add(i);
            said.add(one);
        }
        for (int i = 0; i < cuts.size(); i++) {
            if (screen == null || refused.contains(i) || !(cuts.get(i).as() instanceof Press press)
                    || press.gesture() == null || press.gesture() == Gesture.CLICK) continue;
            String id = TemplateLibrary.sanitizeName(cuts.get(i).name());
            Optional<String> field = FxCall.call(() -> ScreenBook.read(context.services()).screen(screen))
                    .flatMap(on -> on.inputs().entrySet().stream()
                            .filter(e -> e.getValue() instanceof Button b && b.picture() != null
                                    && b.picture().id().equalsIgnoreCase(id))
                            .map(Map.Entry::getKey).findFirst());
            field.ifPresent(f -> said.add(FxCall.call(() -> ScreenEdits.setGesture(context.services(), screen, f,
                    press.gesture(), null))));
        }
        return new Saved(String.join("\n", said), refused.stream().sorted().toList());
    }

    /**
     * The cuts each member cut writes, by place in {@code cuts}: one joining a strip or tree the screen has, itself;
     * the first marked a new strip, every cut so marked, in order, as one new strip, and the others none; the same for
     * a new tree. A cut that is no member is not in it.
     */
    static Map<Integer, List<Integer>> joined(List<Cut> cuts) {
        Map<Integer, List<Integer>> writes = new LinkedHashMap<>();
        Map<Boolean, List<Integer>> fresh = new HashMap<>();
        for (int i = 0; i < cuts.size(); i++) {
            if (!(cuts.get(i).as() instanceof Member member)) continue;
            if (member.group() != null) {
                writes.put(i, List.of(i));
                continue;
            }
            List<Integer> made = fresh.get(member.tree());
            if (made == null) {
                made = new ArrayList<>();
                fresh.put(member.tree(), made);
                writes.put(i, made);
            } else {
                writes.put(i, List.of());
            }
            made.add(i);
        }
        writes.replaceAll((i, at) -> List.copyOf(at));
        return writes;
    }

    /** Whether crop_pictures said {@code name} was not saved while it saved the others: "Not saved, name: …". */
    static boolean notSaved(String said, String name) {
        if (name == null) return false;
        String id = TemplateLibrary.sanitizeName(name);
        return said.lines().anyMatch(l -> l.startsWith("Not saved, " + name + ":")
                || l.startsWith("Not saved, " + id + ":"));
    }

    /** Whether {@code cut} is a picture of a screen, which crop_pictures saves. */
    static boolean picture(Cut cut) {
        return !(cut.as() instanceof Place || cut.as() instanceof Reads || cut.as() instanceof Digits
                || cut.as() instanceof Recut || cut.as() instanceof Spot);
    }

    /** {@code cut}, a spot of {@code screen}, written beside its anchor. On the FX thread. */
    private static String spot(AgentContext context, String screen, Cut cut, Spot spot) {
        if (spot.anchor() == null || !ScreenEdits.pictures(context.services()).contains(spot.anchor())) {
            return ScreenEdits.NOT_WRITTEN + cut.name() + ": pick the picture it sits beside.";
        }
        Rect anchor = ScreenEdits.picture(context.services(), spot.anchor()).place();
        if (anchor == null || anchor.width() <= 0 || anchor.height() <= 0) {
            return ScreenEdits.NOT_WRITTEN + cut.name() + ": " + spot.anchor() + " has no place it was cut at; recut it.";
        }
        Offsets at = offsets(anchor, cut.box(), spot.rows(), spot.columns());
        return ScreenEdits.addSpot(context.services(), screen, cut.name(), spot.anchor(), at.across(), at.down(),
                at.grid());
    }

    /** Where a spot, or a grid's first cell, sits from its anchor, in the anchor's sizes, and the grid's steps. */
    record Offsets(double across, double down, Grid grid) {
    }

    /**
     * The offsets of {@code box}, drawn in the frame {@code anchor} was cut in: one spot, its centre from the anchor's;
     * or {@code rows} by {@code columns} equal cells, the first's centre and the step between two. Three decimals, so
     * the last cell of a wide grid drifts less than a pixel.
     */
    static Offsets offsets(Rect anchor, Rect box, int rows, int columns) {
        int r = Math.max(1, rows);
        int c = Math.max(1, columns);
        double cellWidth = (double) box.width() / c;
        double cellHeight = (double) box.height() / r;
        double ax = anchor.x() + anchor.width() / 2.0;
        double ay = anchor.y() + anchor.height() / 2.0;
        double across = rounded((box.x() + cellWidth / 2 - ax) / anchor.width());
        double down = rounded((box.y() + cellHeight / 2 - ay) / anchor.height());
        Grid grid = r == 1 && c == 1 ? null : Grid.of(r, c, c == 1 ? 0 : rounded(cellWidth / anchor.width()),
                r == 1 ? 0 : rounded(cellHeight / anchor.height()));
        return new Offsets(across, down, grid);
    }

    private static double rounded(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /** {@code cut} as crop_pictures takes it: a member is a button of the screen first. */
    static Box box(Cut cut) {
        boolean polygon = cut.shape() == CutShape.POLYGON;
        Rect b = cut.box();
        Mark mark = switch (cut.as()) {
            case Marker ignored -> Mark.SET;
            case MoreMarker ignored -> Mark.ADD;
            case Absent ignored -> Mark.ABSENT;
            case ScreenLook look when look.marker() -> Mark.SET;
            default -> null;
        };
        boolean popup = cut.as() instanceof Marker m && m.popup() || cut.as() instanceof MoreMarker m2 && m2.popup();
        LookOf lookOf = cut.as() instanceof LookOf l ? l : null;
        ScreenLook screenLook = cut.as() instanceof ScreenLook s ? s : null;
        Press press = cut.as() instanceof Press p ? p : null;
        String when = press != null ? blankless(press.when()) : screenLook != null ? blankless(screenLook.when()) : null;
        return new Box(cut.name(), polygon ? null : b.x(), polygon ? null : b.y(), polygon ? null : b.width(),
                polygon ? null : b.height(), cut.shape(), polygon ? corners(cut.points()) : null, skip(cut.ignore()),
                mark, cut.as() instanceof Back ? Boolean.TRUE : null,
                press != null && press.sometimes() ? Boolean.TRUE : null, popup ? Boolean.TRUE : null,
                lookOf == null ? null : lookOf.input(), lookOf == null ? null : lookOf.state(), when,
                screenLook == null ? null : blankless(screenLook.look()));
    }

    /** edit_pictures' replace of {@code picture} by {@code cut}. */
    static PictureTools.Replace replace(Cut cut, String picture) {
        boolean polygon = cut.shape() == CutShape.POLYGON;
        Rect b = cut.box();
        return new PictureTools.Replace(picture, polygon ? null : b.x(), polygon ? null : b.y(),
                polygon ? null : b.width(), polygon ? null : b.height(), cut.shape(),
                polygon ? corners(cut.points()) : null, skip(cut.ignore()));
    }

    /**
     * The buttons just cut as {@code names}, put in {@code member}'s strip or tree of {@code screen}, after its
     * members, in order: the strip or tree keeps its field, how it scrolls and what was measured of it.
     */
    private static String member(AgentContext context, String screen, List<String> names, Member member) {
        if (screen == null) {
            return ScreenEdits.NOT_WRITTEN + String.join(", ", names)
                    + ": a strip's or a tree's button is a screen's: pick the screen.";
        }
        return FxCall.call(() -> {
            Optional<MapScreen> on = ScreenBook.read(context.services()).screen(screen);
            List<ImageTemplate> pictures = new ArrayList<>();
            for (String name : names) {
                String id = TemplateLibrary.sanitizeName(name);
                Optional<ImageTemplate> picture = on.stream().flatMap(s -> s.inputs().values().stream())
                        .flatMap(i -> i instanceof Button button ? Stream.of(button.picture()) : Stream.empty())
                        .filter(p -> p.id().equalsIgnoreCase(id)).findFirst();
                if (picture.isEmpty()) return ScreenEdits.NOT_WRITTEN + id + " is no button of " + screen + ".";
                pictures.add(picture.get());
            }
            MapScreen held = on.orElseThrow();
            if (member.tree()) {
                Tree had = member.group() == null ? null : held.trees().get(member.group());
                List<Node> nodes = new ArrayList<>(had == null ? List.of() : had.nodes());
                pictures.forEach(p -> nodes.add(Node.of(p)));
                return ScreenEdits.setTree(context.services(), screen, member.group(),
                        had == null ? Tree.of(nodes) : had.nodes(nodes));
            }
            Strip had = member.group() == null ? null : held.strips().get(member.group());
            List<ImageTemplate> members = new ArrayList<>(had == null ? List.of() : had.members());
            members.addAll(pictures);
            if (had == null && member.notches() == 0) {
                return ScreenEdits.NOT_WRITTEN + "a new strip turns by some notches of the wheel: give them.";
            }
            return ScreenEdits.setStrip(context.services(), screen, member.group(),
                    had == null ? Strip.of(Scroll.wheel(member.notches()), members) : had.members(members));
        });
    }

    private static List<Corner> corners(List<Point> points) {
        return points.stream().map(p -> new Corner(p.x(), p.y())).toList();
    }

    private static Skip skip(Rect ignore) {
        return ignore == null ? null : new Skip(ignore.x(), ignore.y(), ignore.width(), ignore.height());
    }

    private static String blankless(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
