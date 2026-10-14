package com.example.serialposition.service;

import com.example.serialposition.dto.PositionResultDto;
import com.example.serialposition.dto.TestResultDto;
import com.example.serialposition.model.TestType;
import com.example.serialposition.util.Percentages;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores one test: which positions were recalled, and the primacy (1-3),
 * middle (4-7) and recency (8-10) percentages. Recalled words that are not on
 * the list have no effect on the score.
 */
@Service
public class ScoringService {

    private static final int PRIMACY_END = 3;
    private static final int MIDDLE_END = 7;

    public TestResultDto score(TestType testType, List<String> words, List<String> recalledWords) {
        Set<String> recalled = recalledWords.stream()
                .map(WordBank::normalize)
                .collect(Collectors.toSet());

        List<PositionResultDto> positions = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            positions.add(new PositionResultDto(i + 1, word, recalled.contains(WordBank.normalize(word))));
        }

        int total = words.size();
        int totalRecalled = (int) positions.stream().filter(PositionResultDto::recalled).count();
        int middleRecalled = (int) positions.subList(PRIMACY_END, MIDDLE_END).stream()
                .filter(PositionResultDto::recalled)
                .count();

        return new TestResultDto(
                testType,
                total,
                totalRecalled,
                middleRecalled,
                Percentages.of(totalRecalled, total),
                regionPercentage(positions, 0, PRIMACY_END),
                regionPercentage(positions, PRIMACY_END, MIDDLE_END),
                regionPercentage(positions, MIDDLE_END, total),
                positions,
                duplicateWords(recalledWords)
        );
    }

    private static double regionPercentage(List<PositionResultDto> positions, int fromInclusive, int toExclusive) {
        List<PositionResultDto> region = positions.subList(fromInclusive, toExclusive);
        int hits = (int) region.stream().filter(PositionResultDto::recalled).count();
        return Percentages.of(hits, region.size());
    }

    private static List<String> duplicateWords(List<String> recalledWords) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String word : recalledWords) {
            counts.merge(WordBank.normalize(word), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }
}
