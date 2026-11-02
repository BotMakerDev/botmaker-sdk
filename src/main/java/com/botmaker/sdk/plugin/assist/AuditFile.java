package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.assist.MapAudit.Audit;
import com.botmaker.sdk.plugin.assist.MapAudit.Fit;
import com.botmaker.sdk.plugin.assist.MapAudit.Spot;
import com.botmaker.sdk.plugin.assist.MapAudit.Where;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link MapAudit}'s matches on disk, so a Studio started again does not match the map's every picture over again:
 * Sword x Staff's first read_map took 24 s after each start (2026-11-02). Each key holds the file times it was made of,
 * so an entry for a shot or picture changed since is never asked for again.
 */
final class AuditFile {

    /** What the file starts with: another file, or one written by another layout, is not read. */
    private static final String MAGIC = "botmaker map audit";
    private static final int VERSION = 1;
    /** The longest string read back: a longer one is a file broken mid-write. */
    private static final int LONGEST = 1 << 20;

    private AuditFile() {}

    /** The audits and fits a file held. */
    record Contents(Map<String, Audit> made, Map<String, Fit> fitted) {
    }

    /** Writes {@code made} and {@code fitted}, matched by {@code build}, to {@code file}, whole or not at all. */
    static void write(Path file, String build, Map<String, Audit> made, Map<String, Fit> fitted) throws IOException {
        Files.createDirectories(file.getParent());
        Path part = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(part)))) {
                string(out, MAGIC);
                out.writeInt(VERSION);
                string(out, build);
                out.writeInt(made.size());
                for (Map.Entry<String, Audit> each : made.entrySet()) {
                    string(out, each.getKey());
                    List<Spot> spots = each.getValue().spots();
                    out.writeInt(spots.size());
                    for (Spot spot : spots) {
                        string(out, spot.picture());
                        out.writeBoolean(spot.marker());
                        out.writeBoolean(spot.absent());
                        out.writeBoolean(spot.sure());
                        out.writeByte(spot.where().ordinal());
                        rect(out, spot.at());
                        rect(out, spot.place());
                        rect(out, spot.area());
                    }
                }
                out.writeInt(fitted.size());
                for (Map.Entry<String, Fit> each : fitted.entrySet()) {
                    string(out, each.getKey());
                    string(out, each.getValue().picture());
                    out.writeByte(each.getValue().where().ordinal());
                    rect(out, each.getValue().at());
                }
            }
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** What {@code file} holds; empty when it is absent, broken, of another layout or matched by another build. */
    static Optional<Contents> read(Path file, String build) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (!MAGIC.equals(string(in)) || in.readInt() != VERSION || !build.equals(string(in))) {
                return Optional.empty();
            }
            Map<String, Audit> made = new LinkedHashMap<>();
            for (int n = count(in); n > 0; n--) {
                String key = string(in);
                List<Spot> spots = new ArrayList<>();
                for (int s = count(in); s > 0; s--) {
                    spots.add(new Spot(string(in), in.readBoolean(), in.readBoolean(), in.readBoolean(), where(in),
                            rect(in), rect(in), rect(in)));
                }
                made.put(key, new Audit(List.copyOf(spots)));
            }
            Map<String, Fit> fitted = new LinkedHashMap<>();
            for (int n = count(in); n > 0; n--) {
                String key = string(in);
                fitted.put(key, new Fit(string(in), where(in), rect(in)));
            }
            return Optional.of(new Contents(made, fitted));
        } catch (IOException | RuntimeException broken) {
            return Optional.empty();
        }
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String string(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > LONGEST) throw new IOException("a string of " + length + " bytes");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static int count(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0) throw new IOException("a count of " + n);
        return n;
    }

    private static Where where(DataInputStream in) throws IOException {
        int ordinal = in.readByte();
        Where[] all = Where.values();
        if (ordinal < 0 || ordinal >= all.length) throw new IOException("no place " + ordinal);
        return all[ordinal];
    }

    private static void rect(DataOutputStream out, Rect rect) throws IOException {
        out.writeBoolean(rect != null);
        if (rect == null) return;
        out.writeInt(rect.x());
        out.writeInt(rect.y());
        out.writeInt(rect.width());
        out.writeInt(rect.height());
    }

    private static Rect rect(DataInputStream in) throws IOException {
        return in.readBoolean() ? new Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt()) : null;
    }
}
