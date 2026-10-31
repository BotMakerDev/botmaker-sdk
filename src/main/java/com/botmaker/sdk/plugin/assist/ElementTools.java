package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ElementRows.Row;
import com.botmaker.sdk.plugin.assist.ElementSaves.As;
import com.botmaker.sdk.plugin.assist.ElementSaves.Pick;
import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The assistant's half of 🗺 Screens' element picker: on a target that shows its app's tree (an emulator), the inputs,
 * texts and lists the tree holds, said as the picker lists them, kind and words, never an id or a bound; and one of
 * them saved by its words, as the picker saves a ticked row ({@link ElementSaves#save}). Discovery tries these
 * instead of cut pictures there.
 */
final class ElementTools {

    record Saved(@Describe("its screen, as the map says") String screen,
                 @Describe("its words: Battery") String words,
                 @Describe(value = "its kind, when words repeat", optional = true) String kind,
                 @Describe(value = "button, marker or readout", optional = true)
                 String as) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("list_elements")
                    .describedAs("An app's inputs, texts and lists in its tree, by kind and words")
                    .takesNothing().handledBy(ElementTools::listElements),
            AssistantTool.named("save_element")
                    .describedAs("Saves a list_elements row to Elements and its screen; a marker starts a new map")
                    .takes(Saved.class).handledBy(ElementTools::saveElement));

    private ElementTools() {}

    static AgentReply listElements(AssistantTool.None none, AgentContext context) {
        if (context.services() == null || context.services().projectDir() == null) {
            return AgentReply.refused(Frames.noProject());
        }
        Optional<ReadableSource> readable = ScreenDesk.readable(context);
        if (readable.isEmpty()) return AgentReply.text(NO_TREE);
        Optional<UiTree> tree = readable.get().tree();
        if (tree.isEmpty()) return AgentReply.refused(UNREAD);
        return AgentReply.text(listed(ElementRows.of(tree.get())));
    }

    static AgentReply saveElement(Saved saved, AgentContext context) {
        StudioServices services = context.services();
        if (services == null || services.projectDir() == null) return AgentReply.refused(Frames.noProject());
        if (saved.words() == null || saved.words().isBlank()) {
            return AgentReply.refused("Give its words, as list_elements gives them. Nothing was saved.");
        }
        if (saved.screen() == null || !MapTools.isName(saved.screen().strip())) {
            return AgentReply.refused("Give the screen it goes on, in plain words. Nothing was saved.");
        }
        Optional<ReadableSource> readable = ScreenDesk.readable(context);
        if (readable.isEmpty()) return AgentReply.refused(NO_TREE);
        // The tree first, then the frame its shot is cut from, as ❄ Freeze reads them.
        Optional<UiTree> tree = readable.get().tree();
        if (tree.isEmpty()) return AgentReply.refused(UNREAD);
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Chosen chosen = chosen(ElementRows.of(tree.get()), saved.words(), saved.kind(), saved.as());
        if (chosen.refusal() != null) return AgentReply.refused(chosen.refusal());
        Optional<String> screen = screenFor(services, saved.screen().strip(), chosen.pick().as());
        if (screen.isEmpty()) {
            return AgentReply.refused("No screen " + saved.screen().strip() + " in the bot's Screens: try_input adds "
                    + "one when an input opens it; the first screen is started by its marker. Nothing was saved.");
        }
        String said = ElementSaves.save(context, screen.get(), tree.get(), frame.get(), List.of(chosen.pick()));
        return said.startsWith(ScreenEdits.NOT_WRITTEN) ? AgentReply.refused(said) : AgentReply.text(said);
    }

    /** The screen's constant; on a map with no screen yet, a marker's screen started as the one the game opens on. */
    private static Optional<String> screenFor(StudioServices services, String typed, As as) {
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        Optional<String> found = ScreenBook.find(graph, typed);
        if (found.isPresent() || as != As.MARKER || !graph.screens().isEmpty()) return found;
        FxCall.call(() -> ScreenEdits.newStartScreen(services, typed));
        return ScreenBook.find(FxCall.call(() -> ScreenBook.read(services)), typed);
    }

    /** One row of {@code rows} chosen, with what it is saved as; or why none is, naming the candidates. */
    record Chosen(Pick pick, String refusal) {}

    /**
     * The one row of {@code rows} whose words are {@code words} (any case), of {@code kind} when given, saved
     * {@code as} (null: the first it offers). Copies of a list's row are one input, saved repeated: the first stands
     * for them. Refused with the candidates when none or several others match.
     */
    static Chosen chosen(List<Row> rows, String words, String kind, String as) {
        String typed = words.strip();
        List<Row> worded = rows.stream().filter(r -> r.words().strip().equalsIgnoreCase(typed)).toList();
        // With no kind, an input wins over a text or a list of the same words (a list says its first row's): kind
        // names the others.
        List<Row> inputs = worded.stream().filter(Row::tappable).toList();
        List<Row> matching = kind == null || kind.isBlank() ? (inputs.isEmpty() ? worded : inputs) : worded.stream()
                .filter(r -> r.kind().displayName().equalsIgnoreCase(kind.strip())
                        || r.kind().name().equalsIgnoreCase(kind.strip().replace(' ', '_')))
                .toList();
        if (matching.isEmpty()) {
            List<Row> near = worded.isEmpty() ? rows.stream().filter(r -> r.words().toLowerCase(Locale.ROOT)
                    .contains(typed.toLowerCase(Locale.ROOT))).toList() : worded;
            return new Chosen(null, "No row \"" + typed + "\"" + (kind == null || kind.isBlank() ? "" : " of kind "
                    + kind.strip()) + " in the app's tree." + (near.isEmpty() ? " list_elements gives what it holds."
                    : " Close: " + said(near) + ".") + " Nothing was saved.");
        }
        boolean copies = matching.size() > 1 && matching.stream().allMatch(r -> r.named().repeated()
                && r.kind() == matching.getFirst().kind());
        if (matching.size() > 1 && !copies) {
            return new Chosen(null, "\"" + typed + "\" is " + matching.size() + " rows: " + said(matching)
                    + ". Give kind to pick one. Nothing was saved.");
        }
        Row row = matching.getFirst();
        List<As> offered = As.offered(row);
        As picked = as == null || as.isBlank() ? offered.getFirst() : offered.stream()
                .filter(a -> a.name().equalsIgnoreCase(as.strip())).findFirst().orElse(null);
        if (picked == null) {
            return new Chosen(null, row.said() + " is saved as " + offered.stream().map(a -> a.name()
                    .toLowerCase(Locale.ROOT)).collect(Collectors.joining(" or ")) + ", not " + as.strip()
                    + ". Nothing was saved.");
        }
        return new Chosen(new Pick(row, picked), null);
    }

    /** One line per row: kind and words, how it is found, what it may be saved as. Never an id's value or a bound. */
    static String listed(List<Row> rows) {
        if (rows.isEmpty()) return NOTHING;
        List<String> lines = new ArrayList<>();
        for (Row row : rows) {
            StringBuilder line = new StringBuilder("- ").append(row.said()).append(": by ")
                    .append(ElementRows.clues(row.named().element()));
            if (row.named().repeated()) line.append("; one of a list's copies, saved repeated");
            if (row.named().byIndex()) line.append("; told apart by its place only");
            line.append("; as ").append(As.offered(row).stream().map(a -> a.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" or ")));
            lines.add(line.toString());
        }
        // Copies say the same: listed once each.
        List<String> once = lines.stream().distinct().toList();
        return once.size() + (once.size() == 1 ? " row" : " rows") + " in the app's tree; save_element saves one by "
                + "its words:\n" + String.join("\n", once);
    }

    private static String said(List<Row> rows) {
        return rows.stream().map(Row::said).distinct().collect(Collectors.joining(", "));
    }

    static final String NO_TREE = "No tree here: this target shows no app's inputs (a game, a window). Cut pictures "
            + "instead (crop_pictures).";
    static final String UNREAD = "The app's tree could not be read: look again (screenshot), then list_elements.";
    static final String NOTHING = "Nothing in this app's tree to save: cut pictures instead (crop_pictures).";
}
