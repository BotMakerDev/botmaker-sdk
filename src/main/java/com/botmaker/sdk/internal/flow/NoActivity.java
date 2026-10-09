package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.flow.Activity;

/**
 * {@link Activity#NONE}, the start of a flow with nothing in it: labelled blank, which is what the canvas and a
 * run read as "no activity". Safe to touch before {@code Activity}, for the reason {@link BuiltinOutcome}
 * gives.
 */
public enum NoActivity implements Activity {
    NONE;

    @Override
    public String label() {
        return "";
    }
}
