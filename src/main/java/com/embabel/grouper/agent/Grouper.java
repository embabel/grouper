package com.embabel.grouper.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.OperationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Agent(description = "Simulate a focus group")
class Grouper {

    private final Logger logger = LoggerFactory.getLogger(Grouper.class);

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
        var specificReactions = operationContext.parallelMap(
                combos,
                15,
                participantMessagePresentation ->
                        testMessageExpressionWithParticipant(participantMessagePresentation, focusGroupRun, operationContext)
        );
        specificReactions.forEach(focusGroupRun::record);
        return focusGroupRun;
    }

    Domain.SpecificReaction testMessageExpressionWithParticipant(
            Domain.ParticipantMessagePresentation messageTest,
            Domain.FocusGroupRun focusGroupRun,
            OperationContext operationContext) {
        var reaction = operationContext.ai()
                .withLlm(messageTest.participant().llm())
                .withPromptContributor(messageTest.participant())
                .creating(Domain.Reaction.class)
                .fromPrompt("""
                        React to the following message given your persona:
                        
                        <message>%s</message>
                        
                        Assess in terms of whether it would produce the following objective in your mind:
                        <objective>%s</objective>
                        """.formatted(messageTest.messageExpression().expression(), messageTest.messageExpression().message().objective()));
        logger.info("Reaction of {} was {}", messageTest.participant(), reaction);
        return new Domain.SpecificReaction(
                messageTest.messageExpression(),
                messageTest.participant(),
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
