package org.sxk.dcnn.earlywarning.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PredictionRecordRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<PredictionRecord> ROW_MAPPER = (rs, i) -> {
        PredictionRecord r = new PredictionRecord();
        r.setId(rs.getLong("id"));
        r.setUserId(rs.getObject("user_id") != null ? rs.getLong("user_id") : null);
        r.setFileName(rs.getString("file_name"));
        r.setFilePath(rs.getString("file_path"));
        r.setFaultName(rs.getString("fault_name"));
        r.setLoadHp(rs.getObject("load_hp") != null ? rs.getInt("load_hp") : null);
        r.setFaultSizeInch(rs.getObject("fault_size_inch") != null ? rs.getDouble("fault_size_inch") : null);
        r.setConfidence(rs.getObject("confidence") != null ? rs.getDouble("confidence") : null);
        r.setIsWarning(rs.getObject("is_warning") != null && rs.getBoolean("is_warning"));
        r.setMessage(rs.getString("message"));
        if (rs.getTimestamp("created_at") != null) {
            r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return r;
    };

    public PredictionRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PredictionRecord save(PredictionRecord record) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO prediction_record (user_id, file_name, file_path, fault_name, load_hp, fault_size_inch, confidence, is_warning, message) VALUES (?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setObject(1, record.getUserId());
            ps.setString(2, record.getFileName());
            ps.setString(3, record.getFilePath());
            ps.setString(4, record.getFaultName());
            ps.setObject(5, record.getLoadHp());
            ps.setObject(6, record.getFaultSizeInch());
            ps.setObject(7, record.getConfidence());
            ps.setBoolean(8, Boolean.TRUE.equals(record.getIsWarning()));
            ps.setString(9, record.getMessage());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) record.setId(key.longValue());
        return record;
    }

    public List<PredictionRecord> findAllOrderByCreatedAtDesc() {
        return jdbc.query("SELECT * FROM prediction_record ORDER BY created_at DESC", ROW_MAPPER);
    }

    public List<PredictionRecord> findByUserIdOrderByCreatedAtDesc(Long userId) {
        if (userId == null) return findAllOrderByCreatedAtDesc();
        return jdbc.query("SELECT * FROM prediction_record WHERE user_id = ? ORDER BY created_at DESC", ROW_MAPPER, userId);
    }

    public List<PredictionRecord> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime start, LocalDateTime end) {
        return jdbc.query(
            "SELECT * FROM prediction_record WHERE created_at >= ? AND created_at <= ? ORDER BY created_at ASC",
            ROW_MAPPER,
            start,
            end
        );
    }

    public PredictionRecord findById(Long id) {
        List<PredictionRecord> list = jdbc.query("SELECT * FROM prediction_record WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }
}
