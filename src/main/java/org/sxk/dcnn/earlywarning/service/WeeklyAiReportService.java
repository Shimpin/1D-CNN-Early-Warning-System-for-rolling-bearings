package org.sxk.dcnn.earlywarning.service;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.sxk.dcnn.earlywarning.config.AppProperties;
import org.sxk.dcnn.earlywarning.entity.PredictionRecord;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class WeeklyAiReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyAiReportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PredictionRecordService predictionRecordService;
    private final LlmChatService llmChatService;
    private final AppProperties appProperties;

    public WeeklyAiReportService(PredictionRecordService predictionRecordService,
                                 LlmChatService llmChatService,
                                 AppProperties appProperties) {
        this.predictionRecordService = predictionRecordService;
        this.llmChatService = llmChatService;
        this.appProperties = appProperties;
    }

    /**
     * 生成“本周故障预测分析报告”。
     * 测试环境按配置每 10 分钟执行一次，因此同一周内会覆盖同名文件。
     */
    public void generateCurrentWeekReport() {
        LocalDate today = LocalDate.now();
        LocalDate weekStartDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime start = weekStartDate.atStartOfDay();
        LocalDateTime end = LocalDateTime.of(today, LocalTime.MAX.withNano(0));

        List<PredictionRecord> records = predictionRecordService.listBetween(start, end);
        String periodText = DATE_FMT.format(weekStartDate) + " 至 " + DATE_FMT.format(today);
        String prompt = buildPrompt(periodText, records);
        String aiResult = llmChatService.chat(prompt);

        try {
            Path reportDir = appProperties.getReportDirPath();
            Files.createDirectories(reportDir);
            Path reportFile = reportDir.resolve(periodText.replace(" 至 ", "_至_") + ".docx");
            writeWordReport(reportFile, periodText, records, aiResult);
            log.info("AI 周报已生成: {}", reportFile);
        } catch (Exception e) {
            log.error("生成 AI 周报失败: {}", e.getMessage(), e);
        }
    }

    private String buildPrompt(String periodText, List<PredictionRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是").append(periodText).append("的滚动轴承故障预警数据，请根据数据进行分析。").append("\n");
        sb.append("数据列表如下：").append("\n");
        sb.append("[").append("\n");
        if (records.isEmpty()) {
            sb.append("  {").append("\"说明\": \"本周暂无故障预测数据\"").append("}").append("\n");
        } else {
            for (PredictionRecord record : records) {
                sb.append("  {")
                    .append("\"预测时间\": \"").append(formatDateTime(record.getCreatedAt())).append("\", ")
                    .append("\"用户ID\": ").append(record.getUserId() == null ? "null" : record.getUserId()).append(", ")
                    .append("\"文件名\": \"").append(safe(record.getFileName())).append("\", ")
                    .append("\"预测结果\": \"").append(safe(record.getFaultName())).append("\", ")
                    .append("\"负载HP\": ").append(record.getLoadHp() == null ? "null" : record.getLoadHp()).append(", ")
                    .append("\"故障尺寸英寸\": ").append(record.getFaultSizeInch() == null ? "null" : record.getFaultSizeInch()).append(", ")
                    .append("\"置信度\": ").append(record.getConfidence() == null ? "null" : record.getConfidence()).append(", ")
                    .append("\"是否预警\": \"").append(Boolean.TRUE.equals(record.getIsWarning()) ? "是" : "否").append("\", ")
                    .append("\"备注\": \"").append(safe(record.getMessage())).append("\"")
                    .append("}")
                    .append("\n");
            }
        }
        sb.append("]").append("\n\n");
        sb.append("请你按照以上滚动轴承故障预警数据做出分析总结，并提出改进建议。");
        return sb.toString();
    }

    private void writeWordReport(Path reportFile, String periodText, List<PredictionRecord> records, String aiResult) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             OutputStream out = Files.newOutputStream(reportFile)) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText("滚动轴承故障预警周分析报告");

            XWPFParagraph meta = document.createParagraph();
            XWPFRun metaRun = meta.createRun();
            metaRun.setText("统计周期：" + periodText);
            metaRun.addBreak();
            metaRun.setText("生成时间：" + DATE_TIME_FMT.format(LocalDateTime.now()));
            metaRun.addBreak();
            metaRun.setText("记录数量：" + records.size());

            XWPFParagraph summaryTitle = document.createParagraph();
            XWPFRun summaryTitleRun = summaryTitle.createRun();
            summaryTitleRun.setBold(true);
            summaryTitleRun.setText("AI 分析总结");

            XWPFParagraph summary = document.createParagraph();
            XWPFRun summaryRun = summary.createRun();
            summaryRun.setText(aiResult == null || aiResult.isBlank() ? "AI 未返回有效分析结果。" : aiResult);

            XWPFParagraph appendixTitle = document.createParagraph();
            XWPFRun appendixTitleRun = appendixTitle.createRun();
            appendixTitleRun.setBold(true);
            appendixTitleRun.setText("原始数据附录");

            if (records.isEmpty()) {
                XWPFParagraph empty = document.createParagraph();
                empty.createRun().setText("本周期内暂无故障预测数据。");
            } else {
                for (PredictionRecord record : records) {
                    XWPFParagraph item = document.createParagraph();
                    XWPFRun run = item.createRun();
                    run.setText(
                        "时间=" + formatDateTime(record.getCreatedAt())
                            + "，文件=" + safe(record.getFileName())
                            + "，结果=" + safe(record.getFaultName())
                            + "，置信度=" + (record.getConfidence() == null ? "-" : record.getConfidence())
                            + "，预警=" + (Boolean.TRUE.equals(record.getIsWarning()) ? "是" : "否")
                            + "，备注=" + safe(record.getMessage())
                    );
                }
            }

            document.write(out);
        }
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "" : DATE_TIME_FMT.format(time);
    }

    private String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "\\\"");
    }
}
