package org.sxk.dcnn.earlywarning.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.sxk.dcnn.earlywarning.config.AppProperties;

@Component
public class WeeklyAiReportTaskInitializer {

    private static final Logger log = LoggerFactory.getLogger(WeeklyAiReportTaskInitializer.class);
    private static final String TASK_KEY = "weekly_ai_report_task";

    private final DynamicTaskService dynamicTaskService;
    private final WeeklyAiReportService weeklyAiReportService;
    private final AppProperties appProperties;

    public WeeklyAiReportTaskInitializer(DynamicTaskService dynamicTaskService,
                                         WeeklyAiReportService weeklyAiReportService,
                                         AppProperties appProperties) {
        this.dynamicTaskService = dynamicTaskService;
        this.weeklyAiReportService = weeklyAiReportService;
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        if (!Boolean.TRUE.equals(appProperties.getReportEnabled())) {
            log.info("AI 周报任务未启用");
            return;
        }
        String cron = appProperties.getReportCron() != null && !appProperties.getReportCron().isBlank()
            ? appProperties.getReportCron()
            : "0 */10 * * * ?";
        dynamicTaskService.startTask(TASK_KEY, cron, weeklyAiReportService::generateCurrentWeekReport);
    }

    @PreDestroy
    public void destroy() {
        dynamicTaskService.stopTask(TASK_KEY);
    }
}
