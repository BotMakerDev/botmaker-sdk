package com.botmaker.sdk.plugin.emulator.signin;

import com.botmaker.plugin.api.run.SideRun;
import com.botmaker.plugin.api.run.SideRuns;
import com.botmaker.sdk.api.signin.Refusal;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.AccountDevice;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.InstanceSignIn.End;
import com.botmaker.shared.emulator.google.InstanceSignIn.Result;
import com.botmaker.shared.emulator.google.SignInDriver;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The Google sign-in as a bot (spec {@code docs/superpowers/specs/2026-11-15-google-sign-in-bot-design.md}): one run of
 * the sign-in bot per instance, in its own process, started through the host's {@link SideRuns}. The run reaches this
 * process only through its {@link SignInHost}, which types every secret; the run's end is read off the host, which
 * checks again on the device whether the account is signed in.
 */
public final class BotSignInDriver implements SignInDriver {

    /** Android's own page for adding a Google account, where the bot starts. */
    static final String ADD_ACCOUNT = "am start -a android.settings.ADD_ACCOUNT_SETTINGS --esa account_types com.google";
    /** How long the run has, counted again after each time it asked the user. */
    static final Duration LIMIT = Duration.ofMinutes(5);
    /** How long the user has to answer what the run asked. */
    static final Duration YOUR_LIMIT = Duration.ofMinutes(15);
    static final int KEY_HOME = 3;
    private static final Duration POLL = Duration.ofMillis(500);

    private final Supplier<SideRuns> runs;
    private final Supplier<Optional<Path>> bot;
    private final LongSupplier clock;
    private final Duration poll;

    /**
     * @param runs where the sign-in bot is started, asked at each sign-in ({@code StudioServices::sideRuns})
     * @param bot  the sign-in bot's folder, a project or an unpacked {@code .bmk}; empty when BotMaker has none
     */
    public BotSignInDriver(Supplier<SideRuns> runs, Supplier<Optional<Path>> bot) {
        this(runs, bot, System::currentTimeMillis, POLL);
    }

    BotSignInDriver(Supplier<SideRuns> runs, Supplier<Optional<Path>> bot, LongSupplier clock, Duration poll) {
        this.runs = runs;
        this.bot = bot;
        this.clock = clock;
        this.poll = poll;
    }

    @Override
    public Attempt signIn(EmulatorInstance instance, AccountDevice device, String email, char[] password,
                          InstanceSignIn.SecondStep second, Operation.Context context) throws Exception {
        Optional<Path> folder = bot.get();
        if (folder.isEmpty()) {
            return new Attempt(new Result(End.FAILED, "BotMaker has no Google sign-in bot to run."), false);
        }
        try (SignInHost host = SignInHost.open(device, email, password, second, context)) {
            context.step("Opening Android's page for adding a Google account…");
            device.shell(ADD_ACCOUNT);
            try {
                SideRun run;
                try {
                    run = runs.get().start(folder.get(), Map.of(InstalledGame.CAPTURE_EMULATOR, instance.reference()),
                            host.environment());
                } catch (IOException e) {
                    return new Attempt(new Result(End.FAILED, "The sign-in bot didn't start: " + e.getMessage()),
                            false);
                }
                Optional<Result> cut;
                try {
                    cut = await(run, host, context);
                } finally {
                    if (run.isRunning()) run.stop();
                }
                Result result = ended(host, email, cut);
                // signed in, the password was good (or not asked); else only if Google asked a step after it
                return new Attempt(result, result.signedIn() || host.passwordAccepted());
            } finally {
                home(device);
            }
        }
    }

    /** Waits for the run to end; a result when it was cut short: cancelled, or out of time. */
    private Optional<Result> await(SideRun run, SignInHost host, Operation.Context context)
            throws InterruptedException {
        long deadline = clock.getAsLong() + LIMIT.toMillis();
        long askedSince = -1;
        while (true) {
            if (context.cancelled()) return Optional.of(new Result(End.CANCELLED, "Cancelled."));
            if (run.await(poll).isPresent() || !run.isRunning()) return Optional.empty();
            long now = clock.getAsLong();
            if (host.waitingForYou()) {
                if (askedSince < 0) askedSince = now;
                if (now - askedSince > YOUR_LIMIT.toMillis()) {
                    return Optional.of(new Result(End.TIMED_OUT,
                            "The screen waited " + YOUR_LIMIT.toMinutes() + " minutes for you."));
                }
                // the run's own time starts again once the user answered
                deadline = now + LIMIT.toMillis();
            } else {
                askedSince = -1;
                if (now > deadline) {
                    return Optional.of(new Result(End.TIMED_OUT,
                            "The sign-in didn't end within " + LIMIT.toMinutes() + " minutes."));
                }
            }
        }
    }

    /** Leaves the instance on its home screen, so no half-typed page is left up for what comes next. */
    private static void home(AccountDevice device) {
        try {
            device.key(KEY_HOME);
        } catch (RuntimeException | Error gone) {
            // dadb surfaces a gone instance as either: nothing is left on it to leave
        }
    }

    /**
     * What the run left: the device says whether it is signed in, cut short or not (Google may finish just as time
     * runs out); else how it was cut, else what the run told the host.
     */
    static Result ended(SignInHost host, String email, Optional<Result> cut) {
        if (host.signedIn()) {
            String typed = email.strip().toLowerCase(Locale.ROOT);
            String listedAs = host.added().filter(added -> !added.equals(typed)).orElse(null);
            return new Result(End.SIGNED_IN, "Signed in.", listedAs);
        }
        if (cut.isPresent()) return cut.get();
        Optional<Refusal> refusal = host.refusal();
        if (refusal.isPresent()) return new Result(endOf(refusal.get()), refusal.get().displayName());
        if (host.cannotType()) {
            return new Result(End.CANNOT_TYPE, "BotMaker types only plain letters, digits and signs (ASCII): sign in by hand.");
        }
        return new Result(End.FAILED, "The sign-in bot stopped before the account was signed in; its run report says where.");
    }

    static End endOf(Refusal refusal) {
        return switch (refusal) {
            case NO_SUCH_ACCOUNT -> End.NO_SUCH_ACCOUNT;
            case WRONG_PASSWORD -> End.WRONG_PASSWORD;
            case REFUSED, UNKNOWN -> End.REFUSED;
        };
    }
}
