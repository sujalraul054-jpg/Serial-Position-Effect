package com.example.serialposition.repository;

import com.example.serialposition.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    @Query("select coalesce(max(p.participantNumber), 0) from Participant p")
    int findMaxParticipantNumber();

    List<Participant> findAllByOrderByParticipantNumberAsc();

    Optional<Participant> findTopByOrderByParticipantNumberDesc();
}
