package com.botmaker.sdk.internal.emulator;

import com.botmaker.shared.emulator.tree.UiTree;

import java.util.Optional;

/**
 * A capture source that also tells what its screen holds: an Android app's tree of inputs and texts, which a bot's
 * {@code Elements} are found in. A game window has none and is no such source.
 */
public interface ReadableSource {

    /** The tree of the app that has focus now, its windows only; empty when it could not be read. */
    Optional<UiTree> tree();
}
