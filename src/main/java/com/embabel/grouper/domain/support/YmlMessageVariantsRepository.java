package com.embabel.grouper.domain.support;

import com.embabel.grouper.domain.MessageVariantsRepository;
import com.embabel.grouper.domain.Model;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.List;

record Messaging(
        Model.Message message,
        List<String> wordings
) {
    Model.MessageVariants createMessageVariants() {
        return new Model.MessageVariants(message, wordings.toArray(new String[0]));
    }
}

public class YmlMessageVariantsRepository implements MessageVariantsRepository {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public Model.MessageVariants findByName(String name) {
        var filePath = java.nio.file.Path.of("data/messages/%s.yml".formatted(name));

        try {
            var messaging = yamlMapper.readValue(filePath.toFile(), Messaging.class);
            return messaging.createMessageVariants();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load message variants from " + filePath, e);
        }
    }
}
