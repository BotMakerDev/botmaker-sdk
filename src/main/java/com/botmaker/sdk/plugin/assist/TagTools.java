package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.plugin.pictures.TagCatalog;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateManifest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The assistant's picture tags, each edit the one 🖼 Manage Pictures and its tag manager make: a picture tagged,
 * untagged or given its whole set, a custom tag declared, renamed or deleted. A tag is offered from the
 * project's catalog and nothing else — an activity's own tag and the custom ones — so a typo is refused with the
 * names there are rather than becoming a group. An activity's tag follows its activity: it is not renamed or
 * deleted here.
 */
final class TagTools {

    record Tagging(@Describe("a declared tag, as list_tags names it") String tag,
                   @Describe("the pictures' names, as list_pictures gives them") List<String> pictures) {
    }

    record SetTags(@Describe("a picture's name, as list_pictures gives it") String picture,
                   @Describe("every tag it should carry, each declared; empty leaves it untagged") List<String> tags) {
    }

    record TagName(@Describe("the tag's name: a label, spaces allowed") String tag) {
    }

    record RenameTag(@Describe("a custom tag, as list_tags names it") String tag,
                     @Describe("its new name") String to) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("list_tags")
                    .describedAs("The project's picture tags, an activity's own first, then the custom ones, each "
                            + "with the pictures it groups, then the untagged pictures")
                    .takesNothing().handledBy(TagTools::listTags),
            AssistantTool.named("tag_pictures")
                    .describedAs("Adds a declared tag to pictures, keeping the tags they have")
                    .takes(Tagging.class).handledBy(TagTools::tagPictures),
            AssistantTool.named("untag_pictures")
                    .describedAs("Takes a tag off pictures; the tag stays declared")
                    .takes(Tagging.class).handledBy(TagTools::untagPictures),
            AssistantTool.named("set_tags")
                    .describedAs("Gives one picture exactly these tags, replacing the ones it has")
                    .takes(SetTags.class).handledBy(TagTools::setTags),
            AssistantTool.named("declare_tag")
                    .describedAs("Declares a custom tag, for grouping pictures that no activity owns (home, popups)")
                    .takes(TagName.class).handledBy(TagTools::declareTag),
            AssistantTool.named("rename_tag")
                    .describedAs("Renames a custom tag; the pictures carrying it follow")
                    .takes(RenameTag.class).handledBy(TagTools::renameTag),
            AssistantTool.named("delete_tag")
                    .describedAs("Deletes a custom tag and takes it off every picture; the pictures stay")
                    .takes(TagName.class).handledBy(TagTools::deleteTag));

    private TagTools() {}

    static AgentReply listTags(AssistantTool.None none, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        StudioServices services = context.services();
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(services));
        Map<String, List<Path>> grouped = FxCall.call(() -> TemplateLibrary.listByTag(services));
        List<String> lines = new ArrayList<>();
        for (TagCatalog.Tag tag : catalog.tags()) {
            lines.add(tag.name() + (tag.isManaged() ? " (activity)" : "") + ": "
                    + names(grouped.getOrDefault(tag.name(), List.of())));
        }
        if (lines.isEmpty()) lines.add("No tags yet: declare_tag makes one, and each activity has its own.");
        List<Path> untagged = grouped.getOrDefault(TemplateManifest.UNTAGGED, List.of());
        if (!untagged.isEmpty()) lines.add("untagged: " + names(untagged));
        return AgentReply.text(String.join("\n", lines));
    }

    static AgentReply tagPictures(Tagging tagging, AgentContext context) {
        return onPictures(tagging, context, true);
    }

    static AgentReply untagPictures(Tagging tagging, AgentContext context) {
        return onPictures(tagging, context, false);
    }

    private static AgentReply onPictures(Tagging tagging, AgentContext context, boolean add) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        TagCatalog.Tag tag = catalog.find(tagging.tag());
        if (tag == null) return AgentReply.refused(undeclared(tagging.tag(), catalog));
        Picked picked = pictures(resources.get(), tagging.pictures());
        if (picked.refusal() != null) return AgentReply.refused(picked.refusal());
        if (add) {
            FxCall.call(() -> {
                TemplateLibrary.addTag(context.services(), picked.names(), tag.name());
                return null;
            });
            return AgentReply.text("Tagged " + String.join(", ", picked.names()) + " " + tag.name() + ".");
        }
        TemplateManifest manifest = TemplateLibrary.manifest(resources.get());
        List<String> had = picked.names().stream().filter(n -> manifest.tagsOf(n).contains(tag.name())).toList();
        if (had.isEmpty()) return AgentReply.refused("None of " + String.join(", ", picked.names()) + " carries "
                + tag.name() + ".");
        FxCall.call(() -> {
            TemplateLibrary.removeTag(resources.get(), had, tag.name());
            return null;
        });
        return AgentReply.text("Took " + tag.name() + " off " + String.join(", ", had) + ".");
    }

    static AgentReply setTags(SetTags set, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), set.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(set.picture()));
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        List<String> tags = set.tags() == null ? List.of() : set.tags();
        for (String tag : tags) {
            if (!catalog.isDeclared(tag)) return AgentReply.refused(undeclared(tag, catalog));
        }
        FxCall.call(() -> {
            TemplateLibrary.applyTags(context.services(), Map.of(base.get(), tags));
            return null;
        });
        List<String> now = catalog.declaredOnly(tags);
        return AgentReply.text(base.get() + (now.isEmpty() ? " is untagged." : " carries " + String.join(", ", now)
                + "."));
    }

    static AgentReply declareTag(TagName name, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        String tag = TemplateManifest.sanitizeTag(name.tag());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        String problem = catalog.nameProblem(tag, null);
        if (problem != null) return AgentReply.refused(problem);
        FxCall.call(() -> TemplateLibrary.declareTag(context.services(), tag));
        return AgentReply.text("Declared " + tag + "; tag_pictures puts it on pictures.");
    }

    static AgentReply renameTag(RenameTag rename, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        TagCatalog.Tag tag = catalog.find(rename.tag());
        if (tag == null) return AgentReply.refused(undeclared(rename.tag(), catalog));
        if (tag.isManaged()) {
            return AgentReply.refused(tag.name() + " follows its activity: rename the activity to rename it.");
        }
        String to = TemplateManifest.sanitizeTag(rename.to());
        String problem = catalog.nameProblem(to, tag.name());
        if (problem != null) return AgentReply.refused(problem);
        FxCall.call(() -> {
            TemplateLibrary.saveManifest(resources.get(),
                    TemplateLibrary.manifest(resources.get()).renamedTag(tag.name(), to));
            return null;
        });
        return AgentReply.text("Renamed " + tag.name() + " to " + to + ".");
    }

    static AgentReply deleteTag(TagName name, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        TagCatalog.Tag tag = catalog.find(name.tag());
        if (tag == null) return AgentReply.refused(undeclared(name.tag(), catalog));
        if (tag.isManaged()) {
            return AgentReply.refused(tag.name() + " belongs to its activity: remove the activity to remove it.");
        }
        FxCall.call(() -> {
            TemplateLibrary.saveManifest(resources.get(),
                    TemplateLibrary.manifest(resources.get()).undeclaring(tag.name()));
            return null;
        });
        return AgentReply.text("Deleted " + tag.name() + "; its pictures stay.");
    }

    /** Each named picture's file name, or why one is not a picture. */
    private record Picked(List<String> names, String refusal) {
    }

    private static Picked pictures(Path resources, List<String> typed) {
        if (typed == null || typed.isEmpty()) return new Picked(List.of(), "Name at least one picture.");
        List<String> names = new ArrayList<>();
        for (String picture : typed) {
            Optional<String> base = Frames.picture(resources, picture);
            if (base.isEmpty()) return new Picked(List.of(), Frames.noPicture(picture));
            if (!names.contains(base.get())) names.add(base.get());
        }
        return new Picked(List.copyOf(names), null);
    }

    private static String undeclared(String tag, TagCatalog catalog) {
        return "No tag called \"" + tag + "\"" + (catalog.names().isEmpty() ? "; declare_tag makes one."
                : "; there are " + String.join(", ", catalog.names()) + ", and declare_tag makes another.");
    }

    /** {@code a, b} by base name, or {@code none}. */
    private static String names(List<Path> files) {
        List<String> names = files.stream().filter(f -> !TemplateLibrary.isUnmodifiedDefaultTemplate(f))
                .map(TemplateLibrary::baseName).toList();
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
