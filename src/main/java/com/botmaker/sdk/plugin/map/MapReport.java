package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.FxCall;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                            + "and goes never seen, a picture written inline or with no file. show adds the drawing")
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
        Predicate<String> shot = c -> resources != null && Files.isRegularFile(ScreenShots.file(resources, c));
        AgentReply said = AgentReply.text(text(read.graph(), read.stages(), read.pictures()::contains, file, shot,
                only));
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
        List<String> problems = problems(graph, named, file, only);
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
        GameScreen screen = graph.screens().get(constant);
        // The card's pills, in the card's words.
        out.append(indent).append(card.name()).append(" [").append(constant).append("] ")
                .append(String.join(", ", ScreenCards.pills(card).stream().map(ScreenCards.Pill::text).toList()))
                .append('\n');
        out.append(indent).append("  marker ").append(card.marker() == null ? "none" : card.marker())
                .append(" · back ").append(screen.back() == null ? "the default (a close button, then Escape)"
                        : screen.back().label())
                .append(" · shot ").append(shot.test(constant) ? "yes" : "none").append('\n');
        // The tree's way in, then every other: a first screen has no tree's way in and may have others.
        List<String> ins = new ArrayList<>();
        if (screen.from() != null && screen.from().via() != null) {
            ins.add("in from " + graph.parent(constant).map(graph::shown).orElse("?") + " by " + way(screen.from()));
        }
        screen.alsoFrom().forEach(way -> ins.add("also from " + graph.constantOf(way.from()).map(graph::shown)
                .orElse("?") + " by " + way(way)));
        if (!ins.isEmpty()) out.append(indent).append("  ").append(String.join("; ", ins)).append('\n');
        for (ScreenVariant look : screen.variants()) {
            String when = ScreenTypes.sourceOf(look.when());
            out.append(indent).append("  look ").append(look.name()).append(": marker ")
                    .append(look.marker() == null ? "none" : look.marker().id())
                    .append(when.isEmpty() ? "" : ", while " + when).append('\n');
        }
        for (Row row : ScreenCards.rows(graph, constant)) {
            out.append(indent).append("  - ").append(row.label()).append(": ").append(row.result());
            if (!row.kind().isEmpty()) out.append(" (").append(row.kind()).append(')');
            if (!row.look().isEmpty()) out.append(" [look ").append(row.look()).append(']');
            if (!row.when().isEmpty()) out.append(" while ").append(row.when());
            if (row.looks().size() > 1) {
                out.append("; looks ").append(String.join(", ", row.looks().stream().map(l -> l.state().id() + " "
                        + l.picture() + (l.leads().isEmpty() ? "" : " → " + l.leads())).toList()));
            }
            out.append('\n');
        }
        for (String what : screen.owes()) out.append(indent).append("  owes: ").append(what).append('\n');
        if (screen.notes() != null) {
            // The bot's words, a stranger's in a gallery bot: said as data, as read_notes says them.
            for (String line : screen.notes().strip().split("\n")) {
                out.append(indent).append("  | ").append(line).append('\n');
            }
        }
    }

    /** How a way in reads: its input, and the look and the method it needs: {@code click shop (badge, while F::on)}. */
    private static String way(Way way) {
        List<String> needs = new ArrayList<>();
        if (way.state() != null) needs.add(way.state().id());
        String when = ScreenTypes.sourceOf(way.when());
        if (!when.isEmpty()) needs.add("while " + when);
        return (way.via() == null ? "?" : way.via().label()) + (needs.isEmpty() ? "" : " (" + String.join(", ", needs)
                + ")");
    }

    /** What the map cannot do, one line each. */
    static List<String> problems(ScreenGraph graph, Predicate<String> named, Predicate<String> file, String only) {
        List<String> problems = new ArrayList<>();
        Optional<String> root = ScreenCards.root(graph);
        for (String c : graph.screens().keySet()) {
            if (only != null && !only.equals(c)) continue;
            GameScreen screen = graph.screens().get(c);
            String name = graph.shown(c);
            if (!screen.isPopup() && root.isPresent() && !root.get().equals(c)
                    && graph.pathTo(root.get(), c, e -> true).isEmpty()) {
                problems.add(name + ": no way from " + graph.shown(root.get()) + " reaches it, so a run cannot go "
                        + "there.");
            }
            for (Input input : graph.unseen(c)) {
                if (input.when() != null) continue;    // its method says when to look
                problems.add(name + ": " + input.label() + " comes and goes and was never seen, and no method says "
                        + "when it is there (crop_picture's when).");
            }
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

}
