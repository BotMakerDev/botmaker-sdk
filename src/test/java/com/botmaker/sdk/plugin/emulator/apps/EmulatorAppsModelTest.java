// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.apps;

import com.botmaker.shared.emulator.ApkLibrary;
import com.botmaker.shared.emulator.CpuAbi;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.platform.Os;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An emulator's apps: the devices offered per OS, their apps, Install on and Downloaded apps. */
class EmulatorAppsModelTest {

    private static final EmulatorInstance WAYDROID = new EmulatorInstance(PlatformId.WAYDROID, "Waydroid", "192.168.240.112", 5555);
    private static final EmulatorInstance LDPLAYER = new EmulatorInstance(PlatformId.LDPLAYER, "LDPlayer-1", "127.0.0.1", 5555);
    private static final EmulatorInstance PHONE = new EmulatorInstance(PlatformId.PHYSICAL, "Pixel", "192.168.1.20", 5555);
    private static final EmulatorInstance AVD = new EmulatorInstance(PlatformId.AVD, "Pixel_8", "127.0.0.1", 5554);
    private static final String GMS = "com.google.android.gms";

    @Test
    void devicesAreThisComputersEmulatorAndPhones() {
        List<EmulatorInstance> found = List.of(PHONE, AVD, LDPLAYER, WAYDROID);
        assertEquals(List.of(WAYDROID, PHONE), AndroidDevices.offered(found, Os.LINUX));
        assertEquals(List.of(LDPLAYER, PHONE), AndroidDevices.offered(found, Os.WINDOWS));
        assertEquals(List.of(PHONE), AndroidDevices.offered(found, Os.MAC));
        assertEquals(List.of(WAYDROID), AndroidDevices.offered(List.of(WAYDROID, WAYDROID), Os.LINUX));
    }

    @Test
    void appsTakeTheDesktopEntryOverTheCacheAndSortByName() {
        AndroidDevices.App entry = new AndroidDevices.App("com.b", "Bravo", Path.of("/b.png"), true);
        AndroidDevices.App unpictured = new AndroidDevices.App("com.c", "Charlie", null, true);
        List<AndroidDevices.App> apps = AndroidDevices.apps(List.of(new EmulatorProbe.InstalledApp("com.b", "b"),
                        new EmulatorProbe.InstalledApp("com.a", "Alpha")), Map.of("com.b", entry, "com.c", unpictured),
                pkg -> Path.of("/cache", pkg + ".png"));
        assertEquals(List.of("Alpha", "Bravo", "Charlie"), apps.stream().map(AndroidDevices.App::name).toList());
        assertEquals(Path.of("/b.png"), apps.get(1).icon());
        assertEquals(Path.of("/cache/com.c.png"), apps.get(2).icon());
    }

    @Test
    void systemAppsAreTheRest() {
        List<AndroidDevices.App> listed = List.of(new AndroidDevices.App("com.a", "Alpha", null, true));
        List<AndroidDevices.App> system = AndroidDevices.systemApps(List.of("com.a", GMS, GMS), listed);
        assertEquals(List.of(GMS), system.stream().map(AndroidDevices.App::packageName).toList());
        assertFalse(system.getFirst().launchable());
    }

    @Test
    void installOnRowsTickThePickedAndSayTheVersion() {
        AndroidDevices.Device way = new AndroidDevices.Device(WAYDROID, true, List.of());
        AndroidDevices.Device phone = new AndroidDevices.Device(PHONE, false, List.of());
        List<InstallOn.Row> rows = InstallOn.rows(List.of(way, phone),
                Map.of("Waydroid", new InstallOn.Facts("11", "x86_64", "1.3.0")), Set.of("Waydroid"));
        assertTrue(rows.get(0).ticked());
        assertFalse(rows.get(1).ticked());
        assertEquals("Waydroid: Waydroid · Android 11 · x86_64 · 1.3.0 → updates", rows.get(0).text());
        assertNull(rows.get(1).facts());
        assertTrue(rows.get(1).text().endsWith("—"), rows.get(1).text());
        assertEquals("not installed", InstallOn.has(new InstallOn.Facts("11", "x86_64", null)));
        assertEquals("1.3.0", InstallOn.versionName("    versionCode=13 minSdk=21\n    versionName=1.3.0\n"));
        assertNull(InstallOn.versionName("Unable to find package"));
    }

    @Test
    void onlyAndroidInstancesTakeApps() {
        assertTrue(EmulatorApps.takesApps(PlatformId.PHYSICAL));
        assertFalse(EmulatorApps.takesApps(PlatformId.AVD));
    }

    @Test
    void downloadedAppsBulkActions() {
        ApkLibrary.Entry new64 = entry("com.a", 3, CpuAbi.X86_64);
        ApkLibrary.Entry old64 = entry("com.a", 2, CpuAbi.X86_64);
        ApkLibrary.Entry arm = entry("com.a", 2, CpuAbi.ARM64_V8A);
        ApkLibrary.Entry other = entry("com.b", 1, CpuAbi.ANY);
        List<DownloadedApps.Row> rows = List.of(new DownloadedApps.Row(new64, List.of("Waydroid")),
                new DownloadedApps.Row(old64, List.of("Waydroid")), new DownloadedApps.Row(arm, List.of("Waydroid")),
                new DownloadedApps.Row(other, List.of()));
        assertEquals(List.of(other), DownloadedApps.installedNowhere(rows));
        assertEquals(List.of(old64), DownloadedApps.allButNewest(rows));
        assertEquals("0 B", DownloadedApps.size(0));
        assertEquals("1.5 KB", DownloadedApps.size(1536));
        assertEquals("140 MB", DownloadedApps.size(140L * 1024 * 1024));
    }

    @Test
    void aDeviceReadLaterTakesItsOwnPlaceOnly() {
        AndroidDevices.Device waydroid = new AndroidDevices.Device(WAYDROID, true, List.of());
        AndroidDevices.Device phone = new AndroidDevices.Device(PHONE, true, List.of());
        AndroidDevices.Device read = new AndroidDevices.Device(PHONE, true,
                List.of(new AndroidDevices.App("com.a", "A", null, true)));
        assertEquals(List.of(waydroid, read), AndroidDevices.replaced(List.of(waydroid, phone), read));
        // A device a later scan no longer lists stays gone.
        assertEquals(List.of(waydroid), AndroidDevices.replaced(List.of(waydroid), read));
    }

    @Test
    void aStoppedDeviceIsNotAskedForItsApps() {
        AndroidDevices.Device stopped = new AndroidDevices.Device(LDPLAYER, false, List.of());
        assertSame(stopped, AndroidDevices.refreshed(stopped, Map.of()));
    }

    private static ApkLibrary.Entry entry(String pkg, long code, CpuAbi abi) {
        return new ApkLibrary.Entry(pkg, code, "1." + code, abi, Path.of("/nowhere", pkg + code + abi.id() + ".apk"));
    }
}
