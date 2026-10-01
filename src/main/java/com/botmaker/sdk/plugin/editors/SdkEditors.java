package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.sdk.api.bot.ActivityName;
import com.botmaker.sdk.api.bot.OutcomeName;
import com.botmaker.sdk.internal.emulator.EmulatorName;
import com.botmaker.sdk.plugin.emulator.EmulatorEditors;

import java.awt.Color;
import java.util.List;

/**
 * The editors this plugin offers that none of its types chooses for itself, in the order it wants them
 * consulted. An editor for one of this plugin's own types — Point, Precision, a picture — is not here: it is
 * declared beside the type, in {@code SdkTypes}.
 *
 * <p>Three kinds:
 * <ul>
 *   <li><b>By parameter.</b> An activity name, an outcome name and a setting are all plain values; the api
 *       says which is which by annotating the parameter ({@code outcome(@OutcomeName String name)}), and the
 *       editor is chosen by the annotation. These are absent from the Parameters window, which has no call
 *       behind a row. The launch annotations and their game grids went on 2026-10-01: the launch stack moved
 *       to {@code internal}, so no offered call carried them.</li>
 *   <li><b>By a run of pictures</b>, which neither a parameter nor a type can say: only the host knows that
 *       several arguments are one list.</li>
 *   <li><b>By another plugin's type</b>: plugin-basics declares {@code java.awt.Color}; this plugin offers a
 *       colour picker that samples the capture target, and the host asks the user which to use.</li>
 * </ul>
 *
 * <p>Order matters only within this list — the host consults its own editors first, whatever a plugin claims —
 * and the one rule inside it is that a narrower match comes before a wider one. Every drawing is
 * {@code () -> X::method}, so building this list links no JavaFX.
 */
public final class SdkEditors {

    private SdkEditors() {}

    /** Built once and shared: an editor holds no state, the value lives in the context it is handed. */
    public static final List<SlotEditor> ALL = List.of(
            SlotEditor.when(SettingHints::claims).draw(() -> SettingsEditors::setting),
            SlotEditor.onParameter(EmulatorName.class).draw(() -> EmulatorEditors::instanceName),
            SlotEditor.onParameter(ActivityName.class).draw(() -> ActivityEditors::activityName),
            SlotEditor.onParameter(OutcomeName.class).draw(() -> ActivityEditors::outcomeName),

            // Several named pictures. Ahead of SdkTypes' single-picture editor because it claims a subset of
            // what that one would, and the host consults a plugin's slot editors before its types' editors:
            // the order is the difference between found.hasAny(coin, gem) drawn as one row and as two pickers.
            SlotEditor.when(TemplateEditors::isRunOfPictures).draw(() -> TemplateEditors::group),

            SlotEditor.forType(Color.class).draw(() -> ColorEditors::color));
}
