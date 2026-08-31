package com.botmaker.sdk.authoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a project's pictures come from — the targets the user has set up, which of them is the default, and
 * the size they are captured at. This is {@code capture.json}, as a value.
 *
 * <h2>Why it is here and not in the editor's own settings</h2>
 *
 * <p>It was stored twice, and nothing kept the two in step: the editor's {@code settings.json} held the list
 * a picker offered, while {@code botmaker-project.properties} held the one spec a <em>running bot</em> reads.
 * A target added in one place was invisible in the other, so the editor and the bot could disagree about
 * which window to look at — and the disagreement is silent, because both files parse.
 *
 * <p>So the list moves where the activities already are: authoring data, owned by the SDK, read and written
 * through {@link Authoring}. The same rule decides it as decides {@code activities.json} — <b>a file whose
 * contents describe the bot belongs to the bot's own SDK version</b>, not to whichever editor happens to be
 * open.
 *
 * <h2>Plain data</h2>
 *
 * <p>Nothing here validates or resolves. A spec may name a window that is not running or a monitor that is
 * not plugged in; that is an ordinary state at authoring time and the editor is where it is reported.
 *
 * @param targets      the targets in the order the user arranged them
 * @param defaultIndex which one is the default, or {@code null} when the project has not chosen — boxed
 *                     because absent and "the first one" are different answers, and an out-of-range value is
 *                     normalised away rather than trusted
 * @param reference    the size a window target is snapped to before every capture, so a project's pictures
 *                     share one resolution and nothing has to be rescaled at match time; {@code null} until
 *                     the project has one, which is an ordinary state and not an error
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptureModel(List<CaptureTargetModel> targets, Integer defaultIndex, Resolution reference) {

    /** The file this model is stored in, relative to the project's resources directory. */
    public static final String FILE_NAME = "capture.json";

    /**
     * A capture size in logical screen pixels.
     *
     * <p>It was the editor's {@code StudioProjectSettings.Resolution} until 2026-08-31 and it is here for the
     * same reason the target list is: <b>a file whose contents describe the bot belongs to the bot's own SDK
     * version.</b> A resolution stored beside the editor's window layout is a resolution the plugin that
     * captures at it cannot read, and the capture overlay is that plugin's.
     */
    public record Resolution(int width, int height) {}

    public CaptureModel {
        targets = targets == null ? List.of() : List.copyOf(targets);
        if (defaultIndex != null && (defaultIndex < 0 || defaultIndex >= targets.size())) defaultIndex = null;
        // A zero or negative size is not a smaller resolution, it is an unusable one — and it can only come
        // from a hand-edited file, so it reads as "none" rather than stopping the project from opening.
        if (reference != null && (reference.width() <= 0 || reference.height() <= 0)) reference = null;
    }

    /** No target set up — how a freshly created project reads. */
    public static CaptureModel empty() {
        return new CaptureModel(List.of(), null, null);
    }

    /** A model over {@code targets} whose default is the first of them, if there is one. */
    public static CaptureModel of(List<CaptureTargetModel> targets) {
        return new CaptureModel(targets, targets == null || targets.isEmpty() ? null : 0, null);
    }

    /** True when the project names no target at all. */
    @JsonIgnore
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    /**
     * The default target, or {@code null} when the project has none.
     *
     * <p>Total: an absent or out-of-range index has already been normalised by the constructor, so this
     * answers the first target when the project has targets but never chose between them. A project with no
     * targets answers {@code null}, which is the state an editor offers a picker for.
     */
    @JsonIgnore
    public CaptureTargetModel defaultTarget() {
        if (targets.isEmpty()) return null;
        return targets.get(defaultIndex == null ? 0 : defaultIndex);
    }

    /** The same list with {@code target} appended, made the default when the model had none. */
    public CaptureModel withTarget(CaptureTargetModel target) {
        if (target == null) return this;
        List<CaptureTargetModel> next = new ArrayList<>(targets);
        next.add(target);
        return new CaptureModel(next, defaultIndex == null ? next.size() - 1 : defaultIndex, reference);
    }

    /** The same list with a different default; an index naming nothing leaves the model alone. */
    public CaptureModel withDefaultIndex(int index) {
        if (index < 0 || index >= targets.size()) return this;
        return new CaptureModel(targets, index, reference);
    }

    /** The same targets at a different capture size; {@code null} clears it. */
    public CaptureModel withReference(Resolution resolution) {
        return new CaptureModel(targets, defaultIndex, resolution);
    }
}
