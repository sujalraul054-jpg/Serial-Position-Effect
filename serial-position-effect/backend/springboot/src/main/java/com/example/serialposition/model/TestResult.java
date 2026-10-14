package com.example.serialposition.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * One test (normal or semantic) taken by a participant. Word lists are stored
 * as comma-separated strings; the per-position outcome is in {@link PositionRecall}.
 */
@Entity
@Table(name = "test_result")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 16)
    private TestType testType;

    /** 1 for the test presented first, 2 for the test presented second. */
    @Column(name = "test_sequence", nullable = false)
    private int testSequence;

    @Column(name = "original_words", nullable = false, length = 500)
    private String originalWords;

    @Column(name = "recalled_words", nullable = false, length = 10000)
    private String recalledWords;

    @Column(name = "total_recalled", nullable = false)
    private int totalRecalled;

    @OneToMany(mappedBy = "testResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("wordPosition ASC")
    private List<PositionRecall> positions = new ArrayList<>();

    public void addPosition(PositionRecall position) {
        positions.add(position);
        position.setTestResult(this);
    }

    public Long getId() {
        return id;
    }

    public Participant getParticipant() {
        return participant;
    }

    public void setParticipant(Participant participant) {
        this.participant = participant;
    }

    public TestType getTestType() {
        return testType;
    }

    public void setTestType(TestType testType) {
        this.testType = testType;
    }

    public int getTestSequence() {
        return testSequence;
    }

    public void setTestSequence(int testSequence) {
        this.testSequence = testSequence;
    }

    public String getOriginalWords() {
        return originalWords;
    }

    public void setOriginalWords(String originalWords) {
        this.originalWords = originalWords;
    }

    public String getRecalledWords() {
        return recalledWords;
    }

    public void setRecalledWords(String recalledWords) {
        this.recalledWords = recalledWords;
    }

    public int getTotalRecalled() {
        return totalRecalled;
    }

    public void setTotalRecalled(int totalRecalled) {
        this.totalRecalled = totalRecalled;
    }

    public List<PositionRecall> getPositions() {
        return positions;
    }
}
