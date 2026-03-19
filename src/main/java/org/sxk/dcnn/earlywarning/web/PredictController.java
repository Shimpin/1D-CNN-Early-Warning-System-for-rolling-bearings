package org.sxk.dcnn.earlywarning.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.sxk.dcnn.earlywarning.config.AppProperties;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;
import org.sxk.dcnn.earlywarning.service.BearingPredictService;
import org.sxk.dcnn.earlywarning.service.OperationLogService;
import org.sxk.dcnn.earlywarning.service.PredictionRecordService;
import org.sxk.dcnn.earlywarning.service.StatsService;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Controller
@RequestMapping("/predict")
public class PredictController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final BearingPredictService predictService;
    private final PredictionRecordService recordService;
    private final OperationLogService operationLogService;
    private final StatsService statsService;
    private final Path uploadDir;

    public PredictController(BearingPredictService predictService,
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

    @GetMapping
    public String uploadPage() {
        return "predict";
    }

    @PostMapping
    public String predict(@RequestParam("file") MultipartFile file, Model model, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "请选择要上传的文件");
            return "predict";
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            model.addAttribute("error", "文件名无效");
            return "predict";
        }
        String lower = originalFilename.toLowerCase();
        if (!lower.endsWith(".mat") && !lower.endsWith(".txt") && !lower.endsWith(".csv")) {
            model.addAttribute("error", "仅支持 .mat / .txt / .csv 格式");
            return "predict";
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            model.addAttribute("error", "文件大小不能超过 10MB");
            return "predict";
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
            operationLogService.log("模型调用", "预测完成: " + result.get("status"));

            model.addAttribute("result", result);
            model.addAttribute("fileName", originalFilename);

            if ("success".equals(result.get("status"))) {
                PredictionRecord record = recordService.saveFromPredictResult(
                    userId, originalFilename, saved.toAbsolutePath().toString(), result);
                statsService.evictChartsCache(userId);
                model.addAttribute("recordId", record.getId());
            }
            return "result";
        } catch (Exception e) {
            operationLogService.log("接口请求", "预测异常: " + e.getMessage());
            model.addAttribute("error", "处理失败: " + e.getMessage());
            return "predict";
        }
    }
}
