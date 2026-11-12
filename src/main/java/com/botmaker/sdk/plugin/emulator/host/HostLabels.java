package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Words kept beside instances: the user's tags, or the bots seen launching one. */
public record HostLabels(List<Label> list) {

    public HostLabels {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostLabels of(Label... labels) {
        return new HostLabels(labels == null ? List.of() : Arrays.asList(labels));
    }

    public static HostLabels none() {
        return new HostLabels(List.of());
    }

    /**
     * One word of one instance.
     *
     * @param instance the instance, {@code ldplayer/LDPlayer-1}
     * @param text     the tag, or the bot's name
     */
    public record Label(String instance, String text) {
        public Label {
            instance = instance == null ? "" : instance.strip();
            text = text == null ? "" : text.strip();
        }
    }
}
