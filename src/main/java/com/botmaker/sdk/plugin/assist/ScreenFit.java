package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Whether the game shows a screen, and where a picture sits on it, before the picture is put on that screen. A picture
 * keeps one place, and that place is true on every screen that uses it: one used where it sits elsewhere (Firestone's
 * close_x, 48 px off on All war machines; chat_map, cut on Map and noted on Home) gets a copy of its own there, cut
 * where it is (2026-10-16).
 */
final class ScreenFit {

    private ScreenFit() {}

    /** Where a picture is on a frame: at its place (or anywhere, for one with no place), elsewhere, or nowhere. */
    enum Where { AT_PLACE, ELSEWHERE, MISSING }

    /** {@code picture} on a frame: where, and its box there when found. */
    record Fit(String picture, Where where, Rect at) {
    }

    /**
     * The pictures to put on a screen, in the order asked, each copy's name in place of a picture found away from its
     * place; the copies still to cut ({@link #save}); the ones the game does not show; and {@code said}, what was copied
     * or why nothing was checked, as the reply's own sentences.
     */
    record Fitted(List<String> pictures, List<Copy> copies, List<String> missing, String said, BufferedImage frame) {
        static Fitted unchecked(List<String> pictures, String why) {
            return new Fitted(pictures, List.of(), List.of(), why, null);
        }

        /**
         * Cuts the copies, once every refusal of the call is behind it: a copy cut for a call refused after would be
         * left behind, and reused stale by the next. Empty when all were cut; else why one was not.
         */
        Optional<String> save(AgentContext context) {
            for (Copy copy : copies) {
                Optional<String> failed = ScreenFit.save(context, copy, frame);
                if (failed.isPresent()) return failed;
            }
            return Optional.empty();
        }
    }

    /** {@code picture}'s copy {@code name}, to cut with its pixels at {@code at}. */
    record Copy(String picture, String name, Rect at) {
    }

