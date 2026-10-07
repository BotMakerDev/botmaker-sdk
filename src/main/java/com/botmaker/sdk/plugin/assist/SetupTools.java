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
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.session.display.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstanceScanner;
import com.botmaker.shared.emulator.Platforms.PlatformStatus;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Where the bot looks and how it runs: its capture source, its {@code Sdk.settings()}, the emulator it
 * watches, and what this machine launches for it. The source and settings are the bot's Java, written on the FX
 * thread as their windows write them; the launch target is this machine's run property. Nothing here starts a
 * game, an emulator or a run.
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
                (s, v) -> with(s, BotSettings.clicks(whole(v, 0), s.clicks().notFoundDelay(), s.clicks().randomize()))),
        NOT_FOUND_DELAY("not_found_delay", "Pause after a miss, ms",
                s -> String.valueOf(s.clicks().notFoundDelay()),
                (s, v) -> with(s, BotSettings.clicks(s.clicks().foundDelay(), whole(v, 0), s.clicks().randomize()))),
        RANDOMIZE_CLICKS("randomize_clicks", "Click a random point inside a match",
                s -> String.valueOf(s.clicks().randomize()),
                (s, v) -> with(s, BotSettings.clicks(s.clicks().foundDelay(), s.clicks().notFoundDelay(), yes(v)))),
        CONFIDENCE("confidence", "How sure a picture match must be, 0 to 1",
                s -> String.valueOf(s.vision().confidence()),
                (s, v) -> with(s, BotSettings.vision(unit(v), s.vision().compareMargin()))),
        COMPARE_MARGIN("compare_margin", "How far ahead the best of several pictures must be, 0 to 1",
                s -> String.valueOf(s.vision().compareMargin()),
                (s, v) -> with(s, BotSettings.vision(s.vision().confidence(), unit(v)))),
        RUN_THE_GAME_IN("run_the_game_in", "Where the game runs",
                s -> s.where().id(),
                (s, v) -> with(s, BotSettings.runIn(choice(BotSettings.Where.values(), v, BotSettings.Where::id),
                        s.takeOver(), s.runIn().displayBackend(), s.runIn().inputBackend()))),
        TAKE_OVER("take_over", "On my desktop, take over the mouse and keyboard (some games ignore window events)",
                s -> String.valueOf(s.takeOver()),
                (s, v) -> s.takeOver(yes(v))),
        DISPLAY_BACKEND("display_backend", "Which private display hosts the game",
                s -> s.runIn().displayBackend().id(),
                (s, v) -> with(s, BotSettings.runIn(s.where(), s.takeOver(), choice(
                        BotSettings.DisplayBackend.values(), v, BotSettings.DisplayBackend::id),
                        s.runIn().inputBackend()))),
        INPUT_BACKEND("input_backend", "Which Linux backend delivers a take-over",
                s -> s.runIn().inputBackend().id(),
                (s, v) -> with(s, BotSettings.runIn(s.where(), s.takeOver(), s.runIn().displayBackend(),
                        choice(BotSettings.InputBackend.values(), v, BotSettings.InputBackend::id)))),
        MAX_RETRY_ATTEMPTS("max_retry_attempts", "No-progress checks before the bot is stuck, 1 or more",
                s -> String.valueOf(s.maxRetryAttempts()),
                (s, v) -> rebuilt(s, whole(v, 1)));

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

    record WindowTitle(@Describe("part of the window's title, as it appears in the taskbar") String window) {
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

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("get_capture_source")
                    .describedAs("Where the bot looks and clicks: Sdk.captureSource(), in words")
                    .takesNothing().handledBy(SetupTools::getCaptureSource),
            AssistantTool.named("set_capture_source")
                    .describedAs("Points the bot at a window, so it looks at and clicks in that window")
                    .takes(WindowTitle.class).handledBy(SetupTools::setCaptureSource),
            AssistantTool.named("list_windows")
                    .describedAs("The windows open on the desktop now, each with its size, for set_capture_source")
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
                    .describedAs("Sets what this computer launches when the bot starts. Kept on this computer, "
                            + "not in the bot's files. Starts nothing")
                    .takes(Target.class).handledBy(SetupTools::setLaunchTarget));

    private SetupTools() {}

    static AgentReply getCaptureSource(AssistantTool.None none, AgentContext context) {
        CaptureSource source = FxCall.call(() -> CaptureValue.current(context.services()));
        return AgentReply.text(source == null ? "Sdk.captureSource() names nothing BotMaker can read: the bot looks "
                + "at the whole desktop, or at what the code says." : CaptureLabels.longLabel(source) + ".");
    }

    static AgentReply setCaptureSource(WindowTitle title, AgentContext context) {
        if (title.window() == null || title.window().isBlank()) return AgentReply.refused("Name a window.");
        String refused = FxCall.call(() -> CaptureValue.CAPTURE.write(context.services(),
                CaptureSource.window(title.window().trim())));
        return refused == null ? AgentReply.text("The bot now looks at the window \"" + title.window().trim() + "\".")
                : AgentReply.refused(refused);
    }

    static AgentReply listWindows(AssistantTool.None none, AgentContext context) {
        List<GenericWindow> found;
        try {
            found = NativeControllerFactory.get().getAllWindows();
        } catch (RuntimeException | LinkageError e) {
            return AgentReply.refused("The open windows cannot be listed here: " + e.getMessage());
        }
        // One line per title: window(title) matches the first window of that title, so a second is the same choice.
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (GenericWindow window : found) {
            String title = window.getTitle();
            if (title == null || title.isBlank() || GamescopeHost.isHost(window) || !seen.add(title)) continue;
            Rectangle r = window.getRect();
            lines.add("\"" + title + "\"" + (r == null ? "" : " " + r.width + "×" + r.height + " at " + r.x + "," + r.y));
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
                return AgentReply.refused("Sdk.settings() is not a single `return BotSettings.of(…);`, so it is left "
                        + "as you wrote it.");
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
            lines.add("\"" + instance.name() + "\" — " + instance.brand() + ", "
                    + (instance.reachable() ? "running" : "stopped"));
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
            String why = CaptureValue.CAPTURE.write(context.services(), CaptureSource.emulator(known.get()));
            if (why == null && target != null) LaunchTargetValue.set(context.services(), target);
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
        LaunchTargetValue.set(context.services(), spec.spec());
        return AgentReply.text("This computer now launches " + spec.describe() + " when the bot starts.");
    }

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

    private static BotSettings with(BotSettings s, BotSettings.Clicks clicks) {
        return BotSettings.of(clicks, s.vision(), s.runIn(), s.maxRetryAttempts(), debug(s));
    }

    private static BotSettings with(BotSettings s, BotSettings.Vision vision) {
        return BotSettings.of(s.clicks(), vision, s.runIn(), s.maxRetryAttempts(), debug(s));
    }

    private static BotSettings with(BotSettings s, BotSettings.RunIn runIn) {
        return BotSettings.of(s.clicks(), s.vision(), runIn, s.maxRetryAttempts(), debug(s));
    }

    private static BotSettings rebuilt(BotSettings s, int maxRetryAttempts) {
        return BotSettings.of(s.clicks(), s.vision(), s.runIn(), maxRetryAttempts, debug(s));
    }

    /** The deprecated part is carried as it is: a value read is written back whole. */
    @SuppressWarnings("deprecation")
    private static boolean debug(BotSettings s) {
        return s.debug();
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
