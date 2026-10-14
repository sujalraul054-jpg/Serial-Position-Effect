package com.example.serialposition.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One participant completes one session: a normal-list test and a semantic-list
 * test, in the order recorded in {@code firstTestType}.
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_number", nullable = false, unique = true)
    private int participantNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_test_type", nullable = false, length = 16)
    private TestType firstTestType;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("testSequence ASC")
    private List<TestResult> tests = new ArrayList<>();

    public void addTest(TestResult test) {
        tests.add(test);
        test.setParticipant(this);
    }

    public String getParticipantCode() {
        return String.format("P%03d", participantNumber);
    }

    public Long getId() {
        return id;
    }

    public int getParticipantNumber() {
        return participantNumber;
    }

    public void setParticipantNumber(int participantNumber) {
        this.participantNumber = participantNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public TestType getFirstTestType() {
        return firstTestType;
    }

    public void setFirstTestType(TestType firstTestType) {
        this.firstTestType = firstTestType;
    }

    public List<TestResult> getTests() {
        return tests;
    }
}
