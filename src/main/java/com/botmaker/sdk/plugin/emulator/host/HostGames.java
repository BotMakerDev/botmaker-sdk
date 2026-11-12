package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** The games an emulator's instances, or one instance, should have installed. */
public record HostGames(List<Game> list) {

    public HostGames {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostGames of(Game... games) {
        return new HostGames(games == null ? List.of() : Arrays.asList(games));
    }

    public static HostGames none() {
        return new HostGames(List.of());
    }

    /**
     * One game.
     *
     * @param of          the emulator ({@code ldplayer}) or the instance ({@code ldplayer/LDPlayer-1})
     * @param packageName the app's package
     * @param label       its name as the window shows it
     * @param removed     an instance's row saying it should not have a game its emulator lists
     */
    public record Game(String of, String packageName, String label, boolean removed) {
        public Game {
            of = of == null ? "" : of.strip();
            packageName = packageName == null ? "" : packageName.strip();
            label = label == null ? "" : label.strip();
        }
    }
}
