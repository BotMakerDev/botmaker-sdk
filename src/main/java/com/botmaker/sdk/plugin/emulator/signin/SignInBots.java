package com.botmaker.sdk.plugin.emulator.signin;

import com.botmaker.plugin.api.run.SideRuns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Where the Google sign-in bot comes from: the catalog's run copy of {@link #REPO}, which the host gets, checks and
 * keeps ({@link SideRuns#catalogCopy}: the newest approved release, its {@code .bmk}'s sha256 checked, the last copy
 * offline). On a developer's computer, {@link #DEV_FOLDER} names a checkout of the bot instead, which runs on whatever
 * SDK its pom resolves, a {@code ~/.m2} build first.
 */
public final class SignInBots {

    /** The sign-in bot's repository, as the catalog lists it. */
    public static final String REPO = "BotMakerDev/botmaker-google-sign-in";
    /** The environment variable naming a local checkout of the bot to run instead of the catalog's. */
    public static final String DEV_FOLDER = "BOTMAKER_DEV_SIGNIN_BOT";

    private SignInBots() {}

    /** The bot's folder: the checkout {@link #DEV_FOLDER} names, else the host's catalog copy. */
    public static BotSignInDriver.BotFolder of(Supplier<SideRuns> runs) {
        return of(runs, System.getenv(DEV_FOLDER));
    }

    static BotSignInDriver.BotFolder of(Supplier<SideRuns> runs, String dev) {
        return () -> {
            if (dev != null && !dev.isBlank()) {
                Path folder = Path.of(dev.strip());
                if (!Files.isRegularFile(folder.resolve("pom.xml"))) {
                    throw new IOException(DEV_FOLDER + " names " + folder + ", which holds no bot.");
                }
                return folder;
            }
            try {
                return runs.get().catalogCopy(REPO);
            } catch (NoSuchMethodError older) {
                // a host whose contract predates catalogCopy: the plugin is newer than this BotMaker
                throw new IOException("This BotMaker is older than its SDK plugin and gets no sign-in bot: update "
                        + "BotMaker.");
            }
        };
    }
}
