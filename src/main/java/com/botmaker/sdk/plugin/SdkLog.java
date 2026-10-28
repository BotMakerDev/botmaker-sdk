package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.PluginLog;
import com.botmaker.plugin.api.StudioServices;

/**
 * Where the plugin half says what went wrong: the open project's {@link StudioServices#log()}, which the host
 * shows under this plugin's name, or standard error while no project is open. A bot's own output never comes here;
 * it is the run's.
 */
public final class SdkLog {

    private static volatile PluginLog log = PluginLog.STANDARD_ERROR;

    private SdkLog() {}

    /** Like {@code GameButton.bind}: the host closes one project before it opens the next. */
    static void bind(StudioServices services) {
        log = services.log();
    }

    static void unbind() {
        log = PluginLog.STANDARD_ERROR;
    }

    /** Something the user may want to know went wrong, and the feature carried on. */
    public static void warn(String message) {
        log.warn(message);
    }
}
