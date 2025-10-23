package com.embabel.agent.api.common.workflow.loop;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.OperationContext;

import java.util.function.Predicate;

/**
 * Interface to be extended by strongly typed agents.
 * Subclasses must have @Agent annotation
 *
 * @param <TEST_SUBJECT>
 * @param <ITERATION_RESULT>
 * @param <FINAL_RESULT>
 */
public interface UntilAcceptable<TEST_SUBJECT, ITERATION_RESULT, FINAL_RESULT> {

    interface Properties {
        int maxIterations();
    }

    String DONE_CONDITION = "done";
    String SHOULD_RUN_CONDITION = "run_condition";

    UntilAcceptable.Properties properties();

    Class<ITERATION_RESULT> repeatedClass();

    Class<TEST_SUBJECT> testClass();

    Predicate<ITERATION_RESULT> fitnessFunction();

    @Action
    FINAL_RESULT initialize();

    @Action(pre = {SHOULD_RUN_CONDITION}, post = {DONE_CONDITION}, canRerun = true)
    ITERATION_RESULT runIteration(
            TEST_SUBJECT test, FINAL_RESULT result, OperationContext context);

    @Condition(name = SHOULD_RUN_CONDITION)
    default boolean shouldRun(OperationContext context) {
        var last = context.lastResult();
        // We run if we have new Test or are just getting started,
        // having run the init method
        return last == null || last.getClass().isInstance(testClass()) || last.getClass().isInstance(repeatedClass());
    }

    @Condition(name = DONE_CONDITION)
    default boolean done(ITERATION_RESULT repeated, OperationContext context) {
        return context.count(repeatedClass()) >= properties().maxIterations() || fitnessFunction().test(repeated);
    }

    @Action(cost = 1.0, post = {DONE_CONDITION}, canRerun = true)
    TEST_SUBJECT evolve(
            ITERATION_RESULT repeated,
            FINAL_RESULT results,
            Ai ai
    );

    @Action(pre = {DONE_CONDITION})
    // TODO would need to parameterize this goal text for this approach to work
    @AchievesGoal(description = "Focus group has considered positioning")
    default FINAL_RESULT result(FINAL_RESULT result) {
        return result;
    }

}
