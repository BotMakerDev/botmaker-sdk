package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Values;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.plugin.source.CaptureValue;
import com.botmaker.shared.config.ProjectFile;
import com.botmaker.shared.config.ProjectProperties;
import com.botmaker.shared.emulator.EmulatorInstances;
import javafx.scene.Node;
import javafx.scene.control.Button;

import java.nio.file.Path;

/**
 * The editor for the instance name of {@code Emulators.use("…")} and {@code Emulators.named("…")} — a pill
 * that opens {@link EmulatorPicker}, so the user chooses a BlueStacks / LDPlayer / MEmu / MuMu / Gameloop
 * instance (or a paired phone) from a list with its brand, a running dot and, for a running instance, its
 * installed apps.
 *
 * <p>The emulator probe, the app cache and the phone-pairing dialog behind it are this plugin's too:
 * {@code botmaker-shared} is published, so scanning for emulator instances is not a host privilege.
 *
 * <p>Matched by the call rather than by the type, like the launch editors, because nothing about
 * {@code String} says it holds an emulator instance name. So it is absent from the Parameters window by
 * construction — a row there has no call behind it.
 */
public final class EmulatorEditors {

    private EmulatorEditors() {}

    /**
     * The pill. Its caption is {@link EmulatorInstances#captionFor}, which is all that can honestly be said
     * from a name alone: the name comes out of a string literal in the user's own source, so the product
     * behind it is unknown, and a paired phone reached this editor exactly the way an emulator did.
     */
    public static Node instanceName(ValueContext ctx) {
        Button pill = Pills.button(label(Values.text(ctx, "")), null);
        pill.setOnAction(e ->
                EmulatorPicker.show(ctx.services(), ctx.services().dialogs().ownerWindow().orElse(null)).ifPresent(chosen -> {
                    String name = chosen.instance().name();
                    if (name == null || name.isBlank()) return;
                    ctx.set(name);
                    pill.setText(label(name));
                    if (chosen.hasApp()) pointProjectAtApp(ctx, name, chosen.appPackage());
                }));
        return pill;
    }

    /**
     * Drilling into a specific app inside an emulator also points the whole project at it: the launch target
     * becomes {@code emu-app:<package>@<instance>} and the default capture target that instance, so
     * {@code Bot.start} brings the app up and a vision call with no source of its own looks at the right
     * screen.
     *
     * <p>Best effort, and silent on failure — the inline {@code Emulators.use(name)} call the user just wrote
     * stands either way, and an editor that threw here would lose the edit as well as the wiring.
     *
     * <p><b>The two halves are written to two different places, and that is the split, not an
     * inconsistency.</b> What the bot <em>launches</em> is a fact about running this bot on this machine, so
     * it stays a {@code botmaker-project.properties} key. Where the bot <em>looks</em> is a fact about the
     * bot, so it is the expression {@code Sdk.captureSource()} returns — Java the user can read, and the
     * only copy of that answer.
     */
    private static void pointProjectAtApp(ValueContext ctx, String instanceName, String appPackage) {
        Path resources = ctx.services().resourcesDir();
        if (resources == null) return;
        ProjectFile.set(resources, ProjectProperties.KEY_LAUNCH_TARGET,
                "emu-app:" + appPackage + "@" + instanceName);
        CaptureValue.point(ctx.services(), new EmulatorSource(instanceName));
    }

    private static String label(String instanceName) {
        return (instanceName == null || instanceName.isBlank())
                ? "Choose a device…" : EmulatorInstances.captionFor(instanceName);
    }
}
