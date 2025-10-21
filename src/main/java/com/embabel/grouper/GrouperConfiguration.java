package com.embabel.grouper;

import com.embabel.grouper.domain.MessageVariantsRepository;
import com.embabel.grouper.domain.ParticipantRepository;
import com.embabel.grouper.domain.support.YmlMessageVariantsRepository;
import com.embabel.grouper.domain.support.YmlParticipantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GrouperConfiguration {

    @Bean
    ParticipantRepository participantRepository() {
        return new YmlParticipantRepository();
    }

    @Bean
    MessageVariantsRepository messageVariantsRepository() {
        return new YmlMessageVariantsRepository();
    }
}
