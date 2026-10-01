package com.botmaker.sdk.api.console;

import com.botmaker.shared.ipc.TelemetryEvent.Ask.Kind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AskTest {

    /** Answers in order, and remembers each prompt it was shown. */
    private static final class Scripted implements Ask.Channel {
        final Deque<String> answers;
        final List<String> prompts = new ArrayList<>();

        Scripted(String... answers) {
            this.answers = new ArrayDeque<>(Arrays.asList(answers));
        }

        @Override
        public String ask(Kind kind, String prompt, List<String> choices) {
            prompts.add(prompt);
            return answers.isEmpty() ? null : answers.poll();
        }
    }

    @Test
    void an_answer_that_does_not_read_is_asked_again_with_a_hint() {
        Scripted channel = new Scripted("twelve", " 12 ");
        assertEquals(12, Ask.ask(channel, Kind.WHOLE, "Rounds?", List.of(), Ask::readWhole));
        assertEquals(List.of("Rounds?", "Rounds? (a whole number, like 12)"), channel.prompts);
    }

    @Test
    void a_cancel_throws() {
        assertThrows(CancellationException.class,
                () -> Ask.ask(new Scripted(), Kind.TEXT, "Name?", List.of(), s -> s));
    }

    @Test
    void each_kind_reads_its_answers() {
        assertEquals(2.5, Ask.readNumber("2,5"));
        assertNull(Ask.readNumber("NaN"));
        assertNull(Ask.readWhole("1.5"));
        assertTrue(Ask.readYesNo("Yes"));
        assertFalse(Ask.readYesNo("n"));
        assertNull(Ask.readYesNo("maybe"));
        List<String> modes = List.of("farm", "fight");
        assertEquals("fight", Ask.readChoice("FIGHT", modes));
        assertEquals("farm", Ask.readChoice("1", modes));
        assertNull(Ask.readChoice("3", modes));
        assertThrows(IllegalArgumentException.class, () -> Ask.choice("Mode?"));
    }

    @Test
    void the_console_numbers_the_choices_and_reads_a_line() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        Ask.Channel console = Ask.console(new PrintStream(printed, true, StandardCharsets.UTF_8),
                new BufferedReader(new StringReader("2\n")));
        String mode = Ask.ask(console, Kind.CHOICE, "Mode?", List.of("farm", "fight"),
                answer -> Ask.readChoice(answer, List.of("farm", "fight")));
        assertEquals("fight", mode);
        String shown = printed.toString(StandardCharsets.UTF_8);
        assertTrue(shown.contains("Mode?") && shown.contains("1) farm") && shown.contains("2) fight"), shown);
        // The end of input is nobody there to answer: a cancel, never a loop.
        assertThrows(CancellationException.class, () -> Ask.ask(console, Kind.TEXT, "Again?", List.of(), s -> s));
    }
}
