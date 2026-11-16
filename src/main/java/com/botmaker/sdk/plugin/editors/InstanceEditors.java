// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.plugin.emulator.SdkPools;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editor of the bot's {@code Instance instance()} (2026-11-15): a pill offering the capture source's own instance
 * and every instance this PC knows, the ones the SDK's pool deals from ({@link SdkPools#instances}). Several runs at
 * once are each dealt one instead of this value.
 */
public final class InstanceEditors {

    private static final String OWN = "The capture source's own";

    private InstanceEditors() {}

    public static Node instance(ValueContext ctx) {
        MenuButton pill = Pills.bare(label(ctx.value(Instance.class).orElse(null)));
        Pills.onOpen(pill, () -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(Pills.item(OWN, () -> choose(ctx, pill, Instance.named(""))));
            for (Instance each : SdkPools.instances(ctx.services())) {
                items.add(Pills.item(each.name(), () -> choose(ctx, pill, each)));
            }
            return items;
        });
        return pill;
    }

    /** The instance's name beside a declared value. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Instance.class).map(instance -> (Node) new Label(label(instance))).orElse(null);
    }

    private static void choose(ValueContext ctx, MenuButton pill, Instance picked) {
        Optional<String> refused = ctx.write(picked);
        if (refused.isPresent()) {
            if (ctx.services() != null) ctx.services().status(refused.get());
            return;
        }
        pill.setText(label(picked));
    }

    private static String label(Instance instance) {
        return instance == null || !instance.isNamed() ? OWN : instance.name();
    }
}
