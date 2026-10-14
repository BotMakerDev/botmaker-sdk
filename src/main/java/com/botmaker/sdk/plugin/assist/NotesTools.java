package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.flow.FlowValue;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The bot's notes on its game ({@link GameNotes}) and the protocol's steps for where the bot is ({@link Protocol}):
 * read_notes and write_notes keep what each session learnt, and read_guide works out the stage from them.
 */
final class NotesTools {

    record Section(@Describe(value = "one section's heading: Campaign; empty for all", optional = true)
                   String section) {
    }

    record Note(@Describe("the section's heading, one line: a screen's name as the game shows it, or a topic: "
            + "Getting around") String section,
                @Describe("its whole new text, Markdown; empty removes the section") String text) {
    }

    record Which(@Describe(value = "a stage by number or name (0 or Setup … 5 or Upkeep); empty for the one the "
            + "bot is at", optional = true) String stage) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("read_notes")
                    .describedAs("Read the bot's notes on its game (" + GameNotes.FILE + "): what earlier sessions "
                            + "learnt automating it, what each screen holds and where each stands. Read them before "
                            + "exploring; the map of the screens is the bot's Screens class")
                    .takes(Section.class).handledBy(NotesTools::readNotes),
            AssistantTool.named("write_notes")
                    .describedAs("Save what you learnt about the game in the bot's notes (" + GameNotes.FILE + "), "
                            + "one section at a time, so the next session starts from it. " + GameNotes.SHAPE
                            + " Short lines, one fact each. The section's whole text is replaced: read_notes it "
                            + "first to keep what holds")
                    .takes(Note.class).handledBy(NotesTools::writeNotes),
            AssistantTool.named("read_guide")
                    .describedAs("The steps of the stage the bot is at, worked out from its home and popups methods, "
                            + "its Screens and its notes (each screen's Stage line), with where each screen stands; "
                            + "or one "
                            + "stage's steps by its number or name. Read it before starting and whenever a stage's "
                            + "check passes")
                    .takes(Which.class).handledBy(NotesTools::readGuide));

    private NotesTools() {}

    static AgentReply readNotes(Section section, AgentContext context) {
        Path project = context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        try {
            return AgentReply.text(GameNotes.read(project, section.section()));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return AgentReply.refused(e.getMessage());
        }
    }

    static AgentReply writeNotes(Note note, AgentContext context) {
        Path project = context.services().projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        // Only an empty text removes a section: a text left out is a mistake, not a removal.
        if (note.text() == null) return AgentReply.refused("Give the section's text; an empty one removes it.");
        try {
            return AgentReply.text(GameNotes.write(project, note.section(), note.text()));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return AgentReply.refused(e.getMessage());
        }
    }

    static AgentReply readGuide(Which which, AgentContext context) {
        if (which.stage() != null && !which.stage().isBlank()) {
            Optional<Protocol.Stage> stage = Protocol.Stage.fromId(which.stage());
            return stage.map(s -> AgentReply.text(s.section())).orElseGet(() -> AgentReply.refused("No stage "
                    + which.stage().strip() + ". The stages: " + String.join(", ", Arrays.stream(Protocol.Stage.values())
                    .map(Protocol.Stage::title).toList()) + "."));
        }
        StudioServices services = context.services();
        Path project = services.projectDir();
        if (project == null) return AgentReply.refused(Frames.noProject());
        // Opened as read_flow opens it: the bot's home and popups are its run's.
        FlowValue.Target target = FxCall.call(() -> FlowValue.target(services));
        if (target.problem() != null) return AgentReply.refused(target.problem());
        try {
            ScreenGraph map = FxCall.call(() -> ScreenBook.read(services));
            return AgentReply.text(Protocol.where(target.run().home() != null, target.run().popups() != null,
                    map, GameNotes.sections(project)).said());
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            return AgentReply.refused(e.getMessage());
        }
    }
}
