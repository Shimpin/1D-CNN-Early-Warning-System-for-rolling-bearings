package org.sxk.dcnn.earlywarning.entity;

import java.time.LocalDateTime;

/**
 * 轴承故障预测记录
 */
public class PredictionRecord {
    private Long id;
    private Long userId;
    private String fileName;
    private String filePath;
    private String faultName;
    private Integer loadHp;
    private Double faultSizeInch;
    private Double confidence;
    private Boolean isWarning;
    private String message;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFaultName() { return faultName; }
    public void setFaultName(String faultName) { this.faultName = faultName; }
    public Integer getLoadHp() { return loadHp; }
    public void setLoadHp(Integer loadHp) { this.loadHp = loadHp; }
    public Double getFaultSizeInch() { return faultSizeInch; }
    public void setFaultSizeInch(Double faultSizeInch) { this.faultSizeInch = faultSizeInch; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Boolean getIsWarning() { return isWarning; }
    public void setIsWarning(Boolean isWarning) { this.isWarning = isWarning; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
