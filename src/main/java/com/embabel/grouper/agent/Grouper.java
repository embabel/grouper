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
 * @param properties      properties
 * @param fitnessFunction fitness function determining when we are satisfied
 */
@Agent(description = "Simulate a focus group")
record Grouper(
        GrouperProperties properties,
        Predicate<FocusGroupRun> fitnessFunction
) {

    private static final Logger logger = LoggerFactory.getLogger(Grouper.class);

    private static final String RUN_FOCUS_GROUP_CONDITION = "run_focus_group";

    private static final String DONE_CONDITION = "results_acceptable";

    Grouper {
        logger.info("Config: {}", properties);
    }

    @Condition(name = RUN_FOCUS_GROUP_CONDITION)
    boolean shouldRunFocusGroup(OperationContext context) {
        var last = context.lastResult();
        // We run if we have new Positioning or are just getting started,
        // having run the init method
        return last instanceof Model.Positioning || last instanceof Model.BestScoringVariants;
    }

    @Action
    Model.BestScoringVariants initialize() {
        return new Model.BestScoringVariants(properties);
    }

    @Action(pre = {RUN_FOCUS_GROUP_CONDITION}, post = {DONE_CONDITION}, canRerun = true)
    FocusGroupRun runFocusGroup(
            Model.FocusGroup focusGroup,
            Model.Positioning positioning,
            Model.BestScoringVariants bestScoringVariants,
            OperationContext context
    ) {
        var focusGroupRun = new FocusGroupRun(focusGroup, positioning);
        logger.info("Will try {} combinations", focusGroupRun.combinations.size());

        var specificReactions = new AtomicInteger(0);
        var results = context.parallelMap(
                focusGroupRun.combinations,
                properties.maxConcurrency(),
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
        bestScoringVariants.updateFrom(focusGroupRun, properties);
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
        return context.count(FocusGroupRun.class) >= properties.maxIterations() || fitnessFunction.test(focusGroupRun);
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
        var creativeControl = properties.nextCreative()
                .promptRunner(ai)
                .withPromptContributor(messageVariants.message())
                .creating(CreativeControl.class)
                .fromPrompt("""
                        Given the objectives, consider
                        the following feedback and previous learnings:
                        %s
                        
                        1. Summarize the feedback in no more than %d words.
                        
                        2. Create new message wordings we could try.
                        
                        Be creative. Try to break through!
                        Feel free to rephrase promising previous attempts for greater impact,
                        or come up with completely new ideas!
                        
                        Never use more than %d variants
                        
                        Best scoring variants so far:
                        %s
                        """.formatted(
                        focusGroupRun.infoString(true, 1),
                        properties.findingsWordCount(),
                        properties.maxVariants(),
                        bestScoringVariants)
                );
        logger.info("Creative input: {}", creativeControl);
        bestScoringVariants.addFinding(creativeControl.summary);
        var newMessageVariants = new Model.MessageVariants(
                messageVariants.message(),
                creativeControl.wordings().toArray(new String[0])
        );

        return new Model.Positioning(List.of(newMessageVariants));
    }

    @Action(pre = {DONE_CONDITION})
    @AchievesGoal(description = "Focus group has considered positioning")
    Model.BestScoringVariants results(
            Model.BestScoringVariants bestScoringVariants) {
        return bestScoringVariants;
    }

    private record CreativeControl(
            String summary,
            List<String> wordings
    ) {
    }
}


