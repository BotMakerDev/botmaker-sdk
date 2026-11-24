// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The feed's thumbnails of the frames its records name, each decoded once while kept. FX thread only. */
final class Thumbnails {

    /** How many thumbnails are kept decoded: the feed's rows on screen. */
    private static final int KEPT = 32;

    private final Function<String, Optional<Path>> frames;
    /** The thumbnails decoded, by file and width, oldest dropped first. */
    private final Map<String, Image> kept = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > KEPT;
        }
    };

    /** {@code frames} finds the file a record's frame names, empty when it is gone. */
    Thumbnails(Function<String, Optional<Path>> frames) {
        this.frames = frames;
    }

    /** The frame {@code frame} names, scaled to {@code width}; empty when its file is gone. */
    Optional<Image> of(String frame, double width) {
        Optional<Path> file = frames.apply(frame);
        if (file.isEmpty()) return Optional.empty();
        // Kept per width; one that failed is decoded again.
        String key = file.get() + "@" + width;
        Image image = kept.get(key);
        if (image == null || image.isError()) {
            image = new Image(file.get().toUri().toString(), width * 2, 0, true, true, true);
            kept.put(key, image);
        }
        return Optional.of(image);
    }
}
