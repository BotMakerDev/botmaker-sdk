package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;

import java.util.Optional;

/**
 * The bot's own constants by name — what an editor that cannot load the bot's classes holds instead of
 * {@code Screens.HOME}, {@code Plans.DAILY} or {@code States.Blocks.MAXED} (2026-10-10; in {@code internal.bot}
 * since flows were deleted, 2026-11-09).
 *
 * <p>The host reads each such constant inside another value as a reference of its name
 * ({@code ManagedValue.referredToAs}) and writes a value equal to one back as that constant. A bot's own constants
 * never meet one at run time.
 */
public final class BotConstants {

    private BotConstants() {}

    /**
     * The bot's state {@code name} of its {@code States} enum {@code group}, {@code States.Blocks.MAXED}, as an editor
     * holds it: never pressed when its group is {@link ButtonState#BLOCKS}. A run never meets one: there the bot's
     * constant is the state.
     */
    public record StateConstant(String group, String name) implements ButtonState {
        @Override
        public boolean blocks() {
            return BLOCKS.equals(group);
        }

        @Override
        public String id() {
            return group + "." + name;
        }

        @Override
        public String toString() {
            return id();
        }
    }

    /** A whole enum of the bot's {@code States}, {@code States.Blocks.class}, by its name. */
    public record StateGroup(String name) {}

    /** The constant {@code name} of the bot's {@code States} enum {@code group}. */
    public static ButtonState state(String group, String name) {
        return new StateConstant(group == null ? "" : group, name == null ? "" : name);
    }

    /** The bot's {@code States} enum {@code name}. */
    public static StateGroup stateGroup(String name) {
        return new StateGroup(name == null ? "" : name);
    }

    /** What starts a reference's name: no constant's name can hold it. */
    private static final String REFERENCE = "→";

    /**
     * The bot's plan {@code name}, {@code Plans.DAILY}, as an editor holds a reference to it: one tier of one goal whose
     * check is the name alone, equal to another only when it names the same constant. A run never meets one: there
     * the bot's constant is the plan.
     *
     * @throws IllegalArgumentException for a blank name, which names no constant
     */
    public static Plan plan(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A plan reference needs a name");
        return Plan.of(Plan.tier(Goal.of(new PlanReference(name))));
    }

    /** The check of {@link #plan}'s one goal: a name, which nothing can answer. */
    private record PlanReference(String name) implements Condition {
        @Override
        public Answer answer() {
            return Answer.UNKNOWN;
        }
    }

    /**
     * The bot's goal {@code name}, {@code Goals.ENOUGH_GOLD}, as an editor holds a reference to it — inside an action's
     * {@code .serves(…)} or a plan's tier: a goal whose check is the name alone, equal to another only when it names
     * the same constant. A run never meets one: there the bot's constant is the goal.
     *
     * @throws IllegalArgumentException for a blank name, which names no constant
     */
    public static Goal goal(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A goal reference needs a name");
        return Goal.of(new GoalReference(name));
    }

    /** The constant {@code goal} names when it is a {@link #goal} reference; empty otherwise. */
    public static Optional<String> goalName(Goal goal) {
        return goal instanceof Goal.Of(GoalReference(String name)) ? Optional.of(name) : Optional.empty();
    }

    /** The check of a {@link #goal} reference: a name, which nothing can answer. */
    private record GoalReference(String name) implements Condition {
        @Override
        public Answer answer() {
            return Answer.UNKNOWN;
        }
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
}
