package com.embabel.grouper.agent;

import com.embabel.common.ai.model.LlmOptions;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The identity is solely responsible for the participant's contribution.
 */
public record PromptedParticipant(
        String name,
        LlmOptions llm,
        String identity
) implements Domain.Participant {
    
    @Override
    public String id() {
        return name + "-" + llm;
    }

    public static List<PromptedParticipant> against(
            String name,
            String identity,
            List<LlmOptions> llms
    ) {
        return llms.stream().map(llm -> new PromptedParticipant(name, llm, identity)).toList();
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
