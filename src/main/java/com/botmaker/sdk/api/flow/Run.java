package com.botmaker.sdk.api.flow;

/**
 * What a run of the bot does around its flows: how the game gets back to a known screen, and how it is made ready
 * for the bot (2026-10-11).
 *
 * <p>A bot can have several flows ({@code Flows.MAIN}, {@code Flows.DAILY}…), but one game: its home screen
 * and its settings do not change with the flow, so they are said once, here, and not in each flow. Which flow
 * runs is the bot's {@code @Param}, which {@code Bot.runFlow} reads off {@code Sdk.java} beside this.
 *
 * <p>Popups the game opens on its own are screens of the bot's map ({@code Setup.popup()}), each closed by its
 * way back before every vision call: no run method clears them (2026-10-16).
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.RUN)
 * public static Run run() {
 *     return Run.of()
 *             .home(Gamebot::goHome)
 *             .setup(Gamebot::setup);
 * }
 * }</pre>
 *
 * @param home  what gets the game back to a known screen: before a step that {@link Flow.Step#goHome() goes
 *              home}, at start-up and after anything unexpected; {@code null} walks to the root screen of the bot's
 *              map ({@code Navigate.to} the first screen reached from none), or does nothing when it has no map.
 *              It is the last resort: a step of an activity that gets stuck first walks back to the screen the
 *              activity's {@code Navigate.to} reached and runs it once more
 * @param setup what makes the game ready for this bot, once it is started: its settings, such as the ones a
 *              user chose in the bot's {@code @Param}s. Run when the bot's {@code doSetup} parameter is ticked;
 *              {@code null} for nothing
 * @param notes what automating the game taught that holds across its screens, in plain lines; {@code null} for none.
 *              A run ignores it: the assistant reads it (read_notes) and keeps it (write_notes)
 */
public record Run(Runnable home, Runnable setup, String notes) {

    public Run {
        notes = notes == null || notes.isBlank() ? null : notes;
    }

    /** No home and no setup: what a project with nothing drawn has. */
    public static final Run NONE = new Run(null, null, null);

    /** A run with no {@link #home} or {@link #setup} yet. */
    public static Run of() {
        return NONE;
    }

    /** This run, getting the game back to a known screen with {@code home} — {@code Gamebot::goHome}. */
    public Run home(Runnable home) {
        return new Run(home, setup, notes);
    }

    /**
     * This run, with {@code notes} on the game as a whole: what holds across its screens (hotkeys, currencies, what
     * never to press). A screen's own notes are in its {@code Setup}. Blank for none.
     */
    public Run notes(String notes) {
        return new Run(home, setup, notes);
    }

    /**
     * This run, making the game ready with {@code setup} — {@code Gamebot::setup} — once the bot has started the
     * game, before {@link #home}, when the bot's {@code doSetup} parameter is ticked. The bot writes it: open the
     * game's settings, set what the bot needs, and call {@code Bot.stop("…")} when the user has to change something
     * by hand.
     */
    public Run setup(Runnable setup) {
        return new Run(home, setup, notes);
    }
}
