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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Builds the results workbook.
 *
 * Sheet "Results": one row per test (two per participant) with exactly 14 columns:
 * participant ID, test type, test order, total recalled, and positions 1-10
 * (1 = recalled, 0 = not recalled).
 *
 * Sheet "Recall Improvement": one row per participant with the normal and semantic
 * recall and the recall improvement as a live Excel formula, plus the mean row.
 */
@Service
public class SpreadsheetExportService {

    private static final int FIXED_COLUMNS = 4;
    private static final String IMPROVEMENT_NOTE =
            "Recall improvement (%) = ((Semantic recall - Normal recall) / Normal recall) \u00d7 100. "
                    + "It is n/a when Normal recall is 0.";

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
            writeImprovementSheet(workbook, headerStyle, participants);
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

        int rowIndex = 1;
        for (Participant participant : participants) {
            String testOrder = participant.getFirstTestType().getLabel() + " first";
            for (TestResult test : participant.getTests()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(participant.getParticipantCode());
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
            }
        }

        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 15 * 256);
        for (int i = 0; i < WordBank.LIST_SIZE; i++) {
            sheet.setColumnWidth(FIXED_COLUMNS + i, 12 * 256);
        }
    }

    private void writeImprovementSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<Participant> participants) {
        Sheet sheet = workbook.createSheet("Recall Improvement");

        String[] headers = {"Participant ID", "Normal Recall", "Semantic Recall", "Recall Improvement (%)"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            writeHeaderCell(header, i, headers[i], headerStyle);
        }

        CellStyle oneDecimal = workbook.createCellStyle();
        oneDecimal.setDataFormat(workbook.createDataFormat().getFormat("0.0"));

        // Spreadsheet row numbers are 1-based; data starts on row 2.
        int rowIndex = 1;
        for (Participant participant : participants) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(participant.getParticipantCode());
            row.createCell(1).setCellValue(recalled(participant, TestType.NORMAL));
            row.createCell(2).setCellValue(recalled(participant, TestType.SEMANTIC));
            writeImprovementFormula(row, rowIndex + 1, oneDecimal);
            rowIndex++;
        }

        int lastRowIndex = rowIndex - 1;
        if (!participants.isEmpty()) {
            int firstDataRow = 2;
            int lastDataRow = participants.size() + 1;
            int meanRowIndex = lastRowIndex + 2;
            Row mean = sheet.createRow(meanRowIndex);
            writeHeaderCell(mean, 0, "Mean of all participants", headerStyle);
            writeFormula(mean, 1, "AVERAGE(B" + firstDataRow + ":B" + lastDataRow + ")", oneDecimal);
            writeFormula(mean, 2, "AVERAGE(C" + firstDataRow + ":C" + lastDataRow + ")", oneDecimal);
            writeImprovementFormula(mean, meanRowIndex + 1, oneDecimal);
            lastRowIndex = meanRowIndex;
        }

        sheet.createRow(lastRowIndex + 2).createCell(0).setCellValue(IMPROVEMENT_NOTE);

        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 15 * 256);
        sheet.setColumnWidth(2, 17 * 256);
        sheet.setColumnWidth(3, 24 * 256);
    }

    /** Column D: ((C - B) / B) * 100, or "n/a" when B (normal recall) is 0. */
    private void writeImprovementFormula(Row row, int spreadsheetRow, CellStyle style) {
        String formula = "IF(B" + spreadsheetRow + "=0,\"n/a\",(C" + spreadsheetRow + "-B" + spreadsheetRow
                + ")/B" + spreadsheetRow + "*100)";
        writeFormula(row, 3, formula, style);
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

    private static int recalled(Participant participant, TestType type) {
        return participant.getTests().stream()
                .filter(test -> test.getTestType() == type)
                .mapToInt(TestResult::getTotalRecalled)
                .findFirst()
                .orElse(0);
    }
}
