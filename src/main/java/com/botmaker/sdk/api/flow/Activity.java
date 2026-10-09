package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.internal.flow.NoActivity;

/**
 * One activity of the bot's flow — a constant of your bot's {@code Activities} enum (2026-10-10), the file
 * BotMaker keeps beside {@code Sdk.java}:
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.ACTIVITIES)
 * public enum Activities implements Activity {
 *     COLLECT, BATTLE
 * }
 *
 * ActivitySwitch.disable(Activities.COLLECT);
 * }</pre>
 *
 * <p>The flow's steps, wires, presets and start all name the constant, so renaming a card on the Activity Flow
 * canvas renames the constant and every use of it by binding, and a typo is a compile error.
 *
 * <p><b>The name is the activity.</b> What the canvas draws on the card and the run's trace prints is the name
 * read as words ({@link Labelled#label()}) — {@code COLLECT} is "Collect" — so the constant holds nothing else.
 * It used to hold its label, {@code Activity.named("Collect")}.
 */
public interface Activity extends Labelled {

    /** No activity: the start of a flow with nothing in it. Labelled blank. */
    Activity NONE = NoActivity.NONE;
}
