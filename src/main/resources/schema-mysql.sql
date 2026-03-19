-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 预测记录表（增加 user_id 关联当前用户）
CREATE TABLE IF NOT EXISTS prediction_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1024),
    fault_name VARCHAR(128),
    load_hp INT,
    fault_size_inch DOUBLE,
    confidence DOUBLE,
    is_warning BOOLEAN,
    message VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 操作日志表（检测日志）
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    operation_type VARCHAR(32) NOT NULL,
    operation_result VARCHAR(256)
);

-- 若已有 prediction_record 表无 user_id，可执行：ALTER TABLE prediction_record ADD COLUMN user_id BIGINT;
