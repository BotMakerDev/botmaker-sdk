package com.botmaker.sdk.api.vision;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.authoring.TemplateNames;

/**
 * This project's own pictures, by file name — {@code Images.named("ore")} is {@code images/ore.png}.
 *
 * <pre>{@code
 * if (ImageFinder.find(Images.named("ore"))) Mouse.click();
 * }</pre>
 *
 * <h2>Not the same question as a picture-valued setting</h2>
 *
 * <p>{@code Settings.load("target", ImageTemplate.class)} reads a <em>variable</em> whose value happens to be
 * a picture — the user chose which one, in the editor. This names a file directly, because the bot's author
 * already knows which picture they mean and there is nothing for the user to choose.
 *
 * <p>It is what replaces the generated {@code Templates} class: {@code Templates.ORE} was one
 * {@code public static final String} per file, regenerated on every capture, rename and delete. What is given
 * up is the same thing given up everywhere a name became text — {@code Templates.ORE} stopped compiling once
 * the file was renamed, and {@code Images.named("ore")} does not. What is bought is that a project's pictures
 * stop being a compiled artefact of the project at all, so adding one is no longer a source edit.
 *
 * <p><b>It lived on {@code com.botmaker.sdk.api.config.Wire} until 2026-09-09</b> — a class deleted outright
 * on 2026-09-11, so this is now the only place the member exists — as {@code Wire.image}, and
 * moving it is most of why that class looked like it did too much: everything else there read a
 * <em>variable</em>, and this reads a file. A picture is vision's business.
 */
@Palette(category = "vision", categoryLabel = "Vision", icon = "🖼", order = 96)
public final class Images {

    private Images() {
    }

    /**
     * The picture of that base name from this project's {@code images/} folder — no directory, no
     * {@code .png}.
     *
     * <p>Total, like everything else that reads a name: a blank or unknown name yields a template pointing at
     * a file that is not there, which fails at the first match rather than at the call. Nothing is read from
     * disk here; an {@link ImageTemplate} loads its pixels the first time a matcher asks for them.
     */
    public static ImageTemplate named(String baseName) {
        return new ImageTemplate(TemplateNames.pathFor(baseName));
    }
}
