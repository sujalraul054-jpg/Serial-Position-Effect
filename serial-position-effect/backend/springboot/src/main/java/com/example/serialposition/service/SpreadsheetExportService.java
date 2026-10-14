package com.example.serialposition.service;

import com.example.serialposition.model.Participant;
import com.example.serialposition.model.PositionRecall;
import com.example.serialposition.model.TestResult;
import com.example.serialposition.model.TestType;
import com.example.serialposition.repository.ParticipantRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Builds the results workbook.
 *
 * One worksheet with one row per test (two per participant), the per-position
 * outcomes and aligned recall summaries for each test.
 */
@Service
public class SpreadsheetExportService {

    private static final int FIXED_COLUMNS = 4;
    private static final int POSITION_COLUMNS = WordBank.LIST_SIZE;
    private static final int TOTAL_IMPROVEMENT_COLUMN = FIXED_COLUMNS + POSITION_COLUMNS;
    private static final int MIDDLE_RECALL_COLUMN = TOTAL_IMPROVEMENT_COLUMN + 1;
    private static final int MIDDLE_IMPROVEMENT_COLUMN = TOTAL_IMPROVEMENT_COLUMN + 2;
    private static final int MIDDLE_START = 4;
    private static final int MIDDLE_END = 7;
    private static final String IMPROVEMENT_NOTE =
            "Total improvement (%) = ((Semantic total recalled - Normal total recalled) / Normal total recalled) * 100. "
                        + "Middle improvement (%) = ((Semantic middle recalled - Normal middle recalled) / Normal middle recalled) * 100. "
                        + "Both improvements are N/A when the corresponding Normal recall is 0.";

    private final ParticipantRepository participantRepository;

    public SpreadsheetExportService(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Transactional(readOnly = true)
    public byte[] export() throws IOException {
        List<Participant> participants = participantRepository.findAllByOrderByParticipantNumberAsc();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            writeResultsSheet(workbook, headerStyle, participants);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeResultsSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<Participant> participants) {
        Sheet sheet = workbook.createSheet("Results");

        String[] fixed = {"Participant ID", "Test Type", "Test Order", "Total Recalled"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < fixed.length; i++) {
            writeHeaderCell(header, i, fixed[i], headerStyle);
        }
        for (int i = 0; i < WordBank.LIST_SIZE; i++) {
            writeHeaderCell(header, FIXED_COLUMNS + i, "Position " + (i + 1), headerStyle);
        }
        writeHeaderCell(header, TOTAL_IMPROVEMENT_COLUMN, "Total Recall Improvement (%)", headerStyle);
        writeHeaderCell(header, MIDDLE_RECALL_COLUMN, "Middle Recall", headerStyle);
        writeHeaderCell(header, MIDDLE_IMPROVEMENT_COLUMN, "Middle Position Recall Improvement (%)", headerStyle);

        int rowIndex = 1;
        CellStyle improvementStyle = workbook.createCellStyle();
        improvementStyle.setDataFormat(workbook.createDataFormat().getFormat("+0.0\"%\";-0.0\"%\";0.0\"%\""));
        CellStyle middleRecallStyle = workbook.createCellStyle();
        middleRecallStyle.setWrapText(true);
        middleRecallStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle participantIdStyle = workbook.createCellStyle();
        participantIdStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        for (Participant participant : participants) {
            String testOrder = participant.getFirstTestType().getLabel() + " first";
            int firstParticipantRow = rowIndex;
            int normalRowIndex = -1;
            int semanticRowIndex = -1;
            for (TestResult test : participant.getTests()) {
                Row row = sheet.createRow(rowIndex++);
                Cell participantId = row.createCell(0);
                if (test.getTestSequence() == 1) {
                    participantId.setCellValue(participant.getParticipantCode());
                }
                participantId.setCellStyle(participantIdStyle);
                row.createCell(1).setCellValue(test.getTestType().getLabel());
                row.createCell(2).setCellValue(testOrder);
                row.createCell(3).setCellValue(test.getTotalRecalled());

                int[] recalled = new int[WordBank.LIST_SIZE];
                for (PositionRecall position : test.getPositions()) {
                    recalled[position.getWordPosition() - 1] = position.isRecalled() ? 1 : 0;
                }
                for (int i = 0; i < recalled.length; i++) {
                    row.createCell(FIXED_COLUMNS + i).setCellValue(recalled[i]);
                }
                if (test.getTestType() == TestType.NORMAL) {
                    normalRowIndex = row.getRowNum();
                } else if (test.getTestType() == TestType.SEMANTIC) {
                    semanticRowIndex = row.getRowNum();
                }
            }
            if (rowIndex - firstParticipantRow == 2) {
                sheet.addMergedRegion(new CellRangeAddress(firstParticipantRow, rowIndex - 1, 0, 0));
            }
            if (normalRowIndex >= 0 && semanticRowIndex >= 0) {
                writeParticipantSummary(
                        participant,
                        sheet,
                        normalRowIndex,
                        semanticRowIndex,
                        improvementStyle,
                        middleRecallStyle);
            }
        }

        int lastDataRow = rowIndex;
        if (!participants.isEmpty()) {
            int meanRowIndex = rowIndex + 1;
            Row mean = sheet.createRow(meanRowIndex);
            mean.setHeightInPoints(32);
            writeHeaderCell(mean, 0, "Mean of all participants", headerStyle);
            writeMeanSummary(mean, rowIndex, improvementStyle, middleRecallStyle);
            lastDataRow = meanRowIndex + 1;
        }
        sheet.createRow(lastDataRow + 1).createCell(0).setCellValue(IMPROVEMENT_NOTE);

        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 15 * 256);
        for (int i = 0; i < WordBank.LIST_SIZE; i++) {
            sheet.setColumnWidth(FIXED_COLUMNS + i, 12 * 256);
        }
        sheet.setColumnWidth(TOTAL_IMPROVEMENT_COLUMN, 26 * 256);
        sheet.setColumnWidth(MIDDLE_RECALL_COLUMN, 38 * 256);
        sheet.setColumnWidth(MIDDLE_IMPROVEMENT_COLUMN, 38 * 256);
    }

