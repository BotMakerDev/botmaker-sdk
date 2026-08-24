package com.botmaker.sdk.templates;

import com.botmaker.sdk.api.bot.Activity;

/**
 * Navigate back to a known-good "home" screen. Called by the supervisor before it relaunches the game during
 * recovery, and before any activity whose "go home first" tick is on. Fill in {@link #run()} for your game,
 * e.g.:
 * <pre>
 *   while (!ImageFinder.find(home)) {
 *       ImageClicker.click(back);
 *       Wait.seconds(1);
 *   }
 * </pre>
 *
 * <p>SEED — Studio writes it once, when the project is created, and never again.
 */
public class GoHome extends Activity<GoHome.Outcome> {

    /** The one instance; referenced by the entry point and FlowDriver. Constructing it registers "GoHome". */
    public static final GoHome INSTANCE = new GoHome();

    /** GoHome reports nothing to route on — it is called directly, not wired into the flow. */
    public enum Outcome { NEXT }

    @Override
    public boolean isEnabled() {
        return true;   // recovery hook — always available
    }

    @Override
    public Outcome run() {
        // TODO: navigate back to your game's home screen.
        return Outcome.NEXT;
    }
}
