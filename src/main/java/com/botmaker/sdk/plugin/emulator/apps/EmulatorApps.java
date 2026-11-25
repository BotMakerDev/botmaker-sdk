// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.apps;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.platform.Os;
import javafx.stage.Window;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An emulator's apps, which the Emulators window manages: Get an app installs one on the instances picked there, and
 * Downloaded apps deletes the files the library keeps. The Game window's Android apps tab only picks an app already
 * on a device as the bot's target.
 */
public final class EmulatorApps {

    private EmulatorApps() {}

    /** Whether {@code product}'s instances take an app on this computer: its Android emulator, and phones. */
    public static boolean takesApps(PlatformId product) {
        return AndroidDevices.offered(product, Os.current());
    }

    /**
     * Get an app over {@code owner}, with {@code picked} ticked in its Install on table. The devices are read off the
     * JavaFX thread first; {@code onDone} runs on it after the installs, {@code onFailed} when they can't be read.
     */
    public static void getApp(StudioServices services, Window owner, List<EmulatorInstance> picked, Runnable onDone,
                              Consumer<String> onFailed) {
        Set<String> ticked = picked.stream().map(EmulatorInstance::reference).collect(Collectors.toSet());
        Async.load("emulator-apps-devices", () -> AndroidDevices.scan(Map.of()),
                devices -> GetAppDialog.show(services, owner, devices, ticked, onDone), onFailed);
    }

    /** The Downloaded apps window over {@code owner}, once the devices are read off the JavaFX thread. */
    public static void downloaded(StudioServices services, Window owner, Consumer<String> onFailed) {
        Async.load("emulator-apps-devices", () -> AndroidDevices.scan(Map.of()),
                devices -> DownloadedAppsWindow.show(services, owner, devices, () -> {}), onFailed);
    }
}
