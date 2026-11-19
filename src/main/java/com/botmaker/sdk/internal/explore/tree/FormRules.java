// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.api.screens.Field;
import com.botmaker.shared.web.FieldKind;
import com.botmaker.shared.web.FormRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a form's tries showed ({@link FormRule}), as the bot keeps it (web discovery phase 7): the submit a rule enables
 * gets it as its {@code .when(…)}, a {@link TreeAnswer.InputRule} of typed field checks, and a field's message is a note
 * on that field. Each names its fields by the screen's field names, found from the labels the tries gave.
 */
public final class FormRules {

    private FormRules() {}

    /**
     * {@code rules}, of a form on {@code screen}, as answers; {@code fieldOf} the screen's field of a label or a
     * submit's name, empty when the screen has none. A rule whose fields are not all found, or whose need no check
     * says, is a note on its input instead, so a rule is never written weaker than the tries showed.
     */
    public static List<TreeAnswer> answers(String screen, List<FormRule> rules,
                                           Function<String, Optional<String>> fieldOf) {
        List<TreeAnswer> answers = new ArrayList<>();
        for (FormRule rule : rules) {
            switch (rule) {
                case FormRule.Enables enables -> {
                    Optional<String> submit = fieldOf.apply(enables.input());
                    if (submit.isEmpty()) continue;
                    List<TreeAnswer.FieldCheck> checks = new ArrayList<>();
                    for (FormRule.Need need : enables.needs()) {
                        Optional<TreeAnswer.FieldCheck> check = fieldOf.apply(need.field()).flatMap(f -> check(f, need));
                        if (check.isEmpty()) {
                            checks = null;
                            break;
                        }
                        checks.add(check.get());
                    }
                    answers.add(checks == null || checks.isEmpty() ? new TreeAnswer.InputNote(screen, submit.get(), said(rule))
                            : new TreeAnswer.InputRule(screen, submit.get(), checks, said(rule)));
                }
                case FormRule.Shows shows -> fieldOf.apply(shows.field())
                        .ifPresent(field -> answers.add(new TreeAnswer.InputNote(screen, field, said(rule))));
            }
        }
        return List.copyOf(answers);
    }

    /** The check of {@code need}, on the screen's field {@code field}; empty for a need no check says. */
    static Optional<TreeAnswer.FieldCheck> check(String field, FormRule.Need need) {
        return switch (need) {
            case FormRule.Need.Filled ignored -> Optional.of(TreeAnswer.FieldCheck.filled(field));
            case FormRule.Need.Valid valid -> {
                Field.Kind kind = kind(valid.kind());
                yield kind == Field.Kind.UNKNOWN ? Optional.empty() : Optional.of(TreeAnswer.FieldCheck.valid(field, kind));
            }
            case FormRule.Need.Holds holds -> holds.text().isEmpty() ? Optional.empty()
                    : Optional.of(TreeAnswer.FieldCheck.holds(field, holds.text()));
            case FormRule.Need.AtLeast atLeast -> atLeast.characters() < 1 ? Optional.empty()
                    : Optional.of(TreeAnswer.FieldCheck.atLeast(field, atLeast.characters()));
            case FormRule.Need.InRange range -> {
                Double min = number(range.min());
                Double max = number(range.max());
                boolean unread = !range.min().isEmpty() && min == null || !range.max().isEmpty() && max == null;
                yield unread || min == null && max == null || min != null && max != null && min > max
                        ? Optional.empty() : Optional.of(TreeAnswer.FieldCheck.inRange(field, min, max));
            }
        };
    }

    /**
     * The {@link Field.Kind} a browser judges a field of {@code kind} by: an email, a URL, a number; {@link
     * Field.Kind#UNKNOWN} for every other, whose validity the probe reads but no check says.
     */
    public static Field.Kind kind(FieldKind kind) {
        return kind == null ? Field.Kind.UNKNOWN : Field.Kind.fromId(kind.id());
    }

    /** The rule as the bot's note says it: the line, then each try. */
    static String said(FormRule rule) {
        List<String> tries = rule.tries().stream().map(FormRule.Try::line).toList();
        return rule.line() + (tries.isEmpty() ? "" : "; tried: " + String.join("; ", tries));
    }

    private static Double number(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            double n = Double.parseDouble(text);
            return Double.isFinite(n) ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
