package samplebot;

import samplebot.params.Parameters;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Pictures;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Sdk;
import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.api.vision.ImageClicker;

/**
 * A bot shaped as Studio writes one, compiled by {@code SampleBotCompileTest} against the SDK under test: an
 * {@code api.*} change that breaks a bot fails the SDK's own tests. Test data only; nothing ships it.
 *
 * <p>Three activities and a flow deciding which of them runs next ({@code Flows.java} in {@code plugins/}), the run's
 * values ({@code Sdk.java} beside it), the pictures, texts and screens Studio keeps, and the parameters.
 */
public final class SampleBot {

    private SampleBot() {}

    public static void main(String[] args) {
        Bot.runFlow(Sdk.class);
    }

    /** Back to the screen every activity expects to start from — the run's {@code .home(SampleBot::goHome)}. */
    public static void goHome() {
        for (int attempt = 0; attempt < 5; attempt++) {
            if (!ImageClicker.click(Pictures.HOME)) {
                return;
            }
            Wait.milliseconds(400);
        }
    }

    /** Makes the game ready for this bot — the run's {@code .setup(SampleBot::setup)}. */
    public static void setup() {
    }

    /** Whether the shop is open today — the shop button's {@code .when(Condition.of(SampleBot::shopOpen))}. */
    public static boolean shopOpen() {
        return true;
    }

    /** Whether an activity that has not got anywhere yet should keep trying, by {@link Parameters#maxAttempts}. */
    static boolean keepTrying(int attempt) {
        return attempt < Parameters.maxAttempts;
    }
}
