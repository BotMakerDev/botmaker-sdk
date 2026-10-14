package com.botmaker.sdk.plugin.pictures;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Import/export of image templates on their own — a {@code .bmtemplates} zip — so a set of templates can move
 * between projects without carrying a whole bot with it.
 *
 * <p>Smaller than a published project: the PNGs alone. Where a picture was cut and the tags it is filed under are
 * its constant's ({@code .at}, {@code .in}, {@code .tags}), which stays in the bot it came from: an imported
 * picture is declared plain, untagged, and is matched at the bot's screen size. An archive written before
 * 2026-10-14 also holds a {@code templates.json}, which an import skips.
 *
 * <p>Layout inside the zip mirrors the images root, one level down, so the file is legible in any zip tool:
 * <pre>
 *   images/accept.png
 * </pre>
 *
 * <p>Import never overwrites: a name already taken in the destination is imported under a suffixed name and
 * reported. A bot names a template through its {@code Pictures} constant, so silently replacing one would
 * change what an existing bot matches against — the safe failure is a duplicate the user can delete, not a
 * substitution they never see.
 *
 * <p><b>Unless the two are the same picture</b>, in which case the import is skipped entirely. Renaming
 * around a name collision is the right answer when the pixels differ and wrong when they don't: exporting a
 * library and importing it back into the same project is a normal thing to do, and without this every
 * round-trip left a full second copy of every template behind.
 */
public final class TemplateArchive {

    private TemplateArchive() {}

    /** The extension of a template-only archive, used by both file choosers. */
    public static final String EXTENSION = ".bmtemplates";

    private static final String IMAGES_PREFIX = "images/";

    /**
     * What an import did.
     *
     * <p>Four outcomes, because they need four different reactions from the user: {@code imported} arrived
     * under its own name, {@code renamed} arrived beside a different template of the same name (and is the
     * one thing here worth looking at afterwards), {@code unchanged} was already in the project pixel for
     * pixel, and {@code skipped} could not be given a usable name at all.
     */
    public record ImportResult(List<String> imported, Map<String, String> renamed,
                               List<String> unchanged, List<String> skipped) {

        public int count() {
            return imported.size();
        }

        /** The one-line version, for the status bar. */
        public String summary() {
            StringBuilder sb = new StringBuilder("Imported " + imported.size() + " template(s)");
            if (!renamed.isEmpty()) sb.append("; ").append(renamed.size()).append(" renamed");
            if (!unchanged.isEmpty()) sb.append("; ").append(unchanged.size()).append(" already here");
            if (!skipped.isEmpty()) sb.append("; skipped ").append(skipped.size()).append(" unreadable entry(ies)");
            return sb.toString();
        }

        /** The paragraph version, for the dialog shown once the import is done. Empty when nothing happened. */
        public String details() {
            StringBuilder sb = new StringBuilder();
            if (!renamed.isEmpty()) {
                sb.append("A template of the same name was already here, holding a different picture, so these "
                        + "came in beside it:\n");
                renamed.forEach((from, to) -> sb.append("    ").append(from).append("  →  ").append(to).append('\n'));
            }
            if (!unchanged.isEmpty()) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append("Already in this project, pixel for pixel — nothing to add:\n    ")
                        .append(String.join(", ", unchanged)).append('\n');
            }
            if (!skipped.isEmpty()) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append("Could not be named, and were left out:\n    ")
                        .append(String.join(", ", skipped)).append('\n');
            }
            return sb.toString().stripTrailing();
        }
    }

    /** Writes {@code templates} (PNG paths from the project's images root) to {@code destination}. */
    public static void export(Collection<Path> templates, Path destination) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) {
            for (Path png : templates) {
                writeEntry(zip, IMAGES_PREFIX + png.getFileName(), Files.readAllBytes(png));
            }
        }
    }

    /** Unpacks {@code archive}'s pictures into the project's images root, renaming around existing names. */
    public static ImportResult importInto(Path resourcesDir, Path archive) throws IOException {
        Map<String, byte[]> entries = readEntries(archive);

        Path imagesRoot = resourcesDir.resolve("images");
        Files.createDirectories(imagesRoot);
        List<String> imported = new ArrayList<>();
        Map<String, String> renamed = new LinkedHashMap<>();
        List<String> unchanged = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String fileName = entry.getKey();
            if (!fileName.toLowerCase().endsWith(".png")) continue;
            String source = fileName.substring(0, fileName.length() - ".png".length());
            String sanitized = TemplateLibrary.sanitizeName(source);
            if (sanitized.isBlank()) {
                skipped.add(source);
                continue;
            }
            // Same name, same picture: the project already has this template.
            if (TemplateLibrary.exists(resourcesDir, sanitized)
                    && TemplateLibrary.sameContent(imagesRoot.resolve(sanitized + ".png"), entry.getValue())) {
                unchanged.add(sanitized);
                continue;
            }
            String target = freeName(resourcesDir, sanitized);
            if (target == null) {
                skipped.add(source);
                continue;
            }
            Files.write(imagesRoot.resolve(target + ".png"), entry.getValue());
            imported.add(target);
            if (!target.equals(sanitized)) renamed.put(sanitized, target);
        }
        return new ImportResult(imported, renamed, unchanged, skipped);
    }

    /**
     * {@code sanitized} if free, else {@code sanitized_2}, {@code sanitized_3}… The bound exists because the
     * alternative to giving up is an unbounded loop on a pathological project; 99 collisions on one name is a
     * user problem, not a case to keep spinning on.
     */
    private static String freeName(Path resourcesDir, String sanitized) {
        if (!TemplateLibrary.exists(resourcesDir, sanitized)) return sanitized;
        for (int i = 2; i <= 99; i++) {
            String candidate = sanitized + "_" + i;
            if (!TemplateLibrary.exists(resourcesDir, candidate)) return candidate;
        }
        return null;
    }

    /** Every entry of {@code archive}, keyed by its file name with the {@code images/} prefix stripped. */
    private static Map<String, byte[]> readEntries(Path archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(archive)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith(IMAGES_PREFIX)) name = name.substring(IMAGES_PREFIX.length());
                // Only a bare file name is ever honoured — an entry naming a directory (or "..") is how a
                // zip writes outside the root it is unpacked into, and nothing this archive contains needs one.
                if (name.isEmpty() || name.contains("/")) continue;
                entries.put(name, zis.readAllBytes());
            }
        }
        return entries;
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
