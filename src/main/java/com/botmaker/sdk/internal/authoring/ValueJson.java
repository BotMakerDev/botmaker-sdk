package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.Range;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.api.value.Visibility;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

/**
 * Jackson's half of the value vocabulary — kept here because the vocabulary itself does not carry it.
 *
 * <h2>Why the contract has no Jackson annotations</h2>
 *
 * <p>{@code botmaker-studio-api} has exactly one dependency, {@code javafx-controls}, at {@code provided}
 * scope. Adding Jackson to it would make every plugin that compiles against the contract resolve a JSON
 * library it may never use, and would tie the contract to that library's own compatibility rate. So the
 * contract declares the wire <em>form</em> — an id out, a total factory back — and whoever owns the file
 * supplies the parser. This class is the SDK doing that for {@code activities.json}.
 *
 * <h2>What is written, and why it is byte-for-byte what was written before</h2>
 *
 * <p>A {@link ValueChoice} is {@code {"type": "<id>", "shape": "<SHAPE>"}} and a {@link Visibility} is its
 * {@code id()} — exactly what the enum-based vocabulary wrote. That is not luck to be preserved by accident:
 * {@link ValueType#id()} is deliberately the name its old enum constant had, and these serializers are what
 * make every project ever written keep its meaning rather than merely intend to.
 *
 * <h2>Reading is total, and that is the whole point of an open vocabulary</h2>
 *
 * <p>A type id nothing in {@code catalog} registered does not throw and does not silently become text: it
 * comes back as {@link ValueType#unknown}, keeps the value the file holds, and declines to emit. Before the
 * vocabulary opened, that state was unreachable; with plugins it is the ordinary state of a project opened
 * without one of them installed, and the alternative — refusing the file, or coercing the value — destroys a
 * user's data because a jar is missing.
 */
public final class ValueJson {

    private ValueJson() {
    }

    /**
     * A Jackson module reading and writing the vocabulary against {@code catalog}.
     *
     * <p>The catalog is a parameter rather than {@link SdkValueTypes#CATALOG} because a host loading plugins
     * hands in the merged one; the SDK alone is simply the case where there is nothing to merge.
     */
    public static SimpleModule module(ValueCatalog catalog) {
        SimpleModule m = new SimpleModule("botmaker-value");
        m.addSerializer(ValueType.class, new TypeOut());
        m.addDeserializer(ValueType.class, new TypeIn(catalog));
        m.addSerializer(ValueChoice.class, new ChoiceOut());
        m.addDeserializer(ValueChoice.class, new ChoiceIn(catalog));
        m.addSerializer(Visibility.class, new VisibilityOut());
        m.addDeserializer(Visibility.class, new VisibilityIn());
        m.addSerializer(Range.class, new RangeOut());
        m.addDeserializer(Range.class, new RangeIn());
        return m;
    }

    // ---- type -------------------------------------------------------------------------------------------

    /** A bare type is its id. It only appears alone in a hand-written file; inside a choice it is nested. */
    private static final class TypeOut extends JsonSerializer<ValueType> {
        @Override
        public void serialize(ValueType v, JsonGenerator g, SerializerProvider p) throws IOException {
            g.writeString(v.id());
        }
    }

    private static final class TypeIn extends JsonDeserializer<ValueType> {
        private final ValueCatalog catalog;

        TypeIn(ValueCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public ValueType deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return catalog.type(p.getValueAsString());
        }
    }

    // ---- choice -----------------------------------------------------------------------------------------

    private static final class ChoiceOut extends JsonSerializer<ValueChoice> {
        @Override
        public void serialize(ValueChoice v, JsonGenerator g, SerializerProvider p) throws IOException {
            g.writeStartObject();
            g.writeStringField("type", v.type().id());
            g.writeStringField("shape", v.shape().name());
            g.writeEndObject();
        }
    }

    /**
     * Reads through {@link ValueChoice#fromWire}, which is where the two legacy readings live — the old
     * {@code CHOICE} pseudo-type, and the {@code list} boolean that predates {@code shape}. A bare string is
     * accepted too: that is what a type written before shapes existed at all looks like.
     */
    private static final class ChoiceIn extends JsonDeserializer<ValueChoice> {
        private final ValueCatalog catalog;

        ChoiceIn(ValueCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        public ValueChoice deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode n = p.readValueAsTree();
            if (n == null || n.isNull()) return null;
            if (n.isTextual()) return ValueChoice.fromWire(catalog, n.asText(), null, null);
            JsonNode list = n.get("list");
            return ValueChoice.fromWire(catalog, text(n.get("type")), text(n.get("shape")),
                    list == null || !list.isBoolean() ? null : list.asBoolean());
        }
    }

    // ---- visibility -------------------------------------------------------------------------------------

    private static final class VisibilityOut extends JsonSerializer<Visibility> {
        @Override
        public void serialize(Visibility v, JsonGenerator g, SerializerProvider p) throws IOException {
            g.writeString(v.id());
        }
    }

    private static final class VisibilityIn extends JsonDeserializer<Visibility> {
        @Override
        public Visibility deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return Visibility.fromId(p.getValueAsString());
        }
    }

    // ---- range ------------------------------------------------------------------------------------------
    //
    // Written by hand rather than left to Jackson's record support for one reason: `isEmpty()` reads as a
    // boolean getter, so the default serializer would put an `"empty"` member into every stored bound. The
    // vocabulary carries no `@JsonIgnore` to say otherwise, so the answer lives here.

    private static final class RangeOut extends JsonSerializer<Range> {
        @Override
        public void serialize(Range v, JsonGenerator g, SerializerProvider p) throws IOException {
            g.writeStartObject();
            g.writeStringField("min", v.min());
            g.writeStringField("max", v.max());
            g.writeEndObject();
        }
    }

    private static final class RangeIn extends JsonDeserializer<Range> {
        @Override
        public Range deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode n = p.readValueAsTree();
            if (n == null || n.isNull()) return Range.NONE;
            return new Range(text(n.get("min")), text(n.get("max")));
        }
    }

    // ---- plumbing ---------------------------------------------------------------------------------------

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }
}
