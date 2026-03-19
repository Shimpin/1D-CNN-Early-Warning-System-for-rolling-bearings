package org.sxk.dcnn.earlywarning.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.sxk.dcnn.earlywarning.entity.OperationLog;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class OperationLogRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<OperationLog> ROW_MAPPER = (rs, i) -> {
        OperationLog log = new OperationLog();
        log.setId(rs.getLong("id"));
        if (rs.getTimestamp("operation_time") != null) {
            log.setOperationTime(rs.getTimestamp("operation_time").toLocalDateTime());
        }
        log.setOperationType(rs.getString("operation_type"));
        log.setOperationResult(rs.getString("operation_result"));
        return log;
    };

    public OperationLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(OperationLog log) {
        jdbc.update(
            "INSERT INTO operation_log (operation_type, operation_result) VALUES (?, ?)",
            log.getOperationType(),
            log.getOperationResult()
        );
    }

    /** 仅展示最新 100 条 */
    public List<OperationLog> findLatest100() {
        return jdbc.query(
            "SELECT id, operation_time, operation_type, operation_result FROM operation_log ORDER BY operation_time DESC LIMIT 100",
            ROW_MAPPER
        );
    }

    public List<OperationLog> searchByTime(Timestamp startTime, Timestamp endTime, int offset, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, operation_time, operation_type, operation_result FROM operation_log WHERE 1=1"
        );
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (startTime != null) {
            sql.append(" AND operation_time >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND operation_time <= ?");
            params.add(endTime);
        }
        sql.append(" ORDER BY operation_time DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long countByTime(Timestamp startTime, Timestamp endTime) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM operation_log WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (startTime != null) {
            sql.append(" AND operation_time >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND operation_time <= ?");
            params.add(endTime);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return count == null ? 0L : count;
    }
}
