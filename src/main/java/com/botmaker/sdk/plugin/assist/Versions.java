package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.text.OcrLanguage;
import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ShadeFit;
import com.botmaker.sdk.internal.screens.ShadeFit.Fit;
import com.botmaker.sdk.internal.vision.FrameChange;
import com.botmaker.sdk.internal.vision.FrameChange.Motion;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.CutShape;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The versions of the bot's buttons Discovery deduces with no AI (2026-11-02): each button a frame shows is fitted
 * against its cut, and a fit off the cut's colours is a version of it, kept per button and frame. The same fit on most
 * of a frame's buttons is a shade over the whole screen (a tutorial's, a popup's), never a version. A fit seen alike on
 * two buttons or more is proposed as a shade, which {@code edit_map learn_shade} writes into {@code Sdk.shades()}; a
 * fit on one button only is said. A part the button shows and its cut lacks (a lock, a badge) is said with its place.
 * <p>
 * What a version means is learned too: try_input pressing one keeps what came of it (nothing: {@code Disabled}; a
 * toast: its words, the author asked for the bot's state; an answer: it does not block), a version shown no more when
 * its button is seen again is {@code Disabled}, and one still shown in a later Studio session lasts, a state of the
 * bot's own. A part added that pressing shows blocks is cut as the button's {@code Disabled} look.
 */
public final class Versions {

    /** The most sightings kept per project, oldest dropped first. */
    static final int MOST_KEPT = 512;
    /** The most presses kept per project, oldest dropped first. */
    static final int MOST_PRESSES = 128;
    /** The fewest buttons shaded alike that tell a shade over the whole screen. */
    static final int SCREEN_LEAST = 3;
    /** How far past a pressed button's box a change is still its own (its pressed look), in pixels. */
    static final int OWN_MARGIN = 4;
    /** The most words of a toast a proposed state's name takes. */
    private static final int NAME_WORDS = 4;
    /** The most characters of a toast kept. */
    private static final int LONGEST_SAID = 80;
    private static final String MAGIC = "botmaker versions";
    private static final int LAYOUT = 2;
    private static final int LONGEST = 1 << 16;

    /** Each project's sightings and presses, read from its file the first time they are asked for. */
    private static final Map<Path, Store> KEPT = new HashMap<>();
    /** This Studio's session: a version seen again in a later one lasts. A new one once {@link #forget} runs. */
    private static long session = new SecureRandom().nextLong();

    private Versions() {}

    /** A button matched on a frame, at {@code at}, its shown pixels fitted against its cut. */
    record Seen(String picture, Rect at, Fit fit) {}

    /**
     * A frame read: the buttons each showing a version of its own, the ones a shade over the whole screen changed
     * alike (none when the screen is not shaded), and the ones shown as cut.
     */
    record Sight(List<Seen> versions, List<Seen> screenWide, List<Seen> plain) {}

    /**
     * A fit kept: on which picture, which frame (by a sample of its pixels), whether the whole screen was shaded alike,
     * and in which Studio session. A fit on the cut's colours ({@link #plain}) is kept only for a button seen in a
     * version before: it says the version went.
     */
    record Sighting(String picture, int frame, boolean screen, Rect at, Fit fit, long session) {

        boolean plain() {
            return !fit.offIdentity();
        }
    }

    /** What pressing a button shown as a version did, the first one found telling most. */
    enum Answer {
        /** Nothing changed but the button itself: it is {@code Disabled}. */
        NOTHING,
        /** Words showed (a toast): the author is asked which state of the bot's it is. */
        TOAST,
        /** It answered as a button does (a screen opened, a change in place): it does not block. */
        ACTED;

        /** What pressing did, in words: {@code said} is the toast's. */
        String said(String said) {
            return switch (this) {
                case NOTHING -> "did nothing";
                case TOAST -> "said \"" + said + "\"";
                case ACTED -> "still answered, so it does not block";
            };
        }
    }

    /** A press of {@code picture} while it showed {@code fit}, what came of it, and a toast's words (blank: none). */
    record Press(String picture, Fit fit, Answer answer, String said) {}

    /**
     * What a fit means: its {@code state}, {@code why} (null: by its kind only), and the state the author is asked to
     * confirm or rename, {@code Blocks.NOT_ENOUGH_GOLD} (null: none to ask).
     */
    record Meant(ButtonState state, String why, ButtonState asks) {}

    /**
     * A fit seen on {@code pictures}: the shade it makes, by the id {@code learn_shade} takes. {@code ids} holds each
     * sighting's own id too, so an id said before still names the group once its first sighting is dropped.
     * {@code why} says how its meaning was learned (null: by its kind only); {@code asks} is the bot state proposed
     * for the author to confirm (null: none), which learning it needs.
     */
    public record Proposal(String id, Shade shade, List<String> pictures, Set<String> ids, String why,
                           ButtonState asks) {

        /** Whether it was seen on two buttons or more, so it is a shade to learn. */
        public boolean shared() {
            return pictures.size() > 1;
        }
    }

    /** What 🗺 Screens' map panel lists under *Shades the game shows*: the fits proposed and the shades learned. */
    public record Shown(List<Proposal> proposed, List<Shade> learned) {}

    /** A project's sightings and presses, oldest first; changed only under the class's lock. */
    private record Store(List<Sighting> sightings, List<Press> presses) {}

    /** The project's proposals and learned shades, read off the FX thread (the sightings' file is read here). */
    public static Shown shown(StudioServices services) {
        Path resources = services.resourcesDir();
        if (resources == null) return new Shown(List.of(), List.of());
        List<Shade> learned = FxCall.call(() -> Frames.shades(services));
        Path store = MapAudit.store(resources, MapAudit.Kept.VERSIONS);
        return new Shown(proposals(kept(resources, store), presses(resources, store), learned),
                learned.stream().filter(s -> s.cause() == Shade.Cause.GAME).toList());
    }

    /** {@code seen} read as one frame: the largest set of alike fits on more than half its buttons shades the screen. */
    static Sight read(List<Seen> seen) {
        List<Seen> off = seen.stream().filter(s -> s.fit().offIdentity()).toList();
        // A bad fit (covered, mid-animation) tells nothing: only a good fit on the cut's colours is plain.
        List<Seen> plain = seen.stream().filter(s -> s.fit().asCut()).toList();
        List<Seen> largest = List.of();
        for (Seen s : off) {
            List<Seen> alike = off.stream().filter(o -> o.fit().alike(s.fit())).toList();
            if (alike.size() > largest.size()) largest = alike;
        }
        if (largest.size() < SCREEN_LEAST || largest.size() * 2 <= seen.size()) return new Sight(off, List.of(), plain);
        List<Seen> wide = largest;
        return new Sight(off.stream().filter(s -> !wide.contains(s)).toList(), wide, plain);
    }

    /**
     * The fits of {@code kept} that no shade of {@code learned} names, grouped alike, each with the shade it would
     * learn, meaning what {@code presses} and the sightings tell ({@link #meant}); a screen-wide one is never
     * proposed. The shade is the group's first fit's, so its id holds while more sightings come.
     */
    static List<Proposal> proposals(List<Sighting> kept, List<Press> presses, List<Shade> learned) {
        List<List<Sighting>> groups = new ArrayList<>();
        for (Sighting s : kept) {
            if (s.screen() || s.plain() || ShadeFit.closest(s.fit(), learned, Set.of(Shade.Cause.GAME)).isPresent()) {
                continue;
            }
            groups.stream().filter(g -> g.getFirst().fit().alike(s.fit())).findFirst()
                    .ifPresentOrElse(g -> g.add(s), () -> groups.add(new ArrayList<>(List.of(s))));
        }
        return groups.stream().map(g -> {
            Fit first = g.getFirst().fit();
            List<String> pictures = g.stream().map(Sighting::picture).distinct().toList();
            Meant meant = meant(first, pictures, kept, presses);
            Shade shade = first.learned(Shade.Cause.GAME, meant.state());
            Set<String> ids = new LinkedHashSet<>();
            g.forEach(s -> ids.add(id(learned(s.fit()))));
            return new Proposal(id(shade), shade, pictures, Set.copyOf(ids), meant.why(), meant.asks());
        }).toList();
    }

    private static Shade learned(Fit fit) {
        return fit.learned(Shade.Cause.GAME, meaning(fit.kind()));
    }

    /**
     * What a version of {@code kind} means until pressing it says otherwise: a dimmed, greyed or faded button is
     * {@code Disabled}, a brighter or tinted one {@code Selected}.
     */
    static State meaning(Shade.Kind kind) {
        return switch (kind) {
            case DIM, GREY, FADE -> State.DISABLED;
            default -> State.SELECTED;
        };
    }

    /**
     * What {@code fit}, seen on {@code pictures}, means: the latest press of one of them that did nothing makes it
     * {@code Disabled}, then one that showed a toast asks the author for a {@code Blocks} state named from its words,
     * then one that answered asks for a {@code Presses} state; with no press, a version its button showed no more when
     * seen again is {@code Disabled}, and one still shown in a later session asks for a {@code Blocks} state; else its
     * kind says ({@link #meaning}).
     */
    static Meant meant(Fit fit, List<String> pictures, List<Sighting> kept, List<Press> presses) {
        State kind = meaning(fit.kind());
        for (Answer answer : Answer.values()) {
            for (Press p : presses.reversed()) {
                if (p.answer() != answer || !pictures.contains(p.picture()) || !p.fit().alike(fit)) continue;
                String why = "pressing " + p.picture() + " " + answer.said(p.said());
                return switch (answer) {
                    case NOTHING -> new Meant(State.DISABLED, why, null);
                    case TOAST -> new Meant(kind, why, BotConstants.state(ButtonState.BLOCKS,
                            constant(p.said(), fit.kind())));
                    case ACTED -> new Meant(kind, why, BotConstants.state(ButtonState.PRESSES, constant("", fit.kind())));
                };
            }
        }
        for (String picture : pictures) {
            if (gone(picture, fit, kept)) {
                return new Meant(State.DISABLED, picture + " showed it no more when seen again", null);
            }
        }
        for (String picture : pictures) {
            if (sessions(picture, fit, kept) > 1) {
                return new Meant(kind, picture + " still shows it in a later session, a lasting state of the bot's",
                        BotConstants.state(ButtonState.BLOCKS, constant("", fit.kind())));
            }
        }
        return new Meant(kind, null, null);
    }

    /** Whether {@code picture} was seen as cut after it showed {@code fit}, the screen not shaded over it. */
    private static boolean gone(String picture, Fit fit, List<Sighting> kept) {
        boolean shown = false;
        for (Sighting s : kept) {
            if (!s.picture().equals(picture) || s.screen()) continue;
            if (s.plain() && shown) return true;
            if (!s.plain() && s.fit().alike(fit)) shown = true;
        }
        return false;
    }

    /** In how many Studio sessions {@code picture} showed {@code fit}. */
    private static long sessions(String picture, Fit fit, List<Sighting> kept) {
        return kept.stream().filter(s -> s.picture().equals(picture) && !s.screen() && !s.plain()
                && s.fit().alike(fit)).map(Sighting::session).distinct().count();
    }

    /**
     * A constant's name from a toast's first words, {@code "Not enough gold!"} → {@code NOT_ENOUGH_GOLD}; from the
     * kind's name when the words give none ({@code DIMMED}).
     */
    static String constant(String said, Shade.Kind kind) {
        String name = words(said);
        return name.isEmpty() || !Character.isLetter(name.charAt(0)) ? words(kind.displayName()) : name;
    }

    private static String words(String text) {
        return Arrays.stream(text.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")).filter(w -> !w.isEmpty())
                .limit(NAME_WORDS).collect(Collectors.joining("_"));
    }

    /** {@code shade}'s id: its kind and four hex digits of its numbers, {@code dim-3fa2}. */
    static String id(Shade shade) {
        int hash = 17;
        for (int c = 0; c < 3; c++) {
            hash = hash * 31 + Long.hashCode(Math.round(shade.gain().get(c) * 100));
            hash = hash * 31 + Long.hashCode(Math.round(shade.offset().get(c) * 100));
        }
        return String.format(Locale.ROOT, "%s-%04x", shade.kind().name().toLowerCase(Locale.ROOT), hash & 0xFFFF);
    }

    /**
     * Each of {@code found} — a picture's file name and where it matched — fitted on {@code frame}, read, kept, and said
     * for which_screen: blank when nothing is off its cut.
     */
    static String seen(AgentContext context, Path resources, Map<String, Rect> found, BufferedImage frame,
                       List<Shade> learned) {
        if (found.isEmpty()) return "";
        Map<String, Integer> frames;
        try {
            // A moving picture's frames side by side are not one wide cut.
            frames = Frames.frames(context, found.keySet());
        } catch (IllegalStateException busy) {
            return "";
        }
        List<Seen> seen = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, Rect> each : found.entrySet()) {
            ImageTemplate picture = new ImageTemplate(TemplateLibrary.fileForName(resources, each.getKey()).toString())
                    .frames(frames.getOrDefault(each.getKey(), 1));
            Optional<Fit> fit = ShadeFit.fit(picture, each.getValue(), frame);
            fit.ifPresent(f -> seen.add(new Seen(each.getKey(), each.getValue(), f)));
            if (fit.isEmpty() || !fit.get().offIdentity()) {
                ShadeFit.addition(picture, each.getValue(), frame).ifPresent(a -> added.add(each.getKey()
                        + " shows a part its cut lacks at " + Frames.box(a.area()) + " (a lock, a badge): try_input "
                        + "it, and pressing it tells what it means."));
            }
        }
        Sight sight = read(seen);
        Path store = MapAudit.store(resources, MapAudit.Kept.VERSIONS);
        record(resources, store, frame, sight);
        return said(sight, seen.size(), proposals(kept(resources, store), presses(resources, store), learned),
                learned, added);
    }

    /** {@code sight} of a frame of {@code buttons} buttons as sentences, {@code added} after; blank for nothing. */
    static String said(Sight sight, int buttons, List<Proposal> proposals, List<Shade> learned, List<String> added) {
        List<String> lines = new ArrayList<>();
        if (!sight.screenWide().isEmpty()) {
            lines.add("The screen is " + sight.screenWide().getFirst().fit().kind().displayName() + " alike on "
                    + sight.screenWide().size() + " of " + buttons + " buttons: a shade over it (a tutorial's, a "
                    + "popup's), not a version of each.");
        }
        for (Seen version : sight.versions()) {
            if (ShadeFit.closest(version.fit(), learned, Set.of(Shade.Cause.GAME)).isPresent()) continue;
            Optional<Proposal> of = proposal(proposals, version.picture(), version.fit());
            String kind = version.fit().kind().displayName();
            lines.add(of.filter(Proposal::shared).map(p -> version.picture() + " shows " + kind + ", as "
                            + String.join(", ", p.pictures().stream().filter(n -> !n.equals(version.picture()))
                            .toList()) + " did: " + learning(p))
                    .orElse(version.picture() + " shows " + kind + ", seen on no other button yet: a shade is "
                            + "learned once two buttons show it."));
        }
        lines.addAll(added);
        return String.join("\n", lines);
    }

    /** How {@code proposal} is learned, in a sentence: the edit, what it means and why, the author asked first. */
    private static String learning(Proposal proposal) {
        String kind = proposal.shade().kind().displayName();
        return "edit_map learn_shade fit " + proposal.id() + (proposal.asks() == null ? ""
                : " state " + proposal.asks().id()) + " keeps it, " + kind + " → " + (proposal.asks() == null
                ? proposal.shade().state().displayName() : proposal.asks().id()) + " for every button" + (proposal.why() == null ? "." : " (" + proposal.why()
                + ").") + (proposal.asks() == null ? "" : " Ask the author which state it is first.");
    }

    /** The proposal of {@code proposals} that holds {@code fit} seen on {@code picture}. */
    private static Optional<Proposal> proposal(List<Proposal> proposals, String picture, Fit fit) {
        return proposals.stream().filter(p -> p.pictures().contains(picture) && p.shade().kind() == fit.kind()
                && fitsAlike(p, fit)).findFirst();
    }

    private static boolean fitsAlike(Proposal proposal, Fit fit) {
        Fit of = new Fit(proposal.shade().kind() == Shade.Kind.GREY, proposal.shade().gain(),
                proposal.shade().offset(), 0, null);
        return of.alike(fit);
    }

    /**
     * A button try_input pressed, as it showed before: the field {@code field} of {@code screen} (null for one the map
     * does not hold yet), its picture's file name, where it matched on {@code before}, and whether a screen
     * {@code opened}.
     */
    record Pressed(String screen, String field, Button button, String picture, Rect at, BufferedImage before,
                   BufferedImage after, Motion motion, boolean opened) {}

    /** What a press told: the sentence said (blank: nothing), and the look cut of its version, written on the input. */
    record Pressing(String said, Optional<Look> look) {
        static final Pressing NONE = new Pressing("", Optional.empty());
    }

    /**
     * What pressing {@code press} tells of the version it showed, kept and said: blank for a button shown as cut, or
     * as a shade the bot learned already. A part its cut lacks that pressing shows blocks is cut as its
     * {@code Disabled} look, {@code <picture>_disabled}, and written on the input. Off the FX thread.
     */
    static Pressing pressed(AgentContext context, Pressed press) {
        try {
            Optional<Path> resources = Frames.resources(context);
            if (resources.isEmpty() || press.after() == null) return Pressing.NONE;
            ImageTemplate picture = new ImageTemplate(TemplateLibrary.fileForName(resources.get(), press.picture())
                    .toString()).frames(Frames.frames(context, press.picture()));
            Optional<Fit> fit = ShadeFit.fit(picture, press.at(), press.before()).filter(Fit::offIdentity);
            Optional<ShadeFit.Addition> added = fit.isPresent() ? Optional.empty()
                    : ShadeFit.addition(picture, press.at(), press.before());
            if (fit.isEmpty() && added.isEmpty()) return Pressing.NONE;
            List<Shade> learned = FxCall.call(() -> Frames.shades(context.services()));
            // A version a learned shade names: a run knows it already.
            if (fit.filter(f -> ShadeFit.closest(f, learned, Set.of(Shade.Cause.GAME)).isPresent()).isPresent()) {
                return Pressing.NONE;
            }
            Optional<Rect> changed = FrameChange.box(press.before(), press.after(), press.motion())
                    .filter(box -> !inside(box, press.at()));
            String read = press.opened() || changed.isEmpty() ? "" : read(press.after(), changed.get());
            Answer answer = answer(press.opened(), changed.isPresent(), read);
            if (added.isPresent()) return addition(context, press, added.get(), answer, read);
            Path store = MapAudit.store(resources.get(), MapAudit.Kept.VERSIONS);
            Fit shown = fit.get();
            record(resources.get(), store, press.before(), new Sight(List.of(new Seen(press.picture(), press.at(),
                    shown)), List.of(), List.of()));
            keep(resources.get(), store, new Press(press.picture(), shown, answer, read));
            Optional<Proposal> of = proposal(proposals(kept(resources.get(), store), presses(resources.get(), store),
                    learned), press.picture(), shown);
            return new Pressing("Pressing " + press.picture() + ", shown " + shown.kind().displayName() + ", "
                    + answer.said(read) + (of.filter(Proposal::shared).isPresent() ? ": " + learning(of.get())
                    : ". A shade is learned once two buttons show it."), Optional.empty());
        } catch (RuntimeException unread) {
            // Studio busy, a picture gone, OCR failing: the press happened, and try_input records it all the same.
            return Pressing.NONE;
        }
    }

    /**
     * What a press tells from what changed: a screen {@code opened} or a change with no words answered, words
     * {@code read} are a toast, and no change {@code beyond} the button itself is nothing.
     */
    static Answer answer(boolean opened, boolean beyond, String read) {
        if (opened) return Answer.ACTED;
        if (!beyond) return Answer.NOTHING;
        return read.isBlank() ? Answer.ACTED : Answer.TOAST;
    }

    /** Whether {@code box} lies within {@code button}, {@link #OWN_MARGIN} around it. */
    static boolean inside(Rect box, Rect button) {
        return box.x() >= button.x() - OWN_MARGIN && box.y() >= button.y() - OWN_MARGIN
                && box.x() + box.width() <= button.x() + button.width() + OWN_MARGIN
                && box.y() + box.height() <= button.y() + button.height() + OWN_MARGIN;
    }

    /** The words OCR reads in {@code box} of {@code frame}, joined; blank for none. */
    private static String read(BufferedImage frame, Rect box) {
        int x = Math.max(0, box.x());
        int y = Math.max(0, box.y());
        int w = Math.min(frame.getWidth(), box.x() + box.width()) - x;
        int h = Math.min(frame.getHeight(), box.y() + box.height()) - y;
        if (w <= 0 || h <= 0) return "";
        String said = LookTools.recognise(frame.getSubimage(x, y, w, h), OcrLanguage.ENGLISH).stream()
                .map(TextMatch::text).filter(t -> t != null && !t.isBlank()).map(String::strip)
                .collect(Collectors.joining(" "));
        return said.length() > LONGEST_SAID ? said.substring(0, LONGEST_SAID) : said;
    }

    /**
     * A part {@code added} on the button {@code press} pressed: cut as its {@code Disabled} look when pressing did
     * nothing and the map holds the input; else said, for the author to name its state.
     */
    private static Pressing addition(AgentContext context, Pressed press, ShadeFit.Addition added, Answer answer,
                                     String read) {
        String part = press.picture() + " shows a part its cut lacks at " + Frames.box(added.area())
                + " (a lock, a badge), and pressing it " + answer.said(read);
        if (answer != Answer.NOTHING || press.field() == null) {
            return new Pressing(part + ": ask the author which state it is, then crop_pictures the button as a look "
                    + "of " + press.picture() + " with that state.", Optional.empty());
        }
        StudioServices services = context.services();
        String name = press.picture() + "_" + State.DISABLED.id();
        try {
            return FxCall.call(() -> {
                Path resources = services.resourcesDir();
                if (resources == null || TemplateLibrary.exists(resources, name)) {
                    return new Pressing(part + ": a picture " + name + " is there already; crop_pictures the button "
                            + "under another name as its Disabled look.", Optional.empty());
                }
                Optional<PictureCuts.Piece> piece = PictureCuts.cut(press.before(), press.at(), CutShape.RECT,
                        List.of());
                if (piece.isEmpty()) return new Pressing(part + ".", Optional.empty());
                PictureCuts.Cut cut = PictureCuts.save(services, piece.get(), name, press.before());
                Look look = Look.of(ScreenBook.picture(services, cut.name()), State.DISABLED);
                Optional<String> failed = ScreenBook.update(services, press.screen(),
                        s -> s.withInput(press.field(), press.button().withLook(look)));
                return failed.map(why -> new Pressing(part + ": cut as " + cut.name() + ", but not noted as its look: "
                                + why, Optional.<Look>empty()))
                        .orElseGet(() -> new Pressing(part + ": cut as " + cut.name() + ", its Disabled look; a run "
                                + "never presses it while it looks so.", Optional.of(look)));
            });
        } catch (IllegalArgumentException | IllegalStateException failed) {
            return new Pressing(part + ": it could not be cut, " + failed.getMessage(), Optional.empty());
        }
    }

    /**
     * Writes the shade of the proposal {@code id} into {@code Sdk.shades()}, said in a sentence beginning
     * {@link ScreenEdits#NOT_WRITTEN} when it was not. On the FX thread.
     */
    static String learn(StudioServices services, String id) {
        return learn(services, id, null);
    }

    /**
     * {@link #learn(StudioServices, String)}, the shade meaning {@code meaning} in place of what was learned of it
     * ({@link #meant}), a state of the bot's added to its {@code States}; null keeps that, refused while the proposal
     * asks the author for a state. On the FX thread.
     */
    public static String learn(StudioServices services, String id, ButtonState meaning) {
        Path resources = services.resourcesDir();
        if (resources == null) return ScreenEdits.NOT_WRITTEN + Frames.noProject();
        Optional<Interactions.Answer> tried = Interactions.proposed(resources, id);
        if (tried.isPresent()) return keep(services, tried.get(), meaning);
        if (meaning == State.NORMAL) {
            return ScreenEdits.NOT_WRITTEN + "a shade the game shows never means Normal: pick what it means.";
        }
        List<Shade> learned = Frames.shades(services);
        String typed = id == null ? "" : id.strip();
        Path store = MapAudit.store(resources, MapAudit.Kept.VERSIONS);
        // A shared group first: two groups' ids may share their four digits.
        Optional<Proposal> proposal = proposals(kept(resources, store), presses(resources, store), learned).stream()
                .filter(p -> p.ids().contains(typed)).min(Comparator.comparing(p -> !p.shared()));
        if (proposal.isEmpty()) {
            return ScreenEdits.NOT_WRITTEN + "no fit " + id + " is proposed; which_screen names the fits it saw.";
        }
        if (!proposal.get().shared()) {
            return ScreenEdits.NOT_WRITTEN + "fit " + id + " was seen on " + proposal.get().pictures().getFirst()
                    + " only; a shade is learned once two buttons show it.";
        }
        if (meaning == null && proposal.get().asks() != null) {
            return ScreenEdits.NOT_WRITTEN + proposal.get().why() + ": give state, " + proposal.get().asks().id()
                    + " or the state the author names.";
        }
        Optional<String> undeclared = meaning == null ? Optional.empty()
                : BotStates.declare(services.pluginValues(), meaning);
        if (undeclared.isPresent()) return ScreenEdits.NOT_WRITTEN + undeclared.get();
        Shade proposed = proposal.get().shade();
        Shade shade = meaning == null ? proposed : new Shade(proposed.kind(), proposed.gain(), proposed.offset(),
                proposed.tolerance(), proposed.cause(), meaning, proposed.geometry());
        List<Shade> all = new ArrayList<>(learned);
        all.add(shade);
        return Frames.writeShades(services, all).map(why -> ScreenEdits.NOT_WRITTEN + why)
                .orElse("Learned: every button shown " + shade.kind().displayName() + " is "
                        + BotStates.label(shade.state()) + " (seen on " + String.join(", ", proposal.get().pictures())
                        + "). Nothing else is cut.");
    }

    /**
     * Writes the shade {@code tried} proposes, of the bot's own input, in place of one learned of the same cause; any
     * {@code meaning} but {@code Normal} refused. On the FX thread.
     */
    private static String keep(StudioServices services, Interactions.Answer tried, ButtonState meaning) {
        if (meaning != null && meaning != State.NORMAL) {
            return ScreenEdits.NOT_WRITTEN + "a shade the bot's own " + Interactions.label(tried.cause())
                    .toLowerCase(Locale.ROOT) + " causes means Normal: leave state out.";
        }
        List<Shade> all = new ArrayList<>(Frames.shades(services).stream()
                .filter(s -> s.cause() != tried.cause()).toList());
        all.add(tried.shade());
        return Frames.writeShades(services, all).map(why -> ScreenEdits.NOT_WRITTEN + why)
                .orElse("Learned: a button under the bot's " + Interactions.label(tried.cause()).toLowerCase(Locale.ROOT)
                        + " shown " + tried.shade().kind().displayName() + " is Normal (seen on "
                        + String.join(", ", tried.pictures()) + ").");
    }

    /**
     * Takes {@code shade} out of {@code Sdk.shades()}, so its fit is proposed again; said in a sentence beginning
     * {@link ScreenEdits#NOT_WRITTEN} when it was not. On the FX thread.
     */
    public static String unlearn(StudioServices services, Shade shade) {
        List<Shade> learned = new ArrayList<>(Frames.shades(services));
        if (!learned.remove(shade)) return ScreenEdits.NOT_WRITTEN + "that shade is learned no more.";
        return Frames.writeShades(services, learned).map(why -> ScreenEdits.NOT_WRITTEN + why)
                .orElse("Forgot: a button shown " + shade.kind().displayName() + " is no longer "
                        + BotStates.label(shade.state()) + ".");
    }

    /** Reads {@code resources}' sightings from their file now, off the FX thread, so {@link #learn} finds them read. */
    static void warm(Path resources) {
        if (resources != null) kept(resources, MapAudit.store(resources, MapAudit.Kept.VERSIONS));
    }

    /** {@code resources}' sightings, read from {@code store} the first time. */
    static synchronized List<Sighting> kept(Path resources, Path store) {
        return List.copyOf(stored(resources, store).sightings());
    }

    /** {@code resources}' presses, read from {@code store} the first time. */
    static synchronized List<Press> presses(Path resources, Path store) {
        return List.copyOf(stored(resources, store).presses());
    }

    private static Store stored(Path resources, Path store) {
        return KEPT.computeIfAbsent(resources, r -> load(store));
    }

    /**
     * Keeps {@code sight} of {@code frame}, each button once per frame, and writes the project's file. A button shown as
     * cut is kept only when it was seen in a version before.
     */
    static synchronized void record(Path resources, Path store, BufferedImage frame, Sight sight) {
        List<Sighting> kept = stored(resources, store).sightings();
        int sample = sample(frame);
        boolean added = false;
        for (Seen s : sight.versions()) added |= add(kept, new Sighting(s.picture(), sample, false, s.at(), s.fit(), session));
        for (Seen s : sight.screenWide()) added |= add(kept, new Sighting(s.picture(), sample, true, s.at(), s.fit(), session));
        for (Seen s : sight.plain()) {
            // Once per version shown: only while the button's last sighting kept is one, so plain frames never
            // crowd the versions out.
            Optional<Sighting> last = kept.reversed().stream().filter(k -> k.picture().equals(s.picture()) && !k.screen())
                    .findFirst();
            if (last.filter(k -> !k.plain()).isPresent()) {
                added |= add(kept, new Sighting(s.picture(), sample, false, s.at(), s.fit(), session));
            }
        }
        if (!added) return;
        while (kept.size() > MOST_KEPT) kept.removeFirst();
        save(store, stored(resources, store));
    }

    /** Keeps {@code press}, in place of the same button's alike one, and writes the project's file. */
    static synchronized void keep(Path resources, Path store, Press press) {
        List<Press> presses = stored(resources, store).presses();
        presses.removeIf(p -> p.picture().equals(press.picture()) && p.fit().alike(press.fit()));
        presses.add(press);
        while (presses.size() > MOST_PRESSES) presses.removeFirst();
        save(store, stored(resources, store));
    }

    /** Adds {@code sighting}, in place of the same button's on the same frame (a recut fits otherwise). */
    private static boolean add(List<Sighting> kept, Sighting sighting) {
        for (int i = 0; i < kept.size(); i++) {
            Sighting k = kept.get(i);
            if (!k.picture().equals(sighting.picture()) || k.frame() != sighting.frame()) continue;
            if (k.equals(sighting)) return false;
            kept.set(i, sighting);
            return true;
        }
        return kept.add(sighting);
    }

    /** Forgets every project's sightings, as a Studio started again, in a new session: the tests start from it. */
    static synchronized void forget() {
        KEPT.clear();
        session = new SecureRandom().nextLong();
    }

    /** A hash of 64 of {@code frame}'s pixels on a grid: the same frame read twice is one. */
    private static int sample(BufferedImage frame) {
        int hash = frame.getWidth() * 31 + frame.getHeight();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash = hash * 31 + frame.getRGB((2 * x + 1) * frame.getWidth() / 16,
                        (2 * y + 1) * frame.getHeight() / 16);
            }
        }
        return hash;
    }

    /** Writes {@code kept}; kept in memory still when it cannot be written: the next change writes it. */
    private static void save(Path file, Store kept) {
        try {
            Files.createDirectories(file.getParent());
            Path part = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
            try {
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(part)))) {
                    out.writeUTF(MAGIC);
                    out.writeInt(LAYOUT);
                    out.writeInt(kept.sightings().size());
                    for (Sighting s : kept.sightings()) {
                        out.writeUTF(s.picture());
                        out.writeInt(s.frame());
                        out.writeBoolean(s.screen());
                        out.writeInt(s.at().x());
                        out.writeInt(s.at().y());
                        out.writeInt(s.at().width());
                        out.writeInt(s.at().height());
                        write(out, s.fit());
                        out.writeLong(s.session());
                    }
                    out.writeInt(kept.presses().size());
                    for (Press p : kept.presses()) {
                        out.writeUTF(p.picture());
                        write(out, p.fit());
                        out.writeUTF(p.answer().name());
                        out.writeUTF(p.said());
                    }
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(part);
            }
        } catch (IOException unwritten) {
            // Kept in memory still.
        }
    }

    private static void write(DataOutputStream out, Fit fit) throws IOException {
        out.writeBoolean(fit.grey());
        for (int c = 0; c < 3; c++) out.writeDouble(fit.gain().get(c));
        for (int c = 0; c < 3; c++) out.writeDouble(fit.offset().get(c));
        out.writeDouble(fit.error());
    }

    private static Fit fit(DataInputStream in) throws IOException {
        boolean grey = in.readBoolean();
        Shade.Channels gain = new Shade.Channels(in.readDouble(), in.readDouble(), in.readDouble());
        Shade.Channels offset = new Shade.Channels(in.readDouble(), in.readDouble(), in.readDouble());
        return new Fit(grey, gain, offset, in.readDouble(), null);
    }

    /** What {@code file} holds; nothing when it is absent, broken or of another layout. */
    private static Store load(Path file) {
        Store none = new Store(new ArrayList<>(), new ArrayList<>());
        if (!Files.isRegularFile(file)) return none;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (!MAGIC.equals(in.readUTF()) || in.readInt() != LAYOUT) return none;
            int n = in.readInt();
            if (n < 0 || n > LONGEST) return none;
            List<Sighting> kept = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String picture = in.readUTF();
                int frame = in.readInt();
                boolean screen = in.readBoolean();
                Rect at = new Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt());
                kept.add(new Sighting(picture, frame, screen, at, fit(in), in.readLong()));
            }
            int m = in.readInt();
            if (m < 0 || m > LONGEST) return none;
            List<Press> presses = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                String picture = in.readUTF();
                Fit fit = fit(in);
                presses.add(new Press(picture, fit, Answer.valueOf(in.readUTF()), in.readUTF()));
            }
            return new Store(kept, presses);
        } catch (IOException | RuntimeException broken) {
            return none;
        }
    }
}
