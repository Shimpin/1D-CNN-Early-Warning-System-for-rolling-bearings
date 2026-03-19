package org.sxk.dcnn.earlywarning.web;

import org.sxk.dcnn.earlywarning.common.Result;
import org.sxk.dcnn.earlywarning.service.LlmChatService;
import org.sxk.dcnn.earlywarning.service.OperationLogService;
import org.sxk.dcnn.earlywarning.service.PredictionRecordService;
import org.sxk.dcnn.earlywarning.service.StatsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 供前端调用的 API：统一返回 Result 格式。
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final ExecutorService SSE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-lm-stream");
        t.setDaemon(true);
        return t;
    });

    private final StatsService statsService;
    private final PredictionRecordService recordService;
    private final OperationLogService operationLogService;
    private final LlmChatService llmChatService;

    public ApiController(StatsService statsService, PredictionRecordService recordService,
                         OperationLogService operationLogService, LlmChatService llmChatService) {
        this.statsService = statsService;
        this.recordService = recordService;
        this.operationLogService = operationLogService;
        this.llmChatService = llmChatService;
    }

    /** 首页图表数据（故障占比饼图、近期预测折线图、故障预警柱状图） */
    @GetMapping("/stats/charts")
    public Result<Map<String, Object>> charts(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = statsService.getChartsData(userId);
        return Result.ok(data);
    }

    /** 预测历史列表：预测时间、数据文件名称、预测结果、预警状态 */
    @GetMapping("/history/list")
    public Result<List<Map<String, Object>>> historyList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> list = recordService.listByUserId(userId).stream()
            .map(r -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("predictTime", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                m.put("fileName", r.getFileName());
                m.put("predictResult", r.getFaultName());
                m.put("warningStatus", Boolean.TRUE.equals(r.getIsWarning()) ? "故障预警" : "正常");
                return m;
            })
            .collect(Collectors.toList());
        return Result.ok(list);
    }

    /**
     * 检测日志：按时间搜索 + 分页。
     * 参数：
     * - startTime/endTime: yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss
     * - page: 页码（从1开始）
     * - size: 每页条数（默认20）
     */
    @GetMapping("/tools/logs")
    public Result<Map<String, Object>> logs(
        @RequestParam(required = false) String startTime,
        @RequestParam(required = false) String endTime,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        LocalDateTime start = parseDateTime(startTime, true);
        LocalDateTime end = parseDateTime(endTime, false);
        Map<String, Object> paged = operationLogService.searchByTime(start, end, page, size);
        @SuppressWarnings("unchecked")
        List<org.sxk.dcnn.earlywarning.entity.OperationLog> rows =
            (List<org.sxk.dcnn.earlywarning.entity.OperationLog>) paged.get("rows");
        List<Map<String, Object>> list = rows.stream().map(log -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("operationTime", log.getOperationTime() != null ? log.getOperationTime().toString() : null);
            m.put("operationType", log.getOperationType());
            m.put("operationResult", log.getOperationResult());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("rows", list);
        data.put("total", paged.get("total"));
        data.put("page", paged.get("page"));
        data.put("size", paged.get("size"));
        return Result.ok(data);
    }

    /** AI 辅助问答（非流式，保留兼容） */
    @PostMapping("/tools/faq")
    public Result<String> faq(@RequestParam String question) {
        String answer = llmChatService.chat(question);
        return Result.ok(answer);
    }

    /** AI 流式问答：SSE，每条 data 为 {"content":"片段"}，前端边收边显 */
    @PostMapping(value = "/tools/faq/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter faqStream(@RequestParam String question) {
        SseEmitter emitter = new SseEmitter(300_000L);
        SSE_EXECUTOR.execute(() -> {
            try {
                llmChatService.streamChat(question, delta ->
                    sendSse(emitter, Map.of("content", delta))
                );
                emitter.complete();
            } catch (Exception e) {
                sendSse(emitter, Map.of("error", e.getMessage() != null ? e.getMessage() : "请求失败"));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private static void sendSse(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private LocalDateTime parseDateTime(String val, boolean startOfDay) {
        if (val == null || val.isBlank()) return null;
        String text = val.trim();
        try {
            if (text.length() == 10) {
                LocalDate d = LocalDate.parse(text);
                return startOfDay ? d.atStartOfDay() : d.atTime(LocalTime.MAX.withNano(0));
            }
            return LocalDateTime.parse(text.replace(" ", "T"));
        } catch (Exception _) {
            return null;
        }
    }
}