        private void writeParticipantSummary(Participant participant, Sheet sheet, int normalRowIndex,
                         int semanticRowIndex, CellStyle percentStyle,
                                         CellStyle middleRecallStyle) {
        Row normalRow = sheet.getRow(normalRowIndex);
        Row semanticRow = sheet.getRow(semanticRowIndex);
        Cell normalImprovement = normalRow.createCell(TOTAL_IMPROVEMENT_COLUMN);
        normalImprovement.setCellValue(0.0);
        normalImprovement.setCellStyle(percentStyle);
        writeFormula(semanticRow, TOTAL_IMPROVEMENT_COLUMN,
            totalImprovementFormula(normalRowIndex + 1, semanticRowIndex + 1), percentStyle);

        int normalMiddle = middleRecalled(test(participant, TestType.NORMAL));
        int semanticMiddle = middleRecalled(test(participant, TestType.SEMANTIC));
        writeMiddleRecall(normalRow, middleRecallValue(normalMiddle), middleRecallStyle);
        writeMiddleRecall(semanticRow, middleRecallValue(semanticMiddle), middleRecallStyle);

        int firstRow = Math.min(normalRowIndex, semanticRowIndex);
        int lastRow = Math.max(normalRowIndex, semanticRowIndex);
        writeFormula(sheet.getRow(firstRow), MIDDLE_IMPROVEMENT_COLUMN,
            middleImprovementFormula(normalRowIndex + 1, semanticRowIndex + 1), percentStyle);
        sheet.addMergedRegion(new CellRangeAddress(
            firstRow, lastRow, MIDDLE_IMPROVEMENT_COLUMN, MIDDLE_IMPROVEMENT_COLUMN));
        }

        private void writeMiddleRecall(Row row, String value, CellStyle style) {
        row.createCell(MIDDLE_RECALL_COLUMN).setCellValue(value);
        row.getCell(MIDDLE_RECALL_COLUMN).setCellStyle(style);
        row.setHeightInPoints(20);
    }

