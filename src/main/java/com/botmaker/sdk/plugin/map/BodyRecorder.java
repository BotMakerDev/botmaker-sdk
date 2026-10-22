package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Argument;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.value.Ref;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.flow.ActivityScreens.Target;

import java.util.Optional;

/**
 * 🗺's <b>Record into</b>: what the author does in Live, written into an activity's body as the map's own calls —
 * a press on {@code TAVERN.beerBuy} becomes {@code Navigate.press(Screens.TAVERN.beerBuy);}, a walk
 * {@code Navigate.to(Screens.TAVERN);}. The host writes each line ({@code PluginValues.appendCall}); this class says
 * which call and names its arguments as the map's own fields, never as Java text.
 */
final class BodyRecorder {

    static final Ref.Of1<Button, Pressed> PRESS = Navigate::press;
    static final Ref.Of1<Hotkey, Pressed> PRESS_KEY = Navigate::press;
    static final Ref.Of2<Button, String, Pressed> TYPE = Navigate::type;
    static final Ref.Of1<Screen, Boolean> TO = Navigate::to;

    private BodyRecorder() {}

    /** {@code Navigate.press(Screens.<screen>.<field>);} appended to {@code into}'s body; why not, else. */
    static Optional<String> press(PluginValues values, Target into, String screen, String field, boolean hotkey) {
        Argument named = Argument.member(SdkValues.SCREEN_MAP, Member.of(screen, field));
        return values.appendCall(into.body(), hotkey ? PRESS_KEY : PRESS, named);
    }

    /** {@code Navigate.type(Screens.<screen>.<field>, "<text>");} appended to {@code into}'s body. */
    static Optional<String> type(PluginValues values, Target into, String screen, String field, String text) {
        return values.appendCall(into.body(), TYPE, Argument.member(SdkValues.SCREEN_MAP, Member.of(screen, field)),
                Argument.value(text));
    }

    /** {@code Navigate.to(Screens.<screen>);} appended to {@code into}'s body. */
    static Optional<String> walk(PluginValues values, Target into, String screen) {
        return values.appendCall(into.body(), TO, Argument.member(SdkValues.SCREEN_MAP, Member.of(screen)));
    }

    /** What the line says once a call went in, or why it did not. */
    static String said(Optional<String> refused, Target into, String what) {
        return refused.map(why -> "Not recorded: " + why).orElse("Recorded into " + into.label() + ": " + what);
    }
}
