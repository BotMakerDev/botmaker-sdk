// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.emulator.AndroidPage;
import com.botmaker.shared.emulator.AppPages;
import com.botmaker.shared.launch.LaunchSpec;

/**
 * Where a picked Android app starts (spec {@code 2026-11-25-game-window-design.md}, Starts on): its first screen, a
 * page from its list, or the page open on the device now when a door or an activity it exports reaches it. A page
 * nothing reaches is no start: walking there is the bot's work, and Use stays off.
 */
final class StartsOn {

    private StartsOn() {}

    /** The three choices of the strip. */
    enum Choice {
        FIRST_SCREEN("Its first screen"),
        PAGE("A page"),
        OPEN_NOW("The page open now");

        private final String displayName;

        Choice(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    /** What the strip says under the choice: green when Use works, red when it doesn't. */
    record Line(boolean ok, String text) {}

    /**
     * The spec Use writes for {@code pkg} on {@code instance}, or {@code null} when the choice gives none: an app with
     * no icon has no first screen, a page must be picked, and the page open now must be one a page reaches.
     */
    static String spec(String pkg, String instance, boolean launchable, Choice choice, AndroidPage page,
                       AppPages.Now now) {
        return switch (choice) {
            case FIRST_SCREEN -> launchable ? LaunchSpec.emulatorApp(pkg, instance, null).spec() : null;
            case PAGE -> page == null ? null : LaunchSpec.emulatorApp(pkg, instance, page.id()).spec();
            case OPEN_NOW -> now instanceof AppPages.Reached reached && pkg.equals(packageOf(reached.page()))
                    ? LaunchSpec.emulatorApp(pkg, instance, reached.page().id()).spec() : null;
        };
    }

    /** What the strip says of {@code now}, the page open on {@code instance}, for {@code pkg}. */
    static Line line(String pkg, String instance, AppPages.Now now) {
        return switch (now) {
            case null -> new Line(false, "Press ↻ to read the page open on " + instance + ".");
            case AppPages.Reached reached when !pkg.equals(packageOf(reached.page())) -> new Line(false,
                    "The page open on " + instance + " is " + packageOf(reached.page()) + "'s: pick that app.");
            case AppPages.Reached reached -> new Line(true, "Starts on " + label(reached.page()) + ".");
            case AppPages.Unreachable unreachable -> new Line(false, unreachable.name() + " is open, but no page "
                    + "opens it directly: walking there is the bot's work.");
            case AppPages.NoPage ignored -> new Line(false, "No app's page is open on " + instance + ".");
        };
    }

    /** A page as the list shows it: a door by its plain name, an activity by its short class. */
    static String label(AndroidPage page) {
        return switch (page) {
            case AndroidPage.Door door -> door.door().displayName();
            case AndroidPage.Activity activity -> {
                String component = activity.component();
                yield component.substring(component.indexOf('/') + 1);
            }
        };
    }

    /** The package {@code page} lands in. */
    static String packageOf(AndroidPage page) {
        return switch (page) {
            case AndroidPage.Door door -> door.door().landsIn();
            case AndroidPage.Activity activity -> activity.component().substring(0, activity.component().indexOf('/'));
        };
    }
}
