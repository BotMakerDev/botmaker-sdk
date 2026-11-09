package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.params.Params;
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
 * member moved a quarter of its step met the strip's end, and the scroll turns the other way once. No number places
 * the strip: its pictures do. How fast it scrolls and settles is its scroll's, which Discovery measures.
 *
 * <p>Its traits are its members' defaults: a strip that {@link #occasional is occasional} is one whose members each
 * may be absent. Its {@link #isTried} is the strip's own, scrolled when Discovery tried it.
 *
 * @param scroll       how it is scrolled
 * @param members      the pictures of its buttons, in the strip's order
 * @param isTried      whether Discovery scrolled it
 * @param isLeaving    whether its members go to screens mapped already that they are no way into
 * @param isInfo       whether its members only tell
 * @param verdict      whether a run exploring on its own may press its members; {@link Verdict#NONE} when null
 * @param isOccasional whether its members each show only at times
 * @param locked       the picture proving it locked on this account; null when it is not
 * @param when         when its members are there; null when not known
 */
public record Strip(Scroll scroll, List<ImageTemplate> members, boolean isTried, boolean isLeaving, boolean isInfo,
                    Verdict verdict, boolean isOccasional, ImageTemplate locked, BooleanSupplier when) implements Input {

    public Strip {
        verdict = verdict == null ? Verdict.NONE : verdict;
        members = members == null ? List.of() : List.copyOf(members);
        isOccasional = isOccasional || when != null;
    }

    /** The buttons {@code members}, by their pictures, scrolled by {@code scroll}. */
    public static Strip of(Scroll scroll, List<ImageTemplate> members) {
        return new Strip(scroll, members, false, false, false, null, false, null, null);
    }

    @Override
    public Strip tried() {
        return new Strip(scroll, members, true, isLeaving, isInfo, verdict, isOccasional, locked, when);
    }

    @Override
    public Strip leaving() {
        return new Strip(scroll, members, isTried, true, isInfo, verdict, isOccasional, locked, when);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Strip leaves() {
        return leaving();
    }

    @Override
    public Strip info() {
        return new Strip(scroll, members, isTried, isLeaving, true, verdict, isOccasional, locked, when);
    }

    @Override
    public Strip never(String why) {
        return verdict(Verdict.never(why));
    }

    @Override
    public Strip free() {
        return verdict(Verdict.FREE);
    }

    @Override
    public <G, T> Strip spends(Params.Of<G, T> fact) {
        return verdict(new Verdict.Spends(fact));
    }

    @Override
    public Strip verdict(Verdict verdict) {
        return new Strip(scroll, members, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when);
    }

    @Override
    public Strip occasional() {
        return new Strip(scroll, members, isTried, isLeaving, isInfo, verdict, true, locked, when);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Strip sometimes() {
        return occasional();
    }

    @Override
    public Strip locked(ImageTemplate proof) {
        return new Strip(scroll, members, isTried, isLeaving, isInfo, verdict, isOccasional, proof, when);
    }

    @Override
    public Strip when(BooleanSupplier condition) {
        return new Strip(scroll, members, isTried, isLeaving, isInfo, verdict, isOccasional, locked, condition);
    }

    /** This strip, its members {@code members}. */
    public Strip members(List<ImageTemplate> members) {
        return new Strip(scroll, members, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when);
    }

    /** This strip, scrolled by {@code scroll}. */
    public Strip scroll(Scroll scroll) {
        return new Strip(scroll, members, isTried, isLeaving, isInfo, verdict, isOccasional, locked, when);
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
