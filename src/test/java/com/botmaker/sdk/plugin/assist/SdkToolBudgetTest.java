package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.ToolSchema;
import com.botmaker.sdk.plugin.map.MapReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the SDK's tools cost an assistant before it calls one: each tool's name, description and input schema as
 * Studio serves them ({@code sdk_<name>}, the description followed by " (from BotMaker SDK)", the schema written as
 * Studio's {@code McpTools.schemaOf} writes it), and the guide {@code plugin_guide} answers. A client that does not
 * defer tools pays all of it on every request. The sizes are written to {@code target/sdk-tool-budget.tsv}, biggest
 * first, and the total may not pass {@link #CEILING}. Studio's own tools have their own budget ({@code McpBudgetTest}).
 */
class SdkToolBudgetTest {

    /**
     * Characters, the tools and the guide together; about four to a token. 44,939 on 2026-10-15, before the tools
     * were typed and merged; 44,060 once typed, measured with the nested schema Studio writes (crop_pictures takes
     * boxes, try_input inputs, edit_map one of its actions), 61 tools where there were 63; 39,778 once the flow,
     * picture, tag and setup tools were merged, 37 tools.
     */
    static final int CEILING = 45_000;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FROM = " (from BotMaker SDK)";

    @Test
    void theSdksToolsStayWithinTheirBudget() throws Exception {
        List<Cost> costs = new ArrayList<>();
        for (AssistantTool<?> tool : SdkAssist.and(MapReport.TOOLS)) {
            costs.add(new Cost("sdk_" + tool.name(), tool.description().length() + FROM.length(),
                    JSON.writeValueAsString(schema(tool.schema().fields(), tool.schema().actions())).length()));
        }
        costs.sort(Comparator.comparingInt(Cost::chars).reversed());
        int guide = SdkAssist.GUIDE.length();
        int total = guide + costs.stream().mapToInt(Cost::chars).sum();

        StringBuilder report = new StringBuilder("tool\tchars\tdescription\tschema\n");
        report.append("(guide)\t").append(guide).append("\t\t\n");
        costs.forEach(c -> report.append(c.name()).append('\t').append(c.chars()).append('\t')
                .append(c.description()).append('\t').append(c.schema()).append('\n'));
        report.append("(total, ").append(costs.size()).append(" tools)\t").append(total).append("\t\t\n");
        Path out = Path.of("target", "sdk-tool-budget.tsv");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);

        assertTrue(total <= CEILING, "The SDK's tools and guide are " + total + " characters, past the " + CEILING
                + " budget: shorten what grew (" + out.toAbsolutePath() + ").");
    }

    /** One tool's size as {@code tools/list} sends it. */
    private record Cost(String name, int description, int schema) {
        int chars() {
            return name.length() + description + schema;
        }
    }

    /** {@code fields} as Studio's {@code McpTools.schemaOf} writes them: nested objects, lists, actions said flat. */
    private static Map<String, Object> schema(List<ToolSchema.Field> fields, List<ToolSchema.Action> actions) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolSchema.Field field : fields) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", field.kind().jsonType());
            switch (field.kind()) {
                case TEXT_LIST -> property.put("items", Map.of("type", "string"));
                case CHOICE_LIST -> property.put("items", Map.of("type", "string", "enum", field.choices()));
                case RECORD -> property.putAll(schema(field.fields(), List.of()));
                case RECORD_LIST -> property.put("items", schema(field.fields(), field.oneOf()));
                case TEXT_MAP -> property.put("additionalProperties", Map.of("type", "string"));
                default -> {
                    if (!field.choices().isEmpty()) property.put("enum", field.choices());
                }
            }
            String description = field.name().equals(AssistantTool.ACTION) && !actions.isEmpty()
                    ? actions.stream().map(a -> a.id() + "(" + String.join(", ", a.required()) + ")"
                    + (a.description().isEmpty() ? "" : ": " + a.description())).collect(Collectors.joining("; "))
                    : field.description() + (field.actions().isEmpty() ? "" : " (" + String.join(", ", field.actions())
                    + ")");
            if (!description.isBlank()) property.put("description", description.strip());
            properties.put(field.name(), property);
            if (!field.optional()) required.add(field.name());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }
}
