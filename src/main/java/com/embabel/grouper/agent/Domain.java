package com.embabel.grouper.agent;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;

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
     * @param positive
     * @param negative
     * @param quotes
     * @param score    Score from 0.0 (worst) to 1.0 (best)
     */
    public record Reaction(
            String positive,
            String negative,
            List<String> quotes,
            double score
    ) {
    }

//    public record MessageSubmission(
//            Message message,
//            Participant participant
//    ) {
//    }

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
    public static class FocusGroupRun {

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


    }

}
