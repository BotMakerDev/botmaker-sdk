// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.api.screens.Field;
import com.botmaker.shared.web.FieldKind;
import com.botmaker.shared.web.FormRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a form's tries showed, as the answers the bot keeps: a submit's typed rule, a field's message as its note. */
class FormRulesTest {

    private static final Map<String, String> FIELDS = Map.of("email", "email", "password", "password",
            "create account", "createAccount");
    private static final Function<String, Optional<String>> FIELD_OF =
            label -> Optional.ofNullable(FIELDS.get(label.strip().toLowerCase()));
    private static final FormRule.Try TRIED = new FormRule.Try(new FormRule.Need.Filled("Email"), Map.of("Email", "a"),
            new FormRule.Answer(false, Map.of(), Set.of(), List.of()));

    @Test
    void aSubmitsRuleIsItsTypedChecksAndAMessageANoteOnItsField() {
        List<FormRule> rules = List.of(
                new FormRule.Enables("Create account", List.of(new FormRule.Need.Valid("Email", FieldKind.EMAIL),
                        new FormRule.Need.AtLeast("Password", 8)), List.of(TRIED)),
                new FormRule.Shows("Email", "Enter a valid email", new FormRule.Need.Valid("Email", FieldKind.EMAIL),
                        List.of(TRIED)));

        List<TreeAnswer> answers = FormRules.answers("SIGN_UP", rules, FIELD_OF);

        TreeAnswer.InputRule rule = assertInstanceOf(TreeAnswer.InputRule.class, answers.get(0));
        assertEquals("createAccount", rule.field());
        assertEquals(List.of(TreeAnswer.FieldCheck.valid("email", Field.Kind.EMAIL),
                TreeAnswer.FieldCheck.atLeast("password", 8)), rule.checks());
        assertTrue(rule.note().contains("; tried: "), rule.note());
        TreeAnswer.InputNote note = assertInstanceOf(TreeAnswer.InputNote.class, answers.get(1));
        assertEquals("email", note.field());
        assertTrue(note.note().contains("Enter a valid email"), note.note());
    }

    @Test
    void aRuleNoCheckSaysWhollyIsANoteNeverAWeakerRule() {
        List<FormRule> rules = List.of(
                new FormRule.Enables("Create account", List.of(new FormRule.Need.Filled("Email"),
                        new FormRule.Need.Valid("Password", FieldKind.TEL)), List.of()),
                new FormRule.Enables("Create account", List.of(new FormRule.Need.Filled("Company")), List.of()),
                new FormRule.Enables("Nowhere", List.of(new FormRule.Need.Filled("Email")), List.of()),
                new FormRule.Enables("Create account", List.of(), List.of()));

        List<TreeAnswer> answers = FormRules.answers("SIGN_UP", rules, FIELD_OF);

        assertEquals(3, answers.size(), "a submit the screen lacks gives nothing: " + answers);
        answers.forEach(answer -> assertInstanceOf(TreeAnswer.InputNote.class, answer));
    }

    @Test
    void aRangeIsCheckedOnlyWhenItsBoundsRead() {
        assertEquals(Optional.of(TreeAnswer.FieldCheck.inRange("age", 18.0, null)),
                FormRules.check("age", new FormRule.Need.InRange("Age", "18", "")));
        assertEquals(Optional.empty(), FormRules.check("age", new FormRule.Need.InRange("Age", "", "")));
        assertEquals(Optional.empty(), FormRules.check("age", new FormRule.Need.InRange("Age", "x", "9")));
        assertEquals(Optional.empty(), FormRules.check("age", new FormRule.Need.InRange("Age", "9", "1")));
        assertEquals(Optional.empty(), FormRules.check("name", new FormRule.Need.Holds("Name", "")));
        assertEquals(Optional.empty(), FormRules.check("name", new FormRule.Need.AtLeast("Name", 0)));
    }

    @Test
    void aBrowsersKindIsTheChecksKindOnlyForWhatACheckJudges() {
        for (FieldKind kind : FieldKind.values()) {
            Field.Kind expected = switch (kind) {
                case EMAIL -> Field.Kind.EMAIL;
                case URL -> Field.Kind.URL;
                case NUMBER -> Field.Kind.NUMBER;
                default -> Field.Kind.UNKNOWN;
            };
            assertEquals(expected, FormRules.kind(kind), kind.name());
        }
        assertEquals(Field.Kind.UNKNOWN, FormRules.kind(null));
    }
}
