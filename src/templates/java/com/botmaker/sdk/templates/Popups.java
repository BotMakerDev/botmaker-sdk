package com.botmaker.sdk.templates;

import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;

/**
 * Dismiss whatever the game has interrupted us with. BotMaker runs this before every vision step (see the
 * {@code PopupGuard.install} line in the entry point), so no activity has to open with its own defensive
 * dismissal code.
 *
 * <p>{@link #run()} already has the loop; fill in {@link #POPUPS} and the body for your game. The shape that
 * works is "which combination is on screen", not "click anything that looks like a cross": the same close
 * button often belongs to the screen the bot is actually working on, and a popup's body usually isn't
 * clickable at all.
 * <pre>
 *   private static final ImageTemplateGroup POPUPS = ImageTemplateGroup.of(mail, claimAll, tapToClose);
 *
 *   ImageFinder.whileFindAny(POPUPS, found -&gt; {
 *       if (found.has(mail) &amp;&amp; found.has(claimAll)) ImageClicker.click(found.get(claimAll));
 *       else if (found.has(tapToClose))              ImageClicker.click(found.get(tapToClose));
 *   });
 * </pre>
 * The loop keeps going while any popup is still up, so a reward stacked behind a mail is cleared too — and
 * the finds inside it are not themselves guarded, so this cannot recurse.
 *
 * <p>Each activity has a "check for popups" tick in Project &rarr; Activity Flow; turn it off for one that
 * works through a popup-shaped screen itself.
 *
 * <p>SEED — Studio writes it once, when the project is created, and never again.
 */
public class Popups extends Activity<Popups.Outcome> {

    /** The one instance; the entry point installs it as the popup guard. */
    public static final Popups INSTANCE = new Popups();

    /** The popups this bot knows how to dismiss. Add your templates here; empty means "no popups". */
    private static final ImageTemplateGroup POPUPS = ImageTemplateGroup.of();

    /** Popups reports nothing to route on — it is called by the guard, not wired into the flow. */
    public enum Outcome { NEXT }

    @Override
    public boolean isEnabled() {
        return true;   // guard hook — always available
    }

    @Override
    public Outcome run() {
        ImageFinder.whileFindAny(POPUPS, found -> {
            // TODO: click the popup this frame found — e.g. ImageClicker.click(found.get(closeButton));
        });
        return Outcome.NEXT;
    }
}
