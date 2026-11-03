package com.botmaker.sdk.api.console;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.run.AskKind;
import com.botmaker.plugin.api.run.RunChannel;
import com.botmaker.sdk.internal.trace.SdkRun;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/**
 * Asks the person running the bot a question and waits for the answer.
 *
 * <pre>{@code
 * String mode = Ask.choice("Mode?", "farm", "fight");
 * int rounds = Ask.whole("How many rounds?");
 * if (Ask.yesNo("Sell the loot?")) sellLoot();
 * }</pre>
 *
 * <p><b>Under Studio the question is a dialog</b>, asked through the run's channel to its host and answered there: a
 * choice is a list, yes/no is two buttons. <b>Anywhere else it is the console</b>: the prompt is printed and the
 * answer read from standard input, so a bot run from a terminal still asks. An answer that is blank or does not
 * read as what was asked for is asked again. Cancelling the dialog, or the end of standard input, throws
 * {@link CancellationException}, which stops the bot unless it catches it.
 *
 * <p>Replaces {@code BotMaker.readLine()}/{@code readInt()}/{@code readDouble()}/{@code readBoolean()}
 * (2026-10-01), which printed a marker Studio scanned the run's output for, and could not say what they asked.
 */
@Palette(category = "console", categoryLabel = "Console", icon = "❓")
public final class Ask {

    private Ask() {}

    /** A line of text; a blank answer is asked again. */
    public static String text(String prompt) {
        return ask(channel(), AskKind.TEXT, prompt, List.of(), answer -> answer.isEmpty() ? null : answer);
    }

    /** A number, fractions allowed ({@code 2.5}). */
    public static double number(String prompt) {
        return ask(channel(), AskKind.NUMBER, prompt, List.of(), Ask::readNumber);
    }

    /** A whole number ({@code 12}). */
    public static int whole(String prompt) {
        return ask(channel(), AskKind.WHOLE, prompt, List.of(), Ask::readWhole);
    }

    /** Yes or no. On the console {@code y}, {@code yes}, {@code n} and {@code no} are understood. */
    public static boolean yesNo(String prompt) {
        return ask(channel(), AskKind.YES_NO, prompt, List.of(), Ask::readYesNo);
    }

    /**
     * One of {@code options}, answered as the option's text. On the console the options are numbered, and the
     * number or the text is accepted.
     *
     * @throws IllegalArgumentException when there is no option to choose
     */
    public static String choice(String prompt, String... options) {
        if (options == null || options.length == 0) throw new IllegalArgumentException("nothing to choose from");
        List<String> choices = List.of(options);
        return ask(channel(), AskKind.CHOICE, prompt, choices, answer -> readChoice(answer, choices));
    }

    // --- Where a question goes ---

    /** Asks once and answers the raw reply, or {@code null} when the person cancelled. */
    interface Channel {
        String ask(AskKind kind, String prompt, List<String> choices);
    }

    /** The host's dialog when the run's host can ask, the console otherwise. */
    private static Channel channel() {
        RunChannel run = SdkRun.channel();
        Channel console = console(System.out);
        return (kind, prompt, choices) -> run.ask(kind, prompt, choices)
                .orElseGet(() -> console.ask(kind, prompt, choices));
    }

    /** One reader for the whole run: a second one over {@code System.in} would lose what the first buffered. */
    private static BufferedReader stdin;

    private static synchronized BufferedReader stdin() {
        if (stdin == null) stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return stdin;
    }

    static Channel console(PrintStream out) {
        return console(out, stdin());
    }

    static Channel console(PrintStream out, BufferedReader in) {
        return (kind, prompt, choices) -> {
            out.print(prompt);
            if (kind == AskKind.YES_NO) out.print(" (yes/no)");
            for (int i = 0; i < choices.size(); i++) out.print(System.lineSeparator() + "  " + (i + 1) + ") " + choices.get(i));
            out.print(System.lineSeparator() + "> ");
            out.flush();
            try {
                return in.readLine(); // null at the end of input: nobody is there to answer
            } catch (IOException unreadable) {
                return null;
            }
        };
    }

    // --- Reading an answer ---

    /** Asks until {@code read} accepts the answer; a {@code null} from {@code read} means ask again. */
    static <T> T ask(Channel channel, AskKind kind, String prompt, List<String> choices, Function<String, T> read) {
        String question = prompt == null ? "" : prompt;
        while (true) {
            String answer = channel.ask(kind, question, choices);
            if (answer == null) throw new CancellationException("cancelled: " + prompt);
            T value = read.apply(answer.strip());
            if (value != null) return value;
            question = prompt + " " + hint(kind);
        }
    }

    private static String hint(AskKind kind) {
        return switch (kind) {
            case NUMBER -> "(a number, like 2.5)";
            case WHOLE -> "(a whole number, like 12)";
            case YES_NO -> "(yes or no)";
            case CHOICE -> "(one of the options)";
            case TEXT, UNKNOWN -> "(an answer is needed)";
        };
    }

    static Double readNumber(String answer) {
        try {
            double value = Double.parseDouble(answer.replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    static Integer readWhole(String answer) {
        try {
            return Integer.parseInt(answer);
        } catch (NumberFormatException notAWholeNumber) {
            return null;
        }
    }

    static Boolean readYesNo(String answer) {
        return switch (answer.toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true" -> Boolean.TRUE;
            case "n", "no", "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    static String readChoice(String answer, List<String> choices) {
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(answer)) return choice;
        }
        Integer index = readWhole(answer);
        return index != null && index >= 1 && index <= choices.size() ? choices.get(index - 1) : null;
    }
}
