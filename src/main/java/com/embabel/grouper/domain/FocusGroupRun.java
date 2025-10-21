package com.embabel.grouper.domain;

import com.embabel.common.core.types.HasInfoString;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Built up as we return results.
 * Exposes stats.
 */
public class FocusGroupRun implements HasInfoString {

    public final Model.FocusGroup focusGroup;

    public final Model.Positioning positioning;

    public final List<Model.ParticipantMessagePresentation> combinations;

    private final List<Model.SpecificReaction> specificReactions = new LinkedList<>();

    public FocusGroupRun(
            Model.FocusGroup focusGroup,
            Model.Positioning positioning) {
        this.focusGroup = focusGroup;
        this.positioning = positioning;

        // Initialize all combinations
        this.combinations = positioning.messageVariants().stream()
                .flatMap(mv -> mv.expressions().stream())
                .flatMap(variant -> focusGroup.participants().stream()
                        .map(participant -> new Model.ParticipantMessagePresentation(participant, variant)))
                .toList();
    }

    public boolean isComplete() {
        return specificReactions.size() == combinations.size();
    }

    public void record(Model.SpecificReaction reaction) {
        specificReactions.add(reaction);
    }

    public List<Model.SpecificReaction> getReactionsForParticipant(Model.Participant participant) {
        return specificReactions.stream()
                .filter(r -> r.participantMessagePresentation().participant().equals(participant))
                .toList();
    }

    public Model.MessageVariantScore getBestPerformingMessageVariant() {
        if (specificReactions.isEmpty()) {
            return null;
        }

        return positioning.messageVariants().stream()
                .flatMap(mv -> mv.expressions().stream())
                .map(this::getAverageScoreForMessageVariant)
                .filter(score -> score.count() > 0) // Only consider variants with reactions
                .max((s1, s2) -> Double.compare(s1.normalizedScore(), s2.normalizedScore()))
                .orElse(null);
    }

    public Model.MessageVariantScore getAverageScoreForMessageVariant(Model.MessageVariant messageVariant) {
        var reactions = specificReactions.stream()
                .filter(r -> r.participantMessagePresentation().messageVariant().equals(messageVariant))
                .toList();

        long count = reactions.size();

        if (count == 0) {
            return new Model.MessageVariantScore(messageVariant, 0.0, 0.0, 0);
        }

        double average = reactions.stream()
                .mapToDouble(r -> r.reaction().rating().score())
                .average()
                .orElse(0.0);

        // Calculate normalized (weighted) average
        double totalWeight = reactions.stream()
                .mapToDouble(r -> focusGroup.normalizedWeight(r.participantMessagePresentation().participant()))
                .sum();

        double normalizedAverage = reactions.stream()
                .mapToDouble(r -> r.reaction().rating().score() *
                                  focusGroup.normalizedWeight(r.participantMessagePresentation().participant()))
                .sum() / totalWeight;

        return new Model.MessageVariantScore(messageVariant, average, normalizedAverage, count);
    }

    public double getAverageScoreForParticipant(Model.Participant participant) {
        return specificReactions.stream()
                .filter(r -> r.participantMessagePresentation().participant().equals(participant))
                .mapToDouble(r -> r.reaction().rating().score())
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
                    Model.MessageVariantScore score = getAverageScoreForMessageVariant(expr);
                    return Map.entry(expr, score);
                })
                .sorted((e1, e2) -> Double.compare(e2.getValue().averageScore(), e1.getValue().averageScore()))
                .toList();

        sb.append(indentStr).append("Message Ranking by Effectiveness:\n");
        sb.append(indentStr).append("---------------------------------\n");
        int rank = 1;
        for (var entry : messageScores) {
            Model.MessageVariant expr = entry.getKey();
            Model.MessageVariantScore score = entry.getValue();
            sb.append(indentStr).append(String.format("%d. %.2f - %s (id: %s)\n",
                    rank++,
                    score.averageScore(),
                    expr.wording().length() > 60 ? expr.wording().substring(0, 57) + "..." : expr.wording(),
                    expr.message().id()));
        }
        sb.append("\n");

        sb.append(indentStr).append("Detailed Results:\n");
        sb.append(indentStr).append("-----------------\n\n");

        for (var entry : messageScores) {
            Model.MessageVariant expr = entry.getKey();
            Model.MessageVariantScore score = entry.getValue();

            sb.append(indentStr).append(String.format("Message: %s (ID: %s)\n", expr.message().content(), expr.message().id()));
            sb.append(indentStr).append(String.format("Objective: %s\n", expr.message().objective()));
            sb.append(indentStr).append(String.format("Expression: %s\n", expr.wording()));
            sb.append(indentStr).append(String.format("Average Score: %.2f - %d reactions\n",
                    score.averageScore(),
                    score.count()));

            if (isVerbose) {
                sb.append(indentStr).append("  Participant Reactions:\n");

                // Get all reactions for this message variant
                var reactions = specificReactions.stream()
                        .filter(r -> r.participantMessagePresentation().messageVariant().equals(expr))
                        .toList();

                for (var reaction : reactions) {
                    Model.Participant participant = reaction.participantMessagePresentation().participant();

                    sb.append(indentStr).append(String.format("    %s: %.2f (%.0f%%)\n",
                            participant.name(),
                            reaction.reaction().rating().score(),
                            reaction.reaction().rating().score() * 100));
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
