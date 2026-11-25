// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlayStoreSearch;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Get an app's Install on table (spec {@code 2026-11-25-game-window-design.md}, Get an app): one row per device the
 * Android apps tab offers, with its running dot, its Android version and CPU, and the version of the app it has now;
 * the device of the chip that is on is ticked. A stopped device says nothing of itself until the install starts it.
 */
final class InstallOn {

    private InstallOn() {}

    /**
     * What a running device says of itself and of the app.
     *
     * @param android     its Android version, {@code 11}
     * @param cpu         its processor's ABI, {@code x86_64}
     * @param versionName the app's version on it, {@code null} when it doesn't have it
     */
    record Facts(String android, String cpu, String versionName) {}

    /** One row: the device, what it said ({@code null} while stopped), and whether it is ticked. */
    record Row(AndroidDevices.Device device, Facts facts, boolean ticked) {

        /** The row's text after its dot: the device, Android · CPU, and the app's version on it. */
        String text() {
            String about = facts == null ? "stopped: started for the install, stopped after"
                    : "Android " + blank(facts.android()) + " · " + blank(facts.cpu());
            return device.instance().caption() + " · " + about + " · " + has(facts);
        }
    }

    /** The rows of {@code devices}: {@code facts} by device reference, the one {@code current} names ticked. */
    static List<Row> rows(List<AndroidDevices.Device> devices, Map<String, Facts> facts, String current) {
        return devices.stream().map(device -> new Row(device, device.running()
                ? facts.get(device.instance().reference()) : null,
                device.instance().reference().equals(current))).toList();
    }

    /** The version a device has: {@code 1.3.0 → updates}, {@code not installed}, or {@code —} when unknown. */
    static String has(Facts facts) {
        if (facts == null) return "—";
        return facts.versionName() == null ? "not installed" : facts.versionName() + " → updates";
    }

    /** {@code versionName=…} in {@code dumpsys package}'s answer, or {@code null}. Pure. */
    static String versionName(String dumpsys) {
        if (dumpsys == null) return null;
        Matcher m = VERSION_NAME.matcher(dumpsys);
        return m.find() ? m.group(1) : null;
    }

    /** What {@code instance}, running, says of itself and of {@code pkg}; {@code null} when it doesn't answer. */
    static Facts read(EmulatorInstance instance, String pkg) {
        try (AdbDevice device = AdbDevice.connect(instance)) {
            String version = pkg != null && PlayStoreSearch.PACKAGE.matcher(pkg).matches()
                    && device.installedVersion(pkg) >= 0 ? versionName(device.shell("dumpsys package " + pkg)) : null;
            return new Facts(device.shell("getprop ro.build.version.release").strip(),
                    device.shell("getprop ro.product.cpu.abi").strip(), version);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String blank(String said) {
        return said == null || said.isBlank() ? "?" : said;
    }

    private static final Pattern VERSION_NAME = Pattern.compile("versionName=(\\S+)");
}
