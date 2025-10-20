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
import io.vavr.collection.Vector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
        return last instanceof Model.Positioning || last instanceof BestScoringVariants;
    }

    @Action
    BestScoringVariants initialize() {
        return new BestScoringVariants();
    }

    @Action(pre = {"positioningIsLastEntry"}, post = {DONE_CONDITION}, canRerun = true)
    FocusGroupRun testPositioning(
            Model.FocusGroup focusGroup,
//            @RequireNameMatch("it")
            Model.Positioning positioning,
            BestScoringVariants bestScoringVariants,
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
        bestScoringVariants.updateFrom(focusGroupRun);
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
            BestScoringVariants bestScoringVariants,
            Ai ai
    ) {
        logger.info("Evolving positioning based on FocusGroupRun {}", focusGroupRun);
        // TODO Should handle > 1 message
        var messageVariants = focusGroupRun.positioning.messageVariants().getFirst();
        var newMessageWordings = config.creative()
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
        // TODO why do we need the FocusGroupRun parameter for this to execute?
    FocusGroupRun results(
            FocusGroupRun focusGroupRun,
            OperationContext context) {
        return context
                .objectsOfType(FocusGroupRun.class)
                .stream()
                .map(it -> (FocusGroupRun) it)
                .max(Comparator.comparing(
                        FocusGroupRun::getBestPerformingMessageVariant,
                        Comparator.nullsLast(Comparator.comparingDouble(Model.MessageVariantScore::averageScore))
                ))
                .orElse(null);
    }

    private class BestScoringVariants {
        private Vector<Model.MessageVariantScore> bestVariants = Vector.empty();

        public List<Model.MessageVariantScore> bestVariants() {
            return bestVariants.asJava();
        }

        public void updateFrom(FocusGroupRun focusGroupRun) {
            var newScores = Vector.ofAll(
                    focusGroupRun.positioning.messageVariants().stream()
                            .flatMap(mv -> mv.expressions().stream())
                            .map(focusGroupRun::getAverageScoreForMessageVariant)
                            .filter(score -> score.count() > 0)
                            .toList()
            );

            bestVariants = bestVariants
                    .appendAll(newScores)
                    .distinctBy(score -> score.messageVariant().wording().trim())
                    .sorted(Comparator.comparingDouble(Model.MessageVariantScore::averageScore).reversed())
                    .take(config.maxVariants());
        }

        @NotNull
        @Override
        public String toString() {
            return bestVariants
                    .sorted(Comparator.comparingDouble(Model.MessageVariantScore::averageScore).reversed())
                    .map(mv -> "%.2f: %s".formatted(mv.averageScore(), mv.messageVariant().wording()))
                    .collect(Collectors.joining("\n"));

        }
    }
}


record NewMessageWordings(
        List<String> wordings
) {

}
