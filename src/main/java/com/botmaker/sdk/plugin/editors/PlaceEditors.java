// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.plugin.emulator.SdkPools;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editor of the bot's {@code Place place()} (2026-11-26, was the instance pill of 2026-11-15): a pill offering the
 * capture source's own instance and every place the SDK's pool deals from ({@link SdkPools#places}). Several runs at
 * once are each dealt one instead of this value.
 */
public final class PlaceEditors {

    /** {@code Place.android("")}, no place picked: the capture source's instance, or where {@code RunIn.where} says. */
    private static final String OWN = "Where it plays alone";

    private PlaceEditors() {}

    public static Node place(ValueContext ctx) {
        MenuButton pill = Pills.bare(label(ctx.value(Place.class).orElse(null)));
        Pills.onOpen(pill, () -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(Pills.item(OWN, () -> choose(ctx, pill, Place.android(""))));
            for (Place each : SdkPools.places(ctx.services())) {
                items.add(Pills.item(label(each), () -> choose(ctx, pill, each)));
            }
            return items;
        });
        return pill;
    }

    /** The place beside a declared value, as the pill says it. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Place.class).map(place -> (Node) new Label(label(place))).orElse(null);
    }

    /** A leftover {@code Instance} of a bot before places: its name, read only, until Studio's upgrade rewrites it. */
    @SuppressWarnings("removal")
    public static Node oldInstance(ValueContext ctx) {
        String name = ctx.value(Instance.class).map(Instance::name).filter(n -> !n.isEmpty()).orElse(OWN);
        return new Label(name + " (an old instance: upgrade the SDK to pick a place)");
    }

    private static void choose(ValueContext ctx, MenuButton pill, Place picked) {
        Optional<String> refused = ctx.write(picked);
        if (refused.isPresent()) {
            if (ctx.services() != null) ctx.services().status(refused.get());
            return;
        }
        pill.setText(label(picked));
    }

    /** A place for a person: an instance by its name, the desktop, a private display by its number, a VM by name. */
    static String label(Place place) {
        if (place == null) return OWN;
        return switch (place.kind()) {
            case ANDROID -> place.name().isEmpty() ? OWN : place.name();
            case DESKTOP -> place.kind().displayName();
            case PRIVATE_DISPLAY -> place.kind().displayName() + " " + place.display();
            case VM, UNKNOWN -> (place.kind().displayName() + " " + place.name()).strip();
        };
    }
}
