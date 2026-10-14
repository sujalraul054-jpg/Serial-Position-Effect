package com.example.serialposition.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Whether the word at one serial position (1-10) of a test was recalled.
 */
@Entity
@Table(name = "position_recall")
public class PositionRecall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_result_id", nullable = false)
    private TestResult testResult;

    @Column(name = "word_position", nullable = false)
    private int wordPosition;

    @Column(name = "word", nullable = false, length = 64)
    private String word;

    @Column(name = "recalled", nullable = false)
    private boolean recalled;

    protected PositionRecall() {
    }

    public PositionRecall(int wordPosition, String word, boolean recalled) {
        this.wordPosition = wordPosition;
        this.word = word;
        this.recalled = recalled;
    }

    public Long getId() {
        return id;
    }

    public TestResult getTestResult() {
        return testResult;
    }

    public void setTestResult(TestResult testResult) {
        this.testResult = testResult;
    }

    public int getWordPosition() {
        return wordPosition;
    }

    public String getWord() {
        return word;
    }

    public boolean isRecalled() {
        return recalled;
    }
}
