package com.botmaker.sdk.authoring;

import com.botmaker.shared.config.CaptureSourceKind;
import com.botmaker.shared.emulator.EmulatorInstances;
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
 *
 * <h2>The derived accessors, and why they are here</h2>
 *
 * <p>Everything below the constructor is derived from the spec, and every one of them existed twice before
 * 2026-08-30: as four sealed record shapes in {@code botmaker-studio}'s own {@code project.capture} package,
 * and again as private helpers in the pilot's target resolver. The two spellings had already drifted — a
 * monitor index that is not a number read as monitor 0 on one side and refused the whole target on the other.
 * A target's identity is its spec, so the questions asked of a spec belong beside it.
 *
 * <p><b>A {@code null} target means the whole desktop</b>, which is what every caller has always meant by an
 * unset default, and the static label helpers say so rather than making each of them repeat the check.
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

    /** The whole virtual desktop, every monitor combined. */
    public static CaptureTargetModel desktop() {
        return of(CaptureSourceKind.DESKTOP.spec(null));
    }

    /** One monitor, by its index into {@code javafx.stage.Screen.getScreens()}. */
    public static CaptureTargetModel monitor(int index) {
        return of(CaptureSourceKind.MONITOR.spec(String.valueOf(Math.max(0, index))));
    }

    /** An application window, matched at capture time by a case-insensitive title substring. */
    public static CaptureTargetModel window(String titleSubstring) {
        return of(CaptureSourceKind.WINDOW.spec(titleSubstring == null ? "" : titleSubstring));
    }

    /** An Android surface captured over ADB, by its instance name. */
    public static CaptureTargetModel emulator(String instanceName) {
        return of(CaptureSourceKind.EMULATOR.spec(instanceName == null ? "" : instanceName));
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

    /**
     * Whether this target names {@code wanted}.
     *
     * <p>The one place a caller asks which of the four forms it is holding. It answers false for an
     * unreadable spec rather than throwing, which is the same tolerance {@link #kind()} already has: a
     * hand-edited project file must open, and a target nothing recognises is offered as unusable rather than
     * refusing the project it sits in.
     */
    @JsonIgnore
    public boolean is(CaptureSourceKind wanted) {
        return kind() == wanted;
    }

    /** True for the whole virtual desktop — including an unreadable spec, which every reader treats as it. */
    @JsonIgnore
    public boolean isDesktop() {
        return kind() == null || kind() == CaptureSourceKind.DESKTOP;
    }

    /**
     * The monitor index this target names, or {@code 0}.
     *
     * <p>Zero for a target that is not a monitor at all and for one whose index is not a number, because both
     * end in the same place: something has to be captured, and the primary screen is the answer that always
     * exists. Callers that need to tell the two apart ask {@link #is} first.
     */
    @JsonIgnore
    public int monitorIndex() {
        if (!is(CaptureSourceKind.MONITOR)) return 0;
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(argument()).trim()));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** The window title substring this target matches on, or {@code null} when it names no window. */
    @JsonIgnore
    public String windowTitle() {
        return is(CaptureSourceKind.WINDOW) ? argument() : null;
    }

    /** The emulator instance this target names, or {@code null} when it names no emulator. */
    @JsonIgnore
    public String emulatorName() {
        return is(CaptureSourceKind.EMULATOR) ? argument() : null;
    }

    /**
     * The long human label — a settings row, a menu entry, the line under a thumbnail.
     *
     * <p>The user's own {@code label} wins when they gave one; otherwise the form says what it is
     * (<em>Screen 2</em>, <em>Window: Diablo IV</em>, <em>Whole desktop (all monitors)</em>). An emulator is
     * captioned by {@link EmulatorInstances#captionFor}, because the same instance name may be a phone or an
     * emulator and only a live scan can say which.
     */
    @JsonIgnore
    public String longLabel() {
        if (!label.isEmpty()) return label;
        CaptureSourceKind kind = kind();
        if (kind == null) return "Whole desktop (all monitors)";
        return switch (kind) {
            case DESKTOP -> "Whole desktop (all monitors)";
            case MONITOR -> "Screen " + (monitorIndex() + 1);
            case WINDOW -> "Window: " + (blank(argument()) ? "(any)" : argument());
            case EMULATOR -> EmulatorInstances.captionFor(argument());
        };
    }

    /**
     * The short label — a toolbar button, the in-block capture-source button, the pilot's header.
     *
     * <p>Same answer as {@link #longLabel()} with the qualifiers dropped: a window is its title alone, and
     * the desktop does not spell out that it means every monitor. One method rather than each surface
     * trimming the long one, so the three of them cannot disagree about how a target is named.
     */
    @JsonIgnore
    public String shortLabel() {
        if (!label.isEmpty()) return label;
        CaptureSourceKind kind = kind();
        if (kind == null) return "Whole desktop";
        return switch (kind) {
            case DESKTOP -> "Whole desktop";
            case MONITOR -> "Screen " + (monitorIndex() + 1);
            case WINDOW -> blank(argument()) ? "Window" : argument();
            case EMULATOR -> blank(argument()) ? "Emulator" : argument();
        };
    }

    /** {@link #longLabel()} for a target that may be absent — an unset default is the whole desktop. */
    public static String longLabelOf(CaptureTargetModel target) {
        return target == null ? desktop().longLabel() : target.longLabel();
    }

    /** {@link #shortLabel()} for a target that may be absent — an unset default is the whole desktop. */
    public static String shortLabelOf(CaptureTargetModel target) {
        return target == null ? desktop().shortLabel() : target.shortLabel();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
