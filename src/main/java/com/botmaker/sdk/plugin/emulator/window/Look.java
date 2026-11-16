// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.Operation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * The pieces the Emulators and Accounts windows draw with (spec {@code 2026-11-14-emulators-look-design.md}): the
 * emulator badge, the state pill, the account avatar, the chip and the cap, styled by {@code emulators.css}. No
 * state: each call builds a node, and the pure parts decide what it says.
 */
public final class Look {

    /** The windows' stylesheet, added to each window's root. */
    public static final String SHEET = Objects.requireNonNull(Look.class.getResource("emulators.css"))
            .toExternalForm();

    /** The style class on each window's root, under which the sheet's own tokens are set. */
    public static final String WINDOW = "emulators-window";

    /** The style class of a Configurations cell whose instance overrides its emulator's value: drawn in the accent. */
    public static final String OWN = "em-own";

    /** The avatar hues the sheet names ({@code em-hue-0} …). */
    static final int HUES = 8;

    /** A pill's colour, named by its style class only; the pill's word is what it says. */
    public enum Tone {
        OK("ok"), MUTED("muted"), WARNING("warning"), ACCENT("accent"), DANGER("danger"), OUTLINED("outlined");

        private final String id;

        Tone(String id) {
            this.id = id;
        }

        String styleClass() {
            return "em-pill-" + id;
        }
    }

    /** What a pill says: its colour and its word. */
    public record Pill(Tone tone, String word) {}

    private Look() {}

    // ---- the pure parts ----

    /** The first letter or digit of {@code name}, upper-cased; {@code ?} when it has none. */
    public static String initial(String name) {
        if (name != null) {
            for (int i = 0; i < name.length(); i = name.offsetByCodePoints(i, 1)) {
                int point = name.codePointAt(i);
                if (Character.isLetterOrDigit(point)) {
                    return new String(Character.toChars(point)).toUpperCase(Locale.ROOT);
                }
            }
        }
        return "?";
    }

    /**
     * One of {@code size} hues for {@code key}, the same on every run and in every view (FNV-1a over its lower-case
     * UTF-8, so keys that differ by a letter land apart).
     */
    public static int hue(String key, int size) {
        if (size <= 0) throw new IllegalArgumentException("size " + size);
        int hash = 0x811c9dc5;
        for (byte one : (key == null ? "" : key.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)) {
            hash ^= one & 0xff;
            hash *= 0x01000193;
        }
        hash ^= hash >>> 16;
        return Math.floorMod(hash, size);
    }

    /** The two letters of {@code platform}'s badge. */
    public static String letters(PlatformId platform) {
        return switch (platform == null ? PlatformId.UNKNOWN : platform) {
            case MUMU -> "Mu";
            case LDPLAYER -> "LD";
            case MEMU -> "ME";
            case BLUESTACKS -> "BS";
            case AVD -> "AV";
            case WAYDROID -> "WD";
            case PHYSICAL -> "Ph";
            case UNKNOWN -> "?";
        };
    }

    /** An instance's pill: not read when it was not read, else running or stopped. */
    public static Pill pill(boolean running, boolean read) {
        if (!read) return new Pill(Tone.OUTLINED, "Not read");
        return running ? new Pill(Tone.OK, "Running") : new Pill(Tone.MUTED, "Stopped");
    }

    /** An operation's pill, in the state's own word. */
    public static Pill pill(Operation.State state) {
        Operation.State known = state == null ? Operation.State.UNKNOWN : state;
        Tone tone = switch (known) {
            case RUNNING -> Tone.ACCENT;
            case NEEDS_YOU -> Tone.WARNING;
            case DONE -> Tone.OK;
            case FAILED -> Tone.DANGER;
            case QUEUED, CANCELLED -> Tone.MUTED;
            case UNKNOWN -> Tone.OUTLINED;
        };
        return new Pill(tone, known.displayName());
    }

    // ---- the nodes ----

