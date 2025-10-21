package com.embabel.grouper.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.event.ProgressUpdateEvent;
import com.embabel.common.util.StringTrimmingUtilsKt;
import com.embabel.grouper.domain.FocusGroupRun;
import com.embabel.grouper.domain.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Agent to simulate a focus group
 *
 * @param config          config
 * @param fitnessFunction fitness function determining when we are satisfied
 */
@Agent(description = "Simulate a focus group")
record Grouper(
        GrouperConfig config,
        Predicate<FocusGroupRun> fitnessFunction
) {

    private static final Logger logger = LoggerFactory.getLogger(Grouper.class);

    private static final String DONE_CONDITION = "results_acceptable";

    Grouper {
        logger.info("Config: {}", config);
    }

    @Condition
    boolean positioningIsLastEntry(OperationContext context) {
        var last = context.lastResult();
        return last instanceof Model.Positioning || last instanceof Model.BestScoringVariants;
    }

    @Action
    Model.BestScoringVariants initialize() {
        return new Model.BestScoringVariants(config);
    }

    @Action(pre = {"positioningIsLastEntry"}, post = {DONE_CONDITION}, canRerun = true)
    FocusGroupRun testPositioning(
            Model.FocusGroup focusGroup,
//            @RequireNameMatch("it")
            Model.Positioning positioning,
            Model.BestScoringVariants bestScoringVariants,
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
        bestScoringVariants.updateFrom(focusGroupRun, config);
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
                        Also consider whether it is effective as <deliverable>%s</deliverable>
                        """.formatted(
                        messagePresentation.messageVariant().wording(),
                        messagePresentation.messageVariant().message().objective(),
                        messagePresentation.messageVariant().message().deliverable()));
        logger.info("Reaction of {} was {}", messagePresentation.participant(), reaction);
        return new Model.SpecificReaction(
                messagePresentation,
                reaction,
                Instant.now()
        );
    }

    @Condition(name = DONE_CONDITION)
    boolean done(FocusGroupRun focusGroupRun, OperationContext context) {
        return context.count(FocusGroupRun.class) > config.maxIterations() || fitnessFunction.test(focusGroupRun);
    }

    @Action(cost = 1.0, post = {DONE_CONDITION}, canRerun = true)
    Model.Positioning evolvePositioning(
            FocusGroupRun focusGroupRun,
            Model.BestScoringVariants bestScoringVariants,
            Ai ai
    ) {
        logger.info("Evolving positioning based on FocusGroupRun {}", focusGroupRun);
        // TODO Should handle > 1 message
        var messageVariants = focusGroupRun.positioning.messageVariants().getFirst();
        var newMessageWordings = config.nextCreative()
                .promptRunner(ai)
                .withPromptContributor(messageVariants.message())
                .creating(NewMessageWordings.class)
                .fromPrompt("""
                        Given the objectives, consider
                        the following feedback:
                        %s
                        
                        Create new message wordings we could try.
                        
                        Be creative. Try to break through!
                        
                        Never use more than %d variants
                        
                        Best scoring variants so far:
                        %s
                        """.formatted(
                        focusGroupRun.infoString(true, 1),
                        config.maxVariants(),
                        bestScoringVariants)
                );
        logger.info("New wordings: {}", newMessageWordings);
        var newMessageVariants = new Model.MessageVariants(
                messageVariants.message(),
                newMessageWordings.wordings().toArray(new String[0])
        );

        return new Model.Positioning(List.of(newMessageVariants));
    }

    @Action(pre = {DONE_CONDITION})
    @AchievesGoal(description = "Focus group has considered positioning")
        // TODO why do we need the BestScoringVariant (and FocusGroupRun?) parameter for this to execute?
    Model.BestScoringVariants results(
            Model.BestScoringVariants bestScoringVariants,
            FocusGroupRun focusGroupRun,
            OperationContext context) {
        return bestScoringVariants;
    }
}


record NewMessageWordings(
        List<String> wordings
) {

}
