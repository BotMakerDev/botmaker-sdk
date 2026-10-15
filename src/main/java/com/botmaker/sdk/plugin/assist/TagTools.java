package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.plugin.pictures.PictureTags;
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
 * untagged or given its whole set (edit_pictures' tag, untag and set_tags), a custom tag declared, renamed or deleted
 * (edit_tags). A tag is offered from the project's catalog and nothing else — an activity's own tag and the custom
 * ones — so a typo is refused with the names there are rather than becoming a group. An activity's tag follows its
 * activity: it is not renamed or deleted here.
 */
final class TagTools {

    /** One edit of the project's custom tags. */
    sealed interface Edit {
    }

    @Describe("declare a custom tag, for grouping pictures no activity owns (home, popups)")
    record Declare(@Describe("the tag: a label, spaces allowed; rename and delete take a custom one, as list_tags "
            + "names it") String tag) implements Edit {
    }

    @Describe("rename a custom tag; the pictures carrying it follow")
    record Rename(String tag, @Describe("its new name") String to) implements Edit {
    }

    @Describe("delete a custom tag and take it off every picture; the pictures stay")
    record Delete(String tag) implements Edit {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("list_tags")
                    .describedAs("The project's picture tags, an activity's own first, then the custom ones, each "
                            + "with the pictures it groups, then the untagged pictures")
                    .takesNothing().handledBy(TagTools::listTags),
            ToolTiming.actions("edit_tags", "Declares, renames or deletes a custom picture tag, as 🖼 Manage "
                    + "Pictures' tag manager does", Edit.class, TagTools::edit));

    private TagTools() {}

    static AgentReply edit(Edit edit, AgentContext context) {
        return switch (edit) {
            case Declare declare -> declareTag(declare, context);
            case Rename rename -> renameTag(rename, context);
            case Delete delete -> deleteTag(delete, context);
        };
    }

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
        if (lines.isEmpty()) lines.add("No tags yet: edit_tags' declare makes one, and each activity has its own.");
        List<Path> untagged = grouped.getOrDefault(TemplateManifest.UNTAGGED, List.of());
        if (!untagged.isEmpty()) lines.add("untagged: " + names(untagged));
        return AgentReply.text(String.join("\n", lines));
    }

    /** {@code typed} pictures given the declared tag {@code typedTag}, or ({@code add} false) it taken off them. */
    static AgentReply onPictures(String typedTag, List<String> typed, AgentContext context, boolean add) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        TagCatalog.Tag tag = catalog.find(typedTag);
        if (tag == null) return AgentReply.refused(undeclared(typedTag, catalog));
        Picked picked = pictures(resources.get(), typed);
        if (picked.refusal() != null) return AgentReply.refused(picked.refusal());
        if (add) {
            List<String> refused = FxCall.call(() -> TemplateLibrary.addTag(context.services(), picked.names(),
                    tag.name()));
            if (!refused.isEmpty()) return AgentReply.refused(String.join(" ", refused));
            return AgentReply.text("Tagged " + String.join(", ", picked.names()) + " " + tag.name() + ".");
        }
        TemplateManifest manifest = FxCall.call(() -> TemplateLibrary.manifest(context.services()));
        List<String> had = picked.names().stream().filter(n -> manifest.tagsOf(n).contains(tag.name())).toList();
        if (had.isEmpty()) return AgentReply.refused("None of " + String.join(", ", picked.names()) + " carries "
                + tag.name() + ".");
        List<String> refused = FxCall.call(() -> TemplateLibrary.removeTag(context.services(), had, tag.name()));
        if (!refused.isEmpty()) return AgentReply.refused(String.join(" ", refused));
        return AgentReply.text("Took " + tag.name() + " off " + String.join(", ", had) + ".");
    }

    /** {@code picture} given exactly {@code typedTags}, each declared. */
    static AgentReply setTags(String picture, List<String> typedTags, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), picture);
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(picture));
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        List<String> tags = typedTags == null ? List.of() : typedTags;
        for (String tag : tags) {
            if (!catalog.isDeclared(tag)) return AgentReply.refused(undeclared(tag, catalog));
        }
        List<String> refused = FxCall.call(() -> TemplateLibrary.applyTags(context.services(),
                Map.of(base.get(), tags)));
        if (!refused.isEmpty()) return AgentReply.refused(String.join(" ", refused));
        List<String> now = catalog.declaredOnly(tags);
        return AgentReply.text(base.get() + (now.isEmpty() ? " is untagged." : " carries " + String.join(", ", now)
                + "."));
    }

    static AgentReply declareTag(Declare declare, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        String tag = TemplateManifest.sanitizeTag(declare.tag());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        String problem = catalog.nameProblem(tag, null);
        if (problem != null) return AgentReply.refused(problem);
        Optional<String> refused = FxCall.call(() -> PictureTags.declare(context.services(), tag));
        if (refused.isPresent()) return AgentReply.refused(refused.get());
        return AgentReply.text("Declared " + tag + " in Tags; edit_pictures' tag puts it on pictures.");
    }

    static AgentReply renameTag(Rename rename, AgentContext context) {
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
        Optional<String> refused = FxCall.call(() -> PictureTags.rename(context.services(), tag.name(), to));
        if (refused.isPresent()) return AgentReply.refused(refused.get());
        return AgentReply.text("Renamed " + tag.name() + " to " + to + ".");
    }

    static AgentReply deleteTag(Delete delete, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        TagCatalog.Tag tag = catalog.find(delete.tag());
        if (tag == null) return AgentReply.refused(undeclared(delete.tag(), catalog));
        if (tag.isManaged()) {
            return AgentReply.refused(tag.name() + " belongs to its activity: remove the activity to remove it.");
        }
        Optional<String> refused = FxCall.call(() -> PictureTags.delete(context.services(), tag.name()));
        if (refused.isPresent()) return AgentReply.refused(refused.get());
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
        return "No tag called \"" + tag + "\"" + (catalog.names().isEmpty() ? "; edit_tags' declare makes one."
                : "; there are " + String.join(", ", catalog.names()) + ", and edit_tags' declare makes another.");
    }

    /** {@code a, b} by base name, or {@code none}. */
    private static String names(List<Path> files) {
        List<String> names = files.stream().filter(f -> !TemplateLibrary.isUnmodifiedDefaultTemplate(f))
                .map(TemplateLibrary::baseName).toList();
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
