package com.embabel.grouper.agent;

import com.embabel.agent.prompt.persona.Actor;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.grouper.domain.FocusGroupRun;
import com.embabel.grouper.domain.Model;
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
        return focusGroupRun.isComplete() && decisionScore(focusGroupRun.getBestPerformingMessageVariant()) > minMessageScore;
    }

    /**
     * We mix the two so messages absolutely hated by a small proportion
     * get penalized
     */
    public double decisionScore(Model.MessageVariantScore messageVariantScore) {
        return (messageVariantScore.normalizedScore() * 5.0 + messageVariantScore.averageScore() * 1.1) / 6.1;
    }

    public Actor<RoleGoalBackstory> nextCreative() {
        return Vector.ofAll(creatives).shuffle().head();
    }

}
