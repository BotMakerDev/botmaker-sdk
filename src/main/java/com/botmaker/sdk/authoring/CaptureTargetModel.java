package com.botmaker.sdk.authoring;

import com.botmaker.shared.config.CaptureSourceKind;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One place a project's pictures come from — a monitor, a window, the whole desktop, an emulator instance.
 *
 * <p><b>The identity is the spec, and the spec's grammar is shared's</b>
 * ({@link CaptureSourceKind}: {@code desktop}, {@code monitor:<index>}, {@code window:<title>},
 * {@code emulator:<instance>}). It is stored as the text rather than as four record shapes for the reason
 * the whole file exists: the same four forms are already what a running bot reads out of
 * {@code botmaker-project.properties}, so a second spelling of them here would be a second grammar to keep
 * in step — and the editor and the bot disagreeing about which window to look at is exactly the class of bug
 * this model is being introduced to end.
 *
 * <p>{@code label} is the user's own name for it, or blank. Purely for a menu row: it is never matched on,
 * and a target whose label is empty is described by its spec.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptureTargetModel(String spec, String label) {

    public CaptureTargetModel {
        spec = spec == null ? "" : spec.trim();
        label = label == null ? "" : label.trim();
    }

    /** A target named only by its spec. */
    public static CaptureTargetModel of(String spec) {
        return new CaptureTargetModel(spec, "");
    }

    /** The kind this spec names, or {@code null} when it names none — an unreadable target, not an error. */
    @JsonIgnore
    public CaptureSourceKind kind() {
        return spec.isEmpty() ? null : CaptureSourceKind.of(spec);
    }

    /** What follows the kind — a monitor index, a window title, an emulator instance — or {@code null}. */
    @JsonIgnore
    public String argument() {
        CaptureSourceKind kind = kind();
        return kind == null ? null : kind.argumentOf(spec);
    }

    /**
     * What to show in a menu: the user's label when they gave one, else the spec itself.
     *
     * <p>The spec rather than a prettied rendering of it, deliberately: {@code window:Diablo IV} says which
     * window <em>and</em> that it is matched by title, and a reader who has to act on a target that is not
     * resolving needs the second half as much as the first.
     */
    @JsonIgnore
    public String describe() {
        return label.isEmpty() ? spec : label;
    }
}
