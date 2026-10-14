package com.example.serialposition.controller;

import com.example.serialposition.dto.AggregateDto;
import com.example.serialposition.dto.DeletedSessionDto;
import com.example.serialposition.service.AggregationService;
import com.example.serialposition.service.SessionService;
import com.example.serialposition.service.SpreadsheetExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/experiment")
public class ResultsController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SessionService sessionService;
    private final AggregationService aggregationService;
    private final SpreadsheetExportService exportService;

    public ResultsController(SessionService sessionService,
                             AggregationService aggregationService,
                             SpreadsheetExportService exportService) {
        this.sessionService = sessionService;
        this.aggregationService = aggregationService;
        this.exportService = exportService;
    }

    /** Deletes the most recent session from the database, and so from the spreadsheet and graph. */
    @DeleteMapping("/sessions/latest")
    public DeletedSessionDto deleteLatestSession() {
        return new DeletedSessionDto(sessionService.deleteLatest());
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> clearSessions() {
        sessionService.clearAll();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/aggregate")
    public AggregateDto aggregate() {
        return aggregationService.aggregate();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() throws IOException {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("memory-experiment-results.xlsx")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(XLSX)
                .body(exportService.export());
    }
}
