package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.Runs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.TraceLine;
import com.botmaker.sdk.internal.bot.SetupOnce;
import javafx.application.Platform;

/**
 * Records on this computer that the bot's setup is done, so the next run skips it.
 *
 * <p>The run reports it on its trace ({@link SetupOnce}); this sets the {@code botmaker.setup} run property
 * {@code off} and keeps the hash of the Setup parameters the setup ran with beside it. The run property is the
 * host's, so the plugin that reads the trace in the host is the one that writes it.
 */
public final class SetupRecorder {

    private static volatile AutoCloseable listening;

    private SetupRecorder() {}

    /** Listens to {@code services}' runs from now on, until {@link #unbind}. */
    public static void bind(StudioServices services) {
        unbind();
        if (services == null) return;
        Runs runs = services.runs();
        // On the FX thread, where every other writer of run properties is: two writers would each rebuild the
        // settings from what they last saw, and one would lose the other's change.
        listening = runs.onTrace(line -> {
            if (SetupOnce.reported(line.writerClass(), line.text()).isPresent()) {
                Platform.runLater(() -> record(runs, line));
            }
        });
    }

    /** Stops listening, when the project closes. */
    public static void unbind() {
        AutoCloseable was = listening;
        listening = null;
        if (was == null) return;
        try {
            was.close();
        } catch (Exception ignored) {
            // Already gone with the project.
        }
    }

    /** Sets the setup off on this computer when {@code line} reports it done. */
    static void record(Runs runs, TraceLine line) {
        if (line == null) return;
        SetupOnce.reported(line.writerClass(), line.text()).ifPresent(hash -> {
            runs.setProperty(SetupOnce.PARAMS_PROPERTY, hash);
            runs.setProperty(SetupOnce.PROPERTY, "off");
        });
    }
}
