package com.embabel.grouper.domain;

import org.springframework.data.repository.Repository;

import java.util.List;

public interface ParticipantRepository extends Repository<Model.Participant, String> {

    List<Model.Participant> findAll();
}