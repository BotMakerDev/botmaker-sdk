package com.botmaker.sdk.api.flow.activities;

import com.botmaker.sdk.api.bot.Activity;

/** A second test activity, whose tick the test flips to exercise the {@code DISABLED} wire. */
public class Selling extends Activity<Selling.Outcome> {

    public enum Outcome { NEXT }

    /** The configured tick, standing in for what {@code Wire.enabled} reads in a real bot. */
    public static boolean on = true;

    @Override
    public boolean isEnabled() {
        return on;
    }

    @Override
    public Outcome run() {
        return Outcome.NEXT;
    }
}
