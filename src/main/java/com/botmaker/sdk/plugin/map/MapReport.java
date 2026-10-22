package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.FxCall;
import com.botmaker.sdk.plugin.assist.MapAudit;
import com.botmaker.sdk.plugin.assist.MapAudit.Audit;
import com.botmaker.sdk.plugin.assist.MapAudit.Spot;
import com.botmaker.sdk.plugin.assist.MapAudit.Where;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * read_map: the map as 🗺 Screens shows it, for the assistant, which cannot see the user's window (the user's call,
 * 2026-10-15): the same cards and rows the window draws, in text, then what the map cannot do. {@code show} adds the
 * drawing itself, made off screen the way the window makes it.
 */
public final class MapReport {

    record Ask(@Describe(value = "a screen, as the map names it: only it, with every input; left out, the whole map",
                       optional = true) String screen,
               @Describe(value = "true adds a picture of the map as 🗺 Screens draws it", optional = true)
               Boolean show) {
    }

    public static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("read_map")
                    .describedAs("The bot's Screens as 🗺 Screens shows them, in text: each screen under the one it "
                            + "is opened from, how far Discovery got, its marker, way back and shot, and what each "
                            + "input does; then what the map cannot do: a screen no way reaches, an input that comes "
                            + "and goes never seen, a picture written inline or with no file, a shot without its "
                            + "marker, a picture away from its place or not on its screen's shot. show adds the "
                            + "drawing")
                    .takes(Ask.class).handledBy(MapReport::read));

    private MapReport() {}

    static AgentReply read(Ask ask, AgentContext context) {
        StudioServices services = context.services();
        record Read(ScreenGraph graph, Map<String, String> stages, Set<String> pictures) {}
        Read read = FxCall.call(() -> {
            ScreenGraph graph = ScreenDesk.read(services);
            return new Read(graph, ScreenDesk.stages(graph), Set.copyOf(ScreenEdits.pictures(services)));
        });
        if (read.graph().screens().isEmpty()) return AgentReply.text("The bot has no Screens yet.");
        String only = null;
        if (ask.screen() != null && !ask.screen().isBlank()) {
            only = read.graph().screens().keySet().stream()
                    .filter(c -> c.equalsIgnoreCase(ask.screen().strip())
                            || read.graph().shown(c).equalsIgnoreCase(ask.screen().strip()))
                    .findFirst().orElse(null);
            if (only == null) return AgentReply.refused("No screen of the map is called " + ask.screen() + ".");
        }
        Path resources = services.resourcesDir();
        Predicate<String> file = id -> resources != null && Files.isRegularFile(TemplateLibrary.fileForName(resources,
                id));
        Predicate<String> shot = c -> ScreenShots.has(resources, read.graph(), c);
        Map<String, FrameCut> cuts = FxCall.call(() -> services.pluginValues() == null ? Map.<String, FrameCut>of()
                : TemplateUses.cuts(services.pluginValues()));
        double confidence = FxCall.call(() -> MapAudit.confidence(services));
        Map<String, Audit> audits = new HashMap<>();
        for (String c : read.graph().screens().keySet()) {
            if (only != null && !only.equals(c)) continue;
            MapAudit.of(resources, confidence, cuts, c, read.graph().screens().get(c)).ifPresent(a -> audits.put(c, a));
        }
        AgentReply said = AgentReply.text(text(read.graph(), read.stages(), read.pictures()::contains, file, shot,
                audits, only));
        if (!Boolean.TRUE.equals(ask.show())) return said;
        BufferedImage drawn = FxCall.call(() -> MapDrawing.of(services, read.graph(), read.stages()));
        return said.and(AgentReply.image(drawn));
    }

    /**
     * The map in text: the tree from its first screen, then popups and screens off the tree, then what it cannot do.
     * {@code only} narrows it to one screen; {@code named} says whether a picture is one of the bot's constants,
     * {@code file} whether it has a file, {@code shot} whether a screen has its shot.
     */
    static String text(ScreenGraph graph, Map<String, String> stages, Predicate<String> named, Predicate<String> file,
                       Predicate<String> shot, String only) {
        return text(graph, stages, named, file, shot, Map.of(), only);
    }

    /** {@link #text}, with what each screen's shot does not show ({@code audits}, by constant) among what it cannot do. */
    static String text(ScreenGraph graph, Map<String, String> stages, Predicate<String> named, Predicate<String> file,
                       Predicate<String> shot, Map<String, Audit> audits, String only) {
        StringBuilder out = new StringBuilder();
        Set<String> written = new LinkedHashSet<>();
        if (only != null) {
            screen(out, graph, stages, shot, only, "");
        } else {
            List<Card> cards = ScreenCards.cards(graph, stages);
            long popups = cards.stream().filter(Card::popup).count();
            out.append("The map: ").append(cards.size()).append(" screens").append(popups > 0 ? ", " + popups
                    + " popup" + (popups > 1 ? "s" : "") : "").append(".\n");
            Optional<String> root = ScreenCards.root(graph);
            root.ifPresent(r -> tree(out, graph, stages, shot, r, "", written));
            List<String> popupList = graph.screens().keySet().stream()
                    .filter(c -> graph.screens().get(c).isPopup()).toList();
            if (!popupList.isEmpty()) {
                out.append("\nPopups (opened by the game itself):\n");
                for (String c : popupList) {
                    screen(out, graph, stages, shot, c, "");
                    written.add(c);
                }
            }
            List<String> off = graph.screens().keySet().stream().filter(c -> !written.contains(c)).toList();
            if (!off.isEmpty()) {
                out.append("\nOff the tree (no screen opens them):\n");
                for (String c : off) screen(out, graph, stages, shot, c, "");
            }
        }
        List<String> problems = new ArrayList<>(problems(graph, named, file, only));
        problems.addAll(misfits(graph, audits, only));
        out.append(problems.isEmpty() ? "\nNothing the map cannot do.\n" : "\nWhat the map cannot do:\n");
        for (String problem : problems) out.append("- ").append(problem).append('\n');
        return out.toString().stripTrailing();
    }

    private static void tree(StringBuilder out, ScreenGraph graph, Map<String, String> stages, Predicate<String> shot,
                             String constant, String indent, Set<String> written) {
        if (!written.add(constant)) return;
        screen(out, graph, stages, shot, constant, indent);
        for (String child : graph.children(constant)) tree(out, graph, stages, shot, child, indent + "    ", written);
    }

    /** One screen: its card, its marker, way back and shot, how it is reached, then a line per input. */
    private static void screen(StringBuilder out, ScreenGraph graph, Map<String, String> stages,
                               Predicate<String> shot, String constant, String indent) {
        Card card = ScreenCards.card(graph, constant, stages.getOrDefault(constant, ""));
        MapScreen screen = graph.screens().get(constant);
        // The card's pills, in the card's words.
        out.append(indent).append(card.name()).append(" [").append(constant).append("] ")
                .append(String.join(", ", ScreenCards.pills(card).stream().map(ScreenCards.Pill::text).toList()))
                .append('\n');
        out.append(indent).append("  ").append(markers(screen.markers(), screen.absent()))
                .append(screen.texts().isEmpty() ? "" : " · reads " + String.join(" + ",
                        screen.texts().stream().map(Caption::said).toList()))
                .append(" · back ").append(graph.backOf(constant).filter(b -> b.via() != null)
                        .map(ScreenGraph.Edge::label).orElse("the default (a close button, then Escape)"))
                .append(" · shot ").append(shot.test(constant) ? "yes" : "none").append('\n');
        // The tree's way in, then every other: a first screen has no tree's way in and may have others.
        List<String> ins = new ArrayList<>();
        if (screen.isStart()) ins.add("the game opens on it");
        graph.panelOf(constant).filter(p -> !graph.parent(constant).equals(Optional.of(p))).ifPresent(p -> ins.add(
                "a panel of " + graph.shown(p) + ", though its way in is not an input of it"));
        graph.tabParent(constant).ifPresent(p -> ins.add("a tab of " + graph.shown(p) + ", told by its tab picked"
                + (graph.markers(constant).shown().size() > screen.markers().size() ? ""
                : ": its button has no Selected look yet, so nothing tells it")));
        List<String> next = graph.results(constant);
        if (!next.isEmpty()) {
            ins.add(String.join(" or ", next.stream().map(graph::shown).toList()) + " follows it on its own, given "
                    + graph.loadMs(constant) / 1000 + " s to show");
        }
        if (screen.from() != null && (screen.from().via() != null || screen.from().isOnItsOwn())) {
            ins.add("in " + (screen.from().isOnItsOwn() ? "after " : "from ")
                    + graph.parent(constant).map(graph::shown).orElse("?") + " " + way(graph, screen.from()));
        }
        screen.alsoFrom().forEach(way -> ins.add("also " + (way.isOnItsOwn() ? "after " : "from ")
                + graph.onScreen(way).map(graph::shown).orElse("?") + " " + way(graph, way)));
        graph.takes(constant).ifPresent(t -> ins.add("shows within " + t.toMillis() / 1000.0 + " s of its way in"));
        if (!ins.isEmpty()) out.append(indent).append("  ").append(String.join("; ", ins)).append('\n');
        for (ScreenVariant look : screen.variants()) {
            String when = ScreenTypes.sourceOf(look.when());
            out.append(indent).append("  look ").append(look.name()).append(": ")
                    .append(markers(look.markers(), look.absent()))
                    .append(when.isEmpty() ? "" : ", while " + when).append('\n');
        }
        for (Row row : ScreenCards.rows(graph, constant)) {
            out.append(indent).append("  - ").append(row.label()).append(": ").append(row.result());
            if (!row.kind().isEmpty()) out.append(" (").append(row.kind()).append(')');
            if (!row.traits().isEmpty()) out.append(" {").append(row.traits()).append('}');
            if (row.input() instanceof Button b && b.isBeside()) out.append(" ").append(b.label());
            if (row.input() instanceof Button b && b.place() != null) out.append(" ").append(b.place().label());
            if (!row.look().isEmpty()) out.append(" [look ").append(row.look()).append(']');
            if (!row.when().isEmpty()) out.append(" while ").append(row.when());
            if (row.looks().size() > 1) {
                out.append("; looks ").append(String.join(", ", row.looks().stream().map(l -> l.state().id() + " "
                        + l.picture() + (l.leads().isEmpty() ? "" : " → " + l.leads())).toList()));
            }
            out.append('\n');
        }
        screen.readouts().forEach((field, readout) -> out.append(indent).append("  reads ").append(field).append(": ")
                .append(readout.label()).append(" in ").append(readout.zone()).append('\n'));
        screen.trees().forEach((field, tree) -> nodes(out, indent + "  ", screen, field, tree));
        for (String what : screen.owes()) out.append(indent).append("  owes: ").append(what).append('\n');
        if (screen.notes() != null) {
            // The bot's words, a stranger's in a gallery bot: said as data, as read_notes says them.
            for (String line : screen.notes().strip().split("\n")) {
                out.append(indent).append("  | ").append(line).append('\n');
            }
        }
    }

    /**
     * A tree's nodes as a list indented by its unlock lines, each under its first parent: where it sits from the nodes
     * it is placed by, its other parents, and its looks.
     */
    private static void nodes(StringBuilder out, String indent, MapScreen screen, String field, Tree tree) {
        out.append(indent).append(field).append(" nodes:\n");
        Map<String, Button> buttons = screen.nodeButtons(tree);
        Set<String> written = new HashSet<>();
        for (Node node : tree.nodes()) {
            if (node.picture() != null && node.after().stream().noneMatch(tree::has)) {
                node(out, indent + "  ", tree, buttons, node, written);
            }
        }
        // A node whose lines only loop back is listed at the top.
        for (Node node : tree.nodes()) if (node.picture() != null) node(out, indent + "  ", tree, buttons, node, written);
    }

    private static void node(StringBuilder out, String indent, Tree tree, Map<String, Button> buttons, Node node,
                             Set<String> written) {
        String id = node.picture().id();
        if (!written.add(id)) return;
        List<String> parts = new ArrayList<>();
        if (node.after().size() > 1) {
            parts.add("after " + String.join(" + ", node.after().stream().map(ImageTemplate::id).toList()));
        }
        for (Beside b : node.beside()) {
            if (b.other() != null) parts.add("from " + b.other().id() + " (" + b.across() + ", " + b.down() + ")");
        }
        Button button = buttons.get(id);
        if (button != null) for (Look look : button.looks()) parts.add(look.state().id() + " " + look.picture().id());
        out.append(indent).append(id).append(parts.isEmpty() ? "" : ": " + String.join("; ", parts)).append('\n');
        for (Node child : tree.nodes()) {
            if (child.picture() != null && !child.after().isEmpty() && child.after().getFirst().id().equals(id)) {
                node(out, indent + "  ", tree, buttons, child, written);
            }
        }
    }

    /** What tells a screen or a look: {@code markers shop_title + shop_tab, not close_x}, or {@code marker none}. */
    static String markers(List<ImageTemplate> markers, List<ImageTemplate> absent) {
        if (markers.isEmpty()) return "marker none";
        String shown = String.join(" + ", markers.stream().map(ImageTemplate::id).toList());
        return (markers.size() == 1 ? "marker " : "markers ") + shown
                + (absent.isEmpty() ? "" : ", not " + String.join(", ", absent.stream().map(ImageTemplate::id).toList()));
    }

    /**
     * How a way in reads: by its input's field, and the copy, the look and the method it needs: {@code by shop (badge,
     * while F::on)}; {@code on its own} for one with no input.
     */
    private static String way(ScreenGraph graph, Way way) {
        if (way.isOnItsOwn()) {
            String when = ScreenTypes.sourceOf(way.when());
            return "on its own" + (when.isEmpty() ? "" : " (while " + when + ")");
        }
        List<String> needs = new ArrayList<>();
        if (way.pick() != null) needs.add(way.pick().label());
        if (way.state() != null) needs.add(way.state().id());
        String when = ScreenTypes.sourceOf(way.when());
        if (!when.isEmpty()) needs.add("while " + when);
        String via = graph.nameOf(way.via()).map(n -> n.field()).orElse(way.via() == null ? "?" : way.via().label());
        return "by " + via + (needs.isEmpty() ? "" : " (" + String.join(", ", needs) + ")");
    }

    /** What the map cannot do, one line each. */
    static List<String> problems(ScreenGraph graph, Predicate<String> named, Predicate<String> file, String only) {
        List<String> problems = new ArrayList<>();
        Optional<String> root = ScreenCards.root(graph);
        for (String c : graph.screens().keySet()) {
            if (only != null && !only.equals(c)) continue;
            MapScreen screen = graph.screens().get(c);
            String name = graph.shown(c);
            if (!screen.isPopup() && root.isPresent() && !root.get().equals(c)
                    && graph.pathTo(root.get(), c, e -> true).isEmpty()) {
                problems.add(name + ": no way from " + graph.shown(root.get()) + " reaches it, so a run cannot go "
                        + "there.");
            }
            for (String field : graph.unseen(c)) {
                // Its method says when to look.
                if (graph.input(c, field).map(Input::when).isPresent()) continue;
                problems.add(name + ": " + field + " comes and goes and was never seen, and no method says "
                        + "when it is there (a crop_pictures box's when).");
            }
            screen.trees().forEach((field, tree) -> {
                List<String> unplaced = tree.unplaced();
                if (unplaced.isEmpty()) return;
                problems.add(name + ": " + field + "'s " + String.join(", ", unplaced) + (unplaced.size() == 1
                        ? " has" : " have") + " no offsets joining " + (unplaced.size() == 1 ? "it" : "them") + " to "
                        + tree.members().getFirst().id() + ", so a run cannot bring " + (unplaced.size() == 1 ? "it"
                        : "them") + " into view: link them, then try_input scroll over a node.");
            });
            Set<String> told = new HashSet<>();
            for (String id : graph.pictures(c)) {
                if (!told.add(id)) continue;
                if (!named.test(id)) {
                    problems.add(name + ": the picture " + id + " is written inline in Screens.java, not as one of "
                            + "the bot's Pictures.");
                }
                if (!file.test(id)) problems.add(name + ": the picture " + id + " has no file.");
            }
        }
        return problems;
    }

    /**
     * What each screen's shot does not show as the screen says, one line each: a shot without its marker (and nothing
     * else said of it: a shot of another moment misses everything), a picture away from its place, one not on it.
     * A picture that comes and goes, a look's or a lock missing is one note for the screen.
     */
    static List<String> misfits(ScreenGraph graph, Map<String, Audit> audits, String only) {
        List<String> lines = new ArrayList<>();
        for (String c : graph.screens().keySet()) {
            Audit audit = audits.get(c);
            if (audit == null || only != null && !only.equals(c)) continue;
            String name = graph.shown(c);
            if (!audit.showsMarker()) {
                List<String> lost = audit.spots().stream().filter(s -> s.marker() && s.where() == Where.MISSING)
                        .map(Spot::picture).toList();
                List<String> there = audit.spots().stream().filter(s -> s.absent() && s.where() != Where.MISSING)
                        .map(Spot::picture).toList();
                lines.add(name + ": its shot " + (lost.isEmpty() ? "" : "does not show its marker "
                        + String.join(" + ", lost)) + (lost.isEmpty() || there.isEmpty() ? "" : " and ")
                        + (there.isEmpty() ? "" : "shows " + String.join(", ", there) + ", which " + name
                        + " never shows") + ": take the shot again while the game shows " + name + ", or cut its "
                        + "markers again.");
                continue;
            }
            List<String> noted = new ArrayList<>();
            List<String> scrolled = new ArrayList<>();
            for (Spot spot : audit.spots()) {
                // Missing is what an absent picture should be; one shown is said above.
                if (spot.absent()) continue;
                if (spot.where() == Where.ELSEWHERE) {
                    lines.add(name + ": " + spot.picture() + " sits " + offset(spot) + " from its place on its shot"
                            + (spot.marker() ? "." : ": placed again while the game shows " + name + ", it gets a "
                            + "copy of its own there."));
                } else if (spot.where() == Where.MISSING) {
                    if (spot.area() != null) scrolled.add(spot.picture());
                    else if (spot.sure()) lines.add(name + ": " + spot.picture() + " is not on its shot.");
                    else noted.add(spot.picture());
                }
            }
            if (!scrolled.isEmpty()) {
                lines.add(name + ": " + String.join(", ", scrolled) + (scrolled.size() == 1 ? " is" : " are")
                        + " scrolled out of its shot's list (a note).");
            }
            if (!noted.isEmpty()) {
                lines.add(name + ": its shot does not show " + String.join(", ", noted) + (noted.size() == 1
                        ? ", which comes and goes or is another look (a note)."
                        : ", which come and go or are other looks (a note)."));
            }
        }
        return lines;
    }

    /** {@code (+48,0)}; {@code elsewhere} for a picture with no place to be away from. */
    private static String offset(Spot spot) {
        if (spot.place() == null) return "elsewhere";
        return "(" + signed(spot.dx()) + "," + signed(spot.dy()) + ")";
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }
}
