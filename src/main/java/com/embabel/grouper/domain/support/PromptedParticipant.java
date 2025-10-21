package com.embabel.grouper.domain.support;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.grouper.domain.Model;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The identity is solely responsible for the participant's contribution.
 */
public record PromptedParticipant(
        String name,
        LlmOptions llm,
        String identity,
        @Override double populationPercentage
) implements Model.Participant {

    @Override
    public String id() {
        return name + "-" + llm;
    }

    public static List<PromptedParticipant> against(
            String name,
            String identity,
            List<LlmOptions> llms,
            double populationPercentage
    ) {
        return llms.stream().map(llm -> new PromptedParticipant(name, llm, identity, populationPercentage)).toList();
    }

    @NotNull
    @Override
    public String contribution() {
        return """
                NAME: %s
                IDENTITY:
                %s
                """.formatted(name, identity);
    }
}
