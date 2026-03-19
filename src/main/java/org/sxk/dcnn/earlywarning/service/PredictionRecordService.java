package org.sxk.dcnn.earlywarning.service;

import org.springframework.stereotype.Service;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;
import org.sxk.dcnn.earlywarning.dao.PredictionRecordRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PredictionRecordService {

    private final PredictionRecordRepository repository;

    public PredictionRecordService(PredictionRecordRepository repository) {
        this.repository = repository;
    }

    public PredictionRecord saveFromPredictResult(Long userId, String fileName, String filePath, Map<String, Object> predictResult) {
        PredictionRecord r = new PredictionRecord();
        r.setUserId(userId);
        r.setFileName(fileName);
        r.setFilePath(filePath);
        r.setFaultName(predictResult.get("fault_name") != null ? predictResult.get("fault_name").toString() : null);
        r.setLoadHp(predictResult.get("load_hp") instanceof Number ? ((Number) predictResult.get("load_hp")).intValue() : null);
        r.setFaultSizeInch(predictResult.get("fault_size_inch") instanceof Number ? ((Number) predictResult.get("fault_size_inch")).doubleValue() : null);
        r.setConfidence(predictResult.get("confidence") instanceof Number ? ((Number) predictResult.get("confidence")).doubleValue() : null);
        r.setIsWarning(Boolean.TRUE.equals(predictResult.get("warning")));
        r.setMessage(predictResult.get("message") != null ? predictResult.get("message").toString() : null);
        return repository.save(r);
    }

    public List<PredictionRecord> listAll() {
        return repository.findAllOrderByCreatedAtDesc();
    }

    public List<PredictionRecord> listByUserId(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<PredictionRecord> listBetween(LocalDateTime start, LocalDateTime end) {
        return repository.findByCreatedAtBetweenOrderByCreatedAtAsc(start, end);
    }

    public PredictionRecord getById(Long id) {
        return repository.findById(id);
    }
}
