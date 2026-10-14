package com.example.serialposition.controller;

import com.example.serialposition.dto.ExperimentPlanDto;
import com.example.serialposition.dto.SessionResultDto;
import com.example.serialposition.dto.SessionSubmissionDto;
import com.example.serialposition.service.ExperimentPlanService;
import com.example.serialposition.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiment")
public class ExperimentController {

    private final ExperimentPlanService planService;
    private final SessionService sessionService;

    public ExperimentController(ExperimentPlanService planService, SessionService sessionService) {
        this.planService = planService;
        this.sessionService = sessionService;
    }

    /** Word lists for a new session, in presentation order. */
    @GetMapping("/plan")
    public ExperimentPlanDto plan() {
        return planService.createPlan();
    }

    /** Scores and stores a completed session. */
    @PostMapping("/sessions")
    public ResponseEntity<SessionResultDto> recordSession(@Valid @RequestBody SessionSubmissionDto submission) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.record(submission));
    }
}
