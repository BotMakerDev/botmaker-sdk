package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Words on a {@link Screen} that are clicked — {@code TextButton.of(Caption.of(Texts.CLAIM, Zones.QUESTS))}: a button
 * known by what it says, not by a picture, in an app or a game translated into many languages. It is pressed at the
 * centre of the words where they are read, by its {@link #gesture} as a {@link Button} is (2026-10-22).
 *
 * @param caption      the words and the zone they are read in
 * @param isTried      whether Discovery tried it and it opened no screen
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param never        why it is never done; null when it may be
 * @param comesAndGoes whether it shows only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param when         when it is there, for one that comes and goes; null when not known
 * @param gesture      how it is pressed; {@link Gesture#CLICK} when null
 * @param hold         how long a {@link Gesture#HOLD} is held; {@link Button#HOLD_TIME} when null, none for any other
 */
public record TextButton(Caption caption, boolean isTried, boolean isLeaving, boolean isInfo, String never,
                         boolean comesAndGoes, ImageTemplate lock, BooleanSupplier when, Gesture gesture,
                         Duration hold) implements Input {

    public TextButton {
        comesAndGoes = comesAndGoes || when != null;
        gesture = gesture == null ? Gesture.CLICK : gesture;
        if (gesture != Gesture.HOLD) hold = null;
    }

    /** {@code caption}'s words clicked where they are read. */
    public static TextButton of(Caption caption) {
        return new TextButton(caption, false, false, false, null, false, null, null, null, null);
    }

    @Override
    public TextButton tried() {
        return new TextButton(caption, true, isLeaving, isInfo, never, comesAndGoes, lock, when, gesture, hold);
    }

    @Override
    public TextButton leaves() {
        return new TextButton(caption, isTried, true, isInfo, never, comesAndGoes, lock, when, gesture, hold);
    }

    @Override
    public TextButton info() {
        return new TextButton(caption, isTried, isLeaving, true, never, comesAndGoes, lock, when, gesture, hold);
    }

    @Override
    public TextButton never(String why) {
        return new TextButton(caption, isTried, isLeaving, isInfo, why, comesAndGoes, lock, when, gesture, hold);
    }

    @Override
    public TextButton sometimes() {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, true, lock, when, gesture, hold);
    }

    @Override
    public TextButton locked(ImageTemplate lock) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when, gesture, hold);
    }

    @Override
    public TextButton when(BooleanSupplier condition) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, comesAndGoes, lock, condition, gesture, hold);
    }

    /** This button, reading {@code caption} instead. */
    public TextButton caption(Caption caption) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when, gesture, hold);
    }

    /** This button, pressed by {@code gesture}: {@code .gesture(Gesture.DOUBLE)}. A hold keeps its time. */
    public TextButton gesture(Gesture gesture) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when, gesture, hold);
    }

    /** This button, held down {@code time} then let go: {@code .hold(Duration.ofSeconds(2))}. */
    public TextButton hold(Duration time) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, comesAndGoes, lock, when, Gesture.HOLD, time);
    }

    /** How long it is held when its gesture is a hold: its own time, else {@link Button#HOLD_TIME}. */
    public Duration holdTime() {
        return hold == null ? Button.HOLD_TIME : hold;
    }

    @Override
    public String label() {
        return caption == null ? "?" : "text " + caption.said();
    }
}
