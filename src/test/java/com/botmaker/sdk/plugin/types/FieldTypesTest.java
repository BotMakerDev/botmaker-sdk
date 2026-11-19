// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.types;

import com.botmaker.java.grammar.BotNames;
import com.botmaker.java.grammar.JavaValue;
import com.botmaker.java.grammar.SourceNode;
import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Field;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.bot.BotConstants;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A form's rule as Studio reads and writes it: each check's field a lazy reference to its screen's input. */
class FieldTypesTest {

    private static final String SCREENS = "com.botmaker.sdk.api.screens.";

    private static final ValueGrammar GRAMMAR = ValueGrammar.of(List.of(), List.of(ScreenTypes.FIELD_FILLED_SHAPE,
            ScreenTypes.FIELD_VALID_SHAPE, ScreenTypes.FIELD_HOLDS_SHAPE, ScreenTypes.FIELD_LACKS_SHAPE,
            ScreenTypes.FIELD_AT_LEAST_SHAPE, ScreenTypes.FIELD_IN_RANGE_SHAPE, ScreenTypes.CONDITION_ALL_SHAPE));

    /** The screens' fields, as the core's lookup names them: {@code Screens.S.f}. */
    private static final BotNames NAMES = new BotNames() {
        @Override
        public Optional<Object> named(QualifiedName written) {
            String[] parts = written.getFullyQualifiedName().split("\\.");
            return parts.length == 3 && parts[0].equals("Screens")
                    ? Optional.of(BotConstants.input(parts[1], parts[2])) : Optional.empty();
        }

        @Override
        public Optional<JavaValue> spell(Object value) {
            BotConstants.InputName input = value instanceof Input in ? BotConstants.inputName(in) : null;
            return input == null ? Optional.empty() : JavaValue.parse("Screens." + input.screen() + "." + input.field());
        }
    };

    @Test
    void theSignUpRuleIsWrittenByItsChecksAndReadBackEqual() {
        Supplier<Button> email = () -> BotConstants.input("SIGN_UP", "email");
        Supplier<Button> password = () -> BotConstants.input("SIGN_UP", "password");
        Condition rule = Condition.all(Field.valid(email, Field.Kind.EMAIL), Field.atLeast(password, 8));

        String java = GRAMMAR.write(BooleanSupplier.class, rule, NAMES).orElseThrow().source();
        assertEquals("Condition.all(Field.valid(() -> Screens.SIGN_UP.email, Field.Kind.EMAIL), "
                + "Field.atLeast(() -> Screens.SIGN_UP.password, 8))", java);

        Condition.All read = (Condition.All) read(Condition.All.class, java);
        Field valid = (Field) read.conditions().getFirst();
        assertEquals(BotConstants.input("SIGN_UP", "email"), valid.input().get());
        assertEquals(java, GRAMMAR.write(BooleanSupplier.class, read, NAMES).orElseThrow().source(), "written back");
    }

    @Test
    void everyCheckIsWrittenAndReadBack() {
        Supplier<Button> age = () -> BotConstants.input("SIGN_UP", "age");
        for (Field field : List.of(Field.filled(age), Field.holds(age, "@"), Field.lacks(age, "@"),
                Field.inRange(age, 18, 120), Field.inRange(age, 18, Double.MAX_VALUE))) {
            String java = GRAMMAR.write(BooleanSupplier.class, field, NAMES).orElseThrow().source();
            Field read = (Field) read(Field.class, java);
            assertEquals(field.getClass(), read.getClass(), java);
            assertEquals(java, GRAMMAR.write(BooleanSupplier.class, read, NAMES).orElseThrow().source());
        }
    }

    private static Object read(Class<?> type, String java) {
        String qualified = java.replaceAll("(?<![.\\w])(Condition|Field)\\.", SCREENS + "$1.");
        return GRAMMAR.read(type, SourceNode.parse(qualified).orElseThrow(), NAMES)
                .orElseThrow(() -> new AssertionError("read nothing of " + qualified));
    }
}
