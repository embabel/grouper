package com.embabel.grouper.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.event.ProgressUpdateEvent;
import com.embabel.common.util.StringTrimmingUtilsKt;
import com.embabel.grouper.domain.Model;
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
    Model.FocusGroupRun testMessages(
            Model.FocusGroup focusGroup,
            Model.Positioning positioning,
            OperationContext operationContext
    ) {
        var focusGroupRun = new Model.FocusGroupRun(focusGroup, positioning);
        logger.info("Will try {} combinations", focusGroupRun.combinations.size());

        var specificReactions = new java.util.concurrent.atomic.AtomicInteger(0);
        var results = operationContext.parallelMap(
                focusGroupRun.combinations,
                config.maxConcurrency(),
                participantMessagePresentation -> {
                    var sp = presentMessageExpressionToParticipants(
                            participantMessagePresentation,
                            operationContext);
                    var count = specificReactions.incrementAndGet();
                    operationContext.getProcessContext().onProcessEvent(
                            new ProgressUpdateEvent(operationContext.getAgentProcess(),
                                    "focus", count, focusGroupRun.combinations.size())
                    );
                    return sp;
                }
        );
        results.forEach(focusGroupRun::record);
        return focusGroupRun;
    }

    Model.SpecificReaction presentMessageExpressionToParticipants(
            Model.ParticipantMessagePresentation messagePresentation,
            OperationContext operationContext) {
        var reaction = operationContext.ai()
                .withLlm(messagePresentation.participant().llm())
                .withPromptContributor(messagePresentation.participant())
                .withId(StringTrimmingUtilsKt.trim(
                        messagePresentation.messageVariant().wording(), 80, 5, "..."
                ) + "_" + messagePresentation.participant().name())
                .creating(Model.Reaction.class)
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
                        """.formatted(messagePresentation.messageVariant().wording(), messagePresentation.messageVariant().message().objective()));
        logger.info("Reaction of {} was {}", messagePresentation.participant(), reaction);
        return new Model.SpecificReaction(
                messagePresentation,
                reaction,
                Instant.now()
        );
    }

    @Condition
    boolean done(Model.FocusGroupRun focusGroupRun) {
        return focusGroupRun.isComplete();
    }

    @Action
    @AchievesGoal(description = "Focus group has considered positioning")
    Model.FocusGroupRun results(Model.FocusGroupRun focusGroupRun) {
        return focusGroupRun;
    }

}
