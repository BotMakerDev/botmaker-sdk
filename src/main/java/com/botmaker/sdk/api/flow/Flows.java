package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.palette.Palette;

/**
 * The bot's own flow, handed to the SDK once.
 *
 * <p>One call, from the file BotMaker gave your project:
 *
 * <pre>{@code
 * public static void install() {
 *     Flows.use(flow());
 * }
 * }</pre>
 *
 * <p>and {@code main} calls {@code Sdk.install()}. That is the whole hand-off, and it is deliberately the
 * dullest thing in this package: an ordinary static call, compile-checked, with no reflection, no service
 * loader and no file. {@link FlowGraph} then walks what was installed.
 *
 * <p><b>Why it is a call rather than something the SDK finds.</b> Anything the SDK discovered — a known
 * class name, an annotation scan, a {@code ServiceLoader} — would be a second way for a bot to be wrong that
 * the compiler could not see. A method that is not called is a method a reader can find with the same
 * search they would use for any other.
 *
 * <p>Nothing is locked: {@code install()} is in your file and is yours to change, call twice, or replace
 * with a flow you build by hand. The editor refuses one thing, which is the body of the
 * {@code @Managed("flow")} method it writes.
 */
@Palette(category = "flow", categoryLabel = "Flow", icon = "⑃", order = 99)
public final class Flows {

    private static volatile Flow current = Flow.NONE;

    private Flows() {}

    /**
     * Uses {@code flow} from now on. {@code null} clears it back to {@link Flow#NONE}, which runs nothing.
     *
     * <p>Installing twice keeps the later flow, which is what defining the same activity twice has always
     * done, and what a reader would expect of a call named like this one.
     */
    public static void use(Flow flow) {
        current = flow == null ? Flow.NONE : flow;
    }

    /** The installed flow, or {@link Flow#NONE} when nothing has been installed. Never {@code null}. */
    public static Flow installed() {
        return current;
    }
}
