package com.embabel.grouper.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.event.ProgressUpdateEvent;
import com.embabel.common.util.StringTrimmingUtilsKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

@ConfigurationProperties(prefix = "grouper")
record GrouperConfig(
        int maxConcurrency
) {
}

@Agent(description = "Simulate a focus group")
record Grouper(
        GrouperConfig config
) {

    private static final Logger logger = LoggerFactory.getLogger(Grouper.class);

    @Action
    Domain.FocusGroupRun createFocusGroupRun(
            Domain.FocusGroup focusGroup,
            Domain.Positioning positioning
    ) {
        return new Domain.FocusGroupRun(focusGroup, positioning);
    }

    @Action(post = {"done"})
    Domain.FocusGroupRun testMessages(
            Domain.FocusGroupRun focusGroupRun,
            OperationContext operationContext
    ) {
        var combos = focusGroupRun.getCompletionMatrix().getAllCombinations();
        logger.info("Will try {} combinations", combos.size());

        var specificReactions = new java.util.concurrent.atomic.AtomicInteger(0);
        var results = operationContext.parallelMap(
                combos,
                config.maxConcurrency(),
                participantMessagePresentation -> {
                    var sp = testMessageExpressionWithParticipant(
                            participantMessagePresentation,
                            operationContext);
                    var count = specificReactions.incrementAndGet();
                    operationContext.getProcessContext().onProcessEvent(
                            new ProgressUpdateEvent(operationContext.getAgentProcess(),
                                    "focus", count, combos.size())
                    );
                    return sp;
                }
        );
        results.forEach(focusGroupRun::record);
        return focusGroupRun;
    }

    Domain.SpecificReaction testMessageExpressionWithParticipant(
            Domain.ParticipantMessagePresentation messagePresentation,
            OperationContext operationContext) {
        var reaction = operationContext.ai()
                .withLlm(messagePresentation.participant().llm())
                .withPromptContributor(messagePresentation.participant())
                .withId(StringTrimmingUtilsKt.trim(
                        messagePresentation.messageExpression().expression(), 25, 3, "..."
                ) + "_" + messagePresentation.participant().name())
                .creating(Domain.Reaction.class)
                .fromPrompt("""
                        You are a member of a focus group.
                        Your replies are confidential and you don't need to worry about
                        anyone knowing what you said, so you can share your feelings
                        honestly without fear of judgment or consequences.
                        Be honest.
                        
                        React to the following message given your persona:
                        
                        <message>%s</message>
                        
                        Assess in terms of whether it would produce the following objective in your mind:
                        <objective>%s</objective>
                        """.formatted(messagePresentation.messageExpression().expression(), messagePresentation.messageExpression().message().objective()));
        logger.info("Reaction of {} was {}", messagePresentation.participant(), reaction);
        return new Domain.SpecificReaction(
                messagePresentation.messageExpression(),
                messagePresentation.participant(),
                reaction,
                Instant.now()
        );
    }

    @Condition
    boolean done(Domain.FocusGroupRun focusGroupRun) {
        return focusGroupRun.isComplete();
    }

    @Action(pre = {"done"})
    @AchievesGoal(description = "Focus group has considered positioning")
    Domain.FocusGroupRun results(Domain.FocusGroupRun focusGroupRun) {
        return focusGroupRun;
    }

}
