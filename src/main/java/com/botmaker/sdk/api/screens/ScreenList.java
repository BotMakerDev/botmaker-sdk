package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;

/**
 * A list of a {@link GameScreen}'s buttons that scrolls, or a column whose slots shift as icons come and go, named by
 * its members — {@code ScreenList.of(-5, List.of(Pictures.TAVERN_HOME, Pictures.ARENA_HOME, Pictures.CAMPAIGN_HOME))}.
 *
 * <p>Each member is the picture of one of the screen's inputs. A member is looked for anywhere along the list: the
 * band its members' places span. One not showing is scrolled to with the wheel over a member that shows, by
 * {@code notches}; a scroll after which no member moved met the list's end, and the scroll turns the other way once.
 * No number places the list: its pictures do (the user's call, 2026-10-16, after Home's left column scrolled over a
 * moving fight).
 *
 * @param notches how far each scroll goes: negative down or toward you, positive up or away
 * @param members the pictures of the inputs in the list, in the list's order
 */
public record ScreenList(int notches, List<ImageTemplate> members) {

    /** The most scrolls each way looking for a member: a list longer than that is a screen's own. */
    public static final int SCROLLS = 8;

    public ScreenList {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static ScreenList of(int notches, List<ImageTemplate> members) {
        return new ScreenList(notches, members);
    }

    /** Whether {@code picture} is one of its members, by its id. */
    public boolean has(ImageTemplate picture) {
        return picture != null && members.stream().anyMatch(m -> m.id().equals(picture.id()));
    }
}
