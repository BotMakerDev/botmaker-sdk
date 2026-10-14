package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.flow.FlowValue;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the project's saved image templates (PNG files under {@code src/main/resources/images}) and maps
 * between a template file and the path string an SDK {@code ImageTemplate(String)} expects.
 *
 * <p>The SDK loads templates from the filesystem relative to the working directory (which is the project
 * root at run time), so the path embedded in code is relative to the project root, e.g.
 * {@code "src/main/resources/images/accept_button.png"} — always forward-slashed for cross-platform use.
 *
 * <p><b>Adding a picture writes no Java.</b> Nothing is generated per file, so there is no constant to keep
 * in step with the folder.
 *
 * <p>A <em>named picture</em> is {@code ImageTemplate}'s own concept, not an editor's, so the plugin that
 * offers the type is the one that owns the folder.
 *
 * <p><b>It is keyed on the resources directory</b>, which is exactly what the plugin contract's
 * {@code StudioServices.resourcesDir()} hands over — so a plugin can reach its own pictures with nothing
 * added to the contract. The images folder and the project root to relativize against are both derivable
 * from that single path.
 */
public final class TemplateLibrary {

    private TemplateLibrary() {}

    /** The images folder of a project whose resources directory is {@code resourcesDir}. */
    public static Path imagesDir(Path resourcesDir) {
        return resourcesDir.resolve("images");
    }

    /**
     * File name of the built-in default template.
     *
     * <p>The SDK's, since it is the SDK that makes the file, on the first look at a project's picture folder
     * ({@link #ensurePlaceholder}). Three questions are asked about
     * it — <em>is this the placeholder?</em> (rename/delete protection), <em>is it still untouched?</em>
     * (export) and <em>is it there at all?</em> — and all three have to be asking about the same file.
     */
    public static final String DEFAULT_TEMPLATE_FILE = TemplateNames.DEFAULT_TEMPLATE_FILE;

    /**
     * Project-root-relative path a fresh {@code new ImageTemplate(...)} references so a newly-dropped vision
     * block compiles immediately against a real (if placeholder) template rather than a missing file.
     */
    public static final String DEFAULT_TEMPLATE_PATH = "src/main/resources/images/" + DEFAULT_TEMPLATE_FILE;

    /** The default template's base name — what a value that names a template by name is seeded with. */
    public static final String DEFAULT_TEMPLATE_NAME =
            DEFAULT_TEMPLATE_FILE.substring(0, DEFAULT_TEMPLATE_FILE.lastIndexOf('.'));

    /** True when {@code file} is the project's built-in default template (protected from rename/delete). */
    public static boolean isDefaultTemplate(Path file) {
        return file != null && file.getFileName().toString().equalsIgnoreCase(DEFAULT_TEMPLATE_FILE);
    }

    /**
     * The placeholder every new project's default template starts as: a 32px teal/white checker.
     *
     * <p>The pattern is the SDK's, for the same reason the file name is: creation writes it there. A second
     * checker here would answer {@link #isUnmodifiedDefaultTemplate} wrongly the day either was adjusted.
     */
    public static BufferedImage defaultTemplateImage() {
        return TemplateNames.defaultTemplateImage();
    }

    /**
     * The placeholder in {@code resourcesDir}'s images folder, made if it is not there.
     *
     * <p><b>Project creation does not write it.</b> A placeholder picture is only meaningful to whoever offers {@code ImageTemplate}, so a project created in an editor that
     * never loaded this plugin has no business being given one — and the surfaces that <em>do</em> need it
     * (the gallery, the picture pickers, a value seeded with {@code default_template}) all hold a resources
     * directory at the moment they need it. Calling this when a picture folder is first looked at is both
     * later and narrower than writing it into every new project.
     *
     * <p>Idempotent, and deliberately so: it is called on every open of every picture surface, and the file
     * it makes is the user's from the moment they point it at something real.
     */
    public static void ensurePlaceholder(Path resourcesDir) throws IOException {
        writePlaceholderAt(resourcesDir.resolve("images").resolve(DEFAULT_TEMPLATE_FILE));
    }

    /**
     * Writes the placeholder at exactly {@code target}, if it is not already there.
     *
     * <p>The path-taking half, for <em>recovery</em>: it knows the path it found missing, and the alternative
     * is a second copy of the "which file is the placeholder" rule for the two to drift apart on.
     */
    public static void writePlaceholderAt(Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        ImageIO.write(defaultTemplateImage(), "png", target.toFile());
    }

