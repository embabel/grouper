package com.embabel.grouper.domain.support;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.grouper.domain.Model;
import com.embabel.grouper.domain.ParticipantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.List;

record ParticipantInfo(
        String name,
        String identity,
        Double populationPercentage
) {
}

record YmlData(
        List<ParticipantInfo> participants,
        List<LlmOptions> llms
) {
}

public class YmlParticipantRepository implements ParticipantRepository {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public List<Model.Participant> findByGroup(String group) {
        var filePath = java.nio.file.Path.of("data/participants/%s.yml".formatted(group));

        try {
            var ymlData = yamlMapper.readValue(filePath.toFile(), YmlData.class);

            // Create cartesian product of participants and LLMs
            return ymlData.participants().stream()
                    .flatMap(participantInfo -> ymlData.llms().stream()
                            .map(llm -> createParticipant(participantInfo, llm)))
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException("Failed to load participants from " + filePath, e);
        }
    }

    private Model.Participant createParticipant(ParticipantInfo info, LlmOptions llm) {
        return new PromptedParticipant(
                info.name(),
                llm,
                info.identity(),
                info.populationPercentage()
        );
    }
}
