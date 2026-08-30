package com.botmaker.sdk.internal.plugin.pilot;

import java.util.prefs.Preferences;

/**
 * The two things the pilot remembers between runs: its pairing token and the port it last bound.
 *
 * <p>Both are per <em>user</em> and not per project — a phone paired once should stay paired, and a stable
 * port is what makes the tailnet-direct URL survive a restart. They lived in the editor's own preferences
 * file while the pilot was the editor's feature; they are the plugin's now, because a plugin's state is the
 * plugin's to keep and the host has no reason to know a pilot token exists.
 *
 * <p>{@link Preferences} rather than a file of our own: this is exactly what it is for, it is per user and
 * per platform without any path logic, and a plugin writing a dotfile beside the editor's would be inventing
 * one. Every failure is swallowed — a locked or unavailable backing store means the pilot re-mints a token
 * and takes an ephemeral port, which is a worse experience and not a broken one.
 */
final class PilotPreferences {

    private static final String NODE = "com/botmaker/sdk/pilot";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_PORT = "port";

    private PilotPreferences() {
    }

    /** The persisted pairing token, or {@code null} when there is none. */
    static String token() {
        String token = read().get(KEY_TOKEN, null);
        return token == null || token.isBlank() ? null : token;
    }

    /** Persists a pairing token; {@code null} revokes the stored one. */
    static void token(String token) {
        Preferences prefs = read();
        try {
            if (token == null || token.isBlank()) prefs.remove(KEY_TOKEN);
            else prefs.put(KEY_TOKEN, token);
            prefs.flush();
        } catch (Exception ignored) {
            // An unwritable store costs a re-pair, never a failed start.
        }
    }

    /** The last bound port, or {@code 0} — which asks the OS for an ephemeral one. */
    static int port() {
        return read().getInt(KEY_PORT, 0);
    }

    /** Persists the port actually bound, so the next start asks for the same one. */
    static void port(int port) {
        Preferences prefs = read();
        try {
            prefs.putInt(KEY_PORT, port);
            prefs.flush();
        } catch (Exception ignored) {
            // As above: the next start simply takes another ephemeral port.
        }
    }

    private static Preferences read() {
        return Preferences.userRoot().node(NODE);
    }
}
