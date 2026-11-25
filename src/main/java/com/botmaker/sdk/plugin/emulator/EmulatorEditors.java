// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.Platforms;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor for an {@code @EmulatorName} argument — {@code Emulators.use("…")}, {@code named}, {@code launch}
 * and {@code stop} — a pill whose menu lists every emulator instance and paired phone on this computer, by its
 * brand and name, read off the JavaFX thread each time it opens, then Emulators…, where one is made or paired.
 * Which app a device starts is the Game window's (its Android apps tab), not this slot's.
 *
 * <p>The emulator discovery behind it is this plugin's too: {@code botmaker-shared} is published, so scanning for
 * emulator instances is not a host privilege.
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
        MenuButton pill = Pills.bare(label(ctx));
        MenuItem emulators = Pills.item("Emulators…", () -> EmulatorsWindow.open(ctx.services()));
        pill.setOnShowing(e -> {
            MenuItem looking = new MenuItem("Looking for devices…");
            looking.setDisable(true);
            pill.getItems().setAll(looking, Pills.separator(), emulators);
            Async.load("emulator-name-devices", Platforms::discoverAll, found -> {
                if (!pill.isShowing()) return;
                List<MenuItem> items = new ArrayList<>();
                for (EmulatorInstance instance : found) {
                    // A phone by its serial, which stays when its model name appears: EmulatorInstance.reference.
                    String name = instance.reference();
                    if (name == null || name.isBlank()) continue;
                    items.add(Pills.item(instance.caption(), () -> {
                        ctx.set(name);
                        pill.setText(instance.caption());
                    }));
                }
                if (items.isEmpty()) {
                    MenuItem none = new MenuItem("No emulator or phone was found");
                    none.setDisable(true);
                    items.add(none);
                }
                items.add(Pills.separator());
                items.add(emulators);
                pill.getItems().setAll(items);
            }, why -> {
                if (pill.isShowing()) looking.setText("Couldn't look for devices: " + why);
            });
        });
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
}
