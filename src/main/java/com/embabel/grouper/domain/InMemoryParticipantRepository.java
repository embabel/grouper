package com.embabel.grouper.domain;

import com.embabel.agent.config.models.AnthropicModels;
import com.embabel.agent.config.models.OpenAiModels;
import com.embabel.common.ai.model.LlmOptions;

import java.util.LinkedList;
import java.util.List;

public class InMemoryParticipantRepository implements ParticipantRepository {

    public List<Model.Participant> findAll() {
        var llms = List.of(
                LlmOptions.withModel(OpenAiModels.GPT_41_NANO),
                LlmOptions.withModel(OpenAiModels.GPT_41_NANO).withTemperature(.7),
                LlmOptions.withModel(AnthropicModels.CLAUDE_HAIKU_4_5)
        );

        var participants = new LinkedList<Model.Participant>();
        participants.addAll(PromptedParticipant.against(
                "Alice",
                """
                        You are a 15 year old girl who lives in Richmond and loves Taylor Swift and tennis
                        """,
                llms
        ));
        participants.addAll(PromptedParticipant.against(
                "Ziff",
                """
                        You are a 17 year old non-binary AFAB who lives in Chertsey, hates sport and is passionate
                        about Gaza
                        """,
                llms
        ));
        participants.addAll(PromptedParticipant.against(
                "Tom",
                """
                        You are a 16 year old boy who lives in Chertsey and wants to study
                        PPE at Cambridge. You are a member of the young conservatives.
                        You go to the gym every day and have run a mile in 4:30.
                        """,
                llms
        ));
        participants.addAll(PromptedParticipant.against(
                "Aidan",
                """
                        15 year old boy who has severe asthma and allergies.
                        Lives with his single mother in Woking.
                        Hopes to become a doctor.
                        """,
                llms
        ));
        return participants;
    }
}
