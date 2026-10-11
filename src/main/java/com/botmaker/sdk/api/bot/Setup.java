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
 * <p>The setup is the bot's own method, {@code .setup(Gamebot::setup)} on {@code Sdk.run()}. It opens the game's
 * settings and sets what the bot needs there — its resolution, its window mode, its language, and whatever a user
 * chose in the bot's {@code @Param}s.
 *
 * <p>It runs <b>once per computer</b>, after the game is first started: the game keeps its settings, so a later run
 * goes straight home. It runs again when a {@code @Param} in {@link #CATEGORY} changes value, and when the person
 * running the bot ticks it; a setup that fails runs again at the next start.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🛠")
public final class Setup {

    /**
     * The parameter category the setup reads: {@code @Param(category = Setup.CATEGORY)}. A change to one of these
     * values makes the setup run again on every computer, so the game gets the new setting.
     */
    public static final String CATEGORY = "Setup";

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
