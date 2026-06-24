package org.example.api_playground.llm.repository;

import org.example.api_playground.llm.model.ApiCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiCallRecordRepository extends JpaRepository<ApiCallRecord, Long> {

    List<ApiCallRecord> findAllByOrderByCreatedAtDesc();

    List<ApiCallRecord> findByModelOrderByCreatedAtDesc(String model);
}