    /**
     * True when {@code file} is the default template and still holds the generated placeholder — nobody has
     * pointed it at anything real yet.
     *
     * <p>Compared pixel by pixel rather than by file bytes: the PNG is re-encoded by whichever ImageIO wrote
     * it, so two encodings of the same picture differ as files while being the same template. Export uses
     * this to leave an untouched placeholder out of an archive, so importing that archive back doesn't add a
     * {@code default_template_2} nobody asked for.
     */
    public static boolean isUnmodifiedDefaultTemplate(Path file) {
        if (!isDefaultTemplate(file)) return false;
        try {
            BufferedImage actual = ImageIO.read(file.toFile());
            BufferedImage pristine = defaultTemplateImage();
            if (actual == null
                    || actual.getWidth() != pristine.getWidth() || actual.getHeight() != pristine.getHeight()) {
                return false;
            }
            for (int y = 0; y < pristine.getHeight(); y++) {
                for (int x = 0; x < pristine.getWidth(); x++) {
                    if (actual.getRGB(x, y) != pristine.getRGB(x, y)) return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;   // unreadable: treat as the user's own, and export it rather than drop it
        }
    }

    /**
     * Normalizes a user-entered template name to the allowed character set: trims surrounding whitespace,
     * replaces every character outside {@code [A-Za-z0-9_]} with {@code _}, and lowercases the result. The
     * result may still be blank (when the input was blank or all-whitespace) — callers must reject blanks and
     * check {@link #exists} for uniqueness. Shared by every naming path (the single-capture prompt and the
     * batch dialog).
     *
     * <p><b>Lowercase, and no {@code -}, because the name is also a Java constant.</b> Every picture is
     * declared in the bot's {@code Pictures} class as {@code ORE = new ImageTemplate("…/ore.png")}, and a block
     * names it through that constant. Restricting the name to a lowercase identifier makes the two exactly
     * reversible — see {@link TemplateNames}. Names captured before this rule keep working; they simply get no
     * constant until they are renamed.
     */
    public static String sanitizeName(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
    }

    /** All saved template PNGs, sorted by file name. */
    public static List<Path> list(Path resourcesDir) {
        Path dir = imagesDir(resourcesDir);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            System.err.println("Failed to list image templates: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * The project-root-relative, forward-slashed path string for {@code templateFile}.
     *
     * <p>Built from the file's own <em>name</em> and the one prefix every template shares
     * ({@link TemplateNames#IMAGE_PREFIX}) rather than by relativizing against a project root, which is what let
     * this class stop needing one. The two agree by construction — a template is a PNG directly inside
     * {@code src/main/resources/images} and there is nowhere else for one to be.
     */
    public static String pathFor(Path templateFile) {
        return TemplateNames.IMAGE_PREFIX + templateFile.getFileName().toString();
    }

    /**
     * The <b>project-root-relative</b> path string for a template named {@code baseName} (without extension) —
     * what gets <em>stored</em>, in a bot's source and in a variable's wire value, so a project stays portable.
     *
     * <p>It is not a path you can open. Two callers did anyway, handing it to {@code Path.of} and resolving it
     * against whatever directory Studio happened to be started from, which is why a template's thumbnail was
     * missing everywhere outside the gallery. Use {@link #fileForName} to open one.
     */
    public static String pathForName(String baseName) {
        return TemplateNames.pathFor(baseName);
    }

    /** The template PNG named {@code baseName} as a file you can actually read — absolute, may not exist. */
    public static Path fileForName(Path resourcesDir, String baseName) {
        return imagesDir(resourcesDir).resolve(baseName + ".png");
    }

    /** Base name (no extension) of a template file, used as its display label. */
    public static String baseName(Path templateFile) {
        String name = templateFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /**
     * Whether a template PNG named {@code baseName} already exists (case-insensitive) — used to block
     * duplicate names so a new capture never silently overwrites an existing template.
     */
    public static boolean exists(Path resourcesDir, String baseName) {
        if (baseName == null || baseName.isBlank()) return false;
        String wanted = (baseName + ".png").toLowerCase(Locale.ROOT);
        Path dir = imagesDir(resourcesDir);
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).equals(wanted));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Saves {@code img} as {@code <imagesRoot>/<baseName>.png} and returns the template's project-root-relative
     * path (the string for {@code new ImageTemplate("…")}). Where it was cut is written on its constant, not
     * beside it ({@link TemplateUses#cut}). {@code baseName} must already be sanitized. Does not check for an
     * existing file — callers gate that via {@link #exists}.
     */
    public static String saveTemplate(Path resourcesDir, BufferedImage img, String baseName) throws IOException {
        Path png = imagesDir(resourcesDir).resolve(baseName + ".png");
        Files.createDirectories(png.getParent());
        ImageIO.write(img, "png", png.toFile());
        return pathFor(png);
    }

    /**
     * Overwrites an existing template's picture, keeping its name, its tags and every block that references
     * it. Its constant is rewritten by the caller ({@link TemplateUses#cut}): a replacement is usually cut from
     * another frame, and a stale {@code .in(Size)} would have the SDK rescale the new picture by the old one's.
     *
     * <p>Separate from {@link #saveTemplate} even though the file write is the same, because the two answer
     * different questions: saving refuses an existing name (that would be a silent overwrite), replacing
     * requires one.
     */
    public static void replaceImage(Path templateFile, BufferedImage img) throws IOException {
        Files.createDirectories(templateFile.getParent());
        ImageIO.write(img, "png", templateFile.toFile());
    }

    // ── Tags ────────────────────────────────────────────────────────────────────────────────────────────
    //
    // The tags are the bot's Java (PictureTags), read on every call rather than cached: pictures are cut by an
    // overlay, a batch dialog, the resource manager and the assistant, and a cache here would be one more
    // thing to invalidate. Every method here that reads or writes them is on the FX thread.

    /** Every picture's tags, read off its constant, and the custom tags {@code Tags} declares. */
    public static TemplateManifest manifest(StudioServices services) {
        return PictureTags.read(services);
    }

    /**
     * The project's declared tags — one per activity, plus the custom ones {@code Tags} declares. Read afresh
     * for the same reason the tags are (see above): the activities are edited by a dialog, a canvas and a
     * repair pass, and a cache here would be another of them to invalidate.
     *
     * <p>It takes the services rather than a folder because half of it is the flow, which is a value in the
     * bot's own Java and read through the host.
     */
    public static TagCatalog tagCatalog(StudioServices services) {
        return TagCatalog.of(activityNames(services), manifest(services).customTags());
    }

    /**
     * The project's activity names in the order its flows list them, each once, or none.
     *
     * <p>Degrading rather than throwing is the difference between a tag picklist that is short and one that
     * will not open — an unreadable flow is reported by the flow editor, not by a tag menu.
     */
    private static List<String> activityNames(StudioServices services) {
        return FlowValue.labels(services.pluginValues(), null).stream()
                .filter(name -> !name.isBlank())
                .toList();
    }

    /**
     * The saved templates grouped as {@code tag → files} over the declared set: {@link TemplateManifest#ALL}
     * first, then every declared tag (empty ones included — a tag exists because it was declared, not because
     * something carries it), then {@link TemplateManifest#UNTAGGED}. A template carrying two tags appears
     * under both; there is only ever one file.
     */
    public static Map<String, List<Path>> listByTag(StudioServices services) {
        Path resourcesDir = services.resourcesDir();
        List<Path> files = list(resourcesDir);
        Map<String, Path> byName = new LinkedHashMap<>();
        for (Path file : files) byName.put(baseName(file), file);

        Map<String, List<Path>> grouped = new LinkedHashMap<>();
        manifest(services).byTag(byName.keySet(), tagCatalog(services)).forEach((tag, names) ->
                grouped.put(tag, names.stream().map(byName::get).filter(Objects::nonNull).toList()));
        return grouped;
    }

    /**
     * Files each template under the tags chosen for it, dropping any that the project doesn't declare. The map is
     * {@code base name → tags}; a name mapped to an empty list is left untagged.
     *
     * @return why a picture's constant was not rewritten, one sentence each; empty when all were
     */
    public static List<String> applyTags(StudioServices services,
                                         Map<String, ? extends Collection<String>> tagsByTemplate) {
        TagCatalog catalog = tagCatalog(services);
        List<String> refused = new ArrayList<>();
        for (Map.Entry<String, ? extends Collection<String>> entry : tagsByTemplate.entrySet()) {
            PictureTags.set(services, entry.getKey(), catalog.declaredOnly(entry.getValue())).ifPresent(refused::add);
        }
        return refused;
    }

    /**
     * Adds one declared {@code tag} to every named template — "add these to this tag", the operation a tag view
     * wants, as opposed to {@link #applyTags} which <em>replaces</em> a template's whole tag set. An undeclared tag
     * is refused rather than declared as a side effect: declaring is {@link #declareTag}'s job, and a typo should
     * not become a group.
     */
    public static List<String> addTag(StudioServices services, Collection<String> baseNames, String tag) {
        if (baseNames.isEmpty() || !tagCatalog(services).isDeclared(tag)) return List.of();
        return retag(services, baseNames, tag, true);
    }

    /** Takes {@code tag} off every named template. The tag itself survives, empty. */
    public static List<String> removeTag(StudioServices services, Collection<String> baseNames, String tag) {
        return retag(services, baseNames, tag, false);
    }

    private static List<String> retag(StudioServices services, Collection<String> baseNames, String tag,
                                      boolean add) {
        TemplateManifest manifest = manifest(services);
        Map<String, List<String>> changed = new LinkedHashMap<>();
        for (String name : baseNames) {
            List<String> tags = new ArrayList<>(manifest.tagsOf(name));
            boolean had = tags.stream().anyMatch(tag::equalsIgnoreCase);
            if (add == had) continue;
            if (add) tags.add(tag);
            else tags.removeIf(tag::equalsIgnoreCase);
            changed.put(name, tags);
        }
        return applyTags(services, changed);
    }

    /** Declares {@code tag} as a custom tag in {@code Tags}; empty when done, else why not. */
    public static Optional<String> declareTag(StudioServices services, String tag) {
        return PictureTags.declare(services, tag);
    }

    /**
     * Renames a template's file. Its tags are on its constant, which {@link TemplateUses#rename} renames with
     * every use; they follow it there.
     */
    public static void renameTemplate(Path resourcesDir, Path file, String newBaseName) throws IOException {
        Path target = imagesDir(resourcesDir).resolve(newBaseName + ".png");
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Deletes a template's file. Its tags go with its constant ({@link TemplateUses#forget}). */
    public static void deleteTemplate(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    // ── The same picture twice ──────────────────────────────────────────────────────────────────────────
    //
    // Importing a picture the library already holds under another name is allowed — the same button in two
    // menus is genuinely two templates to the person authoring the bot. What is not allowed is it happening
    // silently, so the library can answer "who else holds this picture" and the resource manager says so.

    /**
     * A stable fingerprint of what a template <em>looks like</em>: its size followed by its pixels, hashed.
     *
     * <p>Pixels rather than file bytes, for the reason {@link #isUnmodifiedDefaultTemplate} spells out — a PNG
     * is re-encoded by whichever ImageIO wrote it, so two writes of one picture differ as files while being the
     * same template, and a duplicate index built on file bytes would miss exactly the round-trip that produces
     * duplicates. Returns {@code null} for anything that will not decode, which every caller reads as "no
     * opinion" rather than as a group of its own.
     */
    public static String pictureHash(BufferedImage img) {
        if (img == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int w = img.getWidth();
            int h = img.getHeight();
            ByteBuffer row = ByteBuffer.allocate(Integer.BYTES * Math.max(1, w));
            digest.update(ByteBuffer.allocate(8).putInt(w).putInt(h).array());
            for (int y = 0; y < h; y++) {
                row.clear();
                for (int x = 0; x < w; x++) row.putInt(img.getRGB(x, y));
                digest.update(row.array(), 0, row.position());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** {@link #pictureHash(BufferedImage)} of a template file, or null when it isn't there or won't decode. */
    public static String pictureHash(Path file) {
        try {
            return file != null && Files.isRegularFile(file) ? pictureHash(ImageIO.read(file.toFile())) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * True when {@code file} holds the same picture as the PNG bytes {@code incoming}. Used by the archive to
     * skip re-importing what is already here; it lives beside the index so both answers come from one rule.
     */
    public static boolean sameContent(Path file, byte[] incoming) {
        try {
            String mine = pictureHash(file);
            String theirs = incoming == null ? null
                    : pictureHash(ImageIO.read(new ByteArrayInputStream(incoming)));
            return mine != null && mine.equals(theirs);
        } catch (IOException e) {
            return false;   // can't tell ⇒ treat as different, which is the recoverable outcome
        }
    }

    /**
     * Every template that shares its picture with another one, as {@code base name → the other names}, sorted.
     * Templates whose picture nothing else has are absent, so an empty map means "no duplicates anywhere".
     *
     * <p>Built by decoding the library, which is why it is computed when the resource manager opens and not
     * held: a project has tens of small PNGs, and a cache here would be one more thing every capture, import,
     * rename and replace would have to invalidate (see the note on the tags above).
     */
    public static Map<String, List<String>> duplicatePictures(Path resourcesDir) {
        Map<String, List<String>> byHash = new LinkedHashMap<>();
        for (Path file : list(resourcesDir)) {
            String hash = pictureHash(file);
            if (hash != null) byHash.computeIfAbsent(hash, h -> new ArrayList<>()).add(baseName(file));
        }
        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        for (List<String> names : byHash.values()) {
            if (names.size() < 2) continue;
            for (String name : names) {
                duplicates.put(name, names.stream().filter(other -> !other.equals(name)).sorted().toList());
            }
        }
        return duplicates;
    }
}
