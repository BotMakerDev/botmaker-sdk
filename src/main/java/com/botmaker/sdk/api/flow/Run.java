package com.botmaker.sdk.api.flow;

/**
 * What a run of the bot does around its flows: which flow runs when nobody picks one, how the game gets back
 * to a known screen, and how it is made ready for the bot (2026-10-11).
 *
 * <p>A bot can have several flows ({@code Flows.MAIN}, {@code Flows.DAILY}…), but one game: its home screen
 * and its settings do not change with the flow, so they are said once, here, and not in each flow.
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.RUN)
 * public static Run run() {
 *     return Run.of(Flows.MAIN)
 *             .home(Gamebot::goHome)
 *             .setup(Gamebot::setup);
 * }
 * }</pre>
 *
 * <p>The flow is a constant of the bot's {@code Flows} class, so naming one that does not exist is a compile
 * error, and reordering the flows changes nothing.
 *
 * @param flow  the flow a run walks when nobody picks another; {@link Flow#NONE} for nothing
 * @param home  what gets the game back to a known screen: before a step that {@link Flow.Step#goHome() goes
 *              home}, at start-up and after anything unexpected; {@code null} for nothing
 * @param setup what makes the game ready for this bot, once it is started: its settings, such as the ones a
 *              user chose in the bot's {@code @Param}s. Run once per computer ({@code Setup}); {@code null} for
 *              nothing
 */
public record Run(Flow flow, Runnable home, Runnable setup) {

    /** Nothing to run, no home and no setup: what a project with nothing drawn has. */
    public static final Run NONE = new Run(Flow.NONE, null, null);

    public Run {
        flow = flow == null ? Flow.NONE : flow;
    }

    /** A run of {@code flow}, with no {@link #home} or {@link #setup} yet. */
    public static Run of(Flow flow) {
        return new Run(flow, null, null);
    }

    /** This run, getting the game back to a known screen with {@code home} — {@code Gamebot::goHome}. */
    public Run home(Runnable home) {
        return new Run(flow, home, setup);
    }

    /**
     * This run, making the game ready with {@code setup} — {@code Gamebot::setup} — once the bot has started the
     * game, before {@link #home}; once per computer, and again when a {@code Setup.CATEGORY} parameter changes. The
     * bot writes it: open the game's settings, set what the bot needs, and call {@code Setup.fail("…")} when the
     * user has to change something by hand.
     */
    public Run setup(Runnable setup) {
        return new Run(flow, home, setup);
    }
}
