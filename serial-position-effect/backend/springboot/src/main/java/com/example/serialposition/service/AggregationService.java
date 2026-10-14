package com.example.serialposition.service;

import com.example.serialposition.dto.AggregateDto;
import com.example.serialposition.dto.PositionAggregateDto;
import com.example.serialposition.model.PositionRecall;
import com.example.serialposition.model.TestType;
import com.example.serialposition.repository.ParticipantRepository;
import com.example.serialposition.repository.PositionRecallRepository;
import com.example.serialposition.util.Percentages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recall percentage per serial position across all participants, computed
 * separately for each test type. Plotted against position, each series is the
 * serial position curve for that condition.
 */
@Service
public class AggregationService {

    private final ParticipantRepository participantRepository;
    private final PositionRecallRepository positionRecallRepository;

    public AggregationService(ParticipantRepository participantRepository,
                              PositionRecallRepository positionRecallRepository) {
        this.participantRepository = participantRepository;
        this.positionRecallRepository = positionRecallRepository;
    }

    @Transactional(readOnly = true)
    public AggregateDto aggregate() {
        return new AggregateDto(
                (int) participantRepository.count(),
                curve(TestType.NORMAL),
                curve(TestType.SEMANTIC)
        );
    }

    private List<PositionAggregateDto> curve(TestType type) {
        Map<Integer, List<PositionRecall>> byPosition = positionRecallRepository.findByTestResult_TestType(type).stream()
                .collect(Collectors.groupingBy(PositionRecall::getWordPosition));

        return byPosition.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int presented = entry.getValue().size();
                    int recalled = (int) entry.getValue().stream().filter(PositionRecall::isRecalled).count();
                    return new PositionAggregateDto(entry.getKey(), presented, recalled, Percentages.of(recalled, presented));
                })
                .toList();
    }
}
