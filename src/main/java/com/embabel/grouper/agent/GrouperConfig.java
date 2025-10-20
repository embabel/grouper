package com.embabel.grouper.agent;

import com.embabel.agent.prompt.persona.Actor;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.grouper.domain.FocusGroupRun;
import io.vavr.collection.Vector;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.function.Predicate;

@ConfigurationProperties(prefix = "grouper")
public record GrouperConfig(
        int maxConcurrency,
        int maxVariants,
        int maxIterations,
        double minMessageScore,
        List<Actor<RoleGoalBackstory>> creatives
) implements Predicate<FocusGroupRun> {

    @Override
    public boolean test(FocusGroupRun focusGroupRun) {
        // TODO don't have divisive messages
        return focusGroupRun.isComplete() && focusGroupRun.getBestPerformingMessageVariant().averageScore() > minMessageScore;
    }

    public Actor<RoleGoalBackstory> nextCreative() {
        return Vector.ofAll(creatives).shuffle().head();
    }

}
