// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.capture.AutoCapture;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.shared.launch.LaunchSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The tabs of the Choose capture source window (spec {@code 2026-11-25-auto-capture-design.md}, layout A), and which
 * one it opens on: the tab of the value the bot has now.
 */
enum SourceTab {
    AUTOMATIC("Automatic"),
    WINDOWS("Windows"),
    DEVICES("Emulators & phones"),
    SCREENS("Screens"),
    VM("Game VM");

    private final String displayName;

    SourceTab(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    /** The tabs shown: Automatic unless only a surface is asked, the game VM's only when the bot has one. */
    static List<SourceTab> shown(boolean automatic, boolean vm) {
        List<SourceTab> tabs = new ArrayList<>();
        if (automatic) tabs.add(AUTOMATIC);
        tabs.addAll(List.of(WINDOWS, DEVICES, SCREENS));
        if (vm) tabs.add(VM);
        return List.copyOf(tabs);
    }

    /**
     * The tab {@code current} is on, among {@link #shown}: Automatic for the automatic source, none, the project's
     * own, or the whole desktop while a game is launched ({@link AutoCapture#isAutomatic}); a window or the desktop in
     * the game VM when the bot has one, since a capture there reads the guest's.
     */
    static SourceTab opening(CaptureSource current, LaunchSpec target, boolean automatic, boolean vm) {
        CaptureSource whole = CaptureLabels.whole(current);
        if (automatic && AutoCapture.isAutomatic(whole, target)) return AUTOMATIC;
        if (vm && (whole instanceof NamedWindow || CaptureLabels.isDesktop(whole))) return VM;
        if (whole instanceof NamedWindow) return WINDOWS;
        if (whole instanceof EmulatorSource) return DEVICES;
        return SCREENS;
    }
}
