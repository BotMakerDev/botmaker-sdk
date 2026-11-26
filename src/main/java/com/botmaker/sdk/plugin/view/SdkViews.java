// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.view;

import com.botmaker.plugin.api.RunViewHolder;
import com.botmaker.plugin.api.run.RunView;
import com.botmaker.plugin.api.run.ViewTarget;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;

import java.util.Optional;

/** What the host's Remote Pilot shows of the bot's runs: a new {@link SdkRunView} each time the host asks. */
public final class SdkViews implements RunViewHolder {

    @Override
    public RunView view() {
        return new SdkRunView(new SdkRunView.Probes(
                window -> {
                    try {
                        return window.find(NativeControllerFactory.get().getAllWindows())
                                .map(SdkViews::target);
                    } catch (RuntimeException | LinkageError unreadable) {
                        return Optional.empty();
                    }
                },
                new EmulatorSerials(name -> EmulatorInstances.find(name).map(EmulatorInstance::endpoint))));
    }

    /** The open window as the host names it: its native handle, which the host finds it by, and its title now. */
    private static ViewTarget.Window target(GenericWindow window) {
        return new ViewTarget.Window(String.valueOf(window.getNativeHandle()), window.getTitle());
    }
}
