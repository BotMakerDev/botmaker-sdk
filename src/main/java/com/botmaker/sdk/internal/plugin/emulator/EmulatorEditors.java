package com.botmaker.sdk.internal.plugin.emulator;

import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.shared.emulator.EmulatorInstances;
import javafx.scene.Node;
import javafx.scene.control.Button;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The editor for the instance name of {@code Emulators.use("…")} and {@code Emulators.named("…")} — a pill
 * that opens {@link EmulatorPicker}, so the user chooses a BlueStacks / LDPlayer / MEmu / MuMu / Gameloop
 * instance (or a paired phone) from a list with its brand, a running dot and, for a running instance, its
 * installed apps.
 *
 * <p><b>This was the last call-site-matched editor the host still owned.</b> Every other one moved on
 * 2026-08-28; this one stayed because the dialog behind it reached Studio's own emulator probe, app cache and
 * phone-pairing dialog. All three came here with it — and the reason they could is the one this whole
 * direction rests on: {@code botmaker-shared} is published, so scanning for emulator instances was never a
 * host privilege, only something the host happened to be written to do first.
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
        Button pill = Pills.button(label(Slots.stringLiteral(Slots.raw(ctx))), null);
        pill.setOnAction(e ->
                EmulatorPicker.show(ctx.services(), ctx.services().dialogs().owner()).ifPresent(chosen -> {
                    String name = chosen.instance().name();
                    if (name == null || name.isBlank()) return;
                    Slots.writeText(ctx, name);
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
     * <p>It writes through {@link Authoring} rather than through the host, which is the difference this move
     * made: {@code botmaker-project.properties} and {@code capture.json} are this module's files, and the
     * editor that used to do this reached Studio's {@code ProjectCreator} for the first and had no way at all
     * to say the second.
     */
    private static void pointProjectAtApp(ValueContext ctx, String instanceName, String appPackage) {
        Path resources = ctx.services().resourcesDir();
        if (resources == null) return;
        try {
            Authoring.writeLaunchTarget(SdkVersion.latest(), resources,
                    "emu-app:" + appPackage + "@" + instanceName);
            Authoring.writeCaptureSource(SdkVersion.latest(), resources,
                    CaptureTargetModel.emulator(instanceName).spec());
        } catch (IOException ignored) {
            // Best-effort project wiring; the call the user just edited is written either way.
        }
    }

    private static String label(String instanceName) {
        return (instanceName == null || instanceName.isBlank())
                ? "Choose a device…" : EmulatorInstances.captionFor(instanceName);
    }
}
