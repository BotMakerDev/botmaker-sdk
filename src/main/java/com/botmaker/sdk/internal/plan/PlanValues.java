package com.botmaker.sdk.internal.plan;

/** The keys of the values a plan run's lines carry ({@link PlanRun}), which the SDK's reports can count. */
public final class PlanValues {

    /** The goal a line is about, by its constant's name. */
    public static final String GOAL = "plan.goal";
    /** The action a line is about, {@code <Screen constant>.<field>}. */
    public static final String ACTION = "plan.action";

    private PlanValues() {}
}
