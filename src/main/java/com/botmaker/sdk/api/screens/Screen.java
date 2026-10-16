package com.botmaker.sdk.api.screens;

/**
 * One screen of the game, a class nested in the bot's {@code Screens} with one constant: its inputs are its fields,
 * and the rest of it is its {@link #setup}.
 *
 * <pre>{@code
 * public static final class Market extends Screen {
 *     public final Button beerBuy = Button.of(Pictures.BEER_BUY_TAVERN).purpose(Purpose.TASK)
 *             .looks(List.of(Look.of(Pictures.BEER_BUY_OFF_TAVERN, State.DISABLED)));
 *     public final Button close = Button.of(Pictures.CLOSE_MARKET);
 *
 *     @Override
 *     public Setup setup() {
 *         return Setup.named("Market").markers(List.of(Pictures.MARKET_TITLE_TAVERN)).from(HOME.market)
 *                 .back(MARKET.close);
 *     }
 * }
 *
 * public static final Market MARKET = new Market();
 * }</pre>
 *
 * <p>A body names an input of that screen only, {@code Screens.MARKET.beerBuy}, so it never names a picture the map
 * does not hold. The fields are {@code public final}, one expression each, and a class has no constructor body (the
 * user's call, 2026-10-16).
 *
 * <p>The map reads the setup once every constant is set, so it names any screen's inputs ({@code HOME.market}),
 * whichever is declared first. Its own are named through its constant too ({@code MARKET.close}): a bare
 * {@code close} is not read as the map's.
 */
public abstract class Screen {

    protected Screen() {}

    /** Everything about the screen but its inputs, as one chain: {@code Setup.named("Market").markers(…)…}. */
    public abstract Setup setup();
}
