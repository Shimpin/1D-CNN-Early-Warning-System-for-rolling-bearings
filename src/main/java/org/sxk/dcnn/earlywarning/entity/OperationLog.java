package org.sxk.dcnn.earlywarning.entity;

import java.time.LocalDateTime;

/**
 * 操作日志（检测日志）
 */
public class OperationLog {
    private Long id;
    private LocalDateTime operationTime;
    private String operationType;  // 模型调用/数据导入/接口请求
    private String operationResult;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getOperationTime() { return operationTime; }
    public void setOperationTime(LocalDateTime operationTime) { this.operationTime = operationTime; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getOperationResult() { return operationResult; }
    public void setOperationResult(String operationResult) { this.operationResult = operationResult; }
}