    private void writeMeanSummary(Row row, int lastDataRow, CellStyle percentStyle, CellStyle textStyle) {
        String testTypes = "$B$2:$B$" + lastDataRow;
        String normalTotals = "$D$2:$D$" + lastDataRow;
        String normalAverage = "AVERAGEIF(" + testTypes + ",\"Normal\"," + normalTotals + ")";
        String semanticAverage = "AVERAGEIF(" + testTypes + ",\"Semantic\"," + normalTotals + ")";
        writeFormula(row, TOTAL_IMPROVEMENT_COLUMN,
                "IF(" + normalAverage + "=0,\"N/A\",(" + semanticAverage + "-" + normalAverage
                        + ")/" + normalAverage + "*100)", percentStyle);
        String normalMiddleAverage = middleAverageFormula(testTypes, "Normal", lastDataRow);
        String semanticMiddleAverage = middleAverageFormula(testTypes, "Semantic", lastDataRow);
        writeFormula(row, MIDDLE_RECALL_COLUMN,
            middleAverageLabelFormula(normalMiddleAverage, semanticMiddleAverage), textStyle);
        writeFormula(row, MIDDLE_IMPROVEMENT_COLUMN,
                "IF(" + normalMiddleAverage + "=0,\"N/A\",(" + semanticMiddleAverage + "-"
                        + normalMiddleAverage + ")/" + normalMiddleAverage + "*100)", percentStyle);
    }

    private String totalImprovementFormula(int normalRow, int semanticRow) {
        return "IF(D" + normalRow + "=0,\"N/A\",(D" + semanticRow + "-D" + normalRow
                + ")/D" + normalRow + "*100)";
    }

    private String middleImprovementFormula(int normalRow, int semanticRow) {
        String normalMiddle = "SUM(H" + normalRow + ":K" + normalRow + ")";
        String semanticMiddle = "SUM(H" + semanticRow + ":K" + semanticRow + ")";
        return "IF(" + normalMiddle + "=0,\"N/A\",(" + semanticMiddle + "-" + normalMiddle
                + ")/" + normalMiddle + "*100)";
    }

    private String middleAverageFormula(String testTypes, String type, int lastDataRow) {
        String normalFilter = "($B$2:$B$" + lastDataRow + "=\"" + type + "\")";
        String recalled = "($H$2:$H$" + lastDataRow + "+$I$2:$I$" + lastDataRow
                + "+$J$2:$J$" + lastDataRow + "+$K$2:$K$" + lastDataRow + ")";
        return "SUMPRODUCT(" + normalFilter + "*" + recalled + ")/COUNTIF(" + testTypes + ",\"" + type + "\")";
    }

    private String middleAverageLabelFormula(String normalAverage, String semanticAverage) {
        return "\"Normal: \"&TEXT(" + normalAverage + ",\"0.0\")&\"/4 (\"&TEXT("
                + normalAverage + "/4*100,\"0.0\")&\"%)\"&CHAR(10)&\"Semantic: \"&TEXT("
                + semanticAverage + ",\"0.0\")&\"/4 (\"&TEXT(" + semanticAverage
                + "/4*100,\"0.0\")&\"%)\"";
    }

    private static String middleRecallValue(int recalled) {
        return recalled + "/4 (" + String.format(Locale.ROOT, "%.1f", recalled * 100.0 / 4) + "%)";
    }

    private static int middleRecalled(TestResult test) {
        return (int) test.getPositions().stream()
                .filter(position -> position.getWordPosition() >= MIDDLE_START
                        && position.getWordPosition() <= MIDDLE_END)
                .filter(PositionRecall::isRecalled)
                .count();
    }

    private static TestResult test(Participant participant, TestType type) {
        return participant.getTests().stream()
                .filter(test -> test.getTestType() == type)
                .findFirst()
                .orElseThrow();
    }

    private void writeFormula(Row row, int column, String formula, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    private void writeHeaderCell(Row row, int column, String text, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }
}
