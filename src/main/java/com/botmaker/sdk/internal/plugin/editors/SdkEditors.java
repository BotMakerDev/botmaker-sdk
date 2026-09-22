package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.sdk.internal.plugin.emulator.EmulatorEditors;
import com.botmaker.shared.game.EpicLibraryScanner;
import com.botmaker.shared.game.SteamLibraryScanner;

import java.util.List;

/**
 * Every editor this plugin offers, in the order it wants them consulted.
 *
 * <p>Order matters only within this list — the host consults its own editors first and its JDK/enum fallbacks
 * last, whatever a plugin claims. So this is not a precedence table so much as a table of contents, and the
 * one rule inside it is that a narrower match comes before a wider one.
 *
 * <p><b>Everything here matches on the type, not on the call site.</b> That is deliberate and it is what the
 * contract's {@code ValueContext} bought: an editor chosen by type is drawn both in a bot's source and in the
 * Parameters window, while one chosen by {@code enclosingMethod()} can only ever appear in the first. Where a
 * call site genuinely is what decides — a Steam app id and a window title are both {@code String} — the editor
 * asks {@link com.botmaker.plugin.api.slot.ValueContext#asSlot()} and declines when the answer is {@code null}.
 */
public final class SdkEditors {

    private SdkEditors() {}

    /** Built once and shared: an editor holds no state, the value lives in the context it is handed. */
    public static final List<SlotEditor> ALL = List.of(
            // Chosen by the call, and therefore first: every one of these is a String, and the type-based
            // editors below would not claim them — but a plugin loaded before this one might, and a narrower
            // match belongs ahead of a wider one regardless of who is currently holding the wider one.
            SlotEditor.of(CallSites.STEAM_APP_ID,
                    ctx -> LaunchEditors.game(ctx, SteamLibraryScanner::new)),
            SlotEditor.of(CallSites.EPIC_APP_NAME,
                    ctx -> LaunchEditors.game(ctx, EpicLibraryScanner::new)),
            SlotEditor.of(CallSites.LAUNCH_PROGRAM, LaunchEditors::program),
            SlotEditor.of(CallSites.LAUNCH_OPTION, LaunchEditors::option),
            SlotEditor.of(CallSites.BOT_SETTING, SettingsEditors::setting),
            // The emulator instance name, and the last call-site editor the host still owned. It stayed
            // behind on 2026-08-28 because the dialog under it reached Studio's emulator probe, app cache and
            // phone-pairing dialog; all three came here on 2026-08-31, since none of them ever needed the
            // host — botmaker-shared is published, and scanning for emulators was only ever something the
            // editor happened to be written to do first.
            SlotEditor.of(CallSites.EMULATOR_NAME, EmulatorEditors::instanceName),
            // The two names that tie a bot's code to its Activity Flow canvas. Both are a String and both
            // name something drawn elsewhere, so nothing but the call could choose these.
            SlotEditor.of(CallSites.ACTIVITY_NAME, ActivityEditors::activityName),
            SlotEditor.of(CallSites.OUTCOME_NAME, ActivityEditors::outcomeName),

            // Rect, Point, Size, Precision and a single ImageTemplate stood here until 2026-09-23. They are
            // this plugin's own types, so their editors are PluginType.editor in SdkTypes and the host draws
            // them from there; listing them here as well made this plugin claim each type twice.

            // Several named pictures, and it comes ahead of SdkTypes' single-picture editor because it claims
            // a subset of what that one would: an ImageTemplate argument that the host says is one of a run.
            // The host consults a plugin's slot editors before its types' editors, and here the order is the
            // whole difference between "found.hasAny(coin, gem)" drawn as one row and drawn as two pickers.
            //
            // It does NOT yet claim an ImageTemplateGroup slot, though the editor draws that shape too.
            // Filling one is the second of the two edits that let the host seed a group find's body with a
            // Matches switch, and that seeding emits this API from the host — the thing the generation
            // phase exists to move. Claiming the slot now would silently delete the seed.
            SlotEditor.of(TemplateEditors::isRunOfPictures, TemplateEditors::group),
            // Duration and Color are plugin-basics' types, so this plugin cannot declare them: these two are
            // overrides, and the host asks the user which editor to use when both are loaded.
            //
            // A wait length: the unit is invisible in a bare number, and this is the type that carries the
            // random range the humanized wait needs. Both spellings are accepted, and that is not belt and
            // braces — the Parameters window knows this type by the fully-qualified name its vocabulary
            // records, while a slot in source often knows it only as "Duration", because java.time is not in
            // the bot's own type index and so never resolves to a package.
            SlotEditor.of(ctx -> ctx.type().isNamed("java.time.Duration") || ctx.type().isNamed("Duration"),
                    DurationEditor::duration),
            // A colour, and both spellings for the same reason as Duration above: the Parameters window knows
            // this type by the fully-qualified name its vocabulary records, while a slot in source usually
            // knows it only as "Color" — java.awt is not in a bot's own type index either.
            SlotEditor.of(ctx -> ctx.type().isNamed("java.awt.Color") || ctx.type().isNamed("Color"),
                    ColorEditors::color));
}
