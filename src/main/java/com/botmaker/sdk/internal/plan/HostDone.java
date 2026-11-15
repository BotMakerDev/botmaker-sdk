package com.botmaker.sdk.internal.plan;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * What the runs of this PC did ({@link SdkRunValue.Id#DONE}): one row per action of a game on an instance. Rows of
 * text, as the SDK's other values of this PC are, so one a newer BotMaker wrote is kept by an older one as it is.
 */
public record HostDone(List<Row> list) {

    public HostDone {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostDone of(Row... rows) {
        return new HostDone(rows == null ? List.of() : Arrays.asList(rows));
    }

    public static HostDone none() {
        return new HostDone(List.of());
    }

    /**
     * One action's last time.
     *
     * @param instance the emulator instance it was done on, {@code ""} off an emulator
     * @param game     the game it was done in ({@link DoneKey#game})
     * @param action   the action, {@code <Screen constant>.<field>}
     * @param at       when, as an ISO instant
     * @param held     whether its check held
     * @param fails    how many times in a row it has not, up to this one
     */
    public record Row(String instance, String game, String action, String at, boolean held, int fails) {
        public Row {
            instance = instance == null ? "" : instance.strip();
            game = game == null ? "" : game.strip();
            action = action == null ? "" : action.strip();
            at = at == null ? "" : at.strip();
        }

        /** Whether this row is of {@code instance}'s {@code game}. */
        public boolean of(String instance, String game) {
            return this.instance.equals(instance) && this.game.equals(game);
        }
    }
}
