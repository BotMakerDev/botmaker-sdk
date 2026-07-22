package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.internal.config.ProjectDefaults;

/**
 * The SDK's global, ambient <em>capture source</em> — the "where" that every no-source vision and
 * mouse call looks at. This lets bots read cleanly: {@code ImageFinder.find(button)} instead of
 * threading a {@link CaptureSource} through every call.
 *
 * <p>On first use the current source initialises to the <strong>project default source</strong> (as
 * configured in Studio and baked into the generated bot), falling back to the whole {@link Desktop}
 * when none is configured. Override it at runtime with {@link #set(CaptureSource)} — for example to
 * point the whole bot at a game {@link Window} once, up front — and every subsequent no-source call
 * follows until it is changed again.
 *
 * <p>Methods that <em>do</em> take an explicit {@link CaptureSource} always use that argument and
 * ignore this global; the global only fills in the no-source overloads.
 */
public final class Source {

    private static volatile CaptureSource current;

    private Source() {}

    /**
     * The current global capture source, initialised lazily to the project default (or the whole
     * {@link Desktop} if none is configured). Never {@code null}.
     */
    public static CaptureSource current() {
        CaptureSource c = current;
        if (c == null) {
            synchronized (Source.class) {
                c = current;
                if (c == null) {
                    c = resolveDefault();
                    current = c;
                }
            }
        }
        return c;
    }

    /**
     * Override the global capture source until it is changed again. Passing {@code null} resets it
     * back to the project default (or the {@link Desktop}).
     */
    public static void set(CaptureSource source) {
        current = (source == null) ? resolveDefault() : source;
        Debug.log("[Source] set -> " + current);
    }

    private static CaptureSource resolveDefault() {
        CaptureSource projectDefault = ProjectDefaults.source();
        CaptureSource resolved = projectDefault != null ? projectDefault : CaptureSource.desktop();
        Debug.log("[Source] default -> " + resolved);
        return resolved;
    }
}
