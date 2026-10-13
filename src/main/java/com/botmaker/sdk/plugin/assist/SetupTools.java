package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.launch.GameCatalog;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.launch.SessionHandoff;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.screen.OpenWindows;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstanceScanner;
import com.botmaker.shared.emulator.EmulatorLiveness;
import com.botmaker.shared.emulator.Platforms.PlatformStatus;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.Launcher;
import com.botmaker.session.PrivateSession;
import com.botmaker.session.launch.BackgroundLauncher;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Where the bot looks and how it runs: its capture source, its {@code Sdk.settings()}, the emulator it
 * watches, and what it launches. All are the bot's Java, written on the FX thread as their windows write them: the
 * launch target is the first of its {@code launchTargets} this computer's system can launch. Nothing here starts an
 * emulator or a run; {@code launch_game} alone starts the game, on this desktop or in the private display, as a run
 * would.
 */
final class SetupTools {

    private static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);

    /**
     * One of the bot's settings, as {@code set_setting} names it: how it is read off a {@link BotSettings} and
     * how a typed value makes a new one. A closed set the assistant picks from by name.
     */
    enum Setting {
        FOUND_DELAY("found_delay", "Pause after a match, ms",
                s -> String.valueOf(s.clicks().foundDelay()),
                (s, v) -> s.foundDelay(whole(v, 0))),
        NOT_FOUND_DELAY("not_found_delay", "Pause after a miss, ms",
                s -> String.valueOf(s.clicks().notFoundDelay()),
                (s, v) -> s.notFoundDelay(whole(v, 0))),
        RANDOMIZE_CLICKS("randomize_clicks", "Click a random point inside a match",
                s -> String.valueOf(s.clicks().randomize()),
                (s, v) -> s.randomizeClicks(yes(v))),
        CONFIDENCE("confidence", "How sure a picture match must be, 0 to 1",
                s -> String.valueOf(s.vision().confidence()),
                (s, v) -> s.confidence(unit(v))),
        COMPARE_MARGIN("compare_margin", "How far ahead the best of several pictures must be, 0 to 1",
                s -> String.valueOf(s.vision().compareMargin()),
                (s, v) -> s.compareMargin(unit(v))),
        RUN_THE_GAME_IN("run_the_game_in", "Where the game runs",
                s -> s.where().id(),
                (s, v) -> s.where(choice(BotSettings.Where.values(), v, BotSettings.Where::id))),
        TAKE_OVER("take_over", "On my desktop, take over the mouse and keyboard (some games ignore window events)",
                s -> String.valueOf(s.takeOver()),
                (s, v) -> s.takeOver(yes(v))),
        DISPLAY_BACKEND("display_backend", "Which private display hosts the game",
                s -> s.runIn().displayBackend().id(),
                (s, v) -> s.displayBackend(choice(BotSettings.DisplayBackend.values(), v,
                        BotSettings.DisplayBackend::id))),
        INPUT_BACKEND("input_backend", "Which Linux backend delivers a take-over",
                s -> s.runIn().inputBackend().id(),
                (s, v) -> s.inputBackend(choice(BotSettings.InputBackend.values(), v, BotSettings.InputBackend::id))),
        MAX_RETRY_ATTEMPTS("max_retry_attempts", "No-progress checks before the bot is stuck, 1 or more",
                s -> String.valueOf(s.maxRetryAttempts()),
                (s, v) -> s.maxRetryAttempts(whole(v, 1)));

        private final String id;
        private final String displayName;
        private final Function<BotSettings, String> read;
        private final BiFunction<BotSettings, String, BotSettings> write;

        Setting(String id, String displayName, Function<BotSettings, String> read,
                BiFunction<BotSettings, String, BotSettings> write) {
            this.id = id;
            this.displayName = displayName;
            this.read = read;
            this.write = write;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }

        String read(BotSettings settings) {
            return read.apply(settings);
        }

        /** {@code settings} with this one set to {@code typed}; refused with the sentence to show. */
        BotSettings write(BotSettings settings, String typed) {
            return write.apply(settings, typed == null ? "" : typed.trim());
        }
    }

    record WindowTitle(@Describe("the window's title, as list_windows gives it") String window) {
    }

    record SetSetting(@Describe("which setting; get_settings lists them") Setting setting,
                      @Describe("its new value: a number, true or false, or a place's or backend's id")
                      String value) {
    }

    record UseEmulator(@Describe("the emulator's name, as list_emulators gives it") String name,
                       @Describe(value = "an app's package on it, com.example.game; this computer then launches it",
                               optional = true) String app) {
    }

    record Target(@Describe("what to launch: steam:<appId>, epic:<appName>, heroic:<appName>, faugus:<gameId> or "
            + "emu-app:<package>@<emulator>; a command line or an executable is the user's to set") String target) {
    }

    record Launch(@Describe(value = "seconds to wait for the window the bot looks at to open, up to "
            + MAX_LAUNCH_WAIT + "; 0 or left out: answer once it is launched", optional = true) Integer seconds) {
    }

    /** The longest launch_game waits: below the 60 s an MCP client commonly gives a call. */
    static final int MAX_LAUNCH_WAIT = 50;

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("get_capture_source")
                    .describedAs("Where the bot looks and clicks: Sdk.captureSource, in words")
                    .takesNothing().handledBy(SetupTools::getCaptureSource),
            AssistantTool.named("set_capture_source")
                    .describedAs("Points the bot at an open window, so it looks at and clicks in that window; the bot "
                            + "finds it again by its class (and its title when the class is shared), never by title alone")
                    .takes(WindowTitle.class).handledBy(SetupTools::setCaptureSource),
            AssistantTool.named("list_windows")
                    .describedAs("The windows open on the desktop now, each with its class and size, for "
                            + "set_capture_source")
                    .takesNothing().handledBy(SetupTools::listWindows),
            AssistantTool.named("get_settings")
                    .describedAs("The bot's own settings, Sdk.settings(): its pauses, match confidence, input and "
                            + "private display")
                    .takesNothing().handledBy(SetupTools::getSettings),
            AssistantTool.named("set_setting")
                    .describedAs("Changes one of the bot's own settings in Sdk.settings(), as ⚙ Bot Settings does")
                    .takes(SetSetting.class).handledBy(SetupTools::setSetting),
            AssistantTool.named("list_emulators")
                    .describedAs("The Android emulators set up on this computer, each with whether it is running")
                    .takesNothing().handledBy(SetupTools::listEmulators),
            AssistantTool.named("use_emulator")
                    .describedAs("Points the bot at an emulator, and with an app makes this computer launch that "
                            + "app on it. Starts nothing")
                    .takes(UseEmulator.class).handledBy(SetupTools::useEmulator),
            AssistantTool.named("get_launch_target")
                    .describedAs("What this computer launches when the bot starts, if anything")
                    .takesNothing().handledBy(SetupTools::getLaunchTarget),
            AssistantTool.named("set_launch_target")
                    .describedAs("Makes a launcher the bot's first in Sdk.launchTargets: each computer starts the "
                            + "first its system can. Starts nothing")
                    .takes(Target.class).handledBy(SetupTools::setLaunchTarget),
            AssistantTool.named("list_games")
                    .describedAs("The games this computer's launchers have installed, each with the target "
                            + "set_launch_target takes, as the launch chooser lists them")
                    .takesNothing().handledBy(SetupTools::listGames),
            AssistantTool.named("launch_game")
                    .describedAs("Starts the bot's launch target now, without running the bot, unless it is running "
                            + "already: on this desktop, or in a private display when the settings say one, which the "
                            + "look tools then read and the next run joins; given seconds, answers once the window the "
                            + "bot looks at is open")
                    .takes(Launch.class).handledBy(SetupTools::launchGame));

    private SetupTools() {}

    static AgentReply getCaptureSource(AssistantTool.None none, AgentContext context) {
        CaptureSource source = FxCall.call(() -> CaptureValue.current(context.services()));
        return AgentReply.text(source == null ? "Sdk.captureSource names nothing BotMaker can read: the bot looks "
                + "at the whole desktop, or at what the code says." : CaptureLabels.longLabel(source) + ".");
    }

    static AgentReply setCaptureSource(WindowTitle title, AgentContext context) {
        if (title.window() == null || title.window().isBlank()) return AgentReply.refused("Name a window.");
        Optional<CaptureSource> window = CaptureValue.windowTitled(title.window());
        if (window.isEmpty()) {
            return AgentReply.refused("No open window \"" + title.window().trim() + "\" has a window class to find it "
                    + "by: list_windows lists the ones that do.");
        }
        String refused = FxCall.call(() -> CaptureValue.write(context.services(), window.get()).orElse(null));
        return refused == null ? AgentReply.text("The bot now looks at " + CaptureLabels.longLabel(window.get()) + ".")
                : AgentReply.refused(refused);
    }

    static AgentReply listWindows(AssistantTool.None none, AgentContext context) {
        List<GenericWindow> found;
        try {
            found = NativeControllerFactory.get().getAllWindows();
        } catch (RuntimeException | LinkageError e) {
            return AgentReply.refused("The open windows cannot be listed here: " + e.getMessage());
        }
        // One line per title, with the class the bot would find it by; BotMaker's own windows are never the game.
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (GenericWindow window : OpenWindows.pickable(found)) {
            String title = window.getTitle();
            if (!seen.add(title)) continue;
            Rectangle r = window.getRect();
            lines.add("\"" + title + "\" class " + (window.getWindowClass() == null ? "unknown (cannot be picked)"
                    : window.getWindowClass()) + (r == null ? "" : ", " + r.width + "×" + r.height + " at " + r.x
                    + "," + r.y));
        }
        return AgentReply.text(lines.isEmpty() ? "No named window is open." : String.join("\n", lines));
    }

    static AgentReply getSettings(AssistantTool.None none, AgentContext context) {
        BotSettings settings = FxCall.call(() -> SETTINGS.read(context.services()).orElse(BotSettings.DEFAULTS));
        List<String> lines = new ArrayList<>();
        for (Setting setting : Setting.values()) {
            lines.add(setting.id() + " = " + setting.read(settings) + " — " + setting.displayName());
        }
        lines.add("run_the_game_in is one of " + ids(BotSettings.Where.values(), BotSettings.Where::id)
                + "; input_backend one of " + ids(BotSettings.InputBackend.values(), BotSettings.InputBackend::id)
                + "; display_backend one of " + ids(BotSettings.DisplayBackend.values(),
                BotSettings.DisplayBackend::id) + ". take_over only applies to my-desktop.");
        return AgentReply.text(String.join("\n", lines));
    }

    static AgentReply setSetting(SetSetting change, AgentContext context) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            // Asks the host to write Sdk.java first when the project has none, as ⚙ Bot Settings does.
            Optional<ValueContext> ctx = SETTINGS.openOrCreate(services);
            if (ctx.isEmpty()) return AgentReply.refused("This project has no Sdk.settings() to write to.");
            Optional<BotSettings> before = SETTINGS.read(ctx.get());
            if (before.isEmpty()) {
                return AgentReply.refused("Sdk.settings() is not a single `return BotSettings.defaults()…;`, so it is "
                        + "left as you wrote it.");
            }
            BotSettings after;
            try {
                after = change.setting().write(before.get(), change.value());
            } catch (IllegalArgumentException e) {
                return AgentReply.refused(change.setting().id() + ": " + e.getMessage());
            }
            if (after.equals(before.get())) {
                return AgentReply.text(change.setting().id() + " is already " + change.setting().read(after) + ".");
            }
            Optional<String> refused = ctx.get().write(after);
            if (refused.isPresent()) return AgentReply.refused(refused.get());
            return AgentReply.text(change.setting().id() + " is now " + change.setting().read(after)
                    + " in Sdk.settings().");
        });
    }

    static AgentReply listEmulators(AssistantTool.None none, AgentContext context) {
        EmulatorInstanceScanner.Scan scan = new EmulatorInstanceScanner().scan();
        if (scan.instances().isEmpty()) {
            List<String> lines = new ArrayList<>(List.of("No emulator is set up:"));
            for (PlatformStatus status : scan.statuses()) lines.add("• " + status.statusLine());
            return AgentReply.text(String.join("\n", lines));
        }
        List<String> lines = new ArrayList<>();
        for (EmulatorInstance instance : scan.instances()) {
            EmulatorLiveness liveness = EmulatorLiveness.check(instance, scan.instances());
            String problem = liveness.problem(instance);
            lines.add("\"" + instance.name() + "\" — " + instance.brand() + ", " + liveness.label()
                    + (problem == null ? "" : " (" + problem + ")"));
        }
        return AgentReply.text(String.join("\n", lines));
    }

    static AgentReply useEmulator(UseEmulator use, AgentContext context) {
        if (use.name() == null || use.name().isBlank()) return AgentReply.refused("Name an emulator.");
        String name = use.name().trim();
        List<String> names = new EmulatorInstanceScanner().instanceNames();
        Optional<String> known = names.stream().filter(n -> n.equalsIgnoreCase(name)).findFirst();
        if (known.isEmpty()) {
            return AgentReply.refused("No emulator called \"" + name + "\"; there are " + names + ".");
        }
        String app = use.app() == null || use.app().isBlank() ? null : use.app().trim();
        String target = app == null ? null : "emu-app:" + app + "@" + known.get();
        if (target != null) {
            LaunchSpec spec = LaunchSpec.parse(target);
            String unsafe = spec == null ? "\"" + app + "\" is not an app's package." : unsafe(spec);
            if (unsafe != null) return AgentReply.refused(unsafe);
        }
        String refused = FxCall.call(() -> {
            String why = CaptureValue.write(context.services(), CaptureSource.emulator(known.get()))
                    .orElse(null);
            if (why == null && target != null) why = LaunchTargetValue.set(context.services(), target).orElse(null);
            return why;
        });
        if (refused != null) return AgentReply.refused(refused);
        return AgentReply.text("The bot now looks at " + known.get() + (target == null ? "."
                : ", and this computer launches " + app + " on it when the bot starts."));
    }

    static AgentReply getLaunchTarget(AssistantTool.None none, AgentContext context) {
        String current = LaunchTargetValue.current(context.services());
        if (current == null) return AgentReply.text("This computer launches nothing for the bot: it runs against what "
                + "is already open.");
        return AgentReply.text(current + " — " + LaunchSpec.describe(current) + ".");
    }

    static AgentReply setLaunchTarget(Target target, AgentContext context) {
        LaunchSpec spec = LaunchSpec.parse(target.target());
        if (spec == null || !ASSISTANT_KINDS.contains(spec.kind())) {
            return AgentReply.refused("\"" + target.target() + "\" is not a launch target the assistant may set: it "
                    + "is kind:value, the kind one of " + ASSISTANT_KINDS.stream().map(LaunchKind::id).toList()
                    + ". A command line or an executable is set by you, in the launch chooser.");
        }
        String unsafe = unsafe(spec);
        if (unsafe != null) return AgentReply.refused(unsafe);
        String refused = FxCall.call(() -> LaunchTargetValue.set(context.services(), spec.spec()).orElse(null));
        if (refused != null) return AgentReply.refused(refused);
        return AgentReply.text("This computer now launches " + spec.describe() + " when the bot starts.");
    }

    static AgentReply listGames(AssistantTool.None none, AgentContext context) {
        List<GameCatalog.Section> sections = GameCatalog.scan();
        List<String> lines = new ArrayList<>();
        for (GameCatalog.Section section : sections) {
            // Folded sections are every other app in the menu: not games, and set_launch_target takes no
            // desktop entry anyway.
            if (section.folded() || section.items().isEmpty()) continue;
            lines.add(section.title() + (section.stopped() ? " (not running)" : "") + ":");
            for (GameCatalog.Item item : section.items()) {
                LaunchSpec spec = LaunchSpec.parse(item.spec());
                boolean settable = spec != null && ASSISTANT_KINDS.contains(spec.kind());
                lines.add("  " + item.name() + " — " + item.spec() + (settable ? "" : " (set by you, in the chooser)"));
            }
        }
        return AgentReply.text(lines.isEmpty() ? "No launcher on this computer lists a game." : String.join("\n", lines));
    }

    static AgentReply launchGame(Launch launch, AgentContext context) {
        StudioServices services = context.services();
        String current = FxCall.call(() -> LaunchTargetValue.current(services));
        if (current == null) {
            return AgentReply.refused("The bot launches nothing on this computer. list_games and set_launch_target "
                    + "pick what it starts.");
        }
        BotSettings settings = FxCall.call(() -> SETTINGS.read(services).orElse(BotSettings.DEFAULTS));
        if (settings.where() == BotSettings.Where.VM) {
            return AgentReply.refused("The game runs in a game VM: a run starts it there, and a game on this desktop "
                    + "would not be the one the bot sees. run_bot launches it; set_setting run_the_game_in "
                    + "my-desktop launches it here.");
        }
        LaunchSpec spec = LaunchSpec.parse(current);
        if (spec == null) return AgentReply.refused("\"" + current + "\" is not a launch target BotMaker reads.");
        // The same line set_launch_target draws: a command line or an executable the user set runs on their Run,
        // never because text read off a screen asked the assistant to start it now.
        String unsafe = ASSISTANT_KINDS.contains(spec.kind()) ? unsafe(spec) : spec.describe() + " is a command "
                + "line or an executable: it starts when you run the bot, not from the assistant.";
        if (unsafe != null) return AgentReply.refused(unsafe);
        if (QuickLaunch.usesBackgroundSession(spec, settings.where() == BotSettings.Where.PRIVATE_DISPLAY)) {
            return launchPrivately(spec, launch, services);
        }
        CaptureSource source = FxCall.call(() -> CaptureValue.current(services));
        boolean watched = source != null && source.hasWindowIdentity();
        if (Launcher.isRunning(spec)) {
            return AgentReply.text(spec.describe() + " is running already" + (watched && !source.isPresent()
                    ? ", but the window the bot looks at (" + CaptureLabels.longLabel(source) + ") is not open." : "."));
        }
        // On this desktop: a run must not join a private display an earlier launch offered.
        FxCall.call(() -> {
            SessionHandoff.offer(services, null);
            return null;
        });
        // Off this thread: a cold emulator app is minutes of start, and the call answers within MAX_LAUNCH_WAIT.
        CompletableFuture<Void> started = CompletableFuture.runAsync(() -> Launcher.start(spec), LAUNCHING);
        int wait = launch.seconds() == null ? 0 : Math.min(Math.max(launch.seconds(), 0), MAX_LAUNCH_WAIT);
        long until = System.nanoTime() + Math.max(wait, 2) * 1_000_000_000L;
        while (System.nanoTime() < until) {
            if (started.isCompletedExceptionally()) {
                Throwable why = started.handle((ok, e) -> e.getCause() == null ? e : e.getCause()).join();
                return AgentReply.refused(spec.describe() + " could not be launched: " + why.getMessage());
            }
            if (wait > 0 && watched && source.isPresent()) {
                return AgentReply.text("Launched " + spec.describe() + "; " + CaptureLabels.longLabel(source)
                        + " is open.");
            }
            if (wait == 0 && started.isDone()) break;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (wait == 0 || !watched) {
            return AgentReply.text((started.isDone() ? "Launched " : "Launching ") + spec.describe() + ".");
        }
        return AgentReply.text((started.isDone() ? "Launched " : "Still launching ") + spec.describe() + ", but after "
                + wait + "s the window the bot looks at (" + CaptureLabels.longLabel(source) + ") is not open yet. "
                + "list_windows shows what is.");
    }

    /**
     * launch_game when the game runs in a private display: brought up there as ▶ Launch does, so the look tools
     * read it and the next run joins it rather than launching a second copy (2026-10-13). A display already up
     * answers that it is.
     */
    private static AgentReply launchPrivately(LaunchSpec spec, Launch launch, StudioServices services) {
        if (services.resourcesDir() == null) return AgentReply.refused(Frames.noProject());
        PrivateSession live = BackgroundLauncher.forProject(services.resourcesDir()).session();
        if (live != null) {
            return AgentReply.text(spec.describe() + " is running already in the private display "
                    + live.displayName() + ".");
        }
        CompletableFuture<String> done = new CompletableFuture<>();
        // The session is held only once the game's window is up: until then a second call would start a second one.
        if (!STARTING.compareAndSet(null, done)) {
            return AgentReply.text("A private display is still coming up for " + spec.describe()
                    + ": screenshot shows it once it is.");
        }
        done.whenComplete((message, why) -> STARTING.compareAndSet(done, null));
        FxCall.call(() -> {
            QuickLaunch.launchPrivately(spec, services, (ok, message) -> {
                if (ok) done.complete(message);
                else done.completeExceptionally(new IllegalStateException(message));
            });
            return null;
        });
        int wait = launch.seconds() == null ? 0 : Math.min(Math.max(launch.seconds(), 0), MAX_LAUNCH_WAIT);
        try {
            return AgentReply.text(done.get(Math.max(wait, 2), TimeUnit.SECONDS));
        } catch (ExecutionException refused) {
            return AgentReply.refused(spec.describe() + " could not be launched: " + refused.getCause().getMessage());
        } catch (TimeoutException starting) {
            return AgentReply.text("Bringing up a private display and launching " + spec.describe() + " in it. "
                    + (wait > 0 ? "It is not up after " + wait + "s; " : "")
                    + "screenshot shows it once it is.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentReply.text("Launching " + spec.describe() + " in a private display.");
        }
    }

    /** The private display launch_game is bringing up, until it is held or refused; null when none. */
    private static final AtomicReference<CompletableFuture<String>> STARTING = new AtomicReference<>();

    /** One launch at a time, on a daemon thread, so a slow one never holds the assistant's call. */
    private static final java.util.concurrent.Executor LAUNCHING = java.util.concurrent.Executors
            .newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sdk-launch-game");
                t.setDaemon(true);
                return t;
            });

    /**
     * The launch kinds the assistant may set: each names an entry a launcher already knows. Not {@code cli:} or
     * {@code exe:} — those run whatever they say on the next Run, and the assistant reads text off the game's
     * screen, so a line there could have it plant a command. The user sets those in the launch chooser.
     */
    static final Set<LaunchKind> ASSISTANT_KINDS = Set.of(LaunchKind.STEAM, LaunchKind.EPIC, LaunchKind.HEROIC,
            LaunchKind.FAUGUS, LaunchKind.EMULATOR_APP);

    /** An Android package: dot-separated Java-like names, at least two. */
    private static final java.util.regex.Pattern PACKAGE =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    /**
     * A launcher's own id for a game: no character a command line gives a meaning to, and a letter or digit
     * first, so it is never read as an option ({@code -…}).
     */
    private static final java.util.regex.Pattern PLAIN = java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /**
     * Why {@code spec} may not be kept as the assistant wrote it, or null: its token reaches a launcher's
     * command line or an {@code adb shell}, so only plain ids pass.
     */
    static String unsafe(LaunchSpec spec) {
        if (spec.kind() == LaunchKind.EMULATOR_APP) {
            String pkg = spec.emulatorPackage();
            String instance = spec.emulatorInstance();
            if (pkg == null || !PACKAGE.matcher(pkg).matches()) {
                return "\"" + pkg + "\" is not an app's package, like com.example.game.";
            }
            // The name may hold spaces ("Pixel 7"), so it is held to the names the scan found, not to a pattern.
            if (instance == null || new EmulatorInstanceScanner().instanceNames().stream()
                    .noneMatch(instance::equals)) {
                return "\"" + instance + "\" is not an emulator set up here; list_emulators names them.";
            }
            return null;
        }
        return PLAIN.matcher(spec.token()).matches() ? null
                : "\"" + spec.token() + "\" is not a " + spec.kind().displayName() + "'s id: letters, digits, '.', "
                        + "'_' and '-' only.";
    }

    private static int whole(String typed, int least) {
        try {
            int value = Integer.parseInt(typed);
            if (value < least) throw new IllegalArgumentException("it is " + least + " or more, not " + typed);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("it is a whole number, not \"" + typed + "\"");
        }
    }

    private static double unit(String typed) {
        try {
            double value = Double.parseDouble(typed);
            if (!(value >= 0 && value <= 1)) throw new IllegalArgumentException("it is between 0 and 1, not " + typed);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("it is a number between 0 and 1, not \"" + typed + "\"");
        }
    }

    private static boolean yes(String typed) {
        if (typed.equalsIgnoreCase("true")) return true;
        if (typed.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("it is true or false, not \"" + typed + "\"");
    }

    private static <E extends Enum<E>> E choice(E[] values, String typed, Function<E, String> id) {
        for (E value : values) {
            if (id.apply(value).equalsIgnoreCase(typed) || value.name().equalsIgnoreCase(typed)) return value;
        }
        throw new IllegalArgumentException("it is one of " + ids(values, id) + ", not \"" + typed + "\"");
    }

    private static <E> List<String> ids(E[] values, Function<E, String> id) {
        return Arrays.stream(values).map(id).map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }
}
