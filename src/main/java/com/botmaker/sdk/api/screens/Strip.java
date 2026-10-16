package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Buttons of a {@link Screen} that scroll in a list, or shift in a column as icons come and go — {@code
 * Strip.of(Scroll.wheel(-5), List.of(Pictures.TAVERN_HOME, Pictures.ARENA_HOME))}. A panel of text that scrolls is a
 * strip of one member, the picture it is scrolled over.
 *
 * <p>Each member is the picture of one of the screen's {@link Button buttons}, a field of its own, so a body and a
 * way name it as any other ({@code HOME.tavern}); the strip says where it is looked for and how it is scrolled to
 * (the user's call, 2026-10-16). A member is looked for anywhere along the strip: the band its members' places span.
 * One not showing is scrolled to by the strip's {@link #scroll}, over a member that shows; a scroll after which no
 * member moved met the strip's end, and the scroll turns the other way once. No number places the strip: its
 * pictures do.
 *
 * <p>Its traits are its members' defaults: a strip that {@link #sometimes comes and goes} is one whose members each
 * may be absent. Its {@link #did} is the strip's own, scrolled when Discovery tried it.
 *
 * @param scroll       how it is scrolled
 * @param members      the pictures of its buttons, in the strip's order
 * @param did          what scrolling it did when tried; null when untried
 * @param purpose      what its members are for; null when not named yet
 * @param never        why its members are never done; null when they may be
 * @param comesAndGoes whether its members each show only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param when         when its members are there; null when not known
 */
public record Strip(Scroll scroll, List<ImageTemplate> members, Did did, Purpose purpose, String never,
                    boolean comesAndGoes, ImageTemplate lock, BooleanSupplier when) implements Input {

    public Strip {
        members = members == null ? List.of() : List.copyOf(members);
        comesAndGoes = comesAndGoes || when != null;
    }

    /** The buttons {@code members}, by their pictures, scrolled by {@code scroll}. */
    public static Strip of(Scroll scroll, List<ImageTemplate> members) {
        return new Strip(scroll, members, null, null, null, false, null, null);
    }

    @Override
    public Strip did(Did did) {
        return new Strip(scroll, members, did, purpose, never, comesAndGoes, lock, when);
    }

    @Override
    public Strip purpose(Purpose purpose) {
        return new Strip(scroll, members, did, purpose, never, comesAndGoes, lock, when);
    }

    @Override
    public Strip never(String why) {
        return new Strip(scroll, members, did, purpose, why, comesAndGoes, lock, when);
    }

    @Override
    public Strip sometimes() {
        return new Strip(scroll, members, did, purpose, never, true, lock, when);
    }

    @Override
    public Strip locked(ImageTemplate lock) {
        return new Strip(scroll, members, did, purpose, never, comesAndGoes, lock, when);
    }

    @Override
    public Strip when(BooleanSupplier condition) {
        return new Strip(scroll, members, did, purpose, never, comesAndGoes, lock, condition);
    }

    /** This strip, its members {@code members}. */
    public Strip members(List<ImageTemplate> members) {
        return new Strip(scroll, members, did, purpose, never, comesAndGoes, lock, when);
    }

    /** This strip, scrolled by {@code scroll}. */
    public Strip scroll(Scroll scroll) {
        return new Strip(scroll, members, did, purpose, never, comesAndGoes, lock, when);
    }

    /** Whether {@code picture} is one of its members, by its id. */
    public boolean has(ImageTemplate picture) {
        return picture != null && members.stream().anyMatch(m -> m.id().equals(picture.id()));
    }

    @Override
    public String label() {
        return "scroll " + (members.isEmpty() ? "?" : members.getFirst().id());
    }
}
