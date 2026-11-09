package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.internal.bot.SdkValues;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Runner's pick of what a run runs: the bot's {@code plan} parameter, one of the {@code Plans} constants or
 * {@link Plan#NONE}. It replaced the flow choice on 2026-11-09, when flows were deleted.
 */
public final class PlanChoice {

    private PlanChoice() {
    }

    /** What the list says for {@link Plan#NONE}. */
    static final String NO_PLAN = "(no plan)";

    private static final ManagedSet<Plan> PLANS = ManagedSet.of(SdkValues.PLANS);

    /**
     * Whether this value is a pick of a plan: a parameter's value (no call site) that names a {@code Plans} constant
     * or is {@link Plan#NONE}. A {@code Plans} constant's own {@code Plan.of(…)} is not one.
     */
    public static boolean isPlanChoice(ValueContext ctx) {
        return ctx.slot().isEmpty() && ctx.type().is(Plan.class)
                && (PLANS.picked(ctx).isPresent()
                || ctx.value(Plan.class).map(plan -> plan.tiers().isEmpty()).orElse(false));
    }

    /** A list of the bot's plans and {@value #NO_PLAN}; a pick the host refuses is said and undone. */
    public static Node plan(ValueContext ctx) {
        List<String> names = new ArrayList<>();
        names.add(NO_PLAN);
        PLANS.members(ctx.services().pluginValues()).forEach(member -> names.add(member.name()));
        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.getItems().setAll(names);
        String current = PLANS.picked(ctx).map(PluginValues.Member::name).orElse(NO_PLAN);
        box.setValue(current);
        String[] shown = {current};
        box.setOnAction(e -> {
            String picked = box.getValue();
            if (picked == null || picked.equals(shown[0])) return;
            Optional<String> refused = NO_PLAN.equals(picked) ? ctx.write(Plan.NONE)
                    : PLANS.pick(ctx, PluginValues.Member.of(picked));
            if (refused.isPresent()) {
                ctx.services().status(refused.get());
                box.setValue(shown[0]);
            } else {
                shown[0] = picked;
            }
        });
        return box;
    }
}
