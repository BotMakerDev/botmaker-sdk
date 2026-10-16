package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What tells a screen or a look: pictures that all show, and pictures none of which may show. The one rule every
 * reader of the map tells a screen by — a walk, {@code Navigate.isOn}, the assistant's tools and the audit.
 *
 * @param shown  pictures that all show; empty: nothing tells it
 * @param absent pictures none of which may show
 */
public record Markers(List<ImageTemplate> shown, List<ImageTemplate> absent) {

    public Markers {
        shown = shown == null ? List.of() : List.copyOf(shown);
        absent = absent == null ? List.of() : List.copyOf(absent);
    }

    public static Markers of(GameScreen screen) {
        return new Markers(screen.markers(), screen.absent());
    }

    public static Markers of(ScreenVariant look) {
        return new Markers(look.markers(), look.absent());
    }

    /** Whether anything tells it: at least one marker. */
    public boolean any() {
        return !shown.isEmpty();
    }

    /** Its markers' ids as a reply names them: {@code shop_title + shop_tab}. */
    public String ids() {
        return String.join(" + ", shown.stream().map(ImageTemplate::id).toList());
    }

    /** Its markers and its absent pictures, each once. */
    public List<ImageTemplate> pictures() {
        Map<String, ImageTemplate> all = new LinkedHashMap<>();
        for (ImageTemplate p : shown) all.putIfAbsent(p.id(), p);
        for (ImageTemplate p : absent) all.putIfAbsent(p.id(), p);
        return List.copyOf(all.values());
    }

    /** Whether the frame {@code found} answers for shows it: every marker found, no absent picture found. */
    public boolean on(Predicate<ImageTemplate> found) {
        return any() && shown.stream().allMatch(found) && absent.stream().noneMatch(found);
    }

    /** Whether {@code frame} shows it, by {@code reach}. */
    public boolean on(Reach reach, BufferedImage frame) {
        return onEach(reach, List.of(this), frame).getFirst();
    }

    /**
     * For each of {@code sets}, whether {@code frame} shows it: every picture of them looked for once, all on that one
     * frame ({@link Reach#findEach}), so a picture shared by several sets costs one match.
     */
    public static List<Boolean> onEach(Reach reach, List<Markers> sets, BufferedImage frame) {
        Map<String, ImageTemplate> pictures = new LinkedHashMap<>();
        for (Markers set : sets) for (ImageTemplate p : set.pictures()) pictures.putIfAbsent(p.id(), p);
        List<String> ids = new ArrayList<>(pictures.keySet());
        List<Optional<Rect>> found = reach.findEach(List.copyOf(pictures.values()), frame);
        Map<String, Boolean> seen = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) seen.put(ids.get(i), found.get(i).isPresent());
        return sets.stream().map(set -> set.on(p -> seen.getOrDefault(p.id(), false))).toList();
    }
}
