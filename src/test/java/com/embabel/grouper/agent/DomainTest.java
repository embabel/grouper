package com.embabel.grouper.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {

    private Domain.FocusGroupRun focusGroupRun;
    private Domain.Message message1;
    private Domain.Message message2;
    private TestParticipant participant1;
    private TestParticipant participant2;

    // Test implementation of Participant
    private static class TestParticipant implements Domain.Participant {
        private final String name;
        private final String model;

        TestParticipant(String name, String model) {
            this.name = name;
            this.model = model;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String model() {
            return model;
        }

        @Override
        public String contribution() {
            return "Test contribution from " + name;
        }
    }

    @BeforeEach
    void setUp() {
        participant1 = new TestParticipant("Alice", "gpt-4");
        participant2 = new TestParticipant("Bob", "claude");

        Domain.FocusGroup focusGroup = new Domain.FocusGroup(
                List.of(participant1, participant2),
                Instant.now()
        );

        focusGroupRun = new Domain.FocusGroupRun(focusGroup);

        message1 = new Domain.Message("msg1", "First message");
        message2 = new Domain.Message("msg2", "Second message");
    }

    @Test
    void testRecordReaction() {
        Domain.Reaction reaction = new Domain.Reaction(
                "Positive feedback",
                "Negative feedback",
                List.of("quote1", "quote2"),
                8.5
        );

        Domain.SpecificReaction specificReaction = new Domain.SpecificReaction(
                message1,
                participant1,
                reaction,
                Instant.now()
        );

        focusGroupRun.record(specificReaction);

        List<Domain.SpecificReaction> reactions = focusGroupRun.getReactionsForParticipant(participant1);
        assertEquals(1, reactions.size());
        assertEquals(specificReaction, reactions.get(0));
    }

    @Test
    void testGetReactionsForParticipant_NoReactions() {
        List<Domain.SpecificReaction> reactions = focusGroupRun.getReactionsForParticipant(participant1);
        assertTrue(reactions.isEmpty());
    }

    @Test
    void testGetReactionsForParticipant_MultipleReactions() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                message2,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        List<Domain.SpecificReaction> reactions = focusGroupRun.getReactionsForParticipant(participant1);
        assertEquals(2, reactions.size());
    }

    @Test
    void testGetAverageScoreForMessage_SingleReaction() {
        Domain.SpecificReaction reaction = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Positive", "Negative", List.of(), 8.5),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        Domain.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(message1);
        assertEquals(8.5, messageScore.averageScore(), 0.001);
        assertEquals(1, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_MultipleReactions() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                message1,
                participant2,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        Domain.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(message1);
        assertEquals(8.0, messageScore.averageScore(), 0.001);
        assertEquals(2, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_NoReactions() {
        Domain.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(message1);
        assertEquals(0.0, messageScore.averageScore(), 0.001);
        assertEquals(0, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_DifferentMessages() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                message2,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        Domain.MessageScore score1 = focusGroupRun.getAverageScoreForMessage(message1);
        Domain.MessageScore score2 = focusGroupRun.getAverageScoreForMessage(message2);

        assertEquals(7.0, score1.averageScore(), 0.001);
        assertEquals(1, score1.count());
        assertEquals(9.0, score2.averageScore(), 0.001);
        assertEquals(1, score2.count());
    }

    @Test
    void testGetAverageScoreForParticipant_SingleReaction() {
        Domain.SpecificReaction reaction = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Positive", "Negative", List.of(), 8.5),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        double avgScore = focusGroupRun.getAverageScoreForParticipant(participant1);
        assertEquals(8.5, avgScore, 0.001);
    }

    @Test
    void testGetAverageScoreForParticipant_MultipleReactions() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 6.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                message2,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 10.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        double avgScore = focusGroupRun.getAverageScoreForParticipant(participant1);
        assertEquals(8.0, avgScore, 0.001);
    }

    @Test
    void testGetAverageScoreForParticipant_NoReactions() {
        double avgScore = focusGroupRun.getAverageScoreForParticipant(participant1);
        assertEquals(0.0, avgScore, 0.001);
    }

    @Test
    void testGetAverageScoreForParticipant_DifferentParticipants() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                message1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                message1,
                participant2,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        assertEquals(7.0, focusGroupRun.getAverageScoreForParticipant(participant1), 0.001);
        assertEquals(9.0, focusGroupRun.getAverageScoreForParticipant(participant2), 0.001);
    }
}
