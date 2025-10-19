package com.embabel.grouper.agent;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.grouper.domain.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    private Model.FocusGroupRun focusGroupRun;
    private Model.MessageVariant messageVariant1;
    private Model.MessageVariant messageVariant2;
    private TestParticipant participant1;
    private TestParticipant participant2;

    // Test implementation of Participant
    private static class TestParticipant implements Model.Participant {
        private final String name;
        private final LlmOptions llm;

        TestParticipant(String name, LlmOptions llm) {
            this.name = name;
            this.llm = llm;
        }

        @Override
        public String id() {
            return name + "-" + llm.getModel().provider();
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

        Model.FocusGroup focusGroup = new Model.FocusGroup(
                List.of(participant1, participant2)
        );

        Model.Message msg1 = new Model.Message("msg1", "First message content", "Test objective 1");
        Model.Message msg2 = new Model.Message("msg2", "Second message content", "Test objective 2");

        messageVariant1 = new Model.MessageVariant(msg1, "First message wording");
        messageVariant2 = new Model.MessageVariant(msg2, "Second message wording");

        Model.MessageVariants messageVariants1 = new Model.MessageVariants(msg1, List.of(messageVariant1));
        Model.MessageVariants messageVariants2 = new Model.MessageVariants(msg2, List.of(messageVariant2));

        Model.Positioning positioning = new Model.Positioning(List.of(messageVariants1, messageVariants2));

        focusGroupRun = new Model.FocusGroupRun(focusGroup, positioning);
    }

    @Test
    void testRecordReaction() {
        Model.Reaction reaction = new Model.Reaction(
                "Positive feedback",
                "Negative feedback",
                List.of("quote1", "quote2"),
                8.5
        );

        Model.SpecificReaction specificReaction = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                reaction,
                Instant.now()
        );

        focusGroupRun.record(specificReaction);

        List<Model.SpecificReaction> reactions = focusGroupRun.getReactionsForParticipant(participant1);
        assertEquals(1, reactions.size());
        assertEquals(specificReaction, reactions.get(0));
    }

    @Test
    void testGetReactionsForParticipant_NoReactions() {
        List<Model.SpecificReaction> reactions = focusGroupRun.getReactionsForParticipant(participant1);
        assertTrue(reactions.isEmpty());
    }

    @Test
    void testGetReactionsForParticipant_MultipleReactions() {
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant2),
                new Model.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        List<Model.SpecificReaction> reactions = focusGroupRun.getReactionsForParticipant(participant1);
        assertEquals(2, reactions.size());
    }

    @Test
    void testGetAverageScoreForMessage_SingleReaction() {
        Model.SpecificReaction reaction = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Positive", "Negative", List.of(), 8.5),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        Model.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(messageVariant1);
        assertEquals(8.5, messageScore.averageScore(), 0.001);
        assertEquals(1, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_MultipleReactions() {
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant2, messageVariant1),
                new Model.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        Model.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(messageVariant1);
        assertEquals(8.0, messageScore.averageScore(), 0.001);
        assertEquals(2, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_NoReactions() {
        Model.MessageScore messageScore = focusGroupRun.getAverageScoreForMessage(messageVariant1);
        assertEquals(0.0, messageScore.averageScore(), 0.001);
        assertEquals(0, messageScore.count());
    }

    @Test
    void testGetAverageScoreForMessage_DifferentMessages() {
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant2),
                new Model.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        focusGroupRun.record(reaction1);
        focusGroupRun.record(reaction2);

        Model.MessageScore score1 = focusGroupRun.getAverageScoreForMessage(messageVariant1);
        Model.MessageScore score2 = focusGroupRun.getAverageScoreForMessage(messageVariant2);

        assertEquals(7.0, score1.averageScore(), 0.001);
        assertEquals(1, score1.count());
        assertEquals(9.0, score2.averageScore(), 0.001);
        assertEquals(1, score2.count());
    }

    @Test
    void testGetAverageScoreForParticipant_SingleReaction() {
        Model.SpecificReaction reaction = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Positive", "Negative", List.of(), 8.5),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        double avgScore = focusGroupRun.getAverageScoreForParticipant(participant1);
        assertEquals(8.5, avgScore, 0.001);
    }

    @Test
    void testGetAverageScoreForParticipant_MultipleReactions() {
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 6.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant2),
                new Model.Reaction("Great", "Poor", List.of(), 10.0),
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
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant2, messageVariant1),
                new Model.Reaction("Great", "Poor", List.of(), 9.0),
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
        Model.SpecificReaction reaction = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        focusGroupRun.record(reaction);

        assertFalse(focusGroupRun.isComplete());
    }

    @Test
    void testIsComplete_OneParticipantComplete() {
        // participant1 reacts to both messages
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant2),
                new Model.Reaction("Great", "Poor", List.of(), 9.0),
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
        Model.SpecificReaction reaction1 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant1),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        );

        Model.SpecificReaction reaction2 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, messageVariant2),
                new Model.Reaction("Great", "Poor", List.of(), 9.0),
                Instant.now()
        );

        Model.SpecificReaction reaction3 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant2, messageVariant1),
                new Model.Reaction("Okay", "Meh", List.of(), 5.0),
                Instant.now()
        );

        Model.SpecificReaction reaction4 = new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant2, messageVariant2),
                new Model.Reaction("Nice", "Nope", List.of(), 8.0),
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
        Model.Message msg1 = new Model.Message("msg1", "First message content", "Test objective");

        Model.MessageVariant expr1a = new Model.MessageVariant(msg1, "Expression 1A");
        Model.MessageVariant expr1b = new Model.MessageVariant(msg1, "Expression 1B");

        Model.MessageVariants messageVariants = new Model.MessageVariants(msg1, List.of(expr1a, expr1b));
        Model.Positioning positioning = new Model.Positioning(List.of(messageVariants));

        Model.FocusGroup focusGroup = new Model.FocusGroup(List.of(participant1));
        Model.FocusGroupRun run = new Model.FocusGroupRun(focusGroup, positioning);

        // Initially incomplete
        assertFalse(run.isComplete());

        // Add reaction to first wording only
        run.record(new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, expr1a),
                new Model.Reaction("Good", "Bad", List.of(), 7.0),
                Instant.now()
        ));

        // Still incomplete - missing expr1b
        assertFalse(run.isComplete());

        // Add reaction to second wording
        run.record(new Model.SpecificReaction(
                new Model.ParticipantMessagePresentation(participant1, expr1b),
                new Model.Reaction("Great", "Poor", List.of(), 8.0),
                Instant.now()
        ));

        // Now complete
        assertTrue(run.isComplete());
    }
}
