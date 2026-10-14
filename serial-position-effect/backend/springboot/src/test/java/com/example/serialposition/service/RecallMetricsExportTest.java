package com.example.serialposition.service;

import com.example.serialposition.dto.TestResultDto;
import com.example.serialposition.model.Participant;
import com.example.serialposition.model.PositionRecall;
import com.example.serialposition.model.TestResult;
import com.example.serialposition.model.TestType;
import com.example.serialposition.repository.ParticipantRepository;
import com.example.serialposition.util.RecallImprovement;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecallMetricsExportTest {

    @Test
    void scoringCountsOnlyTheFourMiddlePositions() {
        List<String> words = words("word");
        TestResultDto result = new ScoringService().score(
                TestType.NORMAL,
                words,
                List.of("word3", "word4", "word7", "word8"));

        assertEquals(4, result.totalRecalled());
        assertEquals(2, result.middleRecalled());
        assertEquals(50.0, result.middleScore());

        TestResultDto semanticResult = new ScoringService().score(
            TestType.SEMANTIC,
            words,
            List.of("word4", "word5", "word6"));
        assertEquals(TestType.SEMANTIC, semanticResult.testType());
        assertEquals(3, semanticResult.middleRecalled());
        assertEquals(75.0, semanticResult.middleScore());
    }

    @Test
    void improvementUsesRatiosAndHandlesZeroBaselines() {
        assertEquals(100.0, RecallImprovement.of(2, 4));
        assertNull(RecallImprovement.of(0, 4));
        assertEquals(50.0, RecallImprovement.ofPercentages(40.0, 60.0));
        assertEquals(200.0, RecallImprovement.ofPercentages(25.0, 75.0));
        assertNull(RecallImprovement.ofPercentages(0.0, 75.0));
    }

    @Test
    void exportHasOneSheetAndIncludesParticipantMetrics() throws Exception {
        Participant first = participant(1,
            TestType.NORMAL,
                test(TestType.NORMAL, 1, 1, 4),
                test(TestType.SEMANTIC, 2, 4, 5, 6, 10));
        Participant zeroBaseline = participant(2,
            TestType.SEMANTIC,
                test(TestType.NORMAL, 1),
            test(TestType.SEMANTIC, 1, 4));
        ParticipantRepository repository = mock(ParticipantRepository.class);
        when(repository.findAllByOrderByParticipantNumberAsc()).thenReturn(List.of(first, zeroBaseline));

        byte[] bytes = new SpreadsheetExportService(repository).export();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("Results", workbook.getSheetName(0));
            var sheet = workbook.getSheetAt(0);
                assertEquals(17, sheet.getRow(0).getLastCellNum());
            assertEquals("Total Recall Improvement (%)", sheet.getRow(0).getCell(14).getStringCellValue());
                assertEquals("Middle Recall", sheet.getRow(0).getCell(15).getStringCellValue());
                assertEquals("Middle Position Recall Improvement (%)", sheet.getRow(0).getCell(16).getStringCellValue());
                assertEquals(4, sheet.getNumMergedRegions());
                assertEquals(1, sheet.getMergedRegion(0).getFirstRow());
                assertEquals(2, sheet.getMergedRegion(0).getLastRow());
                assertEquals(0, sheet.getMergedRegion(0).getFirstColumn());
                assertEquals(16, sheet.getMergedRegion(1).getFirstColumn());
                assertEquals(1, sheet.getMergedRegion(1).getFirstRow());
                assertEquals(2, sheet.getMergedRegion(1).getLastRow());
                assertEquals(3, sheet.getMergedRegion(2).getFirstRow());
                assertEquals(4, sheet.getMergedRegion(2).getLastRow());
                assertEquals(16, sheet.getMergedRegion(3).getFirstColumn());
                assertEquals(3, sheet.getMergedRegion(3).getFirstRow());
                assertEquals(4, sheet.getMergedRegion(3).getLastRow());
                assertEquals("P001", sheet.getRow(1).getCell(0).getStringCellValue());

            var firstSummary = sheet.getRow(1);
                var firstSemanticRow = sheet.getRow(2);
                assertEquals(0.0, firstSummary.getCell(14).getNumericCellValue());
                assertEquals("1/4 (25.0%)", firstSummary.getCell(15).getStringCellValue());
                assertEquals("3/4 (75.0%)", firstSemanticRow.getCell(15).getStringCellValue());

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                assertEquals(100.0, evaluator.evaluate(firstSemanticRow.getCell(14)).getNumberValue());
                assertEquals(200.0, evaluator.evaluate(firstSummary.getCell(16)).getNumberValue());
                assertEquals("+0.0\"%\";-0.0\"%\";0.0\"%\"",
                    firstSummary.getCell(16).getCellStyle().getDataFormatString());

            var zeroSemanticRow = sheet.getRow(3);
            var zeroSummary = sheet.getRow(4);
            assertEquals("Semantic", zeroSemanticRow.getCell(1).getStringCellValue());
            assertEquals("Normal", zeroSummary.getCell(1).getStringCellValue());
            assertEquals("1/4 (25.0%)", zeroSemanticRow.getCell(15).getStringCellValue());
            assertEquals("0/4 (0.0%)", zeroSummary.getCell(15).getStringCellValue());
            assertEquals("N/A", evaluator.evaluate(zeroSemanticRow.getCell(14)).getStringValue());
            assertEquals("N/A", evaluator.evaluate(zeroSemanticRow.getCell(16)).getStringValue());
        }
    }

    private static Participant participant(int number, TestType firstTestType, TestResult normal, TestResult semantic) {
        Participant participant = new Participant();
        participant.setParticipantNumber(number);
        participant.setFirstTestType(firstTestType);
        if (firstTestType == TestType.NORMAL) {
            participant.addTest(normal);
            participant.addTest(semantic);
        } else {
            participant.addTest(semantic);
            participant.addTest(normal);
        }
        return participant;
    }

    private static TestResult test(TestType type, int sequence, int... recalledPositions) {
        List<Integer> recalled = new ArrayList<>();
        for (int position : recalledPositions) {
            recalled.add(position);
        }

        TestResult result = new TestResult();
        result.setTestType(type);
        result.setTestSequence(sequence);
        result.setTotalRecalled(recalled.size());
        for (int position = 1; position <= 10; position++) {
            result.addPosition(new PositionRecall(position, "word" + position, recalled.contains(position)));
        }
        return result;
    }

    private static List<String> words(String prefix) {
        List<String> words = new ArrayList<>();
        for (int position = 1; position <= 10; position++) {
            words.add(prefix + position);
        }
        return words;
    }
}