package com.botmaker.sdk.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reads the project's saved image templates (PNG files under {@code src/main/resources/images}) and maps
 * between a template file and the path string an SDK {@code ImageTemplate(String)} expects.
 *
 * <p>The SDK loads templates from the filesystem relative to the working directory (which is the project
 * root at run time), so the path embedded in code is relative to the project root, e.g.
 * {@code "src/main/resources/images/accept_button.png"} — always forward-slashed for cross-platform use.
 *
 * <p><b>Adding a picture writes no Java.</b> There was a generated {@code Templates.java} holding one
 * {@code public static final String} per file in the images folder, rewritten after every add, rename and
 * delete, so a bot could name a picture and have a typo be a compile error. A picture is named by its file
 * now, so there is no constant to keep in step. The lineage went the long way round — Studio emitted the
 * file, then the SDK did (a second emitter being a second author of one file), then it stopped being emitted
 * at all — and {@code regenerateTemplatesClass} outlived the file it wrote by three days.
 *
 * <p><b>It was Studio's {@code services.ImageTemplateLibrary} until 2026-08-30</b>, and it is here for the
 * reason {@code capture.json} is: a <em>named picture</em> is {@code ImageTemplate}'s own concept, not an
 * editor's, so the plugin that offers the type has to be the one that owns the folder. Two readers of one
 * folder is the drift the capture-target work spent a whole phase deleting.
 *
 * <p><b>It is keyed on the resources directory</b>, which is {@link Authoring}'s idiom and, not by accident,
 * exactly what the plugin contract's {@code StudioServices.resourcesDir()} hands over — so a plugin can reach
 * its own pictures with nothing added to the contract. Studio's {@code ProjectConfig} answered three
 * questions here (the images folder, the project root to relativize against, the activities file) and every
 * one of them is derivable from that single path.
 *
 * <p>The one method that did <b>not</b> come along is {@code openActivityTag}: it reads the editor's active
 * file, which is host state and nobody else's. It stayed in Studio.
 */
public final class TemplateLibrary {

    private TemplateLibrary() {}

    /** The images folder of a project whose resources directory is {@code resourcesDir}. */
    public static Path imagesDir(Path resourcesDir) {
        return resourcesDir.resolve("images");
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The resolution metadata written alongside every captured template as a {@code <name>.json} sidecar.
     * {@code width}/{@code height} are the template's own pixel size; {@code captureWidth}/{@code
     * captureHeight} are the resolution (physical pixels) of the capture source (window/screen) the template
     * was authored against — the SDK reads these to rescale the template when the target runs at a different
     * resolution (see {@code ImageTemplate.captureResolution()}). {@code captureWidth}/{@code captureHeight}
     * of {@code 0} mean "unknown" (the SDK then falls back to the project-wide default resolution).
     */
    public record TemplateMetadata(int width, int height, int captureWidth, int captureHeight,
                                   String target, String createdAt) {}

    /**
     * File name of the built-in default template shipped in every new project.
     *
     * <p>The SDK's, since it is the SDK that writes the file at creation (the inversion, phase 3). Studio asks
     * two further questions about it — <em>is this the placeholder?</em> (rename/delete protection) and
     * <em>is it still untouched?</em> (export) — and both have to be asking about the same file.
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
     * Writes the placeholder at exactly {@code target}, if it is not already there.
     *
     * <p>Creation does not go through this — the SDK writes the placeholder with the rest of the project —
     * but <em>recovery</em> does: it knows the path it found missing, and the alternative is a second copy of
     * the "which file is the placeholder" rule for the two to drift apart on.
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
     * <p><b>Lowercase, and no {@code -}, because the name is also a Java constant.</b> Every template is
     * declared in the generated {@code Templates} class as {@code YTUJ = "…/ytuj.png"}, and Studio reads that
     * constant back to know which file a block refers to. Restricting the name to a lowercase identifier makes
     * the two exactly reversible — see {@link TemplateNames}. Names captured
     * before this rule keep working; they simply get no constant.
     */
    public static String sanitizeName(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
    }

    /**
     * Whether {@code baseName} would collide with a file the library owns rather than a template — today the
     * one name whose {@code <name>.json} sidecar <em>is</em> {@link TemplateManifest#FILE_NAME}. Checked by
     * every naming path alongside {@link #exists}: saving a template called "templates" would otherwise
     * overwrite the tag manifest with a resolution sidecar.
     */
    public static boolean isReservedName(String baseName) {
        return baseName != null && TemplateManifest.FILE_NAME.equalsIgnoreCase(baseName + ".json");
    }

