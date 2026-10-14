package com.example.serialposition.service;

import com.example.serialposition.dto.PositionResultDto;
import com.example.serialposition.dto.SessionResultDto;
import com.example.serialposition.dto.SessionSubmissionDto;
import com.example.serialposition.dto.TestResultDto;
import com.example.serialposition.dto.TestSubmissionDto;
import com.example.serialposition.exception.SessionNotFoundException;
import com.example.serialposition.model.Participant;
import com.example.serialposition.model.PositionRecall;
import com.example.serialposition.model.TestResult;
import com.example.serialposition.model.TestType;
import com.example.serialposition.repository.ParticipantRepository;
import com.example.serialposition.repository.PositionRecallRepository;
import com.example.serialposition.repository.TestResultRepository;
import com.example.serialposition.util.RecallImprovement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private final ParticipantRepository participantRepository;
    private final TestResultRepository testResultRepository;
    private final PositionRecallRepository positionRecallRepository;
    private final ScoringService scoringService;
    private final WordBank wordBank;

    public SessionService(ParticipantRepository participantRepository,
                          TestResultRepository testResultRepository,
                          PositionRecallRepository positionRecallRepository,
                          ScoringService scoringService,
                          WordBank wordBank) {
        this.participantRepository = participantRepository;
        this.testResultRepository = testResultRepository;
        this.positionRecallRepository = positionRecallRepository;
        this.scoringService = scoringService;
        this.wordBank = wordBank;
    }

    /** Scores and stores a completed session (both tests) as a new participant. */
    @Transactional
    public SessionResultDto record(SessionSubmissionDto submission) {
        List<TestSubmissionDto> submitted = submission.tests();
        validate(submitted);

        Participant participant = new Participant();
        participant.setParticipantNumber(participantRepository.findMaxParticipantNumber() + 1);
        participant.setCreatedAt(LocalDateTime.now());
        participant.setFirstTestType(submitted.get(0).testType());

        List<TestResultDto> scored = new ArrayList<>();
        for (int i = 0; i < submitted.size(); i++) {
            TestSubmissionDto test = submitted.get(i);
            TestResultDto result = scoringService.score(test.testType(), test.words(), test.recalledWords());
            scored.add(result);
            participant.addTest(toEntity(i + 1, test, result));
        }
        participantRepository.save(participant);

        return new SessionResultDto(
                participant.getParticipantCode(),
                participant.getFirstTestType(),
                scored,
                RecallImprovement.of(
                        result(scored, TestType.NORMAL).totalRecalled(),
                        result(scored, TestType.SEMANTIC).totalRecalled()),
                RecallImprovement.ofPercentages(
                        result(scored, TestType.NORMAL).middleScore(),
                        result(scored, TestType.SEMANTIC).middleScore())
        );
    }

    /**
     * Deletes the most recent session, including its test results and per-position rows,
     * so it no longer appears in the spreadsheet or the aggregate curves. Returns its
     * participant ID. The next session reuses that participant number, which also keeps
     * the alternation of test order unchanged.
     */
    @Transactional
    public String deleteLatest() {
        Participant latest = participantRepository.findTopByOrderByParticipantNumberDesc()
                .orElseThrow(() -> new SessionNotFoundException("There is no recorded session to delete."));
        participantRepository.delete(latest);
        return latest.getParticipantCode();
    }

    @Transactional
    public void clearAll() {
        positionRecallRepository.deleteAllInBatch();
        testResultRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
    }

    private void validate(List<TestSubmissionDto> tests) {
        Set<TestType> types = tests.stream().map(TestSubmissionDto::testType).collect(Collectors.toSet());
        if (types.size() != TestType.values().length) {
            throw new IllegalArgumentException("A session must contain one NORMAL test and one SEMANTIC test.");
        }
        for (TestSubmissionDto test : tests) {
            boolean valid = test.testType() == TestType.NORMAL
                    ? wordBank.isValidNormalList(test.words())
                    : wordBank.isValidSemanticList(test.words());
            if (!valid) {
                throw new IllegalArgumentException(
                        "The " + test.testType().getLabel().toLowerCase() + " word list was not generated by this experiment.");
            }
        }
    }

    private TestResult toEntity(int sequence, TestSubmissionDto test, TestResultDto result) {
        TestResult entity = new TestResult();
        entity.setTestType(test.testType());
        entity.setTestSequence(sequence);
        entity.setOriginalWords(String.join(",", test.words()));
        entity.setRecalledWords(String.join(",", test.recalledWords()));
        entity.setTotalRecalled(result.totalRecalled());
        for (PositionResultDto position : result.positionResults()) {
            entity.addPosition(new PositionRecall(position.position(), position.word(), position.recalled()));
        }
        return entity;
    }

    private static TestResultDto result(List<TestResultDto> results, TestType type) {
        return results.stream()
                .filter(result -> result.testType() == type)
                .findFirst()
                .orElseThrow();
    }
}
