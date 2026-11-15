package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A page a run met that no screen of its map matched, kept in the run's folder so the bot's author can map it: the
 * page's tree, masked as it is built. No text holding an {@code @} or a digit is kept, only its length
 * ({@link #masked}); a field keeps no value at all. What reads it never sees an address, a code or what was typed.
 *
 * <p>On disk: {@code page-unknown-NN.json} in the run's folder beside its log, as the run's missed frames are, at
 * most {@value #KEEP} a run, pruned with the folder. {@code {"t":…,"roots":[node…]}}, a node {@code {"id","class",
 * "text","label","field","rect":[x,y,w,h],"children":[…]}}, any field missing read as empty. Every text is masked
 * again as it is read, so a file edited by hand shows no more than one the run wrote.
 *
 * @param atMillis when the run saved it
 * @param roots    the page's windows' top nodes, in the tree's order
 */
public record PageUnknown(long atMillis, List<Node> roots) {

    /** What one masked character reads as. */
    public static final char MASK = '•';
    /** The most pages one run keeps; past it a page is not saved. */
    public static final int KEEP = 10;
    static final String PREFIX = "page-unknown-";
    static final String SUFFIX = ".json";
    /** The deepest a page is read: a tree deeper than any screen's is cut there. */
    static final int MAX_DEPTH = 64;
    private static final ObjectMapper JSON = new ObjectMapper();

    public PageUnknown {
        roots = roots == null ? List.of() : List.copyOf(roots);
    }

    /**
     * One node of the page.
     *
     * @param id        its short resource id ({@code identifierId}), masked
     * @param className the view class Android reports, without its package
     * @param text      its own text, masked; {@code ""} for a field
     * @param label     its accessibility label, masked
     * @param field     whether it takes typing: its value is never kept
     * @param rect      where it is on the screen, in device pixels, or null
     * @param children  its children, in order
     */
    public record Node(String id, String className, String text, String label, boolean field, Rect rect,
                       List<Node> children) {

        public Node {
            id = masked(id);
            className = className == null ? "" : className;
            text = field ? "" : masked(text);
            label = masked(label);
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    /**
     * {@code text} as a page keeps it: as it is unless it holds an {@code @} or a digit, which an address, a phone
     * number or a code would; then one {@link #MASK} per character. Null reads as {@code ""}.
     */
    public static String masked(String text) {
        if (text == null) return "";
        boolean telling = text.chars().anyMatch(c -> c == '@' || Character.isDigit(c));
        return telling ? String.valueOf(MASK).repeat(text.length()) : text;
    }

    /** Writes this page into run folder {@code run} as its next file; empty when the run kept {@value #KEEP}. */
    public Optional<Path> writeTo(Path run) throws IOException {
        synchronized (PageUnknown.class) {
            Files.createDirectories(run);
            List<Path> kept = files(run);
            if (kept.size() >= KEEP) return Optional.empty();
            // After the highest number kept, never over a file: a gap in the numbers costs nothing.
            int next = kept.stream().mapToInt(PageUnknown::number).max().orElse(0) + 1;
            Path file = run.resolve(String.format("%s%02d%s", PREFIX, next, SUFFIX));
            ObjectNode json = JSON.createObjectNode();
            json.put("t", atMillis);
            ArrayNode array = json.putArray("roots");
            roots.forEach(n -> array.add(json(n)));
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return Optional.of(file);
        }
    }

    /** The pages saved in run folder {@code run}, in the order they were saved; one that doesn't read is skipped. */
    public static List<PageUnknown> readFrom(Path run) {
        return readEach(run).stream().map(Saved::page).toList();
    }

    /** Each page of run folder {@code run} that reads, with its file; one that doesn't (half written) costs itself. */
    private static List<Saved> readEach(Path run) {
        List<Saved> out = new ArrayList<>();
        for (Path file : files(run)) {
            try {
                JsonNode json = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
                if (json == null || !json.isObject()) continue;
                List<Node> roots = new ArrayList<>();
                json.path("roots").forEach(n -> roots.add(node(n, 0)));
                out.add(new Saved(run.getFileName().toString(), file.getFileName().toString(),
                        new PageUnknown(json.path("t").asLong(), roots)));
            } catch (IOException | RuntimeException e) {
                // a page that doesn't read costs itself
            }
        }
        return out;
    }

    /** The number in a page's file name, or 0. */
    private static int number(Path file) {
        String name = file.getFileName().toString();
        try {
            return Integer.parseInt(name.substring(PREFIX.length(), name.length() - SUFFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * One page a past run saved, as the Screens window lists it.
     *
     * @param run  the run's folder name ({@code yyyyMMdd-HHmmss})
     * @param file the page's file name in it
     * @param page the page, masked again as read
     */
    public record Saved(String run, String file, PageUnknown page) {}

    /**
     * The pages the newest {@code runs} runs of the bot in {@code botFolder} saved: newest run first, each run's pages
     * in the order it saved them. Empty when no run saved one.
     */
    public static List<Saved> recent(Path botFolder, int runs) {
        Path all = botFolder.resolve(MissFrames.FOLDER);
        if (!Files.isDirectory(all)) return List.of();
        List<Path> newest;
        try (Stream<Path> dirs = Files.list(all)) {
            newest = dirs.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName).reversed())
                    .limit(Math.max(runs, 0)).toList();
        } catch (IOException e) {
            return List.of();
        }
        List<Saved> out = new ArrayList<>();
        for (Path run : newest) out.addAll(readEach(run));
        return out;
    }

    private static List<Path> files(Path run) {
        if (!Files.isDirectory(run)) return List.of();
        try (Stream<Path> files = Files.list(run)) {
            return files.filter(f -> {
                String name = f.getFileName().toString();
                return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
            }).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static ObjectNode json(Node node) {
        ObjectNode json = JSON.createObjectNode();
        if (!node.id().isEmpty()) json.put("id", node.id());
        if (!node.className().isEmpty()) json.put("class", node.className());
        if (!node.text().isEmpty()) json.put("text", node.text());
        if (!node.label().isEmpty()) json.put("label", node.label());
        if (node.field()) json.put("field", true);
        if (node.rect() != null) {
            json.putArray("rect").add(node.rect().x()).add(node.rect().y()).add(node.rect().width())
                    .add(node.rect().height());
        }
        if (!node.children().isEmpty()) {
            ArrayNode children = json.putArray("children");
            node.children().forEach(c -> children.add(json(c)));
        }
        return json;
    }

    private static Node node(JsonNode json, int depth) {
        JsonNode r = json.get("rect");
        Rect rect = r != null && r.isArray() && r.size() == 4
                ? new Rect(r.get(0).asInt(), r.get(1).asInt(), r.get(2).asInt(), r.get(3).asInt()) : null;
        List<Node> children = new ArrayList<>();
        if (depth < MAX_DEPTH) json.path("children").forEach(c -> children.add(node(c, depth + 1)));
        return new Node(json.path("id").asText(""), json.path("class").asText(""), json.path("text").asText(""),
                json.path("label").asText(""), json.path("field").asBoolean(false), rect, children);
    }
}
