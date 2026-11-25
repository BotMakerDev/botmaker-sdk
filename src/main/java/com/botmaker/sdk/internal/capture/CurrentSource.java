// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.api.capture.Source;

/**
 * The ambient source as a value: whatever {@link Source#current()} answers at the moment it is used.
 *
 * <p>It is what {@code Source.current()} is read back as when the host reads a bot's Java. Freezing the answer into
 * a concrete source would stop the declaration following the project's source when that changes.
 */
public final class CurrentSource extends AmbientSource {

    @Override
    public String toString() {
        return "Source.current()";
    }
}
