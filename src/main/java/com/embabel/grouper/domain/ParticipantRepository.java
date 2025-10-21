package com.embabel.grouper.domain;

import java.util.List;

public interface ParticipantRepository {

    List<Model.Participant> findByGroup(String group);
}