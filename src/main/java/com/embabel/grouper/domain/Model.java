package com.embabel.grouper.domain;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;
import com.embabel.common.core.types.HasInfoString;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Holder for public domain types. Avoids proliferation of small files.
 */
public abstract class Model {

    /**
     * A logical message to be evaluated
     *
     * @param id        id of the message, in case we have variants
     * @param content   content of the message, such as "smoking is bad"
     * @param objective objective
     */
    public record Message(
            String id,
            String content,
            String objective) {
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
     * Variants of a particular message
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
    }

    /**
     * Focus group that can be reused in multiple tests
     *
     * @param participants
     */
    public record FocusGroup(
            List<Participant> participants
    ) {
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
            @JsonPropertyDescription("Score from 0.0 (doesn't resonate at all) to 1.0 (love it)")
            double score
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
    public record MessageScore(
            double averageScore,
            long count
    ) {
    }

    /**
     * Built up as we return results
     */
    public static class FocusGroupRun implements HasInfoString {

        public final FocusGroup focusGroup;

        public final Positioning positioning;

        public final List<ParticipantMessagePresentation> combinations;

        private final List<SpecificReaction> specificReactions = new LinkedList<>();

        public FocusGroupRun(
                FocusGroup focusGroup,
                Positioning positioning) {
            this.focusGroup = focusGroup;
            this.positioning = positioning;

            // Initialize all combinations
            this.combinations = positioning.messageVariants().stream()
                    .flatMap(mv -> mv.expressions().stream())
                    .flatMap(variant -> focusGroup.participants().stream()
                            .map(participant -> new ParticipantMessagePresentation(participant, variant)))
                    .toList();
        }

        public boolean isComplete() {
            return specificReactions.size() == combinations.size();
        }

        public void record(SpecificReaction reaction) {
            specificReactions.add(reaction);
        }

        public List<SpecificReaction> getReactionsForParticipant(Participant participant) {
            return specificReactions.stream()
                    .filter(r -> r.participantMessagePresentation().participant().equals(participant))
                    .toList();
        }

        public MessageScore getAverageScoreForMessage(MessageVariant messageVariant) {
            var reactions = specificReactions.stream()
                    .filter(r -> r.participantMessagePresentation().messageVariant().equals(messageVariant))
                    .toList();

            long count = reactions.size();
            double average = reactions.stream()
                    .mapToDouble(r -> r.reaction().score())
                    .average()
                    .orElse(0.0);

            return new MessageScore(average, count);
        }

        public double getAverageScoreForParticipant(Participant participant) {
            return specificReactions.stream()
                    .filter(r -> r.participantMessagePresentation().participant().equals(participant))
                    .mapToDouble(r -> r.reaction().score())
                    .average()
                    .orElse(0.0);
        }

        @NotNull
        @Override
        public String infoString(Boolean verbose, int indent) {
            boolean isVerbose = verbose != null && verbose;
            String indentStr = " ".repeat(indent);
            StringBuilder sb = new StringBuilder();

            sb.append(indentStr).append("Focus Group Results\n");
            sb.append(indentStr).append("===================\n\n");

            // Get all message expressions with their scores, sorted by average score (highest first)
            var messageScores = positioning.messageVariants().stream()
                    .flatMap(mt -> mt.expressions().stream())
                    .map(expr -> {
                        MessageScore score = getAverageScoreForMessage(expr);
                        return Map.entry(expr, score);
                    })
                    .sorted((e1, e2) -> Double.compare(e2.getValue().averageScore(), e1.getValue().averageScore()))
                    .toList();

            // Summary ranking
            sb.append(indentStr).append("Message Ranking by Effectiveness:\n");
            sb.append(indentStr).append("---------------------------------\n");
            int rank = 1;
            for (var entry : messageScores) {
                MessageVariant expr = entry.getKey();
                MessageScore score = entry.getValue();
                sb.append(indentStr).append(String.format("%d. %.0f%% - %s (ID: %s)\n",
                        rank++,
                        score.averageScore() * 100,
                        expr.wording().length() > 60 ? expr.wording().substring(0, 57) + "..." : expr.wording(),
                        expr.message().id()));
            }
            sb.append("\n");

            // Detailed results
            sb.append(indentStr).append("Detailed Results:\n");
            sb.append(indentStr).append("-----------------\n\n");

            for (var entry : messageScores) {
                MessageVariant expr = entry.getKey();
                MessageScore score = entry.getValue();

                sb.append(indentStr).append(String.format("Message: %s (ID: %s)\n", expr.message().content(), expr.message().id()));
                sb.append(indentStr).append(String.format("Objective: %s\n", expr.message().objective()));
                sb.append(indentStr).append(String.format("Expression: %s\n", expr.wording()));
                sb.append(indentStr).append(String.format("Average Score: %.2f (%.0f%%) - %d reactions\n",
                        score.averageScore(),
                        score.averageScore() * 100,
                        score.count()));

                if (isVerbose) {
                    sb.append(indentStr).append("  Participant Reactions:\n");

                    // Get all reactions for this message variant
                    var reactions = specificReactions.stream()
                            .filter(r -> r.participantMessagePresentation().messageVariant().equals(expr))
                            .toList();

                    for (var reaction : reactions) {
                        Participant participant = reaction.participantMessagePresentation().participant();

                        sb.append(indentStr).append(String.format("    %s: %.2f (%.0f%%)\n",
                                participant.name(),
                                reaction.reaction().score(),
                                reaction.reaction().score() * 100));
                        sb.append(indentStr).append(String.format("      Positives: %s\n", reaction.reaction().positives()));
                        sb.append(indentStr).append(String.format("      Negatives: %s\n", reaction.reaction().negatives()));

                        if (!reaction.reaction().quotes().isEmpty()) {
                            sb.append(indentStr).append("      Quotes:\n");
                            for (String quote : reaction.reaction().quotes()) {
                                sb.append(indentStr).append(String.format("        - \"%s\"\n", quote));
                            }
                        }
                    }
                }

                sb.append("\n");
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            return infoString(false, 0);
        }

    }

}
