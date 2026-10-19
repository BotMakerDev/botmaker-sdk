package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;

import java.util.List;

/**
 * The bot's own activities and outcomes by name — what an editor that cannot load the bot's classes holds
 * instead of {@code Activities.Collect.class} and {@code Activities.Collect.WON} (2026-10-10).
 *
 * <p>The host reads each enum nested in the bot's {@code Activities} class as {@link #activity} of its name
 * ({@code ManagedOpenSet.GroupStep.groupedAs}) and each of its constants as {@link #outcome} of the two names
 * ({@code ManagedOpenSet.Steps.ofEnumGroups}), and writes a value equal to one back as that class literal or
 * constant. An outcome carries its enum's name, so {@code Battle.NEXT} and {@code Collect.NEXT} stay two outcomes. A bot's own
 * constants never meet one at run time, where a flow holds the enums'.
 */
public final class BotConstants {

    private BotConstants() {}

    /** The bot's constant {@code name} of the activity enum {@code activity}, as an editor holds it. */
    public record OutcomeConstant(String activityName, String name) implements Outcome {
        @Override
        public Activity activity() {
            return new Activity(activityName);
        }

        @Override
        public String toString() {
            return activityName + "." + name;
        }
    }

    /** The constant {@code name} of the activity enum {@code activity}. */
    public static Outcome outcome(String activity, String name) {
        return new OutcomeConstant(activity == null ? "" : activity, name == null ? "" : name);
    }

    /** The activity enum {@code name}: {@link Activity#NONE} for none. */
    public static Activity activity(String name) {
        return name == null || name.isBlank() ? Activity.NONE : new Activity(name);
    }

    /** What starts a reference's name in {@link #flow}: no enum's name can hold it, so no activity is one. */
    private static final String REFERENCE = "→";

    /**
     * The bot's flow {@code name}, {@code Flows.DAILY}, as an editor holds a reference to it: a flow with no card
     * that equals another only when it names the same constant (2026-10-12, {@code ManagedValue.referredToAs}).
     * The host reads a reference inside another value as this and writes this back as the reference, so a
     * sub-flow card keeps naming its flow while that flow is empty or equal to another. What the flow holds is
     * read from the constant itself ({@code FlowValue.FLOWS}).
     *
     * @throws IllegalArgumentException for a blank name, which names no constant
     */
    public static Flow flow(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A flow reference needs a name");
        return new Flow(List.of(), List.of(), new Activity(REFERENCE + name), Flow.Limits.DEFAULT);
    }

    /**
     * The bot's screen {@code name}, {@code Screens.HOME}, as an editor holds a reference to it: a screen of no
     * class of the bot's, equal to another only when it names the same constant ({@code ManagedValue.referredToAs}).
     * A run never meets one: there the bot's constant is the screen.
     *
     * @throws IllegalArgumentException for a blank name, which names no constant
     */
    public static Screen screen(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A screen reference needs a name");
        return new ScreenReference(name);
    }

    /** A screen an editor holds by its constant's name: its setup is that name and nothing else. */
    private static final class ScreenReference extends Screen {

        private final String name;

        ScreenReference(String name) {
            this.name = name;
        }

        @Override
        public Setup setup() {
            return Setup.named(REFERENCE + name);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ScreenReference that && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * The input {@code field} of the bot's screen {@code screen}, {@code Screens.MARKET.beerBuy}, as an editor holds a
     * reference to it: a button of no picture of the bot's, equal to another only when it names the same field
     * ({@code ManagedValue.fields}). A run never meets one: there the screen's field is the input.
     *
     * @throws IllegalArgumentException for a blank name, which names no field
     */
    public static Button input(String screen, String field) {
        if (screen == null || screen.isBlank() || field == null || field.isBlank()) {
            throw new IllegalArgumentException("An input reference needs its screen and its field");
        }
        return Button.of(new ImageTemplate(REFERENCE + screen + REFERENCE + field));
    }

    /** The screen and the field an {@link #input} reference names: {@code MARKET} and {@code beerBuy}. */
    public record InputName(String screen, String field) {
        @Override
        public String toString() {
            return screen + "." + field;
        }
    }

    /** What {@code input} refers to when it is an {@link #input} reference; else null. */
    public static InputName inputName(Input input) {
        if (!(input instanceof Button button) || button.picture() == null) return null;
        String path = button.picture().filePath();
        if (!path.startsWith(REFERENCE) || !button.equals(Button.of(button.picture()))) return null;
        String[] parts = path.substring(REFERENCE.length()).split(REFERENCE, -1);
        return parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty() ? new InputName(parts[0], parts[1])
                : null;
    }

    /**
     * The bot's tag {@code name}, {@code Tags.UI}, as an editor holds a reference to it: a tag called by the reference
     * mark and the constant's name ({@code ManagedValue.referredToAs}). Like every tag it equals another of the same
     * name in any case, so {@code Tags.UI} and a {@code Tags.ui} beside it read as one. A run never meets one: there
     * the picture holds the tag.
     *
     * @throws IllegalArgumentException for a blank name, which names no constant
     */
    public static PictureTag tag(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A tag reference needs a name");
        return PictureTag.named(REFERENCE + name);
    }

    /** The constant {@code tag} refers to when it is a {@link #tag} reference, {@code UI}; else null. */
    public static String tagName(PictureTag tag) {
        return tag != null && tag.name().startsWith(REFERENCE) ? tag.name().substring(REFERENCE.length()) : null;
    }

    /** The constant {@code screen} refers to when it is a {@link #screen} reference, {@code HOME}; else null. */
    public static String screenName(Screen screen) {
        return screen instanceof ScreenReference reference ? reference.name : null;
    }

    /** The constant {@code flow} refers to when it is a {@link #flow} reference, {@code DAILY}; else null. */
    public static String flowName(Flow flow) {
        if (flow == null || !flow.steps().isEmpty() || !flow.edges().isEmpty()
                || !(flow.start() instanceof Activity start) || !start.name().startsWith(REFERENCE)) {
            return null;
        }
        return start.name().substring(REFERENCE.length());
    }
}
