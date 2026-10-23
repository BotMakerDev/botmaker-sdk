package samplebot.plugins.sdk;

import samplebot.SampleBot;
import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The BotMaker SDK's values for this bot, each an {@code @SdkValue} the SDK reads off this class. */
public final class Sdk {

    @SdkValue(SdkValue.Id.FLOW)
    @Param(category = "Run", visibility = Param.PUBLIC, description = "Which flow the bot runs")
    public static Flow flow = Flows.MAIN;

    @SdkValue(SdkValue.Id.DO_SETUP)
    @Param(category = "Run", visibility = Param.PUBLIC,
            description = "Set the game up first: tick it on a new computer or after changing a game setting")
    public static boolean doSetup = true;

    @SdkValue(SdkValue.Id.SKIP)
    @Param(category = "Run", visibility = Param.PUBLIC, description = "Activities this run skips")
    public static Set<Class<? extends Outcome>> skip = Set.of();

    @SdkValue(SdkValue.Id.LAUNCH_TARGETS)
    @Param(category = "Game", visibility = Param.EDITOR, description = "The games this bot supports, preferred first")
    public static List<Launcher> launchTargets = List.of();

    @SdkValue(SdkValue.Id.LAUNCH_TARGET)
    @Param(category = "Game", visibility = Param.PUBLIC, description = "Which of the bot's games this computer launches")
    public static LaunchChoice launchTarget = LaunchChoice.of("");

    @SdkValue(SdkValue.Id.CAPTURE)
    @Param(category = "Game", visibility = Param.PUBLIC,
            description = "The game's window: where the bot reads pixels from")
    public static CaptureSource captureSource = CaptureSource.desktop();

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

    private Sdk() {}
}
