package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.BotConstants.InputName;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * The map edited by hand from 🗺 Screens' side panel: what the assistant's tools record by trying, set by the user
 * instead (the user's call, 2026-10-15): an input's result, purpose, never, lock or coming and going, an input removed
 * or made the way back, a screen renamed, made a popup, taken back, deleted or given a new shot, pictures the bot has
 * put on a screen, a marker or a picture it never shows added or taken off, a screen added under another without
 * trying its way in, a way in or a way back taken off, and when a way, an input or a look is there, and where a card
 * sits on the map. An input is named by its field ({@code beerBuy}). The assistant's edit_map does each by the same
 * method ({@link MapEdits}). Each answers in a sentence what it wrote, or why it did not. On the FX thread, except
 * {@link #retakeShot}, which reads the game, and {@link #addMarker} and {@link #removeMarker}, which read every shot.
 */
public final class ScreenEdits {

    /** How an answer begins when nothing was written. */
    public static final String NOT_WRITTEN = "Not written: ";

    private ScreenEdits() {}

    /** {@code input} as it was cut, untried: tried, leaving, never and lock cleared, the rest kept. */
    public static Input untried(Input input) {
        return switch (input) {
            case Button b -> new Button(b.picture(), false, false, b.isInfo(), null, b.comesAndGoes(), null,
                    b.looks(), b.when(), b.repeats(), b.cycles());
            case Hotkey h -> new Hotkey(h.key(), false, false, h.isInfo(), null, h.comesAndGoes(), null, h.when());
            case Strip s -> new Strip(s.scroll(), s.members(), false, false, s.isInfo(), null, s.comesAndGoes(), null,
                    s.when());
            case Tree t -> new Tree(t.nodes(), t.isDragged(), t.notches(), false, false, t.isInfo(), null,
                    t.comesAndGoes(), null, t.when());
        };
    }

    /** {@code input} shown only at times, or always ({@code sometimes} false: when it is there goes too). */
    public static Input sometimes(Input input, boolean sometimes) {
        if (sometimes) return input.sometimes();
        return switch (input) {
            case Button b -> new Button(b.picture(), b.isTried(), b.isLeaving(), b.isInfo(), b.never(), false,
                    b.lock(), b.looks(), null, b.repeats(), b.cycles());
            case Hotkey h -> new Hotkey(h.key(), h.isTried(), h.isLeaving(), h.isInfo(), h.never(), false, h.lock(),
                    null);
            case Strip s -> new Strip(s.scroll(), s.members(), s.isTried(), s.isLeaving(), s.isInfo(), s.never(),
                    false, s.lock(), null);
            case Tree t -> new Tree(t.nodes(), t.isDragged(), t.notches(), t.isTried(), t.isLeaving(), t.isInfo(),
                    t.never(), false, t.lock(), null);
        };
    }

    /** {@code input} only telling, or not ({@code info} false). */
    public static Input info(Input input, boolean info) {
        if (info) return input.info();
        return switch (input) {
            case Button b -> new Button(b.picture(), b.isTried(), b.isLeaving(), false, b.never(), b.comesAndGoes(),
                    b.lock(), b.looks(), b.when(), b.repeats(), b.cycles());
            case Hotkey h -> new Hotkey(h.key(), h.isTried(), h.isLeaving(), false, h.never(), h.comesAndGoes(),
                    h.lock(), h.when());
            case Strip s -> new Strip(s.scroll(), s.members(), s.isTried(), s.isLeaving(), false, s.never(),
                    s.comesAndGoes(), s.lock(), s.when());
            case Tree t -> new Tree(t.nodes(), t.isDragged(), t.notches(), t.isTried(), t.isLeaving(), false,
                    t.never(), t.comesAndGoes(), t.lock(), t.when());
        };
    }

    /** Puts {@code now} as {@code constant}'s field {@code field}; {@code now} null removes it. */
    public static String setInput(StudioServices services, String constant, String field, Input now) {
        return said(ScreenBook.update(services, constant,
                        s -> now == null ? s.withoutInput(field) : s.withInput(field, now)),
                now == null ? field + " is off " + constant + "." : "Noted: " + field + ".");
    }

    /**
     * Makes {@code strip} one of {@code constant}'s strips, as its field {@code field} (null: a new one named from its
     * first member), in place of every strip that holds one of its members; a strip of no members (or 0 notches) takes
     * those strips off, their buttons looked for at their places.
     */
    public static String setStrip(StudioServices services, String constant, String field, Strip strip) {
        boolean off = strip.members().isEmpty() || strip.scroll() == null || !strip.scroll().moves();
        String names = strip.members().stream().map(ImageTemplate::id).collect(Collectors.joining(", "));
        return said(ScreenBook.update(services, constant, s -> withStrip(s, field, strip, off)),
                off ? "The strip is off " + constant + ": its buttons are looked for at their places."
                        : constant + " has a strip of " + names + ", each found anywhere along it and scrolled to by "
                        + strip.scroll().label() + " over another that shows.");
    }

    /** {@code screen} without any strip holding one of {@code strip}'s members, and {@code strip} last unless {@code off}. */
    static MapScreen withStrip(MapScreen screen, String field, Strip strip, boolean off) {
        MapScreen now = screen;
        Strip had = null;
        String hadField = null;
        for (Map.Entry<String, Strip> held : screen.strips().entrySet()) {
            if (held.getKey().equals(field) || strip.members().stream().anyMatch(held.getValue()::has)) {
                if (had == null) {
                    had = held.getValue();
                    hadField = held.getKey();
                }
                now = now.withoutInput(held.getKey());
            }
        }
        if (off) return now;
        // The strip it replaces keeps its field and what it says of its members: only its members and scroll change,
        // and a scroll not measured that goes the same way keeps what Discovery measured of the old one.
        Strip made = had == null ? strip : had.members(strip.members()).scroll(kept(had.scroll(), strip.scroll()));
        String name = hadField != null ? hadField : field != null && !now.inputs().containsKey(field) ? field
                : now.fieldFor((strip.members().isEmpty() ? "" : strip.members().getFirst().id()) + " strip");
        return now.withInput(name, made);
    }

    /** {@code now}, or {@code had} when {@code now} is a scroll not measured yet that goes the way {@code had} goes. */
    private static Scroll kept(Scroll had, Scroll now) {
        boolean unmeasured = now.step() == 0 && now.settle().equals(Scroll.SETTLE) && now.most() == Scroll.MOST;
        boolean same = had != null && had.notches() == now.notches() && had.drag() == now.drag()
                && had.px() == now.px() && had.time().equals(now.time());
        return unmeasured && same ? had : now;
    }

    /**
     * Makes the field {@code field} {@code constant}'s way back; a way back it had that was no field of its own is an
     * untried input of it again.
     */
    public static String useAsBack(StudioServices services, String constant, String field) {
        return said(ScreenBook.update(services, constant, s -> keepBack(s).setup(x -> x.back(
                        BotConstants.input(constant, field)))
                        .withInput(field, untried(s.input(field).orElseThrow()))),
                field + " is the way back.");
    }

    /** {@code screen} with its way back, when that is no field of its own, one of its inputs untried. */
    private static MapScreen keepBack(MapScreen screen) {
        Input back = screen.back();
        if (back == null || BotConstants.inputName(back) != null || screen.fieldOf(back).isPresent()) return screen;
        return screen.withInput(screen.fieldFor(back.label()), untried(back));
    }

    /** Gives {@code constant} the name the game shows, {@code name}; its constant stays. */
    public static String rename(StudioServices services, String constant, String name) {
        if (name == null || !MapTools.isName(name.strip())) return NOT_WRITTEN + "name it in plain words, on one line.";
        String typed = name.strip();
        Optional<String> taken = ScreenBook.find(ScreenBook.read(services), typed).filter(c -> !c.equals(constant));
        if (taken.isPresent()) return NOT_WRITTEN + "Screens." + taken.get() + " is called " + typed + " already.";
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.name(typed))), "Renamed " + typed + ".");
    }

    /** Makes {@code constant} a popup, or a screen of the tree again ({@code popup} false). */
    public static String setPopup(StudioServices services, String constant, boolean popup) {
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> popup ? x.popup() : x.notPopup())),
                popup ? "A popup now." : "No longer a popup.");
    }

    /** Puts {@code notes} on {@code constant}, over what it had; blank removes them. */
    public static String setNotes(StudioServices services, String constant, String notes) {
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.notes(notes == null ? null
                        : notes.strip()))),
                notes == null || notes.isBlank() ? "Notes removed." : "Notes saved.");
    }

    /** not_a_screen: {@code constant} off the map, its way in noted on its parent as changing it in place. */
    public static String notAScreen(AgentContext context, String constant) {
        return ScreenDesk.text(MapTools.notAScreen(new MapTools.Mistake(constant), context));
    }

    /** Deletes {@code constant} and its shot; refused while another screen is reached through one of its inputs. */
    public static String delete(StudioServices services, String constant) {
        ScreenGraph graph = ScreenBook.read(services);
        List<String> after = graph.screens().keySet().stream()
                .filter(c -> !c.equals(constant) && graph.opensFrom(c).contains(constant)).map(graph::shown).toList();
        if (!after.isEmpty()) {
            return NOT_WRITTEN + String.join(", ", after) + " is reached from it: delete those first.";
        }
        Optional<String> failed = ScreenBook.step(services, "Delete " + graph.shown(constant),
                () -> ScreenBook.remove(services, constant));
        if (failed.isEmpty()) ScreenShots.remove(services.resourcesDir(), constant);
        return said(failed, "Deleted " + graph.shown(constant) + ".");
    }

    /**
     * {@code constant}'s whole-screen shot taken again from the game now, only while the game shows it: its marker is
     * found. Off the FX thread: it reads the game.
     */
    public static String retakeShot(AgentContext context, String constant) {
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return Frames.noProject();
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return Frames.noScreen();
        ScreenGraph graph;
        try {
            graph = FxCall.call(() -> ScreenBook.read(context.services()));
        } catch (IllegalStateException busy) {
            return "Studio did not hand over the bot's Screens: " + busy.getMessage();
        }
        MapScreen screen = graph.screens().get(constant);
        if (screen == null) return "No screen " + constant + " in the map.";
        if (!screen.hasMarker()) {
            return graph.shown(constant) + " has no marker yet, so nothing tells it from the frame: cut its marker, "
                    + "which takes its shot.";
        }
        if (!ScreenFit.shows(reach.get(), graph, constant, frame.get())) {
            return "The game does not show " + graph.shown(constant) + " now: its markers are not found. The shot was "
                    + "kept; go there first.";
        }
        ScreenShots.save(context.services().resourcesDir(), constant, frame.get());
        return "Shot taken.";
    }

    /** place_pictures: pictures the bot has, put on {@code screen} as inputs, its marker or its way back. */
    public static String place(AgentContext context, String screen, List<String> pictures, String marker,
                               String back) {
        return ScreenDesk.text(MapTools.placePictures(new MapTools.Places(screen, pictures, marker, back, null, null),
                context));
    }

    /**
     * Adds a screen the game calls {@code name} under {@code parent}, opened by its field {@code viaField}, without
     * trying it; with no field, {@code via} is put on {@code parent} first, a field named from what it acts on.
     */
    public static String newScreen(StudioServices services, String parent, String name, String viaField, Input via) {
        if (name == null || !MapTools.isName(name.strip())) return NOT_WRITTEN + "name it in plain words, on one line.";
        ScreenGraph graph = ScreenBook.read(services);
        if (ScreenBook.find(graph, name.strip()).isPresent()) {
            return NOT_WRITTEN + "the map has " + name.strip() + " already.";
        }
        MapScreen on = graph.screens().get(parent);
        if (on == null) return NOT_WRITTEN + "no screen " + parent + " in the map.";
        if (viaField == null && via == null) return NOT_WRITTEN + "name the input that opens it.";
        String field = viaField != null ? viaField : on.fieldOf(via).orElseGet(() -> on.fieldFor(via.label()));
        if (on.input(field).isEmpty() && via == null) return NOT_WRITTEN + parent + " has no input " + field + ".";
        String constant = ScreenBook.constantFor(graph, name.strip());
        MapScreen made = MapScreen.of(Setup.named(name.strip()).from(BotConstants.input(parent, field)));
        Input entrance = on.input(field).orElseGet(() -> untried(via));
        // The entrance on the parent and the new screen: one step of Studio's undo.
        Optional<String> failed = ScreenBook.step(services, "Add " + name.strip(), () -> ScreenBook.update(services,
                parent, s -> s.withInput(field, untried(entrance)))
                .or(() -> ScreenBook.add(services, constant, made)));
        return said(failed, "Added " + name.strip() + " as Screens." + constant + ": it owes its marker.");
    }

    /**
     * Makes {@code to}'s way in through {@code from}'s field {@code viaField} open only while {@code method} answers
     * true — a method reference, {@code Firestone::eventOn} — or always, {@code method} blank.
     */
    public static String setWayWhen(StudioServices services, String to, String from, String viaField,
                                    String method) {
        if (!ScreenTypes.isMethod(method)) return notMethod(method);
        BooleanSupplier when = ScreenTypes.condition(method);
        ScreenGraph graph = ScreenBook.read(services);
        if (ways(graph.screens().get(to)).stream().noneMatch(way -> wayFrom(graph, way, from, viaField))) {
            return noWay(graph, to, from, viaField);
        }
        return said(ScreenBook.update(services, to, s -> withWayWhen(graph, s, from, viaField, when)),
                when == null ? "That way is always open now." : "That way is open only while " + method.strip() + ".");
    }

    /** {@code screen} with its way in through {@code from}'s field {@code viaField} taken only while {@code when}. */
    static MapScreen withWayWhen(ScreenGraph graph, MapScreen screen, String from, String viaField,
                                 BooleanSupplier when) {
        return screen.setup(s -> {
            Setup now = s.from() != null && wayFrom(graph, s.from(), from, viaField) ? s.fromWay(s.from().when(when))
                    : s;
            return now.alsoFrom(now.alsoFrom().stream()
                    .map(way -> wayFrom(graph, way, from, viaField) ? way.when(when) : way).toList());
        });
    }

    /**
     * Takes {@code to}'s way in through {@code from}'s field {@code viaField} off the map: its next way in becomes the
     * tree's when it was the tree's. Refused for a screen's only way in: the screen is deleted instead.
     */
    public static String removeWay(StudioServices services, String to, String from, String viaField) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen screen = graph.screens().get(to);
        if (ways(screen).stream().noneMatch(way -> wayFrom(graph, way, from, viaField))) {
            return noWay(graph, to, from, viaField);
        }
        if (screen.from() != null && withoutWay(graph, screen, from, viaField).ways().isEmpty()) {
            return NOT_WRITTEN + "that is " + graph.shown(to) + "'s only way in: delete " + graph.shown(to)
                    + " instead, or take it for a change of " + graph.shown(from) + " in place (not a screen).";
        }
        return said(ScreenBook.update(services, to, s -> withoutWay(graph, s, from, viaField)),
                "Took " + graph.shown(to) + "'s way in from " + graph.shown(from) + " by " + viaField + " off the "
                        + "map.");
    }

    /** {@code screen} without its ways in through {@code from}'s {@code viaField}; the tree's is its next one. */
    private static MapScreen withoutWay(ScreenGraph graph, MapScreen screen, String from, String viaField) {
        return screen.setup(s -> {
            List<Way> also = s.alsoFrom().stream().filter(way -> !wayFrom(graph, way, from, viaField)).toList();
            if (s.from() == null || !wayFrom(graph, s.from(), from, viaField)) return s.alsoFrom(also);
            return also.isEmpty() ? s.fromWay(null).alsoFrom(also)
                    : s.fromWay(also.getFirst()).alsoFrom(also.subList(1, also.size()));
        });
    }

    /** {@code screen}'s ways in, the tree's first; none for no screen. */
    private static List<Way> ways(MapScreen screen) {
        return screen == null ? List.of() : screen.ways();
    }

    private static String noWay(ScreenGraph graph, String to, String from, String viaField) {
        return NOT_WRITTEN + graph.shown(to) + " has no way in from " + graph.shown(from) + " by " + viaField + ".";
    }

    private static boolean wayFrom(ScreenGraph graph, Way way, String from, String viaField) {
        return graph.nameOf(way.via()).filter(new InputName(from, viaField)::equals).isPresent();
    }

    /**
     * Takes {@code constant}'s own way back off, a walk then closing it by its close button, else Escape: the input
     * it was stays an input of the screen, untried again.
     */
    public static String removeBack(StudioServices services, String constant) {
        ScreenGraph graph = ScreenBook.read(services);
        MapScreen screen = graph.screens().get(constant);
        if (screen == null || screen.back() == null) return NOT_WRITTEN + "it has no way back of its own.";
        Optional<String> field = graph.nameOf(screen.back()).filter(n -> n.screen().equals(constant))
                .map(InputName::field);
        String was = field.orElse(screen.back().label());
        return said(ScreenBook.update(services, constant, s -> {
                    MapScreen kept = keepBack(s).setup(x -> x.back(null));
                    return field.flatMap(kept::input).map(i -> kept.withInput(field.get(), untried(i))).orElse(kept);
                }),
                "No way back of its own: a walk closes it by its close button, else Escape. " + was
                        + " is untried on it again.");
    }

    /**
     * Adds {@code picture} to what tells {@code constant} apart: one more marker, all of which show, or one it never
     * shows ({@code absent}). Refused when another screen's shot shows the set too: a run would take that one for it.
     * Off the FX thread, unlike the other edits: it matches the set over every other screen's shot.
     */
    public static String addMarker(StudioServices services, String constant, ImageTemplate picture, boolean absent) {
        MapScreen screen = FxCall.call(() -> ScreenBook.read(services)).screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        Markers was = Markers.of(screen);
        String id = picture.id();
        if (holds(absent ? was.shown() : was.absent(), id)) {
            return NOT_WRITTEN + id + " is " + (absent ? "one of its markers" : "a picture it never shows")
                    + ": remove it from there first.";
        }
        if (holds(absent ? was.absent() : was.shown(), id)) return "It is told by " + was.said() + " already.";
        Markers now = absent ? new Markers(was.shown(), plus(was.absent(), picture))
                : new Markers(plus(was.shown(), picture), was.absent());
        return toldBy(services, constant, now);
    }

    /**
     * Takes {@code picture} off what tells {@code constant} apart, its markers or the pictures it never shows. Refused
     * for its last marker, and when another screen's shot shows what is left. Off the FX thread, as {@link #addMarker}.
     */
    public static String removeMarker(StudioServices services, String constant, String picture) {
        MapScreen screen = FxCall.call(() -> ScreenBook.read(services)).screens().get(constant);
        if (screen == null) return NOT_WRITTEN + "no screen " + constant + " in the map.";
        Markers was = Markers.of(screen);
        String id = picture == null ? "" : picture.strip();
        if (!holds(was.shown(), id) && !holds(was.absent(), id)) {
            return NOT_WRITTEN + "it is told by " + was.said() + ", not " + id + ".";
        }
        Markers now = new Markers(minus(was.shown(), id), minus(was.absent(), id));
        if (!now.any()) return NOT_WRITTEN + id + " is its last marker: add another first.";
        return toldBy(services, constant, now);
    }

    /** {@code constant} told by {@code now}, unless another screen's shot shows it too. */
    private static String toldBy(StudioServices services, String constant, Markers now) {
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<MapAudit.Clash> clash = MapAudit.clash(services.resourcesDir(), MapAudit.confidence(services), graph,
                constant, now);
        if (clash.isPresent()) return NOT_WRITTEN + MapTools.clashSaid(graph, constant, now, clash.get());
        return said(FxCall.call(() -> ScreenBook.update(services, constant,
                s -> s.setup(x -> x.markers(now.shown()).absent(now.absent())))), "Told by " + now.said() + ".");
    }

    private static boolean holds(List<ImageTemplate> pictures, String id) {
        return pictures.stream().anyMatch(p -> p.id().equalsIgnoreCase(id));
    }

    private static List<ImageTemplate> plus(List<ImageTemplate> pictures, ImageTemplate picture) {
        List<ImageTemplate> more = new ArrayList<>(pictures);
        more.add(picture);
        return more;
    }

    private static List<ImageTemplate> minus(List<ImageTemplate> pictures, String id) {
        return pictures.stream().filter(p -> !p.id().equalsIgnoreCase(id)).toList();
    }

    /** {@code constant}'s look {@code look} shown only while {@code method} answers true; blank: no method says. */
    public static String setLookWhen(StudioServices services, String constant, String look, String method) {
        if (!ScreenTypes.isMethod(method)) return notMethod(method);
        BooleanSupplier when = ScreenTypes.condition(method);
        MapScreen screen = ScreenBook.read(services).screens().get(constant);
        Optional<ScreenVariant> had = screen == null || look == null ? Optional.empty() : screen.variants().stream()
                .filter(v -> v.name().equalsIgnoreCase(look.strip())).findFirst();
        if (had.isEmpty()) {
            return NOT_WRITTEN + "no look " + look + " of it" + (screen == null || screen.variants().isEmpty() ? ""
                    : ": its looks are " + String.join(", ", screen.variants().stream().map(ScreenVariant::name)
                    .toList())) + ".";
        }
        String name = had.get().name();
        return said(ScreenBook.update(services, constant, s -> s.setup(x -> x.withVariant(had.get().when(when)))),
                when == null ? "No method says when " + name + " shows." : name + " shows only while "
                        + method.strip() + ".");
    }

    /** {@code constant}'s field {@code field} there only while {@code method} answers true; blank: no method says. */
    public static String setInputWhen(StudioServices services, String constant, String field, String method) {
        if (!ScreenTypes.isMethod(method)) return notMethod(method);
        BooleanSupplier when = ScreenTypes.condition(method);
        return said(ScreenBook.update(services, constant, s -> s.input(field)
                        .map(input -> s.withInput(field, input.when(when))).orElse(s)),
                when == null ? "No method says when " + field + " is there."
                        : field + " is there only while " + method.strip() + ".");
    }

    /**
     * Puts each screen {@code corners} names at its card's corner on 🗺 Screens' map; a screen already there is not
     * written. Nothing a run reads changes.
     */
    public static String move(StudioServices services, Map<String, Point> corners) {
        ScreenGraph graph = ScreenBook.read(services);
        Map<String, Point> moving = new LinkedHashMap<>();
        corners.forEach((constant, corner) -> {
            MapScreen screen = graph.screens().get(constant);
            if (screen != null && !Objects.equals(screen.at(), corner)) moving.put(constant, corner);
        });
        if (moving.isEmpty()) return "Nothing moved.";
        // One step of Studio's undo for the whole drag, however many cards it pinned.
        String label = moving.size() == 1 ? "Move " + graph.shown(moving.keySet().iterator().next())
                : "Move " + moving.size() + " cards";
        String[] answer = {moving.size() == 1 ? "Moved 1 screen." : "Moved " + moving.size() + " screens."};
        services.history().batch(label, () -> {
            for (Map.Entry<String, Point> corner : moving.entrySet()) {
                Optional<String> failed = ScreenBook.update(services, corner.getKey(),
                        s -> s.setup(x -> x.at(corner.getValue())));
                if (failed.isPresent()) {
                    answer[0] = said(failed, "");
                    return;
                }
            }
        });
        return answer[0];
    }

    /** Puts every card of 🗺 Screens' map back where the layout puts it: no screen keeps a corner of its own. */
    public static String tidy(StudioServices services) {
        ScreenGraph graph = ScreenBook.read(services);
        String[] answer = {"Every card is where the layout puts it."};
        services.history().batch("Tidy the map", () -> {
            for (Map.Entry<String, MapScreen> screen : graph.screens().entrySet()) {
                if (screen.getValue().at() == null) continue;
                Optional<String> failed = ScreenBook.update(services, screen.getKey(), s -> s.setup(x -> x.at(null)));
                if (failed.isPresent()) {
                    answer[0] = said(failed, "");
                    return;
                }
            }
        });
        return answer[0];
    }

    /** The bot's picture {@code name}, as its constant holds it. */
    public static ImageTemplate picture(StudioServices services, String name) {
        return ScreenBook.picture(services, name);
    }

    /** Every picture the bot has, by name. */
    public static List<String> pictures(StudioServices services) {
        Path resources = services.resourcesDir();
        if (resources == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources)) {
            if (!TemplateLibrary.isUnmodifiedDefaultTemplate(file)) names.add(TemplateLibrary.baseName(file));
        }
        names.sort(String::compareTo);
        return names;
    }

    private static String notMethod(String method) {
        return NOT_WRITTEN + "\"" + method.strip() + "\" is no method reference (Firestone::eventOn).";
    }

    private static String said(Optional<String> failed, String done) {
        return failed.map(why -> NOT_WRITTEN + why).orElse(done);
    }
}
