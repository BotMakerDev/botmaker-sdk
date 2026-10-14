package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.input.Hands;
import com.botmaker.sdk.internal.screens.Reach;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The assistant's reach: the frame the look tools read ({@link Frames#look}), each picture matched as a run matches
 * it at the project's confidence, and the {@link Hands} for where the game is.
 *
 * @param resources  the bot's resources, where its pictures are
 * @param confidence the score a run needs
 */
record ToolReach(AgentContext context, Path resources, double confidence, Hands hands) implements Reach {

    /** The reach for {@code context}'s game; empty when no project is open. */
    static Optional<ToolReach> of(AgentContext context) {
        return Frames.resources(context).map(resources -> new ToolReach(context, resources,
                Frames.confidence(context.services()), ActTools.hands(context)));
    }

    @Override
    public Optional<BufferedImage> look() {
        return Frames.look(context);
    }

    /** The bot's picture {@code name}'s best place on {@code frame}, at any score. */
    MatchResult best(String name, BufferedImage frame) {
        return LookTools.best(resources, name, frame);
    }

    /** Whether {@code match} clears the score a run needs. */
    boolean clears(MatchResult match) {
        return match.isFound() && match.confidence() >= confidence;
    }

    @Override
    public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
        return Frames.picture(resources, picture.id()).map(name -> best(name, frame)).filter(this::clears)
                .map(MatchResult::rect);
    }

    @Override
    public void click(Point at) {
        hands.click(at.x(), at.y());
    }

    @Override
    public void key(Key key) {
        hands.key(key);
    }

    @Override
    public void scroll(Point at, int notches) {
        hands.scroll(at.x(), at.y(), notches);
    }

    @Override
    public long nowMs() {
        return System.nanoTime() / 1_000_000;
    }

    @Override
    public void pause(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
