package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.params.RunParams;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The facts a check reads, each the method of a parameter: what a run can go and read when the check answers unknown.
 * Only a check built of facts tells them ({@code Need}, {@code Params.when}, and {@code Condition.all}, {@code any} and
 * {@code not} over those); a bot's own method tells none.
 */
final class FactsRead {

    private FactsRead() {}

    /** What {@code goal}'s check reads; none for a done-goal, which reads a record. */
    static Set<Method> of(Goal goal) {
        return goal instanceof Goal.Of of ? of(of.check()) : Set.of();
    }

    /** What {@code action} needs read: its conditions' facts, its {@code .until}'s and its check's. */
    static Set<Method> of(Do action) {
        Set<Method> read = new LinkedHashSet<>();
        action.conditionNeeds().forEach(need -> add(need, read));
        add(action.until(), read);
        add(action.checks(), read);
        return read;
    }

    /** What {@code check} reads. */
    static Set<Method> of(Condition check) {
        Set<Method> read = new LinkedHashSet<>();
        add(check, read);
        return read;
    }

    private static void add(BooleanSupplier check, Set<Method> read) {
        switch (check) {
            case null -> { }
            case ParamCheck param -> {
                try {
                    read.add(RunParams.parameterOf(param.when().parameter()));
                } catch (IllegalArgumentException noParameter) {
                    // A reference to no parameter answers unknown for good: nothing to go and read.
                }
            }
            case Condition.Not not -> add(not.condition(), read);
            case Condition.All all -> all.conditions().forEach(each -> add(each, read));
            case Condition.Any any -> any.conditions().forEach(each -> add(each, read));
            case Condition.Xor xor -> xor.conditions().forEach(each -> add(each, read));
            case Condition.Of of -> add(of.method(), read);
            default -> { }
        }
    }
}