    /** All saved template PNGs, sorted by file name. */
    public static List<Path> list(Path resourcesDir) {
        Path dir = imagesDir(resourcesDir);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
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
     * ({@link WireText#IMAGE_PREFIX}) rather than by relativizing against a project root, which is what let
     * this class stop needing one. The two agree by construction — a template is a PNG directly inside
     * {@code src/main/resources/images} and there is nowhere else for one to be.
     */
    public static String pathFor(Path templateFile) {
        return WireText.IMAGE_PREFIX + templateFile.getFileName().toString();
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
        return WireText.IMAGE_PREFIX + baseName + ".png";
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

    /** The metadata sidecar ({@code <name>.json}) that lives next to a template PNG. */
    public static Path sidecarFor(Path templateFile) {
        return templateFile.resolveSibling(baseName(templateFile) + ".json");
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
     * Saves {@code img} as {@code <imagesRoot>/<baseName>.png} plus a {@code <baseName>.json} resolution
     * sidecar, and returns the template's project-root-relative path (the string for
     * {@code new ImageTemplate("…")}). {@code captureWidth}/{@code captureHeight} are the capture source's
     * physical resolution (pass {@code 0} when unknown); {@code targetTitle} may be {@code null}.
     * {@code baseName} must already be sanitized. Does not check for an existing file — callers gate that
     * via {@link #exists}.
     */
    public static String saveTemplate(Path resourcesDir, BufferedImage img, String baseName,
                                      int captureWidth, int captureHeight, String targetTitle) throws IOException {
        Path png = imagesDir(resourcesDir).resolve(baseName + ".png");
        Files.createDirectories(png.getParent());
        ImageIO.write(img, "png", png.toFile());
        TemplateMetadata meta = new TemplateMetadata(img.getWidth(), img.getHeight(),
                Math.max(0, captureWidth), Math.max(0, captureHeight), targetTitle, Instant.now().toString());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(sidecarFor(png).toFile(), meta);
        return pathFor(png);
    }

    /**
     * Overwrites an existing template's picture, keeping its name, its tags and every block that references
     * it. The resolution sidecar is rewritten too — a replacement is usually recaptured against a different
     * window size, and a stale {@code captureWidth} would have the SDK rescale the new picture by the old
     * picture's ratio.
     *
     * <p>Separate from {@link #saveTemplate} even though the file write is the same, because the two answer
     * different questions: saving refuses an existing name (that would be a silent overwrite), replacing
     * requires one.
     */
    public static void replaceImage(Path templateFile, BufferedImage img,
                                    int captureWidth, int captureHeight, String targetTitle) throws IOException {
        Files.createDirectories(templateFile.getParent());
        ImageIO.write(img, "png", templateFile.toFile());
        TemplateMetadata meta = new TemplateMetadata(img.getWidth(), img.getHeight(),
                Math.max(0, captureWidth), Math.max(0, captureHeight), targetTitle, Instant.now().toString());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(sidecarFor(templateFile).toFile(), meta);
    }

    // ── Tags ────────────────────────────────────────────────────────────────────────────────────────────
    //
    // The manifest is read from disk on every call rather than cached. Templates are captured by an overlay,
    // a batch dialog and the resource manager, and imported by an archive — a cache here would be a fifth
    // thing to invalidate, for a file of a few hundred bytes read only when a menu opens.

    /** The project's tag manifest, or an empty one when it has none. */
    public static TemplateManifest manifest(Path resourcesDir) {
        return TemplateManifest.read(imagesDir(resourcesDir));
    }

    /** Persists {@code manifest} for the project; best-effort — a failure loses tags, never templates. */
    public static void saveManifest(Path resourcesDir, TemplateManifest manifest) {
        try {
            manifest.write(imagesDir(resourcesDir));
        } catch (IOException e) {
            System.err.println("Failed to save template tags: " + e.getMessage());
        }
    }

    /**
     * The project's declared tags — one per activity, plus the custom ones from the manifest. Read from disk
     * for the same reason the manifest is (see above): the activities are edited by a dialog, a canvas and a
     * repair pass, and a cache here would be another of them to invalidate.
     */
    public static TagCatalog tagCatalog(Path resourcesDir) {
        return TagCatalog.of(activityNames(resourcesDir), manifest(resourcesDir).customTags());
    }

    /**
     * The project's activity names in file order, or none when the file is absent or will not parse.
     *
     * <p>Degrading here rather than throwing is the difference between a tag picklist that is short and one
     * that will not open. An unreadable {@code activities.json} is a real problem and the flow editor is
     * where the user meets it; a template's tag menu is not the place to report it.
     */
    private static List<String> activityNames(Path resourcesDir) {
        try {
            return Authoring.readModel(SdkVersion.latest(), resourcesDir).activities().stream()
                    .map(ActivityModel::name)
                    .toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * The saved templates grouped as {@code tag → files} over the declared set: {@link TemplateManifest#ALL}
     * first, then every declared tag (empty ones included — a tag exists because it was declared, not because
     * something carries it), then {@link TemplateManifest#UNTAGGED}. A template carrying two tags appears
     * under both; there is only ever one file.
     */
    public static Map<String, List<Path>> listByTag(Path resourcesDir) {
        List<Path> files = list(resourcesDir);
        Map<String, Path> byName = new LinkedHashMap<>();
        for (Path file : files) byName.put(baseName(file), file);

        Map<String, List<Path>> grouped = new LinkedHashMap<>();
        manifest(resourcesDir).byTag(byName.keySet(), tagCatalog(resourcesDir)).forEach((tag, names) ->
                grouped.put(tag, names.stream().map(byName::get).filter(Objects::nonNull).toList()));
        return grouped;
    }

    /**
     * Files each template under the tags chosen for it, dropping any that the project doesn't declare, and
     * saves once. The map is {@code base name → tags}; a name mapped to an empty list is left untagged.
     *
     * <p>One call rather than one per template because the manifest is a single file: saving inside a loop
     * would rewrite it once per capture and, if the last write lost, silently drop the earlier ones.
     */
    public static void applyTags(Path resourcesDir,
                                 Map<String, ? extends Collection<String>> tagsByTemplate) {
        if (tagsByTemplate.isEmpty()) return;
        TagCatalog catalog = tagCatalog(resourcesDir);
        TemplateManifest updated = manifest(resourcesDir);
        for (Map.Entry<String, ? extends Collection<String>> entry : tagsByTemplate.entrySet()) {
            updated = updated.withTags(entry.getKey(), catalog.declaredOnly(entry.getValue()));
        }
        saveManifest(resourcesDir, updated);
    }

    /**
     * Adds one declared {@code tag} to every named template and saves once — "add these to this tag", the
     * operation a tag view wants, as opposed to {@link #applyTags} which <em>replaces</em> a template's whole
     * tag set. An undeclared tag is refused rather than declared as a side effect: declaring is
     * {@link #declareTag}'s job, and a typo should not become a group.
     */
    public static void addTag(Path resourcesDir, Collection<String> baseNames, String tag) {
        if (baseNames.isEmpty() || !tagCatalog(resourcesDir).isDeclared(tag)) return;
        saveManifest(resourcesDir, manifest(resourcesDir).tagged(baseNames, tag));
    }

    /** Takes {@code tag} off every named template and saves once. The tag itself survives, empty. */
    public static void removeTag(Path resourcesDir, Collection<String> baseNames, String tag) {
        if (baseNames.isEmpty()) return;
        saveManifest(resourcesDir, manifest(resourcesDir).untagged(baseNames, tag));
    }

    /** Declares {@code tag} as a custom tag and saves; returns the catalog it now belongs to. */
    public static TagCatalog declareTag(Path resourcesDir, String tag) {
        saveManifest(resourcesDir, manifest(resourcesDir).declaring(tag));
        return tagCatalog(resourcesDir);
    }

    /**
     * Renames a template: the PNG, its resolution sidecar and its tags, in that order. Single entry point
     * because the three used to be moved by the caller and the tags weren't moved at all — the manifest is
     * keyed by base name, so a rename that skips it silently untags the template.
     */
    public static void renameTemplate(Path resourcesDir, Path file, String newBaseName) throws IOException {
        Path target = imagesDir(resourcesDir).resolve(newBaseName + ".png");
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        Path oldSidecar = sidecarFor(file);
        if (Files.exists(oldSidecar)) {
            Files.move(oldSidecar, sidecarFor(target), StandardCopyOption.REPLACE_EXISTING);
        }
        saveManifest(resourcesDir, manifest(resourcesDir).renamed(baseName(file), newBaseName));
    }

    /** Deletes a template: the PNG, its resolution sidecar and its manifest entry. */
    public static void deleteTemplate(Path resourcesDir, Path file) throws IOException {
        Files.deleteIfExists(file);
        Files.deleteIfExists(sidecarFor(file));
        saveManifest(resourcesDir, manifest(resourcesDir).without(baseName(file)));
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
     * rename and replace would have to invalidate (see the note on the manifest above).
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

    /**
     * Templates the manifest still files under a tag but whose PNG is no longer on disk — someone deleted the
     * file outside Studio. {@link #list} simply stops returning them, which is why nothing noticed: the tags
     * survive, the generated constant goes away on the next regeneration, and any block naming the template
     * fails at run time with a missing file. The resource manager asks about these when it opens.
     */
    public static List<String> missingTemplates(Path resourcesDir) {
        return manifest(resourcesDir).tagsByTemplate().keySet().stream()
                .filter(name -> !exists(resourcesDir, name))
                .sorted()
                .toList();
    }

    /**
     * The declared tag named {@code candidate}, or {@code null} — what a caller with a suggestion in hand
     * checks it against before offering it.
     *
     * <p>This is the half of Studio's {@code openActivityTag} that could travel. The other half — <em>which
     * activity is open in the editor right now</em> — is host state that nothing but the host can answer, so
     * it stayed there as {@code ImageTemplates.openActivityTag} and calls this to finish the job. The split
     * is the same one the whole move runs on: a picture folder is the plugin's, an open file is the editor's.
     *
     * <p>It <em>selects</em> a declared tag, it does not create one, so a file that is no longer a declared
     * activity offers nothing rather than conjuring a tag out of a file name.
     */
    public static String declaredTag(Path resourcesDir, String candidate) {
        TagCatalog.Tag tag = candidate == null ? null : tagCatalog(resourcesDir).find(candidate);
        return tag == null ? null : tag.name();
    }
}
