package org.sxk.dcnn.earlywarning.web;

import org.sxk.dcnn.earlywarning.common.Result;
import org.sxk.dcnn.earlywarning.config.AppProperties;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;
import org.sxk.dcnn.earlywarning.service.BearingPredictService;
import org.sxk.dcnn.earlywarning.service.OperationLogService;
import org.sxk.dcnn.earlywarning.service.PredictionRecordService;
import org.sxk.dcnn.earlywarning.service.StatsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PredictApiController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final BearingPredictService predictService;
    private final PredictionRecordService recordService;
    private final OperationLogService operationLogService;
    private final StatsService statsService;
    private final Path uploadDir;

    public PredictApiController(BearingPredictService predictService,
                                PredictionRecordService recordService,
                                OperationLogService operationLogService,
                                StatsService statsService,
                                AppProperties appProperties) {
        this.predictService = predictService;
        this.recordService = recordService;
        this.operationLogService = operationLogService;
        this.statsService = statsService;
        this.uploadDir = appProperties.getUploadDirPath();
    }

    @PostMapping("/predict")
    public Result<Map<String, Object>> predict(@RequestParam("file") MultipartFile file,
                                               HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.fail("文件名无效");
        }
        String lower = originalFilename.toLowerCase();
        if (!lower.endsWith(".mat") && !lower.endsWith(".txt") && !lower.endsWith(".csv")) {
            return Result.fail("仅支持 .mat / .txt / .csv 格式");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail("文件大小不能超过 10MB");
        }
        Long userId = (Long) request.getAttribute("userId");
        try {
            operationLogService.log("数据导入", "上传文件: " + originalFilename);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            Path saved = uploadDir.resolve(System.currentTimeMillis() + "_" + originalFilename);
            file.transferTo(saved.toFile());

            operationLogService.log("模型调用", "开始预测: " + originalFilename);
            Map<String, Object> result = predictService.predict(saved);
            String status = result.get("status") != null ? result.get("status").toString() : "error";
            operationLogService.log("模型调用", "预测完成: " + status);

            Map<String, Object> data = new HashMap<>(result);
            data.put("fileName", originalFilename);
            if ("success".equals(status)) {
                PredictionRecord record = recordService.saveFromPredictResult(
                    userId, originalFilename, saved.toAbsolutePath().toString(), result);
                statsService.evictChartsCache(userId);
                data.put("recordId", record.getId());
                String faultName = result.get("fault_name") != null ? result.get("fault_name").toString() : "";
                boolean warning = Boolean.TRUE.equals(result.get("warning"));
                data.put("alertMessage", warning
                    ? "检测到" + faultName + "，建议及时检修。"
                    : "检测结果正常。");
            }
            return Result.ok(data);
        } catch (Exception e) {
            operationLogService.log("接口请求", "预测异常: " + e.getMessage());
            return Result.fail("处理失败: " + e.getMessage());
        }
    }
}
