package com.botmaker.sdk.templates;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.templates.meta.Template;

/**
 * The bot's entry point. Studio renames the class after the project and rewrites the package; nothing else
 * in this file is filled in, which is why it carries no token.
 *
 * <p>SEED — Studio writes it once, when the project is created, and never again. Everything below is the
 * user's from that moment on.
 */
@Template(id = "ENTRY_POINT", kind = Template.Kind.SEED, target = "${CLASS}.java")
public class BotMain {

    public static void main(String[] args) {
        // Click delays, match confidence, and whether to drive the real mouse and keyboard (which is what a
        // game needs — it ignores the quiet background clicks BotMaker sends by default) are project
        // settings, applied by the SDK before the first click. Edit them in the Studio's Input & Clicks
        // dialog.

        // Runs Popups.run() before every vision step, so a daily reward or a mail popup is dismissed
        // instead of hiding whatever the next find was looking for. Popups.java is yours: it decides which
        // templates mean "a popup is up", and how to close each one.
        PopupGuard.install(Popups.INSTANCE::execute);

        // Walks the Activity Flow forever; on a crash or a stuck screen it runs GoHome and restarts the
        // game you picked in the Studio.
        Bot.start(FlowDriver::run, GoHome.INSTANCE::execute);
    }
}
