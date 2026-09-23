package com.botmaker.sdk.plugin.flow;

import javafx.scene.Scene;

/**
 * The flow editor's own stylesheet, and the one line that puts it on a scene.
 *
 * <p>The {@code .flow-*} rules were in the host's {@code blocks.css} until 2026-09-11, which is where they
 * had to be while the canvas was Studio's. They are this plugin's now, for the same reason the canvas is: a
 * host stylesheet naming one plugin's concepts is a look a second plugin could never have.
 *
 * <p>What it does <em>not</em> carry is a palette. Every colour in it resolves a {@code -bm-*} token the
 * host's theme publishes on the scene root, so the canvas follows the editor into a dark theme without
 * knowing what the themes are — and a host that renames a token is a host this plugin fails to match rather
 * than one it fights. The stylesheet is added <b>after</b> the host's, so its own {@code .root} block can
 * derive the half-dozen flow-specific tokens from the general ones.
 */
final class FlowStyles {

    private static final String SHEET = FlowStyles.class.getResource("flow.css").toExternalForm();

    private FlowStyles() {}

    /** Adds the flow stylesheet to {@code scene}, after whatever the host already put there. */
    static Scene apply(Scene scene) {
        if (scene != null && !scene.getStylesheets().contains(SHEET)) scene.getStylesheets().add(SHEET);
        return scene;
    }
}
