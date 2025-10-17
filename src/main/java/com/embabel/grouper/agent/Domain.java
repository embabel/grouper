package com.embabel.grouper.agent;

import com.embabel.common.ai.prompt.PromptContributor;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class Domain {

    /**
     * A message to be evaluated
     *
     * @param id      id of the message, in case we have variants
     * @param content content of this instance of the message
     */
    public record Message(
            String id,
            String content) {
    }

    /**
     * Map from id to Message
     *
     * @param messaging
     */
    public record Positioning(Map<String, List<Message>> messaging) {
    }

    public enum Gender {
        MALE, FEMALE, NON_BINARY, PREFER_NOT_TO_SAY, OTHER
    }

    /**
     * Participant in a focus group
     */
    public interface Participant extends PromptContributor {

        String name();

        /***
         * A participant may run on multiple models
         */
        String model();
    }

    public record FocusGroup(
            List<Participant> participants,
            Instant timestamp
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

    /**
     * Reaction of one participant to a given message
     */
    public record SpecificReaction(
            Message message,
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

    public static class FocusGroupRun {

        public final FocusGroup focusGroup;

        private final Map<Participant, List<SpecificReaction>> reactionsByParticipant = new HashMap<>();

        public FocusGroupRun(FocusGroup focusGroup) {
            this.focusGroup = focusGroup;
        }

        public void record(SpecificReaction reaction) {
            reactionsByParticipant
                    .computeIfAbsent(reaction.participant(), k -> new LinkedList<>())
                    .add(reaction);
        }

        public List<SpecificReaction> getReactionsForParticipant(Participant participant) {
            return reactionsByParticipant.getOrDefault(participant, List.of());
        }

        public MessageScore getAverageScoreForMessage(Message message) {
            var reactions = reactionsByParticipant.values().stream()
                    .flatMap(List::stream)
                    .filter(r -> r.message().equals(message))
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
