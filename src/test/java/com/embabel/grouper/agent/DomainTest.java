package com.embabel.grouper.agent;

import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainTest {

    private Domain.FocusGroupRun focusGroupRun;
    private Domain.MessageExpression messageExpression1;
    private Domain.MessageExpression messageExpression2;
    private TestParticipant participant1;
    private TestParticipant participant2;

    // Test implementation of Participant
    private static class TestParticipant implements Domain.Participant {
        private final String name;
        private final LlmOptions llm;

        TestParticipant(String name, LlmOptions llm) {
            this.name = name;
            this.llm = llm;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public LlmOptions llm() {
            return llm;
        }

        @Override
        public String contribution() {
            return "Test contribution from " + name;
        }
    }

    @BeforeEach
    void setUp() {
        participant1 = new TestParticipant("Alice", LlmOptions.withAutoLlm());
        participant2 = new TestParticipant("Bob", LlmOptions.withAutoLlm());

        Domain.FocusGroup focusGroup = new Domain.FocusGroup(
                List.of(participant1, participant2)
        );

        Domain.Message msg1 = new Domain.Message("msg1", "First message detail", "Test objective 1");
        Domain.Message msg2 = new Domain.Message("msg2", "Second message detail", "Test objective 2");

        messageExpression1 = new Domain.MessageExpression(msg1, "First message expression");
        messageExpression2 = new Domain.MessageExpression(msg2, "Second message expression");

        Domain.MessageTest messageTest1 = new Domain.MessageTest(msg1, List.of(messageExpression1));
        Domain.MessageTest messageTest2 = new Domain.MessageTest(msg2, List.of(messageExpression2));

        Domain.Positioning positioning = new Domain.Positioning(List.of(messageTest1, messageTest2));

        focusGroupRun = new Domain.FocusGroupRun(focusGroup, positioning);
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
                messageExpression1,
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
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression2,
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
                messageExpression1,
                participant1,
                new Domain.Reaction("Positive", "Negative", List.of(), 8.5),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        Domain.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(messageExpression1);
        assertEquals(8.5, messageScore.averageScore(), 0.001);
        assertEquals(1, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_MultipleReactions() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression1,
                participant2,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        Domain.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(messageExpression1);
        assertEquals(8.0, messageScore.averageScore(), 0.001);
        assertEquals(2, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_NoReactions() {
        Domain.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(messageExpression1);
        assertEquals(0.0, messageScore.averageScore(), 0.001);
        assertEquals(0, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_DifferentMessages() {
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression2,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        Domain.MessageScore score1 = focusGroupRun.getAverageScoreForMessage(messageExpression1);
        Domain.MessageScore score2 = focusGroupRun.getAverageScoreForMessage(messageExpression2);

        assertEquals(7.0, score1.averageScore(), 0.001);
        assertEquals(1, score1.count());
        assertEquals(9.0, score2.averageScore(), 0.001);
        assertEquals(1, score2.count());
    }

    @Test
    void testGetAverageScoreForParticipant_SingleReaction() {
        Domain.SpecificReaction reaction = new Domain.SpecificReaction(
                messageExpression1,
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
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 6.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression2,
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
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression1,
                participant2,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        assertEquals(7.0, focusGroupRun.getAverageScoreForParticipant(participant1), 0.001);
        assertEquals(9.0, focusGroupRun.getAverageScoreForParticipant(participant2), 0.001);
    }

    @Test
    void testIsComplete_NoReactions() {
        assertFalse(focusGroupRun.isComplete());
    }

    @Test
    void testIsComplete_PartialReactions() {
        // Only participant1 reacts to messageExpression1
        Domain.SpecificReaction reaction = new Domain.SpecificReaction(
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        assertFalse(focusGroupRun.isComplete());
    }

    @Test
    void testIsComplete_OneParticipantComplete() {
        // participant1 reacts to both messages
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression2,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        // Still incomplete because participant2 hasn't reacted
        assertFalse(focusGroupRun.isComplete());
    }

    @Test
    void testIsComplete_AllReactionsPresent() {
        // All participants react to all messages
        Domain.SpecificReaction reaction1 = new Domain.SpecificReaction(
                messageExpression1,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction2 = new Domain.SpecificReaction(
                messageExpression2,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction3 = new Domain.SpecificReaction(
                messageExpression1,
                participant2,
                new Domain.Reaction("Okay", "Meh", List.of(), 5.0),
                Instant.now()
        );

        Domain.SpecificReaction reaction4 = new Domain.SpecificReaction(
                messageExpression2,
                participant2,
                new Domain.Reaction("Nice", "Nope", List.of(), 8.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);
        focusGroupRun.record(reaction3);
        focusGroupRun.record(reaction4);

        assertTrue(focusGroupRun.isComplete());
    }

    @Test
    void testIsComplete_WithMultipleExpressionsPerMessage() {
        // Create a more complex positioning with multiple expressions per message
        Domain.Message msg1 = new Domain.Message("msg1", "First message detail", "Test objective");

        Domain.MessageExpression expr1a = new Domain.MessageExpression(msg1, "Expression 1A");
        Domain.MessageExpression expr1b = new Domain.MessageExpression(msg1, "Expression 1B");

        Domain.MessageTest messageTest = new Domain.MessageTest(msg1, List.of(expr1a, expr1b));
        Domain.Positioning positioning = new Domain.Positioning(List.of(messageTest));

        Domain.FocusGroup focusGroup = new Domain.FocusGroup(List.of(participant1));
        Domain.FocusGroupRun run = new Domain.FocusGroupRun(focusGroup, positioning);

        // Initially incomplete
        assertFalse(run.isComplete());

        // Add reaction to first expression only
        run.record(new Domain.SpecificReaction(
                expr1a,
                participant1,
                new Domain.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        ));

        // Still incomplete - missing expr1b
        assertFalse(run.isComplete());

        // Add reaction to second expression
        run.record(new Domain.SpecificReaction(
                expr1b,
                participant1,
                new Domain.Reaction("Great", "Poor", List.of(), 8.0),
                Instant.now()
        ));

        // Now complete
        assertTrue(run.isComplete());
    }
}