    /** {@code platform}'s badge, its name in the tooltip. */
    public static Label badge(PlatformId platform) {
        PlatformId known = platform == null ? PlatformId.UNKNOWN : platform;
        Label badge = new Label(letters(known));
        badge.getStyleClass().addAll("em-badge", "em-badge-" + known.id());
        badge.setTooltip(new Tooltip(known.displayName()));
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    /** A running operation's thin bar: filled to {@code progress} when its step says how far it is, else moving. */
    public static ProgressBar progress(double progress) {
        ProgressBar bar = new ProgressBar(progress > 0 ? progress : ProgressBar.INDETERMINATE_PROGRESS);
        bar.getStyleClass().add("em-progress");
        bar.setPrefWidth(90);
        bar.setMinWidth(90);
        return bar;
    }

    /** A pill: a dot and the word. */
    public static Label pill(Pill pill) {
        Region dot = new Region();
        dot.getStyleClass().add("em-dot");
        Label label = new Label(pill.word(), dot);
        label.getStyleClass().addAll("em-pill", pill.tone().styleClass());
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    /** {@code name}'s avatar: its initial on the hue {@code key} keeps. */
    public static Label avatar(String name, String key) {
        Label avatar = new Label(initial(name));
        avatar.getStyleClass().addAll("em-avatar", "em-hue-" + hue(key, HUES));
        avatar.setMinWidth(Region.USE_PREF_SIZE);
        return avatar;
    }

    /** A chip: a badge or an avatar, then a name. */
    public static Label chip(Node lead, String name) {
        Label chip = new Label(name, lead);
        chip.getStyleClass().add("em-chip");
        return chip;
    }

    /** A state dot alone, as a grid's column header shows it: the pill's tone, its word in the tooltip. */
    public static Region dot(Pill pill) {
        Region dot = new Region();
        dot.getStyleClass().addAll("em-state-dot", pill.tone().styleClass());
        Tooltip.install(dot, new Tooltip(pill.word()));
        return dot;
    }

    /** An inspector's heading: {@code lead} (a badge or an avatar), then the words. */
    public static Label heading(String words, Node lead) {
        Label title = Styles.on(new Label(words, lead), StyleClasses.DIALOG_SUBHEADING);
        title.setGraphicTextGap(8);
        title.setWrapText(true);
        return title;
    }

    /** A line of words that wraps. */
    public static Label line(String words) {
        Label label = new Label(words);
        label.setWrapText(true);
        return label;
    }

    /** A muted line of words that wraps. */
    public static Label muted(String words) {
        return Styles.on(line(words), StyleClasses.MUTED_TEXT);
    }

    /** A small upper-case heading over an inspector's section. */
    public static Label cap(String words) {
        Label cap = new Label(words.toUpperCase(Locale.ROOT));
        cap.getStyleClass().add("em-cap");
        return cap;
    }

    /**
     * {@code tabs} drawn as a segmented bar: one toggle per tab, kept in step with the pane's selection both ways.
     * The pane keeps its content, keys and {@code select}; the sheet hides its own strip ({@code em-tabs}).
     */
    public static HBox segmented(TabPane tabs) {
        tabs.getStyleClass().add("em-tabs");
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox();
        bar.getStyleClass().add("em-segmented");
        for (Tab tab : tabs.getTabs()) {
            ToggleButton one = new ToggleButton();
            // the window renames its tabs with their counts after each read
            one.textProperty().bind(tab.textProperty());
            one.setUserData(tab);
            one.setToggleGroup(group);
            one.setMinWidth(Region.USE_PREF_SIZE);
            one.setSelected(tabs.getSelectionModel().getSelectedItem() == tab);
            bar.getChildren().add(one);
        }
        if (!bar.getChildren().isEmpty()) {
            bar.getChildren().getFirst().getStyleClass().add("em-first");
            bar.getChildren().getLast().getStyleClass().add("em-last");
        }
        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                // a press on the picked one unpicks it: the pane still shows its tab
                if (was != null) was.setSelected(true);
                return;
            }
            tabs.getSelectionModel().select((Tab) now.getUserData());
        });
        tabs.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> group.getToggles().stream()
                .filter(one -> one.getUserData() == now).findFirst().ifPresent(one -> one.setSelected(true)));
        return bar;
    }
}
