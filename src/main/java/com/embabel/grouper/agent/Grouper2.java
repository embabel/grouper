package com.embabel.grouper.agent;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilBuilder;
import com.embabel.agent.core.Agent;
import com.embabel.agent.event.ProgressUpdateEvent;
import com.embabel.common.util.StringTrimmingUtilsKt;
import com.embabel.grouper.domain.FocusGroupRun;
import com.embabel.grouper.domain.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

//@Configuration
class Grouper2 {

    private final Logger logger = LoggerFactory.getLogger(Grouper2.class);

    private final GrouperConfig config;
    private final Predicate<FocusGroupRun> fitnessFunction;


    Grouper2(GrouperConfig config, Predicate<FocusGroupRun> fitnessFunction) {
        this.config = config;
        this.fitnessFunction = fitnessFunction;
    }

    @Bean
    Agent grouper2() {
        return RepeatUntilBuilder
                .returning(FocusGroupRun.class)
                .withMaxIterations(config.maxIterations())
                .repeating(tac ->
                        testPositioning(tac.getInput().lastResult(), tac))
                .until(tac -> fitnessFunction.test(tac.getInput().lastResult()))
                .buildAgent("focuser", "Run a focus group");
    }


    FocusGroupRun testPositioning(
            FocusGroupRun focusGroupRun,
            OperationContext context
    ) {
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


    Model.Positioning evolvePositioning(
            FocusGroupRun focusGroupRun,
            Ai ai
    ) {
        logger.info("Evolving positioning based on FocusGroupRun {}", focusGroupRun);
        // TODO Should handle > 1 message
        var messageVariants = focusGroupRun.positioning.messageVariants().getFirst();
        var newMessageWords = ai
                .withLlmByRole("best")
                .creating(NewMessageWordings.class)
                .fromPrompt("""
                        Given the following feedback,
                        %s
                        
                        Create new message wordings we could try.
                        Preserve good-scoring messages, remove poorer ones
                        Be creative. Try to break through!
                        
                        Old messages: %s
                        """.formatted(
                        focusGroupRun.infoString(true, 1),
                        messageVariants.expressions())
                );
        var newMessageVariants = new Model.MessageVariants(
                messageVariants.message(),
                newMessageWords.wordings().toArray(new String[0])
        );

        return new Model.Positioning(List.of(newMessageVariants));
    }


}

