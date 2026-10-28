package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.EditorHolder;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.sdk.internal.emulator.EmulatorName;
import com.botmaker.sdk.api.flow.ActivityClass;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.emulator.EmulatorEditors;
import com.botmaker.sdk.plugin.map.ScreenConstantView;

import java.awt.Color;
import java.util.List;

/**
 * The editors this plugin offers that none of its types chooses for itself, in the order it wants them
 * consulted. An editor for one of this plugin's own types — Point, Precision, a picture — is not here: it is
 * declared beside the type, in {@code SdkTypes}.
 *
 * <p>Three kinds:
 * <ul>
 *   <li><b>By parameter or call.</b> An emulator name and a setting are plain values; the api says which is
 *       which by annotating the parameter or by a table keyed on the method, and the editor is chosen by that.
 *       These are absent from the Parameters window, which has no call behind a row. Activities and outcomes
 *       are types of their own ({@code SdkTypes}), claimed by type.</li>
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
public final class SdkEditors implements EditorHolder {

    private SdkEditors() {}

    @Override
    public List<SlotEditor> editors() {
        return ALL;
    }

    /** Built once and shared: an editor holds no state, the value lives in the context it is handed. */
    public static final List<SlotEditor> ALL = List.of(
            SlotEditor.when(SettingHints::claims).draw(() -> SettingsEditors::setting),
            SlotEditor.onParameter(EmulatorName.class).draw(() -> EmulatorEditors::instanceName),
            // An activity is its enum's class literal, which no type of this plugin's can claim: a Class.
            SlotEditor.onParameter(ActivityClass.class).draw(() -> ActivityEditors::activity),
            // The bot's run choices, @Params Bot.runFlow reads: which flow, and the activities it skips. Only a
            // value with no call site, a parameter's: a Flows constant's own Flow.of(…) is the flow editor's.
            SlotEditor.when(ActivityEditors::isFlowChoice).draw(() -> ActivityEditors::flow),
            // A constant of Flows.java or Screens.java, shown as its window shows it (the user's call, 2026-10-15):
            // the file is the plugin's, so the editor asks for a preview, and the view is both; the windows edit them.
            SlotEditor.when(FlowConstantView::isFlowConstant)
                    .draw(() -> FlowConstantView::node, () -> FlowConstantView::node),
            SlotEditor.when(ScreenConstantView::isScreenConstant)
                    .draw(() -> ScreenConstantView::node, () -> ScreenConstantView::node),
            SlotEditor.when(ActivityEditors::isSkippedActivity).draw(() -> ActivityEditors::switchable),
            // A screen of the bot's map in a block, Navigate.to(Screens.HOME). A Screen is only parts, like Flow:
            // no type of this plugin's holds it, so no type's editor can draw it.
            SlotEditor.when(ScreenEditors::isScreen).draw(() -> ScreenEditors::screen),
            // An input of the map in a block, Navigate.press(Screens.MARKET.beerBuy): a screen, then one of its fields
            // on its shot. A Pick and its Order are parts, which no type of this plugin's holds either.
            SlotEditor.when(InputPicker::isInput).draw(() -> InputPicker::input),
            SlotEditor.when(ScreenEditors::isPick).draw(() -> ScreenEditors::pick),
            SlotEditor.when(ScreenEditors::isOrder).draw(() -> ScreenEditors::order),

            // Several named pictures. Ahead of SdkTypes' single-picture editor because it claims a subset of
            // what that one would, and the host consults a plugin's slot editors before its types' editors:
            // the order is the difference between found.hasAny(coin, gem) drawn as one row and as two pickers.
            SlotEditor.when(TemplateEditors::isRunOfPictures).draw(() -> TemplateEditors::group),

            SlotEditor.forType(Color.class).draw(() -> ColorEditors::color));
}
