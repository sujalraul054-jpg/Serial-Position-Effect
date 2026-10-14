package com.example.serialposition.repository;

import com.example.serialposition.model.PositionRecall;
import com.example.serialposition.model.TestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRecallRepository extends JpaRepository<PositionRecall, Long> {

    List<PositionRecall> findByTestResult_TestType(TestType testType);
}
