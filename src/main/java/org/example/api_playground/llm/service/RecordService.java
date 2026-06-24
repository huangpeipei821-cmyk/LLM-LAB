package org.example.api_playground.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api_playground.llm.model.ApiCallRecord;
import org.example.api_playground.llm.repository.ApiCallRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private final ApiCallRecordRepository repository;

    @Transactional
    public void save(ApiCallRecord record) {
        repository.save(record);
        log.info("调用记录已保存: model={}, tokens={}, duration={}ms", record.getModel(), record.getTotalTokens(), record.getDurationMs());
    }

    @Transactional(readOnly = true)
    public List<ApiCallRecord> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
