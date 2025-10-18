package com.embabel.grouper.agent;

import com.embabel.common.ai.model.LlmOptions;
import org.jetbrains.annotations.NotNull;

/**
 * The identity is solely responsible for the participant's contribution.
 */
public record PromptedParticipant(
        String name,
        LlmOptions llm,
        String identity
) implements Domain.Participant {

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
