package org.sxk.dcnn.earlywarning.service;

import org.springframework.stereotype.Service;
import org.sxk.dcnn.earlywarning.entity.OperationLog;
import org.sxk.dcnn.earlywarning.dao.OperationLogRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OperationLogService {

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    public void log(String operationType, String operationResult) {
        OperationLog log = new OperationLog();
        log.setOperationType(operationType);
        log.setOperationResult(operationResult);
        repository.save(log);
    }

    public List<OperationLog> listLatest100() {
        return repository.findLatest100();
    }

    public Map<String, Object> searchByTime(LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        Timestamp startTs = startTime == null ? null : Timestamp.valueOf(startTime);
        Timestamp endTs = endTime == null ? null : Timestamp.valueOf(endTime);
        List<OperationLog> rows = repository.searchByTime(startTs, endTs, offset, safeSize);
        long total = repository.countByTime(startTs, endTs);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }
}
