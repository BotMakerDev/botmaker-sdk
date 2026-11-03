package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.internal.trace.SdkRun;

/**
 * The keys of the values the SDK's screen lines carry, which its report views count ({@code
 * plugin/run/SdkReportViews}); each value is a screen as the map shows it, or a number.
 */
public final class ScreenValues {

    /** The screen the game was last seen on; every line said while one is known carries it. */
    public static final String SCREEN = SdkRun.SCREEN;
    /** A walk's start: the screen it set out from, or {@link #NONE}. */
    public static final String FROM = "from";
    /** A walk's end: the screen it was going to. */
    public static final String TO = "to";
    /** The screen a walk was going to when the game showed none of the map's: beside {@code screen=none}. */
    public static final String TARGET = "target";
    /** How often a run came to a screen, on the run's last lines. */
    public static final String VISITS = "visits";
    /** How long a run stayed on a screen, in milliseconds, on the run's last lines. */
    public static final String MILLIS = "millis";
    /** A map check's counts, on its last line: the map's screens, those reached, its edges, those that arrived and
     * those never tried. */
    public static final String SCREENS = "screens";
    public static final String REACHED = "reached";
    public static final String EDGES = "edges";
    public static final String OK = "ok";
    public static final String NOT_TRIED = "not_tried";
    /** No screen of the map: where a walk set out from, or the game is, when none matched. */
    public static final String NONE = "none";

    private ScreenValues() {}
}
