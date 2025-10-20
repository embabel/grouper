package com.embabel.grouper.agent;

import com.embabel.agent.prompt.persona.Actor;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.grouper.domain.FocusGroupRun;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.function.Predicate;

@ConfigurationProperties(prefix = "grouper")
record GrouperConfig(
        int maxConcurrency,
        int maxVariants,
        int maxIterations,
        double minMessageScore,
        Actor<RoleGoalBackstory> creative
) implements Predicate<FocusGroupRun> {

    @Override
    public boolean test(FocusGroupRun focusGroupRun) {
        return focusGroupRun.isComplete() && focusGroupRun.getBestPerformingMessageVariant().averageScore() > minMessageScore;
    }

}
