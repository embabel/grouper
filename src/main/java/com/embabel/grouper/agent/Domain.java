package com.embabel.grouper.agent;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;
import com.embabel.common.core.types.HasInfoString;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

public abstract class Domain {

    /**
     * A message to be evaluated
     *
     * @param id        id of the message, in case we have variants
     * @param detail    detail of this instance of the message
     * @param objective objective
     */
    public record Message(
            String id,
            String detail,
            String objective) {
    }

    /**
     * Expression of a message
     *
     * @param message
     * @param expression
     */
    public record MessageExpression(
            Message message,
            String expression
    ) {
    }

    public record MessageTest(
            Message message,
            List<MessageExpression> expressions
    ) {

        public MessageTest(Message message, String... expressions) {
            this(message,
                    Arrays.stream(expressions).map(e -> new MessageExpression(message, e)).toList());
        }
    }

    /**
     * Map from name to Message
     * This allows us to test multiple variants of the same
     * message name
     *
     */
    public record Positioning(List<MessageTest> messageTests) {
    }

    public enum Gender {
        MALE, FEMALE, NON_BINARY, PREFER_NOT_TO_SAY, OTHER
    }

    /**
     * Participant in a focus group
     */
    public interface Participant extends PromptContributor {

        String name();

        LlmOptions llm();
    }

    public record FocusGroup(
            List<Participant> participants
    ) {
    }

    public record FocusGroupSubmission(
            FocusGroup focusGroup,
            Positioning positioning
    ) {
    }

    /**
     *
     * @param positives
     * @param negatives
     * @param quotes
     * @param score     Score from 0.0 (worst) to 1.0 (best)
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
     * Reaction of one participant to a given message
     */
    public record SpecificReaction(
            MessageExpression message,
            Participant participant,
            Reaction reaction,
            Instant timestamp
    ) {
    }

    /**
     * Average score for a message with the count of reactions
     */
    public record MessageScore(
            double averageScore,
            long count
    ) {
    }

    /**
     * Represents a combination of a participant and a message expression
     */
    public record ParticipantMessagePresentation(
            Participant participant,
            MessageExpression messageExpression
    ) {
    }

    /**
     * Matrix tracking completion status for all participant/message expression combinations
     */
    public record CompletionMatrix(
            Map<ParticipantMessagePresentation, Boolean> completionStatus
    ) {
        public boolean isComplete() {
            return completionStatus.values().stream().allMatch(Boolean::booleanValue);
        }

        public boolean hasReaction(Participant participant, MessageExpression messageExpression) {
            return completionStatus.getOrDefault(
                    new ParticipantMessagePresentation(participant, messageExpression),
                    false
            );
        }

        public List<ParticipantMessagePresentation> getAllCombinations() {
            return List.copyOf(completionStatus.keySet());
        }

        public List<ParticipantMessagePresentation> getCompletedCombinations() {
            return completionStatus.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        public List<ParticipantMessagePresentation> getIncompleteCombinations() {
            return completionStatus.entrySet().stream()
                    .filter(e -> !e.getValue())
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    /**
     * Built up as we return results
     */
    public static class FocusGroupRun implements HasInfoString {

        public final FocusGroup focusGroup;

        public final Positioning positioning;

        private final Map<Participant, List<SpecificReaction>> reactionsByParticipant = new HashMap<>();

        private final Map<ParticipantMessagePresentation, Boolean> matrixData = new HashMap<>();

        public FocusGroupRun(
                FocusGroup focusGroup,
                Positioning positioning) {
            this.focusGroup = focusGroup;
            this.positioning = positioning;

            // Initialize matrix with all combinations set to false
            var allMessageExpressions = positioning.messageTests().stream()
                    .flatMap(mt -> mt.expressions().stream())
                    .toList();

            for (var participant : focusGroup.participants()) {
                for (MessageExpression messageExpression : allMessageExpressions) {
                    matrixData.put(new ParticipantMessagePresentation(participant, messageExpression), false);
                }
            }
        }

        public CompletionMatrix getCompletionMatrix() {
            return new CompletionMatrix(Map.copyOf(matrixData));
        }

        public boolean isComplete() {
            return getCompletionMatrix().isComplete();
        }

        public void record(SpecificReaction reaction) {
            reactionsByParticipant
                    .computeIfAbsent(reaction.participant(), k -> new LinkedList<>())
                    .add(reaction);

            // Update matrix
            ParticipantMessagePresentation key = new ParticipantMessagePresentation(
                    reaction.participant(),
                    reaction.message()
            );
            matrixData.put(key, true);
        }

        public List<SpecificReaction> getReactionsForParticipant(Participant participant) {
            return reactionsByParticipant.getOrDefault(participant, List.of());
        }

        public MessageScore getAverageScoreForMessage(MessageExpression messageExpression) {
            var reactions = reactionsByParticipant.values().stream()
                    .flatMap(List::stream)
                    .filter(r -> r.message().equals(messageExpression))
                    .toList();

            long count = reactions.size();
            double average = reactions.stream()
                    .mapToDouble(r -> r.reaction().score())
                    .average()
                    .orElse(0.0);

            return new MessageScore(average, count);
        }

        public double getAverageScoreForParticipant(Participant participant) {
            return reactionsByParticipant.getOrDefault(participant, List.of()).stream()
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
            var messageScores = positioning.messageTests().stream()
                    .flatMap(mt -> mt.expressions().stream())
                    .map(expr -> {
                        MessageScore score = getAverageScoreForMessage(expr);
                        return Map.entry(expr, score);
                    })
                    .sorted((e1, e2) -> Double.compare(e2.getValue().averageScore(), e1.getValue().averageScore()))
                    .toList();

            for (var entry : messageScores) {
                MessageExpression expr = entry.getKey();
                MessageScore score = entry.getValue();

                sb.append(indentStr).append(String.format("Message: %s (ID: %s)\n", expr.message().detail(), expr.message().id()));
                sb.append(indentStr).append(String.format("Objective: %s\n", expr.message().objective()));
                sb.append(indentStr).append(String.format("Expression: %s\n", expr.expression()));
                sb.append(indentStr).append(String.format("Average Score: %.2f (%.0f%%) - %d reactions\n",
                        score.averageScore(),
                        score.averageScore() * 100,
                        score.count()));

                if (isVerbose) {
                    sb.append(indentStr).append("  Participant Reactions:\n");

                    // Get all reactions for this message expression
                    var reactions = reactionsByParticipant.entrySet().stream()
                            .flatMap(e -> e.getValue().stream()
                                    .filter(r -> r.message().equals(expr))
                                    .map(r -> Map.entry(e.getKey(), r)))
                            .toList();

                    for (var reactionEntry : reactions) {
                        Participant participant = reactionEntry.getKey();
                        SpecificReaction reaction = reactionEntry.getValue();

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
