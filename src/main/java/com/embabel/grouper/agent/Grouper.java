package com.embabel.grouper.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.event.ProgressUpdateEvent;
import com.embabel.common.util.StringTrimmingUtilsKt;
import com.embabel.grouper.domain.FocusGroupRun;
import com.embabel.grouper.domain.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final String DONE_CONDITION = "results_acceptable";

    @Action(post = {DONE_CONDITION})
    FocusGroupRun testPositioning(
            Model.FocusGroup focusGroup,
            Model.Positioning positioning,
            OperationContext context
    ) {
        var focusGroupRun = new FocusGroupRun(focusGroup, positioning);
        logger.info("Will try {} combinations", focusGroupRun.combinations.size());

        var specificReactions = new AtomicInteger(0);
        var results = context.parallelMap(
                focusGroupRun.combinations,
                config.maxConcurrency(),
                participantMessagePresentation -> {
                    var sp = presentMessageVariantToParticipants(
                            participantMessagePresentation,
                            context);
                    var count = specificReactions.incrementAndGet();
                    context.getProcessContext().onProcessEvent(
                            new ProgressUpdateEvent(context.getAgentProcess(),
                                    "focus", count, focusGroupRun.combinations.size())
                    );
                    return sp;
                }
        );
        results.forEach(focusGroupRun::record);
        return focusGroupRun;
    }

    Model.SpecificReaction presentMessageVariantToParticipants(
            Model.ParticipantMessagePresentation messagePresentation,
            OperationContext context) {
        var reaction = context.ai()
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

    @Condition(name = DONE_CONDITION)
    boolean done(FocusGroupRun focusGroupRun) {
        return focusGroupRun.isComplete();
    }

    @Action(pre = {DONE_CONDITION})
    @AchievesGoal(description = "Focus group has considered positioning")
    FocusGroupRun results(FocusGroupRun focusGroupRun) {
        return focusGroupRun;
    }

}