    /**
     * {@code pictures} checked on the game before they go on the screen {@code typed}: as they are when at their place;
     * a copy, to cut where it sits, when found elsewhere; missing when not found. Unchecked, and said so, while the game
     * shows another screen or no frame, and for a screen with no marker yet, which nothing tells apart from the one
     * under it.
     *
     * @param fixed  pictures never copied, a screen's marker: found anywhere, they go on as they are
     * @param hidden pictures that come and go: not found, they go on as they are
     */
    static Fitted fit(AgentContext context, String typed, List<String> pictures, Set<String> fixed,
                      Set<String> hidden) {
        if (pictures.isEmpty()) return Fitted.unchecked(pictures, "");
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(context.services()));
        Optional<String> constant = ScreenBook.find(graph, typed);
        Optional<ToolReach> reach = ToolReach.of(context);
        if (constant.isEmpty() || reach.isEmpty()) return Fitted.unchecked(pictures, "");
        String shown = graph.shown(constant.get());
        if (graph.screens().get(constant.get()).marker() == null) {
            return Fitted.unchecked(pictures, " Not checked on the game: " + shown + " has no marker yet.");
        }
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return Fitted.unchecked(pictures, " Not checked on the game: no frame of it.");
        if (!shows(reach.get(), graph, constant.get(), frame.get())) {
            return Fitted.unchecked(pictures, " Not checked on the game: it does not show " + shown + " now.");
        }
        Path resources = reach.get().resources();
        // A name the bot has no picture for is left as typed: the caller says it has none.
        List<String> named = pictures.stream().map(p -> Frames.picture(resources, p).orElse(p)).toList();
        List<String> known = named.stream().filter(p -> TemplateLibrary.exists(resources, p)).distinct().toList();
        Map<String, FrameCut> cuts = LookTools.cuts(context);
        Map<String, Fit> found = new HashMap<>();
        for (Fit fit : fits(reach.get(), cuts, known, frame.get())) found.put(fit.picture(), fit);
        List<String> kept = new ArrayList<>();
        List<Copy> copies = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> copied = new ArrayList<>();
        for (int i = 0; i < named.size(); i++) {
            String picture = named.get(i);
            Fit fit = found.get(picture);
            boolean typedFixed = fixed.contains(pictures.get(i)) || fixed.contains(picture);
            boolean typedHidden = hidden.contains(pictures.get(i)) || hidden.contains(picture);
            if (fit == null || fit.where() == Where.AT_PLACE
                    || fit.where() == Where.ELSEWHERE && typedFixed
                    || fit.where() == Where.MISSING && typedHidden) {
                kept.add(picture);
            } else if (fit.where() == Where.MISSING) {
                missing.add(picture);
            } else {
                String name = TemplateLibrary.sanitizeName(picture + "_" + constant.get().toLowerCase(Locale.ROOT));
                // A copy cut before is used only while it is where the game shows it now.
                if (TemplateLibrary.exists(resources, name)) {
                    if (fits(reach.get(), cuts, List.of(name), frame.get()).getFirst().where() != Where.AT_PLACE) {
                        missing.add(name + " (cut before, no longer where " + picture + " sits)");
                        continue;
                    }
                } else if (copies.stream().noneMatch(c -> c.name().equals(name))) {
                    copies.add(new Copy(picture, name, fit.at()));
                }
                kept.add(name);
                copied.add(name + " (" + picture + " sits at " + fit.at().x() + "," + fit.at().y()
                        + " here, away from its place)");
            }
        }
        return new Fitted(kept, copies, missing, copied.isEmpty() ? ""
                : " Used a copy of its own on " + shown + ": " + String.join(", ", copied.stream().distinct().toList())
                + ".", frame.get());
    }

    /**
     * Whether {@code frame} shows {@code constant}: its marker, or one of its looks' markers, is found. A screen with no
     * marker yet shows wherever it is asked for: nothing tells it apart.
     */
    static boolean shows(ToolReach reach, ScreenGraph graph, String constant, BufferedImage frame) {
        GameScreen screen = graph.screens().get(constant);
        if (screen == null || screen.marker() == null) return true;
        return reach.find(screen.marker(), frame).isPresent()
                || new ScreenWalk(graph, reach, ScreenWalk.STEP_MS, null).lookOn(constant, frame).isPresent();
    }

    /**
     * The screen of the map {@code frame} shows in place of {@code typed}, for a cut noted on {@code typed} while the game
     * shows another; empty when it shows {@code typed}, when {@code typed} has no marker yet (the screen being cut,
     * opened over the one below it), or when no screen of the map shows.
     */
    static Optional<String> otherScreen(AgentContext context, String typed, BufferedImage frame) {
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(context.services()));
        Optional<String> constant = ScreenBook.find(graph, typed);
        Optional<ToolReach> reach = ToolReach.of(context);
        if (constant.isEmpty() || reach.isEmpty() || shows(reach.get(), graph, constant.get(), frame)) {
            return Optional.empty();
        }
        ScreenWalk walk = new ScreenWalk(graph, reach.get(), ScreenWalk.STEP_MS, null);
        return walk.popupOn(frame).or(() -> walk.whereOn(frame, constant.get()))
                .filter(c -> !c.equals(constant.get())).map(graph::shown);
    }

    /**
     * Where each of {@code pictures} is on {@code frame}: looked for near its place first ({@link LookTools#NEAR}), as
     * the assistant's looks do; one not there is then looked for over the whole frame. Two batches, each frame prepared
     * once.
     */
    static List<Fit> fits(ToolReach reach, Map<String, FrameCut> cuts, List<String> pictures, BufferedImage frame) {
        List<MatchResult> near = batch(reach.resources(), cuts, pictures, frame, true);
        List<String> away = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            if (!reach.clears(near.get(i))) away.add(pictures.get(i));
        }
        List<MatchResult> anywhere = away.isEmpty() ? List.of() : batch(reach.resources(), cuts, away, frame, false);
        List<Fit> fits = new ArrayList<>();
        for (int i = 0, next = 0; i < pictures.size(); i++) {
            String picture = pictures.get(i);
            if (reach.clears(near.get(i))) fits.add(new Fit(picture, Where.AT_PLACE, near.get(i).rect()));
            else {
                MatchResult best = anywhere.get(next++);
                fits.add(reach.clears(best) ? new Fit(picture, Where.ELSEWHERE, best.rect())
                        : new Fit(picture, Where.MISSING, null));
            }
        }
        return fits;
    }

    /** Each picture's best match, near its place ({@code near}) or anywhere; not found when it has no file. */
    private static List<MatchResult> batch(Path resources, Map<String, FrameCut> cuts, List<String> pictures,
                                           BufferedImage frame, boolean near) {
        List<ImageTemplate> templates = new ArrayList<>();
        try {
            for (String picture : pictures) {
                FrameCut cut = cuts.get(picture);
                ImageTemplate file = new ImageTemplate(TemplateLibrary.fileForName(resources, picture).toString());
                if (near && cut != null && cut.place() != null) file = file.at(cut.place());
                templates.add(cut == null || cut.frame() == null ? file : file.in(cut.frame()));
            }
            StillSource still = new StillSource(frame);
            return ToolTiming.timed(ToolTiming.Phase.MATCH, () -> near
                    ? ImageFinder.bestMatchesNear(templates, LookTools.NEAR, still)
                    : ImageFinder.bestMatches(templates, still));
        } finally {
            // Released after the match: an unreleased template is native memory.
            templates.forEach(ImageTemplate::close);
        }
    }

    /**
     * {@code copy} cut: its picture's own pixels (a part a run skips stays skipped) placed where it sits on {@code frame},
     * {@code close_x_all_war_machines}. Empty when cut; else why not.
     */
    private static Optional<String> save(AgentContext context, Copy copy, BufferedImage frame) {
        Path resources = context.services().resourcesDir();
        BufferedImage image;
        try {
            image = ImageIO.read(TemplateLibrary.fileForName(resources, copy.picture()).toFile());
        } catch (IOException unreadable) {
            return Optional.of("Could not read " + copy.picture() + ".png: " + unreadable.getMessage());
        }
        if (image == null) return Optional.of(copy.picture() + ".png is not an image.");
        PictureCuts.Piece piece = new PictureCuts.Piece(image, copy.at());
        try {
            FxCall.call(() -> PictureCuts.save(context.services(), piece, copy.name(), frame));
            return Optional.empty();
        } catch (IllegalArgumentException refused) {
            return Optional.of(refused.getMessage());
        }
    }
}
