package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The settings an emulator's instances, or one instance, should have, one row per field:
 * {@code HostSettings.of(new HostSettings.Setting("ldplayer/LDPlayer-1", "screen", "1080x1920@320"))}.
 */
public record HostSettings(List<Setting> list) {

    public HostSettings {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostSettings of(Setting... settings) {
        return new HostSettings(settings == null ? List.of() : Arrays.asList(settings));
    }

    public static HostSettings none() {
        return new HostSettings(List.of());
    }

    /**
     * One wanted value.
     *
     * @param of    the emulator ({@code ldplayer}) or the instance ({@code ldplayer/LDPlayer-1}) it is set on
     * @param field the setting's id, {@code screen}; one a newer BotMaker knows is kept as it is
     * @param value the value as text, {@code 1080x1920@320}, {@code 4}, {@code on}
     */
    public record Setting(String of, String field, String value) {
        public Setting {
            of = of == null ? "" : of.strip();
            field = field == null ? "" : field.strip();
            value = value == null ? "" : value.strip();
        }
    }
}
