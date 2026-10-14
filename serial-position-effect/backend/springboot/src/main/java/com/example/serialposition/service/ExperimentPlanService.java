package com.example.serialposition.service;

import com.example.serialposition.dto.ExperimentPlanDto;
import com.example.serialposition.dto.TestPlanDto;
import com.example.serialposition.model.TestType;
import com.example.serialposition.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

/**
 * Builds the word lists for a new session and decides which test comes first.
 * The first test alternates with the participant number (odd: normal first,
 * even: semantic first) so both orders are used equally often.
 */
@Service
public class ExperimentPlanService {

    private final WordBank wordBank;
    private final ParticipantRepository participantRepository;
    private final Random random = new Random();

    public ExperimentPlanService(WordBank wordBank, ParticipantRepository participantRepository) {
        this.wordBank = wordBank;
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public ExperimentPlanDto createPlan() {
        WordBank.ListPair lists = wordBank.generate(random);
        TestPlanDto normal = new TestPlanDto(TestType.NORMAL, lists.normal());
        TestPlanDto semantic = new TestPlanDto(TestType.SEMANTIC, lists.semantic());

        int nextParticipantNumber = participantRepository.findMaxParticipantNumber() + 1;
        boolean normalFirst = nextParticipantNumber % 2 == 1;
        return new ExperimentPlanDto(normalFirst ? List.of(normal, semantic) : List.of(semantic, normal));
    }
}
