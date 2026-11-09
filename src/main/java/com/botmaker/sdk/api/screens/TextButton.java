package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Words on a {@link Screen} that are clicked — {@code TextButton.of(Caption.of(Texts.CLAIM, Area.of(Pictures.QUEST_ROW, 3, 0, 1.5, 1)))}: a button
 * known by what it says, not by a picture, in an app or a game translated into many languages. It is pressed at the
 * centre of the words where they are read, by its {@link #gesture} as a {@link Button} is (2026-10-22).
 *
 * <p>Words that show only on their screen tell it: {@link #marking}; words that never show there tell it apart from a
 * screen alike: {@link #absent} (2026-11-02).
 *
 * @param caption      the words and the area they are read in, beside a picture
 * @param isTried      whether Discovery tried it and it opened no screen
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param never        why it is never done; null when it may be
 * @param isOccasional whether it shows only at times
 * @param locked       the picture proving it locked on this account; null when it is not
 * @param when         when it is there, for one that comes and goes; null when not known
 * @param gesture      how it is pressed; {@link Gesture#CLICK} when null
 * @param hold         how long a {@link Gesture#HOLD} is held; {@link Button#HOLD_TIME} when null, none for any other
 * @param isMarking    whether its words tell its screen: read there while the game shows it
 * @param isAbsent     whether its words are never read while the game shows its screen
 */
public record TextButton(Caption caption, boolean isTried, boolean isLeaving, boolean isInfo, String never,
                         boolean isOccasional, ImageTemplate locked, BooleanSupplier when, Gesture gesture,
                         Duration hold, boolean isMarking, boolean isAbsent) implements Input {

    public TextButton {
        isOccasional = isOccasional || when != null;
        gesture = gesture == null ? Gesture.CLICK : gesture;
        if (gesture != Gesture.HOLD) hold = null;
        if (isAbsent) isMarking = false;
    }

    /** {@code caption}'s words clicked where they are read. */
    public static TextButton of(Caption caption) {
        return new TextButton(caption, false, false, false, null, false, null, null, null, null, false, false);
    }

    @Override
    public TextButton tried() {
        return new TextButton(caption, true, isLeaving, isInfo, never, isOccasional, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    @Override
    public TextButton leaving() {
        return new TextButton(caption, isTried, true, isInfo, never, isOccasional, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    @Override
    @Deprecated(forRemoval = true)
    public TextButton leaves() {
        return leaving();
    }

    @Override
    public TextButton info() {
        return new TextButton(caption, isTried, isLeaving, true, never, isOccasional, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    @Override
    public TextButton never(String why) {
        return new TextButton(caption, isTried, isLeaving, isInfo, why, isOccasional, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    @Override
    public TextButton occasional() {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, true, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    @Override
    @Deprecated(forRemoval = true)
    public TextButton sometimes() {
        return occasional();
    }

    @Override
    public TextButton locked(ImageTemplate proof) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, proof, when, gesture, hold,
                isMarking, isAbsent);
    }

    @Override
    public TextButton when(BooleanSupplier condition) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, condition, gesture,
                hold, isMarking, isAbsent);
    }

    /** This button, reading {@code caption} instead. */
    public TextButton caption(Caption caption) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    /** This button, pressed by {@code gesture}: {@code .gesture(Gesture.DOUBLE)}. A hold keeps its time. */
    public TextButton gesture(Gesture gesture) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, when, gesture, hold,
                isMarking, isAbsent);
    }

    /** This button, held down {@code time} then let go: {@code .hold(Duration.ofSeconds(2))}. */
    public TextButton hold(Duration time) {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, when, Gesture.HOLD,
                time, isMarking, isAbsent);
    }

    /** This button, its words telling its screen: read there while the game shows it. */
    @Link
    public TextButton marking() {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, when, gesture, hold,
                true, false);
    }

    /** This button, its words telling its screen apart from one alike by never being read there. */
    @Link
    public TextButton absent() {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, when, gesture, hold,
                false, true);
    }

    /** This button, its words telling nothing of its screen: neither {@link #marking} nor {@link #absent}. */
    public TextButton unmarked() {
        return new TextButton(caption, isTried, isLeaving, isInfo, never, isOccasional, locked, when, gesture, hold,
                false, false);
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
