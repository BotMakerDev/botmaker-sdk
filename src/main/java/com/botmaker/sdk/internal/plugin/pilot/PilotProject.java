package com.botmaker.sdk.internal.plugin.pilot;

import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.CaptureModel;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.shared.config.ProjectFile;

import java.awt.Dimension;
import java.nio.file.Path;

/**
 * What the pilot needs to know about the project it is serving, read from that project's own files.
 *
 * <p>This is the seam the move out of the editor turned on. The pilot used to hold a
 * {@code ProjectSettingsService} — the editor's live settings object — and ask it for the default capture
 * target and the reference resolution. A plugin has neither that class nor any way to be handed one, and the
 * contract deliberately does not grow a service for it: <b>the host is only the only possible source of
 * which project is open</b>, and the answers themselves are in files a plugin can read.
 *
 * <p>So the host supplies a directory and this reads: {@code capture.json} through {@link Authoring}, exactly
 * as the editor now writes it, and the capture resolution out of {@code botmaker-project.properties}. Both
 * are read on demand rather than cached — the user changes a target in another window while the pilot is
 * streaming, and a cache is how the pilot ends up pointing at the previous one.
 *
 * <p>Every answer is best-effort, because the pilot is a live stream: a project mid-save, a hand-edited file
 * or a directory that has gone away all yield "nothing configured", which every caller already handles.
 */
public final class PilotProject {

    private final Path resourcesDir;

    public PilotProject(Path resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    /** The project's resources directory, or {@code null} when the pilot is serving nothing. */
    public Path resourcesDir() {
        return resourcesDir;
    }

    /** The project's default capture target, or {@code null} when it names none. */
    public CaptureTargetModel defaultTarget() {
        return capture().defaultTarget();
    }

    /**
     * The size a nested display is created at — the project's capture resolution, or {@code null} when it has
     * never set one and the session's own default should stand.
     */
    public Dimension referenceSize() {
        return resourcesDir == null ? null : ProjectFile.captureSize(resourcesDir);
    }

    /** The {@code capture.source} spec the running bot resolves, or {@code null}. */
    public String captureSource() {
        return resourcesDir == null ? null : ProjectFile.captureSource(resourcesDir);
    }

    private CaptureModel capture() {
        if (resourcesDir == null) return CaptureModel.empty();
        try {
            return Authoring.readCapture(SdkVersion.latest(), resourcesDir);
        } catch (Exception unreadable) {
            return CaptureModel.empty();
        }
    }
}
