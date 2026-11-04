package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import samplebot.SampleBot;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.screens.State;

import java.util.List;
import java.util.Map;

/** The BotMaker SDK's values for this bot, each an {@code @SdkValue} the SDK reads off this class. */
public final class Sdk {

    @SdkValue(SdkValue.Id.LAUNCH_TARGETS)
    public static List<Launcher> launchTargets() {
        return List.of(Launcher.of("steam:1145350"));
    }

    @SdkValue(SdkValue.Id.VM)
    public static String vm() {
        return "";
    }

    @SdkValue(SdkValue.Id.RUN)
    public static Run run() {
        return Run.of()
                .home(SampleBot::goHome)
                .setup(SampleBot::setup);
    }

    @SdkValue(SdkValue.Id.FLOW_LAYOUT)
    public static FlowLayout flowLayout() {
        return FlowLayout.of(Map.ofEntries(
                Map.entry("MAIN", FlowLayout.cards(Map.ofEntries(
                        Map.entry("Collect", FlowLayout.at(80, 80)),
                        Map.entry("Battle", FlowLayout.at(380, 80)),
                        Map.entry("Rest", FlowLayout.at(380, 300)))))), true);
    }

    @SdkValue(SdkValue.Id.SETTINGS)
    public static BotSettings settings() {
        return BotSettings.defaults();
    }

    @SdkValue(SdkValue.Id.SHADES)
    public static Shades shades() {
        return Shades.of(new Shade(Shade.Kind.DIM, new Shade.Channels(0.45, 0.45, 0.45), new Shade.Channels(0, 0, 0),
                0.08, Shade.Cause.GAME, State.DISABLED, new Shade.Geometry(0, 1.0, 1.0, 0, 0)),
                new Shade(Shade.Kind.SCALE, new Shade.Channels(1, 1, 1), new Shade.Channels(0, 0, 0), 0.04,
                        Shade.Cause.HOVER, State.NORMAL, new Shade.Geometry(0, 1.1, 1.1, 0, 0)));
    }

    private Sdk() {}
}
