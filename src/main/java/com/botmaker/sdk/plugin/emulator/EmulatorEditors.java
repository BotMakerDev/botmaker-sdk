package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.api.emulator.EmulatorSource;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.shared.emulator.EmulatorInstances;
import javafx.scene.Node;
import javafx.scene.control.Button;

/**
 * The editor for an {@code @EmulatorName} argument — {@code Emulators.use("…")}, {@code named}, {@code launch}
 * and {@code stop} — a pill that opens {@link EmulatorPicker}, so the user chooses a BlueStacks / LDPlayer /
 * MEmu / MuMu / Gameloop instance (or a paired phone) from a list with its brand, a running dot and, for a
 * running instance, its installed apps.
 *
 * <p>The emulator probe, the app cache and the phone-pairing dialog behind it are this plugin's too:
 * {@code botmaker-shared} is published, so scanning for emulator instances is not a host privilege.
 *
 * <p>Claimed by the parameter's annotation rather than by the type, like the launch editors, because nothing
 * about {@code String} says it holds an emulator instance name. So it is absent from the Parameters window by
 * construction — a row there has no parameter behind it.
 */
public final class EmulatorEditors {

    /** What an empty slot's pill says. */
    static final String CHOOSE = "Choose a device…";

    private EmulatorEditors() {}

    /**
     * The pill. Its caption is {@link EmulatorInstances#captionFor}, which is all that can honestly be said
     * from a name alone: the name comes out of a string literal in the user's own source, so the product
     * behind it is unknown, and a paired phone reached this editor exactly the way an emulator did.
     */
    public static Node instanceName(ValueContext ctx) {
        Button pill = Pills.button(label(ctx), null);
        pill.setOnAction(e ->
                EmulatorPicker.show(ctx.services(), Modals.owner(ctx.services())).ifPresent(chosen -> {
                    String name = chosen.instance().name();
                    if (name == null || name.isBlank()) return;
                    ctx.set(name);
                    pill.setText(EmulatorInstances.captionFor(name));
                    if (chosen.hasApp()) pointProjectAtApp(ctx, name, chosen.appPackage());
                }));
        return pill;
    }

    /**
     * The pill's text: the caption of the name the slot holds, or — when the argument is something the host
     * could not read as a name, such as a constant or a variable — that source as written, so a slot that is
     * filled never reads as empty.
     */
    static String label(ValueContext ctx) {
        return ctx.value(String.class)
                .filter(name -> !name.isBlank())
                .map(EmulatorInstances::captionFor)
                .orElseGet(() -> ctx.value(String.class).isPresent() ? CHOOSE : Slots.sourceOr(ctx, CHOOSE));
    }

    /**
     * Drilling into a specific app inside an emulator also points the whole project at it: the launch target
     * becomes {@code emu-app:<package>@<instance>} and the default capture target that instance, so
     * {@code Bot.run} brings the app up and a vision call with no source of its own looks at the right screen.
     * The status line says so, since both change outside the block the user clicked.
     *
     * <p>Best effort — the inline call the user just wrote stands either way, and an editor that threw here
     * would lose the edit as well as the wiring.
     *
     * <p><b>The two halves are written to two different places, and that is the split, not an
     * inconsistency.</b> What the bot <em>launches</em> is a fact about running this bot on this machine, so
     * it is this machine's run property ({@link LaunchTargetValue}). Where the bot <em>looks</em> is a fact about the
     * bot, so it is the expression {@code Sdk.captureSource()} returns — Java the user can read, and the
     * only copy of that answer.
     */
    private static void pointProjectAtApp(ValueContext ctx, String instanceName, String appPackage) {
        LaunchTargetValue.set(ctx.services(), "emu-app:" + appPackage + "@" + instanceName);
        CaptureValue.point(ctx.services(), new EmulatorSource(instanceName));
        ctx.services().status("This computer now launches " + appPackage + " on " + instanceName
                + ", and the bot captures from " + instanceName + ".");
    }
}
