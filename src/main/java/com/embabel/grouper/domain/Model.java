package com.embabel.grouper.domain;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;
import com.embabel.grouper.agent.GrouperProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.vavr.collection.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Holder for public domain types. Avoids proliferation of small files.
 */
public abstract class Model {

    /**
     * A logical message to be evaluated
     *
     * @param id          id of the message, in case we have variants
     * @param content     content of the message, such as "smoking is bad"
     * @param objective   objective
     * @param deliverable what we want: e.g. a slogan or a blog
     */
    public record Message(
            String id,
            String content,
            String objective,
            String deliverable) implements PromptContributor {
        @NotNull
        @Override
        public String contribution() {
            return """
                    Message is %s
                    Objective is %s
                    The deliverable result is %s
                    """.formatted(content, objective, deliverable);
        }
    }

    /**
     * Particular wording of a message
     *
     * @param message message we're expressing
     * @param wording particular wording, such as "smoking is a health hazard"
     */
    public record MessageVariant(
            Message message,
            String wording
    ) {
    }

    /**
     * Conceptual Message although with multiple variants.
     *
     * @param message
     * @param expressions
     */
    public record MessageVariants(
            Message message,
            List<MessageVariant> expressions
    ) {

        public MessageVariants(Message message, String... expressions) {
            this(message,
                    Arrays.stream(expressions).map(e -> new MessageVariant(message, e)).toList());
        }
    }

    /**
     * Positioning, which can include multiple Messages
     */
    public record Positioning(List<MessageVariants> messageVariants) {
    }

    /**
     * Participant in a focus group
     */
    public interface Participant extends PromptContributor {

        /**
         * id must be unique. There can be multiple participants
         * with the same name, but different models
         */
        String id();

        String name();

        LlmOptions llm();

        /**
         * Raw population percentage this participant represents.
         * Will be automatically normalized across all participants in the focus group.
         * Default is 1.0 (equal weighting).
         */
        default double populationPercentage() {
            return 1.0;
        }
    }

    /**
     * Focus group that can be reused in multiple tests
     *
     * @param participants
     */
    public record FocusGroup(
            List<Participant> participants
    ) {
        private Map<Participant, Double> normalizedWeights() {
            double total = participants.stream()
                    .mapToDouble(Participant::populationPercentage)
                    .sum();
            return Vector.ofAll(participants)
                    .toMap(p -> p, p -> p.populationPercentage() / total)
                    .toJavaMap();
        }

        /**
         * Get the normalized weight for a participant (0.0 to 1.0, sum of all weights = 1.0)
         */
        public double normalizedWeight(Participant participant) {
            return normalizedWeights().getOrDefault(participant, 0.0);
        }
    }

    /**
     * Reaction data
     */
    public record Reaction(
            @JsonPropertyDescription("Things that resonate about the message")
            String positives,
            @JsonPropertyDescription("Things that backfire about the message")
            String negatives,
            @JsonProperty("Quotes saying how this message makes me feel")
            List<String> quotes,
            @JsonPropertyDescription("Likert rating")
            LikertRating rating
    ) {
    }

    /**
     * Represents a combination of a participant and a message variant
     */
    public record ParticipantMessagePresentation(
            Participant participant,
            MessageVariant messageVariant
    ) {
    }

    /**
     * Reaction of one participant to a given message variant
     */
    public record SpecificReaction(
            ParticipantMessagePresentation participantMessagePresentation,
            Reaction reaction,
            Instant timestamp
    ) {
    }

    /**
     * Average score for a message variant, with count of reactions
     */
    public record MessageVariantScore(
            MessageVariant messageVariant,
            double averageScore,
            double normalizedScore,
            long count
    ) {
    }

    public static class BestScoringVariants {
        private Vector<MessageVariantScore> bestVariants = Vector.empty();
        private final GrouperProperties config;
        private Vector<String> findings = Vector.empty();

        public BestScoringVariants(GrouperProperties config) {
            this.config = config;
        }

        public List<Model.MessageVariantScore> bestVariants() {
            return bestVariants.asJava();
        }

        public void updateFrom(FocusGroupRun focusGroupRun,
                               GrouperProperties config) {
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
                    .sorted(Comparator.comparingDouble(config::decisionScore).reversed())
                    .take(config.maxVariants());
        }

        public void addFinding(String finding) {
            findings = findings.append(finding);
        }

        @NotNull
        @Override
        public String toString() {
            var variants = bestVariants
                    .sorted(Comparator.comparingDouble(Model.MessageVariantScore::normalizedScore).reversed())
                    .map(mv -> "%.2f: %s".formatted(config.decisionScore(mv), mv.messageVariant().wording()))
                    .collect(Collectors.joining("\n"));
            return """
                    %s
                    
                    Findings:
                    - %s
                    """.formatted(variants, findings.collect(Collectors.joining("\n- ")));

        }
    }

}
