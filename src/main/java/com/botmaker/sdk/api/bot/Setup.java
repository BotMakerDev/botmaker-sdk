package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.console.Debug;

/**
 * What the bot's setup says when the game is not as the bot needs it and the bot cannot change it itself.
 *
 * <pre>{@code
 * public static void setup() {
 *     ImageClicker.click(Pictures.SETTINGS);
 *     if (!ImageFinder.exists(Pictures.WINDOWED_TICKED)) {
 *         Setup.fail("Set the game to windowed mode in Settings ▸ Display.");
 *     }
 *     Gamebot.goHome();
 * }
 * }</pre>
 *
 * <p>The setup is the bot's own method, {@code .setup(Gamebot::setup)} on the flow, run after the game is started:
 * at each run's start and after each restart. It opens the game's settings and sets what the bot needs there —
 * its resolution, its window mode, its language, and whatever a user chose in the bot's {@code @Param}s.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🛠")
public final class Setup {

    private Setup() {}

    /**
     * Ends the run, telling whoever runs the bot what to change in the game: {@code whatToChange} is shown as the
     * run's error.
     */
    public static void fail(String whatToChange) {
        Debug.error("Setup: " + whatToChange);
        Bot.stop();
    }
}
