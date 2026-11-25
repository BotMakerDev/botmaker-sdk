// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorAppCache;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLiveness;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The devices the Android apps tab offers (spec {@code 2026-11-25-game-window-design.md}, Android apps): every instance
 * of this computer's emulator — Waydroid on Linux, LDPlayer on Windows — and every connected phone, each with the apps
 * last seen on it. Other emulators stay the Emulators window's: a bot runs on the one this computer's product makes.
 */
final class AndroidDevices {

    private AndroidDevices() {}

    /** One app on a device: what a tile shows, and whether it has an icon of its own to start from. */
    record App(String packageName, String name, Path icon, boolean launchable) {}

    /** One chip: the device, whether it runs now, and its apps, the ones with an icon first in name order. */
    record Device(EmulatorInstance instance, boolean running, List<App> apps) {

        Device {
            apps = List.copyOf(apps);
        }

        /** The chip's text: a running dot, the device's caption and its count of apps. */
        String chip() {
            return (running ? "● " : "○ ") + instance.caption() + " " + apps.size();
        }

        /** The spec of {@code app} on this device, starting on its first screen. */
        String spec(App app) {
            return LaunchSpec.emulatorApp(app.packageName(), instance.reference(), null).spec();
        }
    }

    /** Whether the tab offers {@code product}'s instances on {@code os}: this computer's emulator, and phones anywhere. */
    static boolean offered(PlatformId product, Os os) {
        return switch (product) {
            case PHYSICAL -> true;
            case WAYDROID -> os == Os.LINUX;
            case LDPLAYER -> os == Os.WINDOWS;
            default -> false;
        };
    }

    /** The instances of {@code found} the tab offers on {@code os}: the emulator's first, then phones, each once. */
    static List<EmulatorInstance> offered(List<EmulatorInstance> found, Os os) {
        Map<String, EmulatorInstance> once = new LinkedHashMap<>();
        found.stream().filter(i -> offered(i.platformId(), os))
                .sorted(Comparator.comparing(i -> i.platformId() == PlatformId.PHYSICAL))
                .forEach(i -> once.putIfAbsent(i.identity(), i));
        return List.copyOf(once.values());
    }

    /**
     * {@code instance}'s apps from what was seen of it: {@code menu}, the desktop's Android entries (Waydroid's, with
     * their icons), over {@code cached}, the ones last read off the device, whose icons {@code icon} gives.
     */
    static List<App> apps(List<EmulatorProbe.InstalledApp> cached, Map<String, GameCatalog.Item> menu,
                          Function<String, Path> icon) {
        Map<String, App> apps = new LinkedHashMap<>();
        menu.forEach((pkg, item) -> apps.put(pkg, new App(pkg, item.name(),
                item.artwork() != null ? item.artwork() : icon.apply(pkg), true)));
        for (EmulatorProbe.InstalledApp app : cached) {
            apps.putIfAbsent(app.packageName(), new App(app.packageName(), app.display(), icon.apply(app.packageName()),
                    true));
        }
        return apps.values().stream().sorted(Comparator.comparing(App::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** {@code all}, a device's every package, less the ones {@code listed}: its system apps, which no icon starts. */
    static List<App> systemApps(List<String> all, List<App> listed) {
        Set<String> shown = new HashSet<>();
        listed.forEach(app -> shown.add(app.packageName()));
        List<App> system = new ArrayList<>();
        for (String pkg : all) {
            if (shown.add(pkg)) system.add(new App(pkg, pkg, null, false));
        }
        system.sort(Comparator.comparing(App::name));
        return system;
    }

    /**
     * Every device offered here and its apps, from discovery and the app cache; a running one is asked for its apps
     * first. Blocks on discovery and ADB: off the JavaFX thread. {@code menu} maps a Waydroid package to its entry.
     */
    static List<Device> scan(Map<String, GameCatalog.Item> menu) {
        EmulatorAppCache cache = EmulatorAppCache.shared();
        List<Device> devices = new ArrayList<>();
        for (EmulatorInstance instance : offered(Platforms.discoverAll(), Os.current())) {
            boolean running = EmulatorLiveness.running(instance);
            if (running) EmulatorProbe.refresh(instance, cache);
            Map<String, GameCatalog.Item> own = instance.platformId() == PlatformId.WAYDROID ? menu : Map.of();
            devices.add(new Device(instance, running, apps(cache.packages(instance), own,
                    pkg -> cache.iconPath(instance, pkg))));
        }
        return devices;
    }

    /** {@code device}'s system apps, read off it; empty when it doesn't answer. Blocks on ADB. */
    static List<App> systemApps(Device device) {
        try (AdbDevice adb = AdbDevice.connect(device.instance())) {
            return systemApps(adb.allPackages(), device.apps());
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
